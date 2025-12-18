import os
import json
import logging
from datetime import datetime
from decimal import Decimal

import boto3
from zoneinfo import ZoneInfo

TABLE_NAME = os.environ["VERSE_OF_DAY_TABLE_NAME"]
APP_TZ = os.environ.get("APP_TIMEZONE", "America/Chicago")

dynamodb = boto3.resource("dynamodb")
table = dynamodb.Table(TABLE_NAME)

logger = logging.getLogger()
logger.setLevel(logging.INFO)


def _convert_decimals(obj):
    """Recursively convert DynamoDB Decimals to plain ints."""
    if isinstance(obj, list):
        return [_convert_decimals(x) for x in obj]
    if isinstance(obj, dict):
        return {k: _convert_decimals(v) for k, v in obj.items()}
    if isinstance(obj, Decimal):
        return int(obj)
    return obj


def _response(status_code: int, body: dict) -> dict:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
        },
        "body": json.dumps(body),
    }


def handler(event, context):
    # Use the same timezone as the generator Lambda
    now = datetime.now(ZoneInfo(APP_TZ))
    today = now.date().isoformat()

    logger.info("Fetching VerseOfTheDay for %s in timezone %s", today, APP_TZ)

    resp = table.get_item(Key={"date": today})
    if "Item" not in resp:
        logger.warning("VerseOfTheDay not found for %s", today)
        return _response(404, {"error": "Verse not generated yet for today"})

    item = _convert_decimals(resp["Item"])

    body = {
        "date": item["date"],
        "book": item["book"],
        "chapter": item["chapter"],
        "verse": item["verse"],
        "text": item["text"],
        "image_url": item.get("image_url", ""),
    }

    return _response(200, body)