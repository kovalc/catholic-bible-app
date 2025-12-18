terraform {
  backend "s3" {
    bucket         = "catholic-bible-tfstate-ckoval"
    key            = "catholic-bible/infra/terraform.tfstate"
    region         = "us-west-2"
    dynamodb_table = "catholic-bible-tflock"
    encrypt        = true
  }
}