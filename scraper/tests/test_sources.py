from career_scraper.filtering import deduplicate, normalize
from career_scraper.sources import fetch_ashby


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
