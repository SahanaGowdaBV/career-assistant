from __future__ import annotations

import threading
import time
from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


class PublicHttpClient:
    def __init__(self, timeout: float = 15.0, retries: int = 2, rate_limit_seconds: float = 0.25):
        self.timeout = timeout
        self.rate_limit_seconds = max(0.0, rate_limit_seconds)
        self._last_request = 0.0
        self._lock = threading.Lock()
        self.session = requests.Session()
        retry = Retry(
            total=max(0, retries),
            connect=max(0, retries),
            read=max(0, retries),
            status=max(0, retries),
            backoff_factor=0.4,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=frozenset(("GET", "POST")),
            respect_retry_after_header=True,
        )
        self.session.mount("https://", HTTPAdapter(max_retries=retry))
        self.session.headers.update({
            "Accept": "application/json, text/plain;q=0.9, text/html;q=0.8",
            "User-Agent": "CareerAssistant-UAE-PublicJobs/1.0",
        })

    def _wait(self) -> None:
        with self._lock:
            wait_for = self.rate_limit_seconds - (time.monotonic() - self._last_request)
            if wait_for > 0:
                time.sleep(wait_for)
            self._last_request = time.monotonic()

    def get_json(self, url: str, **kwargs: Any) -> Any:
        self._wait()
        response = self.session.get(url, timeout=self.timeout, **kwargs)
        response.raise_for_status()
        return response.json()

    def get_text(self, url: str, **kwargs: Any) -> str:
        self._wait()
        response = self.session.get(url, timeout=self.timeout, **kwargs)
        response.raise_for_status()
        return response.text

    def post_json(self, url: str, payload: dict[str, Any], **kwargs: Any) -> Any:
        self._wait()
        response = self.session.post(url, json=payload, timeout=self.timeout, **kwargs)
        response.raise_for_status()
        return response.json()

    def post(self, url: str, payload: dict[str, Any], *, headers: dict[str, str] | None = None) -> Any:
        self._wait()
        response = self.session.post(url, json=payload, headers=headers, timeout=self.timeout)
        response.raise_for_status()
        return response.json()
