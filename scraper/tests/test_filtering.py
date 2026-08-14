from career_scraper.filtering import (
    choose_overlapping_experience,
    deduplicate,
    extract_experience,
    is_target_role,
    is_uae_location,
    normalize,
)
from career_scraper.models import Job, RawJob


def raw_job(**overrides) -> RawJob:
    values = {
        "title": "Senior DevOps Engineer",
        "company": "Example Company",
        "location": "Dubai, United Arab Emirates",
        "description": "Requires 5-7 years of relevant experience with Kubernetes.",
        "source": "WORKDAY",
        "source_id": "example-123",
        "url": "https://example.com/jobs/123",
        "posted_at": "2026-08-14T08:30:00Z",
    }
    values.update(overrides)
    return RawJob(**values)


def test_uae_filter_accepts_only_supported_locations():
    assert is_uae_location("Dubai, UAE")
    assert is_uae_location("Abu Dhabi, United Arab Emirates")
    assert is_uae_location("Sharjah, UAE")
    assert is_uae_location("Remote - UAE")
    assert is_uae_location("United Arab Emirates")
    assert is_uae_location("Ajman, UAE")
    assert not is_uae_location("Remote - EMEA")
    assert not is_uae_location("Global Remote")


def test_india_exclusion_wins_even_if_uae_is_also_present():
    assert not is_uae_location("Dubai, UAE / Bengaluru, India")
    job, reason = normalize(raw_job(location="Hyderabad, India"))
    assert job is None
    assert reason == "location"


def test_other_non_uae_regions_are_rejected_even_when_mixed_with_uae():
    for location in (
        "Dubai, UAE / Riyadh, Saudi Arabia",
        "UAE / Cairo, Egypt",
        "United Arab Emirates / Europe",
        "Dubai / United States",
    ):
        assert not is_uae_location(location)


def test_role_filter_is_strict_to_requested_role_families():
    accepted = [
        "DevOps Engineer",
        "Senior DevOps Engineer",
        "Lead DevOps Engineer",
        "DevSecOps Engineer",
        "Site Reliability Engineer",
        "SRE",
        "Cloud Engineer",
        "Cloud Architect",
        "Platform Engineer",
    ]
    for title in accepted:
        assert is_target_role(title), title
    for title in (
        "Lead SOC Engineer (DevOps)",
        "Cloud Security Engineer",
        "Senior Engineer, Platform Engineering and Architecture",
        "Senior Engineer - DevOps",
        "Junior DevOps Engineer",
        "Software Engineer",
        "Cloud Account Executive",
        "Platform Product Manager",
        "Data Engineer",
    ):
        assert not is_target_role(title), title


def test_security_false_positive_regressions():
    assert not is_target_role("Lead SOC Engineer (DevOps)")
    assert is_target_role("Senior DevOps Engineer")
    assert is_target_role("DevSecOps Engineer")
    assert not is_target_role("Cloud Security Engineer")
    assert is_target_role("Platform Engineer")


def test_experience_filter_requires_explicit_overlap_with_four_to_eight_years():
    assert choose_overlapping_experience(extract_experience("Requires 4-6 years of experience")) == (4, 6)
    assert choose_overlapping_experience(extract_experience("At least 5 years of experience")) == (5, None)
    assert choose_overlapping_experience(extract_experience("3 to 5 years experience")) == (3, 5)
    assert choose_overlapping_experience(extract_experience("Minimum 9 years experience")) is None
    assert choose_overlapping_experience(extract_experience("Three years experience")) is None
    assert choose_overlapping_experience(extract_experience("No experience range listed")) is None


def test_unknown_experience_is_accepted_with_nullable_fields():
    job, reason = normalize(raw_job(description="Experience building reliable Kubernetes platforms."))
    assert reason is None
    assert job is not None
    assert job.experience_unknown is True
    assert job.experience_min is None
    assert job.experience_max is None


def test_compatible_experience_is_accepted_and_incompatible_is_rejected():
    compatible, compatible_reason = normalize(raw_job(description="Requires 3-5 years of experience."))
    assert compatible_reason is None
    assert compatible is not None
    assert compatible.experience_unknown is False
    assert (compatible.experience_min, compatible.experience_max) == (3, 5)

    too_junior, junior_reason = normalize(raw_job(description="Requires exactly 2 years of experience."))
    too_senior, senior_reason = normalize(raw_job(description="Minimum 9 years of experience."))
    assert too_junior is None and junior_reason == "experience"
    assert too_senior is None and senior_reason == "experience"


def test_normalization_matches_backend_ingestion_contract():
    job, reason = normalize(raw_job(description="<p>Minimum 5 years of experience</p>"))
    assert reason is None
    assert job is not None
    assert job.to_ingestion_dict() == {
        "title": "Senior DevOps Engineer",
        "company": "Example Company",
        "location": "Dubai, United Arab Emirates",
        "description": "Minimum 5 years of experience",
        "experienceMin": 5,
        "experienceMax": None,
        "experienceUnknown": False,
        "source": "WORKDAY",
        "sourceId": "example-123",
        "url": "https://example.com/jobs/123",
        "postedAt": "2026-08-14T08:30:00Z",
    }


def test_deduplication_uses_source_id_and_canonicalized_url():
    first, _ = normalize(raw_job())
    assert first is not None
    same_source = Job(**{**first.__dict__, "url": "https://example.com/jobs/other"})
    same_url = Job(**{**first.__dict__, "source_id": "different", "url": "https://example.com/jobs/123/"})
    unique = Job(**{**first.__dict__, "source_id": "unique", "url": "https://example.com/jobs/unique"})
    jobs, duplicate_count = deduplicate([first, same_source, same_url, unique])
    assert jobs == [first, unique]
    assert duplicate_count == 2
