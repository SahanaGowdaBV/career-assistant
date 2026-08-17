from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True)
class RawJob:
    title: str
    company: str
    location: str
    description: str
    source: str
    source_id: str
    url: str
    posted_at: str | int | None = None


@dataclass(frozen=True)
class Job:
    title: str
    company: str
    location: str
    description: str
    experience_min: int | None
    experience_max: int | None
    experience_unknown: bool
    source: str
    source_id: str
    url: str
    posted_at: str | None

    def to_ingestion_dict(self) -> dict[str, Any]:
        data = asdict(self)
        return {
            "title": data["title"],
            "company": data["company"],
            "location": data["location"],
            "description": data["description"],
            "experienceMin": data["experience_min"],
            "experienceMax": data["experience_max"],
            "experienceUnknown": data["experience_unknown"],
            "source": data["source"],
            "sourceId": data["source_id"],
            "url": data["url"],
            "postedAt": data["posted_at"],
        }


@dataclass
class SourceResult:
    source: str
    kind: str
    status: str = "pending"
    fetched: int = 0
    discovered: int = 0
    accepted: int = 0
    rejected: int = 0
    duplicates: int = 0
    error_type: str | None = None
    elapsed_ms: int = 0

    def safe_dict(self) -> dict[str, Any]:
        return asdict(self)
