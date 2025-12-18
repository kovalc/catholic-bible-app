# catholic-bible-app

Android Catholic Bible app providing a daily Scripture verse with sacred art, powered by AWS (API Gateway, Lambda, DynamoDB, S3) + OpenAI image generation stored in AWS Secrets Manager.

## Architecture

**Android App (Kotlin)**
- Calls a simple HTTP endpoint to fetch today’s verse + image URL.

**AWS Backend**
- **EventBridge (daily cron)** → `daily-verse-generator` Lambda
- Lambda picks a verse, generates an image (OpenAI), uploads PNG to **S3**, and stores results in **DynamoDB**
- **API Gateway (HTTP API)** → `bible-verse-api` Lambda
- Lambda returns today’s verse from DynamoDB

## API

### Get today’s verse
`GET /verse/today`

Response example:
```json
{
  "date": "2025-12-18",
  "book": "Exodus",
  "chapter": 36,
  "verse": 10,
  "text": "…",
  "image_url": "https://<bucket>.s3.amazonaws.com/<key>.png"
}