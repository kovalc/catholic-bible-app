import os
import json
import random
import base64
import logging
from datetime import datetime, date
from urllib import request as urlrequest
from urllib.error import URLError, HTTPError
from decimal import Decimal

import boto3
from zoneinfo import ZoneInfo
from boto3.dynamodb.conditions import Key

logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamodb = boto3.resource("dynamodb")
secrets_client = boto3.client("secretsmanager")
s3_client = boto3.client("s3")

BIBLE_TABLE_NAME = os.environ["BIBLE_TABLE_NAME"]
VERSE_OF_DAY_TABLE_NAME = os.environ["VERSE_OF_DAY_TABLE_NAME"]
IMAGE_BUCKET_NAME = os.environ["IMAGE_BUCKET_NAME"]

TOTAL_VERSES = int(os.environ.get("TOTAL_VERSES", "31435"))

OPENAI_SECRET_NAME = os.environ.get("OPENAI_SECRET_NAME", "openai-api-key")
APP_TZ = os.environ.get("APP_TIMEZONE", "America/Chicago")

bible_table = dynamodb.Table(BIBLE_TABLE_NAME)
votd_table = dynamodb.Table(VERSE_OF_DAY_TABLE_NAME)


def decimal_to_native(obj):
    """Convert DynamoDB Decimals (and nested lists/dicts) into plain int/float."""
    if isinstance(obj, list):
        return [decimal_to_native(i) for i in obj]
    if isinstance(obj, dict):
        return {k: decimal_to_native(v) for k, v in obj.items()}
    if isinstance(obj, Decimal):
        return int(obj) if obj % 1 == 0 else float(obj)
    return obj


def get_openai_api_key() -> str:
    """Read the OpenAI API key from AWS Secrets Manager."""
    try:
        logger.info(f"Loading OpenAI API key from Secrets Manager: {OPENAI_SECRET_NAME}")
        resp = secrets_client.get_secret_value(SecretId=OPENAI_SECRET_NAME)
        secret = resp.get("SecretString")
        if not secret:
            raise RuntimeError("SecretString is empty")
        return secret
    except Exception as e:
        logger.error(f"Failed to load OpenAI API key from Secrets Manager: {e}")
        raise


def get_random_verse() -> dict:
    """
    Pick a random verse using the canonical-index GSI (fast, no scan).
    Requires DynamoDB GSI: canonical-index-gsi with hash key canonical_index (N).
    """
    idx = random.randint(1, TOTAL_VERSES)

    resp = bible_table.query(
        IndexName="canonical-index-gsi",
        KeyConditionExpression=Key("canonical_index").eq(idx),
        Limit=1,
    )

    items = resp.get("Items", [])
    if not items:
        raise RuntimeError(f"No verse found for canonical_index={idx}")

    verse = items[0]
    logger.info(
        "Selected verse: verse_id=%s %s %s:%s (canonical_index=%s)",
        verse.get("verse_id"),
        verse.get("book"),
        verse.get("chapter"),
        verse.get("verse"),
        verse.get("canonical_index"),
    )
    return verse


def build_response(item: dict) -> dict:
    safe_item = decimal_to_native(item)
    return {
        "statusCode": 200,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
        },
        "body": json.dumps(safe_item),
    }


def _unsplash_fallback_url(verse: dict) -> str:
    """Fallback Unsplash image if AI generation fails."""
    text = verse.get("text", "").lower()
    book = verse.get("book", "").lower()

    if any(w in text for w in ["love", "charity", "merciful", "kindness"]):
        theme = "love"
    elif any(w in text for w in ["fear", "enemy", "battle", "war", "fight"]):
        theme = "armor+of+god"
    elif any(w in text for w in ["peace", "rest", "quiet", "still"]):
        theme = "peace"
    elif any(w in text for w in ["light", "lamp", "sun", "shine", "glory"]):
        theme = "light"
    elif any(w in text for w in ["night", "darkness", "shadow"]):
        theme = "night"
    elif book in ("psalms", "psalm"):
        theme = "mountains+sunrise"
    else:
        theme = "bible+cross"

    return f"https://source.unsplash.com/featured/?{theme}"


def _generate_ai_image_and_upload(verse: dict) -> str:
    """
    Call OpenAI's image generation API using HTTP (stdlib only),
    upload the resulting PNG to S3, and return the public URL.
    """
    api_key = get_openai_api_key()

    verse_text = verse.get("text", "")
    book = verse.get("book", "Unknown")
    chapter = verse.get("chapter", "")
    verse_num = verse.get("verse", "")

    prompt = (
        "Create a beautiful Catholic devotional illustration inspired by this Bible verse. "
        "Style: gentle, reverent, warm light, suitable for a daily prayer app wallpaper. "
        "No text in the image itself.\n\n"
        f"Verse: {book} {chapter}:{verse_num}\n"
        f"Text: {verse_text}"
    )

    body = {
        "model": "gpt-image-1",
        "prompt": prompt,
        "size": "1024x1024",
        "n": 1
    }

    req = urlrequest.Request(
        "https://api.openai.com/v1/images/generations",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    raw = ""
    try:
        # generous timeout; Lambda timeout is 120s
        with urlrequest.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
    except HTTPError as e:
        error_body = ""
        try:
            error_body = e.read().decode("utf-8", errors="ignore")
        except Exception:
            pass
        logger.error(
            "OpenAI HTTPError %s %s. Response body: %s",
            e.code,
            e.reason,
            error_body[:1000],
        )
        raise
    except Exception as e:
        logger.error("Unexpected error calling OpenAI image API: %s", e)
        raise

    try:
        data = json.loads(raw)
        b64_data = data["data"][0]["b64_json"]
    except Exception as e:
        logger.error("Failed parsing OpenAI image response: %s | raw=%s", e, raw[:1000])
        raise

    image_bytes = base64.b64decode(b64_data)

    today_str = date.today().isoformat()
    safe_book = str(book).replace(" ", "_")
    s3_key = f"{safe_book}_{chapter}_{verse_num}_{today_str}.png"

    logger.info("Uploading AI-generated image to s3://%s/%s", IMAGE_BUCKET_NAME, s3_key)

    s3_client.put_object(
        Bucket=IMAGE_BUCKET_NAME,
        Key=s3_key,
        Body=image_bytes,
        ContentType="image/png",
    )

    return f"https://{IMAGE_BUCKET_NAME}.s3.amazonaws.com/{s3_key}"


def pick_image_url(verse: dict) -> str:
    """
    First try AI-generated image via OpenAI; if that fails,
    fall back to a simple Unsplash URL so the app always gets something.
    """
    try:
        return _generate_ai_image_and_upload(verse)
    except Exception as e:
        logger.exception("AI image generation failed, using fallback image: %s", e)
        return _unsplash_fallback_url(verse)


def handler(event, context):
    # Use app timezone for "today"
    now = datetime.now(ZoneInfo(APP_TZ))
    today_str = now.date().isoformat()
    logger.info("Generating verse for %s (timezone %s)", today_str, APP_TZ)

    # 1) Check if we already picked a verse for today
    existing = votd_table.get_item(Key={"date": today_str})
    if "Item" in existing:
        logger.info("Verse for today already exists; returning existing item.")
        return build_response(existing["Item"])

    # 2) Pick a random verse from the whole table
    verse = get_random_verse()

    # 3) Choose / generate an image URL based on the verse
    image_url = pick_image_url(verse)

    # 4) Build the item we store in VerseOfTheDay
    item = {
        "date": today_str,
        "verse_id": verse["verse_id"],
        "book": verse["book"],
        "chapter": int(verse["chapter"]),
        "verse": int(verse["verse"]),
        "text": verse["text"],
        "canonical_index": int(verse["canonical_index"]),
        "image_url": image_url,
    }

    votd_table.put_item(Item=item)
    logger.info("Stored VerseOfTheDay item for %s", today_str)

    return build_response(item)