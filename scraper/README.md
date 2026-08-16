# UAE public job scraper

This component reads only public candidate-facing ATS endpoints. It supports
Workday, Oracle HCM, Greenhouse, Lever, and Workable without accounts, cookies, browser
sessions, CAPTCHA handling, or private APIs.

The pipeline defaults to dry-run and processes at most 200 target-role
candidates per run. A job must have an explicit UAE location, no excluded
non-UAE location marker, and a target DevOps/SRE/cloud/platform title. Numeric
experience requirements must overlap 4–8 years; jobs without a numeric range
are retained with nullable experience fields and `experienceUnknown=true` for
review. Live ingestion remains capped at 50 jobs.

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
