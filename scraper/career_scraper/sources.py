from __future__ import annotations

import json
import re
from typing import Any, Iterable
from urllib.parse import quote, urlencode

from bs4 import BeautifulSoup

from .config import SEARCH_TERMS
from .filtering import clean_text, is_target_role
from .http import PublicHttpClient
from .models import RawJob


def _location_name(value: object) -> str:
    if isinstance(value, dict):
        return clean_text(value.get("name"))
    return clean_text(value)


def fetch_greenhouse(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    slug = source["slug"]
    payload = client.get_json(f"https://boards-api.greenhouse.io/v1/boards/{quote(slug)}/jobs?content=true")
    jobs = payload.get("jobs", []) if isinstance(payload, dict) else []
    output = []
    for item in jobs:
        if not isinstance(item, dict) or not item.get("id") or not is_target_role(item.get("title", "")):
            continue
        location = _location_name(item.get("location"))
        if not location and isinstance(item.get("offices"), list):
            location = ", ".join(_location_name(office) for office in item["offices"] if _location_name(office))
        output.append(RawJob(
            title=clean_text(item.get("title")),
            company=source["name"],
            location=location,
            description=clean_text(item.get("content")),
            source="GREENHOUSE",
            source_id=f"{slug}-{item['id']}",
            url=clean_text(item.get("absolute_url")),
            posted_at=item.get("updated_at"),
        ))
    return output


def fetch_lever(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    slug = source["slug"]
    payload = client.get_json(f"https://api.lever.co/v0/postings/{quote(slug)}?mode=json")
    output = []
    for item in payload if isinstance(payload, list) else []:
        if not isinstance(item, dict) or not item.get("id") or not is_target_role(item.get("text", "")):
            continue
        categories = item.get("categories") if isinstance(item.get("categories"), dict) else {}
        list_text = "\n".join(
            f"{part.get('text', '')}\n" + "\n".join(part.get("content", []) or [])
            for part in item.get("lists", [])
            if isinstance(part, dict)
        )
        description = "\n".join(filter(None, (clean_text(item.get("descriptionPlain") or item.get("description")), clean_text(list_text))))
        output.append(RawJob(
            title=clean_text(item.get("text")),
            company=source["name"],
            location=clean_text(categories.get("location")),
            description=description,
            source="LEVER",
            source_id=f"{slug}-{item['id']}",
            url=clean_text(item.get("hostedUrl") or item.get("applyUrl")),
            posted_at=item.get("createdAt"),
        ))
    return output


def fetch_ashby(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    slug = source["slug"]
    payload = client.get_json(f"https://api.ashbyhq.com/posting-api/job-board/{quote(slug)}")
    jobs = payload.get("jobs", []) if isinstance(payload, dict) else []
    output = []
    for item in jobs:
        if (
            not isinstance(item, dict)
            or not item.get("id")
            or item.get("isListed") is False
        ):
            continue
        locations = [clean_text(item.get("location"))]
        for secondary in item.get("secondaryLocations", []) or []:
            if isinstance(secondary, dict):
                locations.append(clean_text(secondary.get("location") or secondary.get("name")))
            else:
                locations.append(clean_text(secondary))
        output.append(RawJob(
            title=clean_text(item.get("title")),
            company=source["name"],
            location=" / ".join(location for location in locations if location),
            description=clean_text(item.get("descriptionPlain") or item.get("descriptionHtml")),
            source="COMPANY_CAREER_PAGE",
            source_id=f"ashby-{slug}-{item['id']}",
            url=clean_text(item.get("jobUrl") or item.get("applyUrl")),
            posted_at=item.get("publishedAt"),
        ))
    return output


def fetch_amazon(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    """Read Amazon's official public job-search JSON endpoint."""
    output: list[RawJob] = []
    seen: set[str] = set()
    for term in SEARCH_TERMS:
        payload = client.get_json(
            "https://www.amazon.jobs/en/search.json",
            params={
                "base_query": term,
                "loc_query": "United Arab Emirates",
                "result_limit": 100,
            },
        )
        for item in payload.get("jobs", []) if isinstance(payload, dict) else []:
            if not isinstance(item, dict) or not item.get("id") or not is_target_role(item.get("title", "")):
                continue
            identifier = clean_text(item["id"])
            if identifier in seen:
                continue
            seen.add(identifier)
            path = clean_text(item.get("job_path"))
            output.append(RawJob(
                title=clean_text(item.get("title")),
                company=source["name"],
                location=clean_text(item.get("location")),
                description="\n".join(filter(None, (
                    clean_text(item.get("description")),
                    clean_text(item.get("basic_qualifications")),
                    clean_text(item.get("preferred_qualifications")),
                ))),
                source="COMPANY_CAREER_PAGE",
                source_id=f"amazon-{identifier}",
                url=path if path.startswith("http") else f"https://www.amazon.jobs{path}",
                posted_at=item.get("posted_date"),
            ))
    return output


def _workday_url(source: dict[str, Any], external_path: str) -> str:
    if external_path.startswith("http"):
        return external_path
    if external_path.startswith("/job/"):
        return f"https://{source['host']}/{source['site']}{external_path}"
    return f"https://{source['host']}{external_path}"


def fetch_workday(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    endpoint = f"https://{source['host']}/wday/cxs/{source['tenant']}/{source['site']}/jobs"
    output: list[RawJob] = []
    seen: set[str] = set()
    for term in SEARCH_TERMS:
        offset = 0
        for _page in range(3):
            payload = client.post_json(endpoint, {
                "appliedFacets": source.get("facets", {}),
                "limit": 20,
                "offset": offset,
                "searchText": term,
            })
            summaries = payload.get("jobPostings", []) if isinstance(payload, dict) else []
            if not summaries:
                break
            for summary in summaries:
                if not isinstance(summary, dict) or not is_target_role(summary.get("title", "")):
                    continue
                external_path = clean_text(summary.get("externalPath"))
                key = external_path or clean_text(summary.get("title"))
                if not key or key in seen:
                    continue
                seen.add(key)
                detail = client.get_json(f"https://{source['host']}/wday/cxs/{source['tenant']}/{source['site']}{external_path}") if external_path else {}
                info = detail.get("jobPostingInfo", {}) if isinstance(detail, dict) else {}
                identifier = next(iter(summary.get("bulletFields", []) or []), None) or key
                output.append(RawJob(
                    title=clean_text(info.get("title") or summary.get("title")),
                    company=source["name"],
                    location=clean_text(info.get("location") or summary.get("locationsText")),
                    description=clean_text(info.get("jobDescription") or info.get("jobDescriptionText") or summary.get("description")),
                    source="WORKDAY",
                    source_id=f"{source['tenant']}-{source['site']}-{identifier}",
                    url=_workday_url(source, clean_text(info.get("externalUrl") or info.get("externalPath") or external_path)),
                    posted_at=info.get("startDate") or summary.get("postedOn"),
                ))
            offset += len(summaries)
            if len(summaries) < 20 or offset >= int(payload.get("total") or 0):
                break
    return output


def _iter_oracle_requisitions(payload: object) -> Iterable[dict[str, Any]]:
    if not isinstance(payload, dict):
        return
    for item in payload.get("items", []):
        if not isinstance(item, dict):
            continue
        for job in item.get("requisitionList", []) or []:
            if isinstance(job, dict):
                yield job


def fetch_oracle(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    output: list[RawJob] = []
    seen: set[str] = set()
    expand = "requisitionList.workLocation,requisitionList.otherWorkLocations,requisitionList.secondaryLocations"
    for term in SEARCH_TERMS:
        finder = f"findReqs;siteNumber={source['site']},limit=25,offset=0,keyword={term}"
        query = urlencode({"onlyData": "true", "expand": expand, "finder": finder})
        payload = client.get_json(f"https://{source['host']}/hcmRestApi/resources/latest/recruitingCEJobRequisitions?{query}")
        for item in _iter_oracle_requisitions(payload):
            identifier = clean_text(item.get("Id"))
            if not identifier or identifier in seen or not is_target_role(item.get("Title", "")):
                continue
            seen.add(identifier)
            detail_payload = client.get_json(
                f"https://{source['host']}/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails",
                params={
                    "onlyData": "true",
                    "finder": f"ById;Id={identifier},siteNumber={source['site']}",
                },
            )
            detail_items = detail_payload.get("items", []) if isinstance(detail_payload, dict) else []
            detail = detail_items[0] if detail_items and isinstance(detail_items[0], dict) else item
            work_locations = item.get("workLocation") if isinstance(item.get("workLocation"), list) else []
            location = clean_text(detail.get("PrimaryLocation") or item.get("PrimaryLocation") or (work_locations[0].get("LocationName") if work_locations else ""))
            description = "\n".join(clean_text(detail.get(field)) for field in (
                "ExternalDescriptionStr", "ShortDescriptionStr", "ExternalResponsibilitiesStr", "ExternalQualificationsStr"
            ) if detail.get(field))
            output.append(RawJob(
                title=clean_text(detail.get("Title") or item.get("Title")),
                company=source["name"],
                location=location,
                description=description,
                source="ORACLE_HCM",
                source_id=f"{source['host']}-{source['site']}-{identifier}",
                url=f"https://{source['host']}/hcmUI/CandidateExperience/en/sites/{source['site']}/job/{identifier}",
                posted_at=detail.get("ExternalPostedStartDate") or item.get("PostedDate"),
            ))
    return output


def fetch_workable(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    """Read Workable's public widget and candidate job pages.

    The widget supplies current vacancies and locations. Descriptions are read
    from the public JobPosting JSON-LD on matching candidate pages only.
    """
    slug = source["slug"]
    payload = client.get_json(f"https://apply.workable.com/api/v1/widget/accounts/{quote(slug)}")
    entries = payload.get("jobs", []) if isinstance(payload, dict) else []
    output = []
    for item in entries:
        if not isinstance(item, dict) or not item.get("shortcode") or not is_target_role(item.get("title", "")):
            continue
        location_parts = [item.get("city"), item.get("state"), item.get("country")]
        location = ", ".join(clean_text(part) for part in location_parts if clean_text(part))
        if not location and isinstance(item.get("locations"), list):
            location = ", ".join(clean_text(part) for part in item["locations"] if clean_text(part))
        url = clean_text(item.get("url") or item.get("shortlink") or item.get("application_url"))
        description = ""
        if url:
            page = BeautifulSoup(client.get_text(url), "html.parser")
            for script in page.select('script[type="application/ld+json"]'):
                try:
                    structured = json.loads(script.string or script.get_text())
                except (TypeError, json.JSONDecodeError):
                    continue
                values = structured if isinstance(structured, list) else [structured]
                posting = next((value for value in values if isinstance(value, dict) and value.get("@type") == "JobPosting"), None)
                if posting:
                    description = clean_text(posting.get("description"))
                    break
        output.append(RawJob(
            title=clean_text(item.get("title")),
            company=source["name"],
            location=location,
            description=description,
            source="WORKABLE",
            source_id=f"{slug}-{item['shortcode']}",
            url=url,
            posted_at=item.get("published_on") or item.get("created_at"),
        ))
    return output


def _smartrecruiters_location(item: dict[str, Any]) -> str:
    location = item.get("location") if isinstance(item.get("location"), dict) else {}
    return ", ".join(clean_text(location.get(part)) for part in ("city", "region", "country") if clean_text(location.get(part)))


def fetch_smartrecruiters(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    slug = source["slug"]
    output: list[RawJob] = []
    offset = 0
    while offset < 500:
        payload = client.get_json(
            f"https://api.smartrecruiters.com/v1/companies/{quote(slug)}/postings",
            params={"limit": 100, "offset": offset},
        )
        summaries = payload.get("content", []) if isinstance(payload, dict) else []
        if not summaries:
            break
        for summary in summaries:
            if not isinstance(summary, dict) or not summary.get("id") or not is_target_role(summary.get("name", "")):
                continue
            identifier = str(summary["id"])
            detail = client.get_json(f"https://api.smartrecruiters.com/v1/companies/{quote(slug)}/postings/{quote(identifier)}")
            item = detail if isinstance(detail, dict) else summary
            job_ad = item.get("jobAd") if isinstance(item.get("jobAd"), dict) else {}
            sections = job_ad.get("sections") if isinstance(job_ad.get("sections"), dict) else {}
            description = "\n".join(
                clean_text(value.get("text") or value.get("title") if isinstance(value, dict) else value)
                for value in sections.values()
                if value
            )
            output.append(RawJob(
                title=clean_text(item.get("name") or summary.get("name")),
                company=source["name"],
                location=_smartrecruiters_location(item or summary),
                description=description,
                source="SMARTRECRUITERS",
                source_id=f"{slug}-{identifier}",
                url=clean_text(item.get("ref") or summary.get("ref")),
                posted_at=item.get("releasedDate") or summary.get("releasedDate"),
            ))
        offset += len(summaries)
        if len(summaries) < 100 or offset >= int(payload.get("totalFound") or 0):
            break
    return output


def _phenom_ddo(page: str) -> dict[str, Any]:
    match = re.search(r"phApp\.ddo\s*=\s*(\{.*?\});\s*phApp\.experimentData", page, re.DOTALL)
    if not match:
        return {}
    try:
        value = json.loads(match.group(1))
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def _phenom_jobs(ddo: dict[str, Any]) -> list[dict[str, Any]]:
    search = ddo.get("eagerLoadRefineSearch") if isinstance(ddo.get("eagerLoadRefineSearch"), dict) else {}
    data = search.get("data") if isinstance(search.get("data"), dict) else {}
    jobs = data.get("jobs")
    return jobs if isinstance(jobs, list) else []


def fetch_phenom(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    base_url = str(source["base_url"]).rstrip("/")
    site_path = str(source.get("site_path") or "global/en").strip("/")
    output: list[RawJob] = []
    seen: set[str] = set()
    for term in SEARCH_TERMS:
        page = client.get_text(f"{base_url}/{site_path}/search-results", params={"keywords": term})
        for item in _phenom_jobs(_phenom_ddo(page)):
            if not isinstance(item, dict) or not is_target_role(item.get("title", "")):
                continue
            identifier = clean_text(item.get("jobId") or item.get("reqId") or item.get("jobSeqNo"))
            if not identifier or identifier in seen:
                continue
            seen.add(identifier)
            job_url = f"{base_url}/{site_path}/job/{quote(identifier)}"
            detail_ddo = _phenom_ddo(client.get_text(job_url))
            detail_container = detail_ddo.get("jobDetail") if isinstance(detail_ddo.get("jobDetail"), dict) else {}
            detail_data = detail_container.get("data") if isinstance(detail_container.get("data"), dict) else {}
            detail = detail_data.get("job") if isinstance(detail_data.get("job"), dict) else {}
            description = clean_text(
                detail.get("description")
                or detail.get("jobDescription")
                or item.get("descriptionTeaser")
                or (item.get("ml_job_parser") or {}).get("descriptionTeaser")
            )
            output.append(RawJob(
                title=clean_text(detail.get("title") or item.get("title")),
                company=clean_text(item.get("brand") or item.get("businessUnit") or source["name"]),
                location=clean_text(detail.get("location") or item.get("location") or item.get("cityStateCountry")),
                description=description,
                source="COMPANY_CAREER_PAGE",
                source_id=f"phenom-{item.get('jobSeqNo') or identifier}",
                url=job_url,
                posted_at=detail.get("postedDate") or item.get("postedDate"),
            ))
    return output


def fetch_official_html(source: dict[str, Any], client: PublicHttpClient) -> list[RawJob]:
    """Parse target links and JobPosting JSON-LD from an official career site."""
    list_url = str(source["list_url"])
    base_url = str(source.get("base_url") or list_url)
    output: list[RawJob] = []
    seen: set[str] = set()
    for term in SEARCH_TERMS:
        page = BeautifulSoup(client.get_text(list_url, params={"query": term}), "html.parser")
        for link in page.select('a[href*="/jobs/"]'):
            title = clean_text(link.get_text(" ", strip=True))
            if not is_target_role(title):
                continue
            href = clean_text(link.get("href"))
            url = href if href.startswith("http") else f"{base_url.rstrip('/')}/{href.lstrip('/')}"
            if url in seen:
                continue
            seen.add(url)
            detail = BeautifulSoup(client.get_text(url), "html.parser")
            posting: dict[str, Any] = {}
            for script in detail.select('script[type="application/ld+json"]'):
                try:
                    value = json.loads(script.string or script.get_text())
                except (TypeError, json.JSONDecodeError):
                    continue
                values = value if isinstance(value, list) else [value]
                posting = next((entry for entry in values if isinstance(entry, dict) and entry.get("@type") == "JobPosting"), {})
                if posting:
                    break
            location_value = posting.get("jobLocation")
            location = clean_text(location_value)
            if isinstance(location_value, dict):
                address = location_value.get("address") if isinstance(location_value.get("address"), dict) else {}
                location = ", ".join(clean_text(address.get(part)) for part in ("addressLocality", "addressRegion", "addressCountry") if clean_text(address.get(part)))
            identifier = url.rstrip("/").rsplit("/", 1)[-1].split("-", 1)[0]
            output.append(RawJob(
                title=clean_text(posting.get("title") or title),
                company=source["name"],
                location=location,
                description=clean_text(posting.get("description")),
                source="COMPANY_CAREER_PAGE",
                source_id=f"official-{source.get('slug')}-{identifier}",
                url=url,
                posted_at=posting.get("datePosted"),
            ))
    return output


FETCHERS = {
    "greenhouse": fetch_greenhouse,
    "lever": fetch_lever,
    "ashby": fetch_ashby,
    "amazon": fetch_amazon,
    "workday": fetch_workday,
    "oracle": fetch_oracle,
    "workable": fetch_workable,
    "smartrecruiters": fetch_smartrecruiters,
    "phenom": fetch_phenom,
    "official_html": fetch_official_html,
}
