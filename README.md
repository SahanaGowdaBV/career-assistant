# Career Assistant MVP

A Spring Boot 4 / Java 21 API, PostgreSQL/Flyway data layer, safe fixture ingestion pipeline, and responsive Next.js 16 dashboard for UAE DevOps opportunities. The application is deliberately dry-run only: it discovers, scores and tracks jobs but never submits a real application.

## Architecture

`frontend` calls REST APIs under `/api`. `backend` owns companies, jobs, scores, documents, applications and profile settings. `scraper` contains credential-free fixtures; `POST /api/scraper/ingest` normalizes, filters and deduplicates them by source plus source ID. Only Dubai, Abu Dhabi, Sharjah and UAE-remote roles overlapping 4–8 years are accepted; India markers are rejected.

## Local setup

Requirements: Java 21, Node 22+, npm, and PostgreSQL for normal local use. Tests use isolated H2 and never contact Supabase.

Backend environment variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Start safely with `cd backend && ./mvnw spring-boot:run` after pointing these at a disposable local database. Never use production/Supabase credentials during development.

Frontend: set `NEXT_PUBLIC_API_URL=http://localhost:8080/api` in your shell/runtime environment, then run `cd frontend && npm ci && npm run dev`. Without the API it intentionally shows UAE demo data and a demo-mode notice.

Tests: `cd backend && ./mvnw test`; `cd frontend && npm run lint && npx tsc --noEmit && npm run build`.

Demo ingestion: start the local backend, then run `./scripts/ingest-demo.sh`. The committed fixture has `dryRun: true`; change it only against a disposable local database to persist it. Source URLs and IDs are retained and duplicates are skipped.

## GitHub Actions and Supabase

Backend and frontend CI use dependency caching and concurrency guards. The scheduled/manual scraper uses only `secrets.CAREER_API_URL` and refuses non-dry-run execution. Add secrets in repository Settings; never commit them.

For Supabase deployment, first create a fresh staging project, set the backend datasource variables from its direct PostgreSQL connection, review V6 and all earlier Flyway migrations, take a backup, and start the backend once to migrate. Validate staging before repeating against production. This repository does not connect or migrate Supabase automatically.

## Current limitations

Live portal scraping, CAPTCHA handling and real auto-apply are excluded. Unsupported automation becomes `PENDING_REVIEW` with a reason. Application dry-runs record selected resume and cover-letter IDs; resume content is stored as authored and no experience or skill is invented. Authentication, object storage, portal-specific adapters, notifications and production observability remain production work.
