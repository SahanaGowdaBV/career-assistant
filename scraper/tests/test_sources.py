from career_scraper.filtering import deduplicate, normalize
import pytest

from career_scraper.config import SOURCES, validate_source
from career_scraper.sources import fetch_amazon, fetch_ashby, fetch_workday


class FakeClient:
    def get_json(self, _url):
        return {
            "jobs": [
                {
                    "id": "b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb",
                    "isListed": True,
                    "title": "Platform Engineer (Developer Enablement)",
                    "location": "Dubai",
                    "secondaryLocations": [],
                    "descriptionPlain": "Bring 5+ years of experience building developer platforms.",
                    "jobUrl": "https://jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb",
                    "publishedAt": "2026-08-24T08:00:00Z",
                },
                {
                    "id": "0e2861ef-b501-4591-88bd-f418a63620d8",
                    "isListed": True,
                    "title": "Senior Platform Engineer (Infrastructure)",
                    "location": "Dubai",
                    "secondaryLocations": [],
                    "descriptionPlain": "Own reliable infrastructure. You have 5+ years of experience in platform engineering.",
                    "jobUrl": "https://jobs.ashbyhq.com/ziina/0e2861ef-b501-4591-88bd-f418a63620d8",
                    "publishedAt": "2026-04-21T15:25:03Z",
                },
                {
                    "id": "marketing-1",
                    "isListed": True,
                    "title": "Head of Marketing",
                    "location": "Dubai",
                    "descriptionPlain": "Marketing leadership.",
                    "jobUrl": "https://jobs.ashbyhq.com/ziina/marketing-1",
                },
                {
                    "id": "unlisted-1",
                    "isListed": False,
                    "title": "Platform Engineer",
                    "location": "Dubai",
                    "jobUrl": "https://jobs.ashbyhq.com/ziina/unlisted-1",
                },
            ]
        }


def test_ashby_fetcher_enumerates_every_active_board_posting_before_filters():
    jobs = fetch_ashby({"slug": "ziina", "name": "Ziina"}, FakeClient())

    assert len(jobs) == 3
    assert {job.source_id for job in jobs} == {
        "ashby-ziina-b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb",
        "ashby-ziina-0e2861ef-b501-4591-88bd-f418a63620d8",
        "ashby-ziina-marketing-1",
    }


def test_ziina_platform_postings_are_normalized_and_not_collapsed():
    raw = fetch_ashby({"slug": "ziina", "name": "Ziina"}, FakeClient())
    normalized = [job for item in raw if (job := normalize(item)[0]) is not None]
    unique, duplicates = deduplicate(normalized)

    assert duplicates == 0
    assert len(unique) == 2
    assert {job.source_id for job in unique} == {
        "ashby-ziina-b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb",
        "ashby-ziina-0e2861ef-b501-4591-88bd-f418a63620d8",
    }


def test_target_infrastructure_posting_preserves_official_fields():
    raw = fetch_ashby({"slug": "ziina", "name": "Ziina"}, FakeClient())
    target = next(item for item in raw if item.source_id.endswith("0e2861ef-b501-4591-88bd-f418a63620d8"))
    job, reason = normalize(target)

    assert reason is None
    assert job is not None
    assert job.title == "Senior Platform Engineer (Infrastructure)"
    assert job.company == "Ziina"
    assert job.location == "Dubai"
    assert job.url == "https://jobs.ashbyhq.com/ziina/0e2861ef-b501-4591-88bd-f418a63620d8"
    assert job.source_id == "ashby-ziina-0e2861ef-b501-4591-88bd-f418a63620d8"
    assert "5+ years" in job.description
    assert (job.experience_min, job.experience_max) == (5, None)


class AmazonClient:
    def get_json(self, url, **kwargs):
        assert url == "https://www.amazon.jobs/en/search.json"
        assert kwargs["params"]["loc_query"] == "United Arab Emirates"
        return {"jobs": [{
            "id": "amazon-123",
            "title": "Cloud Infrastructure Engineer",
            "location": "Dubai, United Arab Emirates",
            "description": "Operate AWS infrastructure.",
            "basic_qualifications": "5+ years of experience with Kubernetes.",
            "preferred_qualifications": "Terraform experience preferred.",
            "job_path": "/en/jobs/amazon-123/cloud-infrastructure-engineer",
            "posted_date": "September 1, 2026",
        }]}


def test_amazon_uses_official_public_feed_and_stable_job_id():
    jobs = fetch_amazon({"kind": "amazon", "name": "Amazon"}, AmazonClient())
    assert jobs
    assert {job.source_id for job in jobs} == {"amazon-amazon-123"}
    assert jobs[0].url == "https://www.amazon.jobs/en/jobs/amazon-123/cloud-infrastructure-engineer"
    assert "5+ years" in jobs[0].description


class WorkdayClient:
    def __init__(self):
        self.search_payloads = []

    def post_json(self, _url, payload):
        if payload["searchText"] == "":
            return {"facets": [{
                "facetParameter": "locationCountry",
                "values": [
                    {"descriptor": "India", "id": "india-id"},
                    {"descriptor": "United Arab Emirates", "id": "uae-id"},
                ],
            }]}
        self.search_payloads.append(payload)
        if payload["searchText"] != "Cloud Engineer":
            return {"total": 0, "jobPostings": []}
        return {
            "total": 2,
            "jobPostings": [
                {
                    "title": "Cloud Engineer",
                    "locationsText": "Dubai, United Arab Emirates",
                    "externalPath": "/job/Dubai/Cloud-Engineer_1",
                    "bulletFields": ["REQ-1"],
                },
                {
                    "title": "Cloud Engineer",
                    "locationsText": "Bengaluru, India",
                    "externalPath": "/job/Bengaluru/Cloud-Engineer_2",
                    "bulletFields": ["REQ-2"],
                },
            ],
        }

    def get_json(self, url):
        if url.endswith("/job/Dubai/Cloud-Engineer_1"):
            return {"jobPostingInfo": {
                "title": "Cloud Engineer",
                "location": "Dubai, United Arab Emirates",
                "jobDescription": "Five years of AWS platform experience.",
                "externalPath": "/job/Dubai/Cloud-Engineer_1",
            }}
        return {"jobPostingInfo": {
            "title": "Cloud Engineer",
            "location": "Bengaluru, India",
            "jobDescription": "Five years of AWS platform experience.",
            "externalPath": "/job/Bengaluru/Cloud-Engineer_2",
        }}


def test_workday_discovers_and_applies_uae_country_facet_before_enumeration():
    client = WorkdayClient()
    jobs = fetch_workday({
        "kind": "workday",
        "name": "Example",
        "host": "example.wd1.myworkdayjobs.com",
        "tenant": "example",
        "site": "External",
    }, client)

    assert len(jobs) == 2
    assert {job.location for job in jobs} == {"Dubai, United Arab Emirates", "Bengaluru, India"}
    assert all(payload["appliedFacets"] == {"locationCountry": ["uae-id"]} for payload in client.search_payloads)


def test_workday_skips_global_enumeration_when_no_uae_country_facet_exists():
    class NoUaeWorkdayClient:
        def post_json(self, _url, payload):
            assert payload["searchText"] == ""
            return {"facets": [{
                "facetParameter": "locationCountry",
                "values": [{"descriptor": "India", "id": "india-id"}],
            }]}

    assert fetch_workday({
        "kind": "workday",
        "name": "Example",
        "host": "example.wd1.myworkdayjobs.com",
        "tenant": "example",
        "site": "External",
    }, NoUaeWorkdayClient()) == []


def test_official_source_configuration_is_allowlisted_and_includes_verified_additions():
    for source in SOURCES:
        validate_source(source)
    names = [source["name"] for source in SOURCES]
    assert len(names) >= 80
    assert len(names) == len(set(names))
    assert set(names) >= {
        "Amazon", "Accenture", "Ziina", "Careem", "Cisco", "PwC", "Autodesk", "Visa", "Unilever",
        "NVIDIA", "Intel", "Salesforce", "Red Hat", "Microsoft", "Google", "IBM", "SAP",
        "Oracle", "Core42", "Presight", "Technology Innovation Institute", "du",
        "Khazna Data Centers", "Emirates Group",
    }
    active_phenom_names = {"G42", "Core42", "Presight", "Technology Innovation Institute"}
    assert {
        source["name"] for source in SOURCES if source["kind"] == "phenom"
    } >= active_phenom_names
    active_workday_names = {"Cisco", "PwC", "Autodesk", "Visa", "Unilever", "NVIDIA", "Intel", "Salesforce", "Red Hat"}
    workday_mncs = {source["name"]: source for source in SOURCES if source["name"] in active_workday_names}
    assert all(source["career_url"].startswith("https://") for source in workday_mncs.values())
    assert {source["site"] for source in workday_mncs.values()} == {
        "Cisco_Careers", "Global_Experienced_Careers", "Ext", "Visa", "Unilever_Experienced_Professionals",
        "NVIDIAExternalCareerSite", "External", "External_Career_Site", "Jobs",
    }
    with pytest.raises(ValueError):
        validate_source({"kind": "official_html", "name": "Unsafe", "list_url": "http://private.invalid/jobs"})
    with pytest.raises(ValueError):
        validate_source({"kind": "linkedin", "name": "LinkedIn"})
