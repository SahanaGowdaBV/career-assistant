# UAE public job scraper

This component reads only public candidate-facing ATS endpoints. It supports
Workday, Oracle HCM, Greenhouse, Lever, and Workable without accounts, cookies, browser
sessions, CAPTCHA handling, or private APIs. The source catalog contains 92
Dubai/UAE employers: 35 active public ATS integrations and 57 catalog entries
whose endpoints still need verification. A catalog entry is deliberately
reported as `catalog_only` and is never contacted until an adapter is verified.
Add or promote a source in `career_scraper/config.py` only after its official
public ATS endpoint has been verified.

The pipeline defaults to dry-run and processes at most 200 target-role
candidates per run. A job must have at least one explicit UAE location and a
target DevOps, DevSecOps, SRE, platform, cloud, infrastructure, production,
Kubernetes, or related solutions-architecture title. Multi-location vacancies
remain eligible when the UAE is one of the advertised locations; India-only and
other non-UAE vacancies remain excluded. Junior, graduate, trainee, and intern
roles are excluded. Numeric experience requirements must overlap 4–8 years;
jobs without a numeric range are retained with nullable experience fields and
`experienceUnknown=true` for review. Matching descriptions are scanned for the
configured AWS/Kubernetes/Terraform/Docker/CI/CD and observability keywords.
Those keywords affect ranking, not basic eligibility. Live ingestion remains
capped at 50 jobs.

For Workday sources, the scraper first discovers the public UAE country facet
and applies it before paging or enforcing the per-source candidate cap. This
prevents high-volume global employers from crowding UAE vacancies out of the
scan.

```bash
python -m pip install -r scraper/requirements.txt
python scraper/run.py --dry-run --max-results 200 --max-candidates 200 \
  --summary-file /tmp/career-scraper-summary.json \
  --output-file /tmp/career-scraper-jobs.json
```

Live ingestion is opt-in and goes only through the backend deduplicating API:

```bash
CAREER_API_URL=http://localhost:8080/api \
SCRAPER_INGESTION_TOKEN='<configured-secret>' \
python scraper/run.py --live --max-results 50
```

The scraper never creates applications and never writes to Supabase directly.
