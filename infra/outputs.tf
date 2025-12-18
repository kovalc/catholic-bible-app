output "bible_verses_table_name" {
  value = aws_dynamodb_table.bible_verses.name
}

output "verse_of_the_day_table_name" {
  value = aws_dynamodb_table.verse_of_the_day.name
}

output "daily_verse_lambda_name" {
  value = aws_lambda_function.daily_verse.function_name
}

output "verse_api_url" {
  value = aws_apigatewayv2_api.verse_http_api.api_endpoint
}