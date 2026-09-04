from career_scraper.models import RawJob
from career_scraper.pipeline import Pipeline
from career_scraper import sources as source_module


def test_pipeline_isolates_source_failures_and_caps_results(monkeypatch):
    def broken(_source, _client):
        raise TimeoutError("public endpoint timed out")

    def working(source, _client):
        return [RawJob(
            title="Platform Engineer",
            company=source["name"],
            location="Abu Dhabi, UAE",
            description="Requires 4-8 years of professional experience.",
            source="GREENHOUSE",
            source_id=f"{source['name']}-1",
            url=f"https://example.com/{source['name']}/1",
            posted_at=None,
        )]

    monkeypatch.setitem(source_module.FETCHERS, "greenhouse", broken)
    monkeypatch.setitem(source_module.FETCHERS, "lever", working)
    monkeypatch.setattr("career_scraper.pipeline.FETCHERS", source_module.FETCHERS)
    pipeline = Pipeline(
        sources=[
            {"name": "Broken", "kind": "greenhouse"},
            {"name": "Working", "kind": "lever"},
        ],
        client=object(),
        max_results=1,
    )
    jobs = pipeline.run()
    assert len(jobs) == 1
    assert [result.status for result in pipeline.source_results] == ["failed", "ok"]
    assert pipeline.source_results[0].error_type == "TimeoutError"


def test_pipeline_caps_processed_candidates(monkeypatch):
    def many(source, _client):
        return [RawJob(
            title="Cloud DevOps Engineer",
            company=source["name"],
            location="Dubai, UAE",
            description="Experience with cloud platforms.",
            source="GREENHOUSE",
            source_id=f"candidate-{index}",
            url=f"https://example.com/jobs/{index}",
        ) for index in range(5)]

    monkeypatch.setitem(source_module.FETCHERS, "greenhouse", many)
    monkeypatch.setattr("career_scraper.pipeline.FETCHERS", source_module.FETCHERS)
    pipeline = Pipeline(
        sources=[{"name": "Many", "kind": "greenhouse"}],
        client=object(),
        max_results=5,
        max_candidates=3,
    )
    jobs = pipeline.run()
    assert len(jobs) == 3
    assert pipeline.candidates_processed == 3
    assert pipeline.source_results[0].discovered == 5
    assert pipeline.source_results[0].fetched == 3


def test_pipeline_deduplicates_canonical_url_across_sources(monkeypatch):
    def jobs(source, _client):
        suffix = "?utm_source=partner" if source["name"] == "Second" else "/"
        return [RawJob(
            title="Platform Engineer",
            company=source["name"],
            location="Dubai, UAE",
            description="Requires 5 years of experience.",
            source=source["name"].upper(),
            source_id=f"{source['name']}-id",
            url=f"https://careers.example.com/jobs/123{suffix}",
        )]

    monkeypatch.setitem(source_module.FETCHERS, "greenhouse", jobs)
    monkeypatch.setitem(source_module.FETCHERS, "lever", jobs)
    monkeypatch.setattr("career_scraper.pipeline.FETCHERS", source_module.FETCHERS)
    pipeline = Pipeline(sources=[
        {"name": "First", "kind": "greenhouse"},
        {"name": "Second", "kind": "lever"},
    ], client=object(), max_results=5)

    result = pipeline.run()

    assert len(result) == 1
    assert pipeline.duplicates == 1
