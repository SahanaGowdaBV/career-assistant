from __future__ import annotations

import json
import logging
import time
from collections import Counter
from pathlib import Path
from typing import Any

from .config import SOURCES
from .filtering import normalize
from .http import PublicHttpClient
from .models import Job, SourceResult
from .sources import FETCHERS


LOGGER = logging.getLogger("career_scraper")


def log_event(event: str, **fields: object) -> None:
    LOGGER.info(json.dumps({"event": event, **fields}, separators=(",", ":"), sort_keys=True))


class Pipeline:
    def __init__(
        self,
        *,
        sources: list[dict[str, Any]] | None = None,
        client: PublicHttpClient | None = None,
        max_results: int = 50,
        max_candidates: int = 200,
    ):
        if max_results < 1 or max_results > 200:
            raise ValueError("max_results must be between 1 and 200")
        if max_candidates < 1 or max_candidates > 200:
            raise ValueError("max_candidates must be between 1 and 200")
        self.sources = sources if sources is not None else SOURCES
        self.client = client or PublicHttpClient()
        self.max_results = max_results
        self.max_candidates = max_candidates
        self.candidates_processed = 0
        self.source_results: list[SourceResult] = []
        self.rejection_reasons: Counter[str] = Counter()
        self.duplicates = 0

    def run(self) -> list[Job]:
        accepted: list[Job] = []
        seen_source: set[tuple[str, str]] = set()
        seen_url: set[str] = set()
        for source in self.sources:
            if len(accepted) >= self.max_results or self.candidates_processed >= self.max_candidates:
                break
            name, kind = str(source.get("name") or "unknown"), str(source.get("kind") or "unknown")
            result = SourceResult(source=name, kind=kind)
            self.source_results.append(result)
            started = time.monotonic()
            log_event("source_started", source=name, kind=kind)
            try:
                fetcher = FETCHERS[kind]
                raw_jobs = fetcher(source, self.client)
                result.discovered = len(raw_jobs)
                for raw in raw_jobs:
                    if self.candidates_processed >= self.max_candidates:
                        break
                    self.candidates_processed += 1
                    result.fetched += 1
                    job, reason = normalize(raw)
                    if job is None:
                        result.rejected += 1
                        self.rejection_reasons[reason or "unknown"] += 1
                        continue
                    key = (job.source, job.source_id.casefold())
                    url_key = job.url.rstrip("/").casefold()
                    if key in seen_source or url_key in seen_url:
                        result.duplicates += 1
                        self.duplicates += 1
                        continue
                    seen_source.add(key)
                    seen_url.add(url_key)
                    accepted.append(job)
                    result.accepted += 1
                    if len(accepted) >= self.max_results:
                        break
                result.status = "ok"
            except Exception as exc:  # source isolation is intentional
                result.status = "failed"
                result.error_type = type(exc).__name__
                log_event("source_failed", source=name, kind=kind, error_type=type(exc).__name__)
            finally:
                result.elapsed_ms = round((time.monotonic() - started) * 1000)
                log_event(
                    "source_finished",
                    source=name,
                    kind=kind,
                    status=result.status,
                    discovered=result.discovered,
                    fetched=result.fetched,
                    accepted=result.accepted,
                    rejected=result.rejected,
                    duplicates=result.duplicates,
                    elapsed_ms=result.elapsed_ms,
                )
        return accepted

    def summary(self, *, dry_run: bool, jobs: list[Job], ingestion: dict[str, Any] | None = None) -> dict[str, Any]:
        return {
            "dryRun": dry_run,
            "maxResults": self.max_results,
            "maxCandidates": self.max_candidates,
            "candidatesProcessed": self.candidates_processed,
            "sourcesAttempted": len(self.source_results),
            "sources": [result.safe_dict() for result in self.source_results],
            "jobsAccepted": len(jobs),
            "jobsExperienceUnknown": sum(job.experience_unknown for job in jobs),
            "jobsRejected": sum(result.rejected for result in self.source_results),
            "duplicatesWithinRun": self.duplicates,
            "rejectionReasons": dict(sorted(self.rejection_reasons.items())),
            "ingestion": ingestion,
        }

    @staticmethod
    def write_json(path: str | Path, data: object) -> None:
        target = Path(path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
