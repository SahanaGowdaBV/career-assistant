from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from urllib.parse import urlparse

from .http import PublicHttpClient
from .pipeline import Pipeline, log_event


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Scrape public UAE career endpoints and optionally ingest matching jobs.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", default=True, help="Scrape only (default).")
    mode.add_argument("--live", action="store_true", help="Send accepted jobs to the backend ingestion API.")
    parser.add_argument("--max-results", type=int, default=50)
    parser.add_argument("--max-candidates", type=int, default=200)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--rate-limit-seconds", type=float, default=0.25)
    parser.add_argument("--api-url", default=os.environ.get("CAREER_API_URL"))
    parser.add_argument("--summary-file", default="scraper-summary.json")
    parser.add_argument("--output-file", help="Optional full public job output; do not upload this artifact.")
    return parser


def ingestion_url(base: str) -> str:
    value = base.rstrip("/")
    return f"{value}/scraper/ingest"


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(message)s", stream=sys.stderr)
    dry_run = not args.live
    ingestion_token = None
    if not dry_run:
        if args.max_results > 50:
            raise SystemExit("--live permits at most 50 results")
        if not args.api_url:
            raise SystemExit("CAREER_API_URL or --api-url is required for --live")
        parsed = urlparse(args.api_url)
        if parsed.scheme not in ("http", "https") or not parsed.netloc:
            raise SystemExit("CAREER_API_URL must be an http(s) URL")
        ingestion_token = os.environ.get("SCRAPER_INGESTION_TOKEN")
        if not ingestion_token:
            raise SystemExit("SCRAPER_INGESTION_TOKEN is required for --live")

    client = PublicHttpClient(timeout=args.timeout, retries=args.retries, rate_limit_seconds=args.rate_limit_seconds)
    pipeline = Pipeline(client=client, max_results=args.max_results, max_candidates=args.max_candidates)
    jobs = pipeline.run()
    payload = {"dryRun": dry_run, "jobs": [job.to_ingestion_dict() for job in jobs]}
    ingestion = None
    if not dry_run and jobs:
        response = client.post(
            ingestion_url(args.api_url),
            payload,
            headers={"X-Scraper-Ingestion-Token": ingestion_token},
        )
        ingestion = {
            "accepted": int(response.get("accepted", 0)),
            "rejected": int(response.get("rejected", 0)),
            "duplicates": int(response.get("duplicates", 0)),
            "saved": len(response.get("jobs", []) or []),
        } if isinstance(response, dict) else {"responseType": type(response).__name__}
        log_event("ingestion_finished", **ingestion)
    elif not dry_run:
        ingestion = {"accepted": 0, "rejected": 0, "duplicates": 0, "saved": 0}

    summary = pipeline.summary(dry_run=dry_run, jobs=jobs, ingestion=ingestion)
    pipeline.write_json(args.summary_file, summary)
    if args.output_file:
        pipeline.write_json(args.output_file, payload)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
