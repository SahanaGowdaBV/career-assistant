#!/usr/bin/env bash
set -euo pipefail
curl --fail-with-body -X POST "${CAREER_API_URL:-http://localhost:8080/api}/scraper/ingest" -H 'Content-Type: application/json' --data-binary @scraper/fixtures/uae-devops-jobs.json
