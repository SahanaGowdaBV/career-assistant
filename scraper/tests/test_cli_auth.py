from __future__ import annotations

from typing import Any

import pytest

from career_scraper import cli


class FakeJob:
    def to_ingestion_dict(self) -> dict[str, str]:
        return {"title": "Platform Engineer"}


class FakePipeline:
    jobs: list[FakeJob] = []

    def __init__(self, **_: Any):
        pass

    def run(self) -> list[FakeJob]:
        return self.jobs

    def summary(self, *, dry_run: bool, jobs: list[FakeJob], ingestion: dict[str, Any] | None = None) -> dict[str, Any]:
        return {"dryRun": dry_run, "jobs": len(jobs), "ingestion": ingestion}

    def write_json(self, _path: str, _value: dict[str, Any]) -> None:
        pass


class FakeHttpClient:
    def __init__(self, **_: Any):
        self.posts: list[tuple[str, dict[str, Any], dict[str, str] | None]] = []

    def post(
        self,
        url: str,
        payload: dict[str, Any],
        *,
        headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        self.posts.append((url, payload, headers))
        return {"accepted": 1, "rejected": 0, "duplicates": 0, "jobs": [{}]}


def install_fakes(monkeypatch: pytest.MonkeyPatch, jobs: list[FakeJob]) -> FakeHttpClient:
    client = FakeHttpClient()
    FakePipeline.jobs = jobs
    monkeypatch.setattr(cli, "PublicHttpClient", lambda **_kwargs: client)
    monkeypatch.setattr(cli, "Pipeline", FakePipeline)
    return client


def test_live_ingestion_sends_configured_authentication_header(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Any,
    capsys: pytest.CaptureFixture[str],
) -> None:
    monkeypatch.setenv("CAREER_API_URL", "https://backend.example/api")
    monkeypatch.setenv("SCRAPER_INGESTION_TOKEN", "test-ingestion-token")
    client = install_fakes(monkeypatch, [FakeJob()])

    assert cli.main(["--live", "--summary-file", str(tmp_path / "summary.json")]) == 0

    assert len(client.posts) == 1
    url, payload, headers = client.posts[0]
    assert url == "https://backend.example/api/scraper/ingest"
    assert payload["dryRun"] is False
    assert headers == {"X-Scraper-Ingestion-Token": "test-ingestion-token"}
    captured = capsys.readouterr()
    assert "test-ingestion-token" not in captured.out
    assert "test-ingestion-token" not in captured.err


def test_live_ingestion_fails_before_scraping_when_token_is_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("CAREER_API_URL", "https://backend.example/api")
    monkeypatch.delenv("SCRAPER_INGESTION_TOKEN", raising=False)
    monkeypatch.setattr(cli, "PublicHttpClient", lambda **_kwargs: pytest.fail("scraping must not start"))

    with pytest.raises(SystemExit, match="SCRAPER_INGESTION_TOKEN is required for --live"):
        cli.main(["--live"])


def test_live_ingestion_fails_before_scraping_when_api_url_is_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("CAREER_API_URL", raising=False)
    monkeypatch.setenv("SCRAPER_INGESTION_TOKEN", "test-ingestion-token")
    monkeypatch.setattr(cli, "PublicHttpClient", lambda **_kwargs: pytest.fail("scraping must not start"))

    with pytest.raises(SystemExit, match="CAREER_API_URL or --api-url is required for --live"):
        cli.main(["--live"])


def test_dry_run_needs_no_backend_secrets(monkeypatch: pytest.MonkeyPatch, tmp_path: Any) -> None:
    monkeypatch.delenv("CAREER_API_URL", raising=False)
    monkeypatch.delenv("SCRAPER_INGESTION_TOKEN", raising=False)
    client = install_fakes(monkeypatch, [])

    assert cli.main(["--dry-run", "--summary-file", str(tmp_path / "summary.json")]) == 0
    assert client.posts == []
