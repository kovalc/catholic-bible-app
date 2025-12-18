terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ============================================================
# Look up existing Secrets Manager secret (DO NOT manage value)
# ============================================================
data "aws_secretsmanager_secret" "openai_api_key" {
  name = "openai-api-key"
}

# =========================
# DynamoDB: BibleVerses
# =========================
resource "aws_dynamodb_table" "bible_verses" {
  name         = "BibleVerses"
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "verse_id"

  attribute {
    name = "verse_id"
    type = "S"
  }

  attribute {
    name = "canonical_index"
    type = "N"
  }

  global_secondary_index {
    name            = "canonical-index-gsi"
    hash_key        = "canonical_index"
    projection_type = "ALL"
  }
}

# =========================
# DynamoDB: VerseOfTheDay
# =========================
resource "aws_dynamodb_table" "verse_of_the_day" {
  name         = "VerseOfTheDay"
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "date"

  attribute {
    name = "date"
    type = "S"
  }
}

# =========================
# S3 bucket for Verse Images
# =========================
resource "aws_s3_bucket" "votd_images" {
  bucket = "catholic-bible-votd-images-ckoval"
}

resource "aws_s3_bucket_policy" "votd_images_public" {
  bucket = aws_s3_bucket.votd_images.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.votd_images.arn}/*"
      }
    ]
  })
}

# =========================
# IAM Role for Daily Verse Lambda
# =========================
resource "aws_iam_role" "daily_verse_lambda_role" {
  name = "daily-verse-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "daily_verse_lambda_policy" {
  name = "daily-verse-lambda-policy"
  role = aws_iam_role.daily_verse_lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Logs
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },

      # DynamoDB (Bible + VerseOfTheDay)
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:Query"
        ]
        Resource = [
          aws_dynamodb_table.bible_verses.arn,
          aws_dynamodb_table.verse_of_the_day.arn
        ]
      },

      # S3 image bucket
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.votd_images.arn}/*"
      },

      # Secrets Manager (OpenAI key)
      {
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = data.aws_secretsmanager_secret.openai_api_key.arn
      }
    ]
  })
}

# =========================
# Package Daily Verse Lambda
# =========================
data "archive_file" "daily_verse_zip" {
  type        = "zip"
  source_file = "${path.module}/../backend/daily_verse_lambda/handler.py"
  output_path = "${path.module}/daily_verse_lambda.zip"
}

# =========================
# Daily Verse Lambda
# =========================
resource "aws_lambda_function" "daily_verse" {
  function_name = "daily-verse-generator"
  role          = aws_iam_role.daily_verse_lambda_role.arn
  handler       = "handler.handler"
  runtime       = "python3.11"

  filename         = data.archive_file.daily_verse_zip.output_path
  source_code_hash = data.archive_file.daily_verse_zip.output_base64sha256

  # Plenty of time for OpenAI + S3
  timeout = 120

  environment {
    variables = {
      BIBLE_TABLE_NAME        = aws_dynamodb_table.bible_verses.name
      VERSE_OF_DAY_TABLE_NAME = aws_dynamodb_table.verse_of_the_day.name
      TOTAL_VERSES            = tostring(var.total_verses)
      IMAGE_BUCKET_NAME       = aws_s3_bucket.votd_images.bucket
      APP_TIMEZONE            = "America/Chicago"

      # Secret name used by backend/daily_verse_lambda/handler.py
      OPENAI_SECRET_NAME = data.aws_secretsmanager_secret.openai_api_key.name
    }
  }
}

# =========================
# EventBridge Daily Schedule
# =========================
resource "aws_cloudwatch_event_rule" "daily_verse_rule" {
  name = "daily-verse-rule"
  # 06:00 UTC = 00:00 CST (when offset -6)
  schedule_expression = "cron(0 6 * * ? *)"
}

resource "aws_cloudwatch_event_target" "daily_verse_target" {
  rule      = aws_cloudwatch_event_rule.daily_verse_rule.name
  target_id = "daily-verse-lambda"
  arn       = aws_lambda_function.daily_verse.arn
}

resource "aws_lambda_permission" "allow_eventbridge_daily_verse" {
  statement_id  = "AllowExecutionFromEventBridgeDailyVerse"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.daily_verse.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.daily_verse_rule.arn
}

# ============================================================
# Verse API Lambda (GET /verse/today)
# ============================================================
resource "aws_iam_role" "verse_api_lambda_role" {
  name = "verse-api-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "verse_api_lambda_policy" {
  name = "verse-api-lambda-policy"
  role = aws_iam_role.verse_api_lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem"]
        Resource = aws_dynamodb_table.verse_of_the_day.arn
      }
    ]
  })
}

data "archive_file" "verse_api_zip" {
  type        = "zip"
  source_file = "${path.module}/../backend/verse_api_lambda/handler.py"
  output_path = "${path.module}/verse_api_lambda.zip"
}

resource "aws_lambda_function" "verse_api" {
  function_name = "bible-verse-api"
  role          = aws_iam_role.verse_api_lambda_role.arn
  handler       = "handler.handler"
  runtime       = "python3.11"

  filename         = data.archive_file.verse_api_zip.output_path
  source_code_hash = data.archive_file.verse_api_zip.output_base64sha256

  timeout = 10

  environment {
    variables = {
      VERSE_OF_DAY_TABLE_NAME = aws_dynamodb_table.verse_of_the_day.name
      APP_TIMEZONE            = "America/Chicago"
    }
  }
}

# =========================
# API Gateway HTTP API
# =========================
resource "aws_apigatewayv2_api" "verse_http_api" {
  name          = "bible-verse-http-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "verse_api_integration" {
  api_id                 = aws_apigatewayv2_api.verse_http_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.verse_api.arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "verse_today_route" {
  api_id    = aws_apigatewayv2_api.verse_http_api.id
  route_key = "GET /verse/today"
  target    = "integrations/${aws_apigatewayv2_integration.verse_api_integration.id}"
}

resource "aws_apigatewayv2_stage" "verse_http_stage" {
  api_id      = aws_apigatewayv2_api.verse_http_api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "allow_apigw_invoke_verse_api" {
  statement_id  = "AllowAPIGatewayInvokeVerseApi"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.verse_api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.verse_http_api.execution_arn}/*/*"
}