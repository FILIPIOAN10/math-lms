#!/usr/bin/env bash
# F11, one time: copies every file of the `uploads` volume (student photos, question images, EN chapter pictures, AI
# import pages) into the private S3 bucket, under the same keys the S3 provider reads (uploads/<folder>/<name>). The
# database keeps only the names, so nothing in it changes. Run it on the server, then switch FILE_STORAGE_PROVIDER=s3
# (docs/DEPLOY.md section 9). Safe to run twice: `aws s3 sync` copies only what is missing or changed.
#
#   S3_BUCKET=math-lms-files ./deploy/s3-migrate-uploads.sh
#
# Needs the AWS CLI on the host and the EC2 instance role (s3:PutObject/ListBucket on the bucket). Read-only on the
# volume: the files stay on disk until you remove them yourself, after checking the app on S3.
set -euo pipefail

cd "$(dirname "$0")/.."
: "${S3_BUCKET:?set S3_BUCKET to the bucket name}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file ${ENV_FILE:-.env.prod}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "[$(date -Is)] copying the uploads volume out of the backend container"
$COMPOSE exec -T backend sh -c 'tar cf - -C /app uploads' | tar xf - -C "$WORK"
LOCAL_COUNT="$(find "$WORK/uploads" -type f | wc -l | tr -d ' ')"
echo "[$(date -Is)] $LOCAL_COUNT file(s) on disk"

aws s3 sync "$WORK/uploads" "s3://$S3_BUCKET/uploads" --sse AES256 --only-show-errors

REMOTE_COUNT="$(aws s3 ls "s3://$S3_BUCKET/uploads/" --recursive | wc -l | tr -d ' ')"
echo "[$(date -Is)] $REMOTE_COUNT object(s) in s3://$S3_BUCKET/uploads"
if [ "$REMOTE_COUNT" -lt "$LOCAL_COUNT" ]; then
  echo "FEWER objects than files: do NOT switch the provider yet" >&2
  exit 1
fi
echo "OK - set FILE_STORAGE_PROVIDER=s3 and S3_BUCKET=$S3_BUCKET in .env.prod, then: $COMPOSE up -d backend"
