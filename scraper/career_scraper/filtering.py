from __future__ import annotations

import html
import re
from datetime import datetime, timedelta, timezone
from typing import Iterable
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from bs4 import BeautifulSoup

from .models import Job, RawJob


TARGET_MIN = 4
TARGET_MAX = 8

UAE_MARKERS = (
    "united arab emirates",
    "u.a.e",
    "uae",
    "dubai",
    "abu dhabi",
    "sharjah",
)
INDIA_MARKERS = (
    "india",
    "bengaluru",
    "bangalore",
    "hyderabad",
    "pune",
    "chennai",
    "mumbai",
    "noida",
    "gurugram",
    "gurgaon",
    "delhi",
    "karnataka",
)
EXCLUDED_LOCATION_MARKERS = INDIA_MARKERS + (
    "saudi arabia",
    "riyadh",
    "jeddah",
    "egypt",
    "cairo",
    "europe",
    "european union",
    "united states",
    "u.s.a",
    "usa",
)

ROLE_PATTERNS = tuple(
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"^(?:senior\s+|lead\s+)?dev[\s-]?ops engineer\b",
        r"^devsecops engineer\b",
        r"^site reliability engineer\b",
        r"^senior site reliability engineer\b",
        r"^sre\b",
        r"^(?:senior\s+|lead\s+)?platform engineer\b",
        r"^cloud devops engineer\b",
        r"^cloud infrastructure engineer\b",
        r"^infrastructure engineer\b",
    )
)

PRIMARY_TITLE_SEPARATOR = re.compile(
    r"\s*(?:\(|\||/|,|:)\s*|\s+[-–—]\s+",
    re.IGNORECASE,
)

RANGE_PATTERNS = (
    re.compile(r"\b(\d{1,2})\s*(?:-|–|—|to)\s*(\d{1,2})\s*(?:\+\s*)?(?:years?|yrs?)\b", re.I),
    re.compile(r"\b(?:minimum|min\.?|at least|more than|over)\s*(?:of\s*)?(\d{1,2})\s*\+?\s*(?:years?|yrs?)\b", re.I),
    re.compile(r"\b(\d{1,2})\s*\+\s*(?:years?|yrs?)\b", re.I),
    re.compile(r"\bup to\s*(\d{1,2})\s*(?:years?|yrs?)\b", re.I),
    re.compile(r"\b(\d{1,2})\s*(?:years?|yrs?)\s+(?:of\s+)?(?:professional\s+|relevant\s+|industry\s+)?experience\b", re.I),
)


def clean_text(value: object) -> str:
    raw = html.unescape(str(value or ""))
    if "<" in raw and ">" in raw:
        raw = BeautifulSoup(raw, "html.parser").get_text("\n", strip=True)
    return re.sub(r"[ \t]+", " ", re.sub(r"\r\n?", "\n", raw)).strip()


def is_uae_location(location: str) -> bool:
    text = clean_text(location).lower()
    if any(marker in text for marker in EXCLUDED_LOCATION_MARKERS):
        return False
    return any(marker in text for marker in UAE_MARKERS)


def normalize_primary_title(title: str) -> str:
    """Return the first title clause, excluding parenthetical/trailing roles."""
    return PRIMARY_TITLE_SEPARATOR.split(clean_text(title), maxsplit=1)[0].strip()


def is_target_role(title: str, *, additional_target_titles: Iterable[str] = ()) -> bool:
    text = clean_text(title)
    if re.search(r"\b(?:junior|intern|internship|graduate|trainee)\b", text, re.I):
        return False
    primary_title = normalize_primary_title(text)
    if any(pattern.fullmatch(primary_title) for pattern in ROLE_PATTERNS):
        return True
    configured_titles = {
        normalize_primary_title(configured_title).casefold()
        for configured_title in additional_target_titles
    }
    return primary_title.casefold() in configured_titles


def extract_experience(text: str) -> list[tuple[int, int | None]]:
    normalized = clean_text(text)
    ranges: list[tuple[int, int | None]] = []
    occupied: list[tuple[int, int]] = []

    for index, pattern in enumerate(RANGE_PATTERNS):
        for match in pattern.finditer(normalized):
            if any(match.start() < end and match.end() > start for start, end in occupied):
                continue
            if index == 0:
                low, high = int(match.group(1)), int(match.group(2))
                if low > high:
                    low, high = high, low
                value = (low, high)
            elif index in (1, 2):
                value = (int(match.group(1)), None)
            elif index == 3:
                value = (0, int(match.group(1)))
            else:
                years = int(match.group(1))
                value = (years, years)
            if value[0] <= 40 and (value[1] is None or value[1] <= 40):
                ranges.append(value)
                occupied.append((match.start(), match.end()))
    return ranges


def choose_overlapping_experience(ranges: Iterable[tuple[int, int | None]]) -> tuple[int, int | None] | None:
    matches = []
    for low, high in ranges:
        effective_high = high if high is not None else 100
        if low <= TARGET_MAX and effective_high >= TARGET_MIN:
            matches.append((low, high))
    if not matches:
        return None
    return sorted(matches, key=lambda item: (abs(item[0] - TARGET_MIN), item[1] is None, item[1] or 100))[0]


def normalize_posted_at(value: str | int | None, *, now: datetime | None = None) -> str | None:
    if value in (None, ""):
        return None
    current = now or datetime.now(timezone.utc)
    if isinstance(value, int):
        timestamp = value / 1000 if value > 10_000_000_000 else value
        return datetime.fromtimestamp(timestamp, tz=timezone.utc).isoformat().replace("+00:00", "Z")
    text = clean_text(value)
    relative = re.search(r"(?:posted\s+)?(\d+)\s+days?\s+ago", text, re.I)
    if relative:
        return (current - timedelta(days=int(relative.group(1)))).isoformat().replace("+00:00", "Z")
    if re.search(r"posted\s+today|today", text, re.I):
        return current.isoformat().replace("+00:00", "Z")
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
    except ValueError:
        return None


def normalize(raw: RawJob) -> tuple[Job | None, str | None]:
    title = clean_text(raw.title)
    location = clean_text(raw.location)
    description = clean_text(raw.description)
    company = clean_text(raw.company)
    url = clean_text(raw.url)
    source_id = clean_text(raw.source_id)

    if not is_uae_location(location):
        return None, "location"
    if not is_target_role(title):
        return None, "role"
    ranges = extract_experience(f"{title}\n{description}")
    experience = choose_overlapping_experience(ranges)
    if ranges and experience is None:
        return None, "experience"
    if not all((title, company, location, source_id, url)) or not url.startswith(("https://", "http://")):
        return None, "normalization"
    return Job(
        title=title[:500],
        company=company[:500],
        location=location[:500],
        description=description,
        experience_min=experience[0] if experience else None,
        experience_max=experience[1] if experience else None,
        experience_unknown=experience is None,
        source=raw.source,
        source_id=source_id[:500],
        url=url[:1000],
        posted_at=normalize_posted_at(raw.posted_at),
    ), None


def deduplicate(jobs: Iterable[Job]) -> tuple[list[Job], int]:
    unique: list[Job] = []
    seen_source: set[tuple[str, str]] = set()
    seen_url: set[str] = set()
    duplicates = 0
    for job in jobs:
        source_key = (job.source, job.source_id.casefold())
        url_key = canonical_url(job.url)
        if source_key in seen_source or url_key in seen_url:
            duplicates += 1
            continue
        seen_source.add(source_key)
        seen_url.add(url_key)
        unique.append(job)
    return unique, duplicates


def canonical_url(value: str) -> str:
    parsed = urlsplit(clean_text(value))
    query = urlencode([(key, item) for key, item in parse_qsl(parsed.query, keep_blank_values=True)
                       if not key.casefold().startswith("utm_") and key.casefold() not in {"source", "ref", "referrer"}])
    path = parsed.path.rstrip("/") or "/"
    return urlunsplit((parsed.scheme.casefold(), parsed.netloc.casefold(), path, query, ""))
