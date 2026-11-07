#!/bin/bash
# LocalStack init hook: mounted into /etc/localstack/init/ready.d/, so LocalStack runs it
# itself once SNS/SQS/S3 are ready (no sleep, no separate CLI container).
#
# Edit the lists below, then recreate the container:  docker compose down && docker compose up -d
# Runs inside the LocalStack container, where `awslocal` already points at LocalStack.

set -euo pipefail

REGION="us-east-1"
ACCOUNT_ID="000000000000"

# SNS topic -> SQS queue that subscribes to it ("<topic> <queue>", one pair per line).
# Every topic and queue named here is created.
SUBSCRIPTIONS="
  create-applicant-request        create-applicant-request-applicant-credit-score-svc
  create-applicant-response       create-applicant-response-application-ingestion-svc
  applicant-credit-pull-request   applicant-credit-pull-request-applicant-credit-score-svc
  applicant-credit-pull-response  applicant-credit-pull-response-applicant-orchestrator-svc
  applicant-doc-request           applicant-doc-request-applicant-portal-svc
  applicant-doc-response          applicant-doc-response-applicant-orchestrator-svc
"

# S3 buckets
BUCKETS="
  applicant-document-bucket-local
"

echo "$SUBSCRIPTIONS" | while read -r topic queue; do
  [ -z "$topic" ] && continue

  awslocal sns create-topic --name "$topic" > /dev/null
  awslocal sqs create-queue --queue-name "$queue" > /dev/null
  awslocal sns subscribe \
    --topic-arn "arn:aws:sns:${REGION}:${ACCOUNT_ID}:${topic}" \
    --protocol sqs \
    --notification-endpoint "arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${queue}" > /dev/null

  echo "SNS ${topic} -> SQS ${queue}"
done

for bucket in $BUCKETS; do
  awslocal s3 mb "s3://${bucket}" > /dev/null
  echo "S3 ${bucket}"
done

echo "LocalStack resources ready"
