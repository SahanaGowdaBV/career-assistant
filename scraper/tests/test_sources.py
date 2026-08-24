from career_scraper.filtering import normalize
from career_scraper.sources import fetch_ashby


class FakeClient:
    def get_json(self, _url):
        return {
            "jobs": [
                {
                    "id": "platform-1",
                    "isListed": True,
                    "title": "Platform Engineer (Developer Enablement)",
                    "location": "Dubai",
                    "secondaryLocations": [],
                    "descriptionPlain": "Bring 3+ years of experience building developer platforms.",
                    "jobUrl": "https://jobs.ashbyhq.com/example/platform-1",
                    "publishedAt": "2026-08-24T08:00:00Z",
                },
                {
                    "id": "support-1",
                    "isListed": True,
                    "title": "Platform Support Engineer",
                    "location": "Dubai",
                    "descriptionPlain": "Support role.",
                    "jobUrl": "https://jobs.ashbyhq.com/example/support-1",
                },
            ]
        }


def test_ashby_fetcher_maps_only_target_roles_to_ingestion_contract():
    jobs = fetch_ashby({"slug": "example", "name": "Example"}, FakeClient())

    assert len(jobs) == 1
    assert jobs[0].source_id == "ashby-example-platform-1"
    normalized, reason = normalize(jobs[0])
    assert reason is None
    assert normalized is not None
    assert normalized.title == "Platform Engineer (Developer Enablement)"
    assert normalized.location == "Dubai"
    assert (normalized.experience_min, normalized.experience_max) == (3, None)
