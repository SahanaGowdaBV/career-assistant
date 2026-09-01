# Production deployment checklist

## Railway backend

Create a Railway service from this repository with **Root Directory** set to `backend`. The committed `backend/railway.toml` selects the multi-stage Java 21 Dockerfile, checks `/api/health`, and restarts failed deployments. Use the Dockerfile build; no custom start command is needed.

Configure the variables listed in `backend/.env.production.example`. Required authentication variables are `SUPABASE_AUTH_ISSUER`, `SUPABASE_AUTH_JWKS_URI`, and `APP_ALLOWED_EMAILS`; the application fails closed when they are absent. Set the database variables to production PostgreSQL. Keep `FLYWAY_ENABLED=true` for production. Railway supplies `PORT`; the application defaults to `8080` locally.

Keep `AUTO_APPLY_ENABLED=false` and `AUTO_APPLY_DRY_RUN=true`. The dry-run worker validates UAE location, supported ATS, `READY_TO_APPLY`, the score threshold (default 80), mandatory source-backed answers, duplicate/attempt state, and the daily limit (default 1), but it never submits an external form. Review redacted previews only; tokens and personal field values are not returned.

## Vercel frontend

Import this repository as a Vercel project and set **Root Directory** to `frontend`. Use the detected Next.js framework and default build command. Configure the three variables in `frontend/.env.production.example`; the production build fails clearly if any is missing. `NEXT_PUBLIC_SUPABASE_ANON_KEY` is a browser public key, never a service-role key.

## Supabase

Use the project Auth issuer and JWKS URL for Railway authentication variables. Add the Vercel deployment URL and local callback URL to Auth redirect allowlists. Keep the storage bucket private and provide the service-role key only to Railway when required; never expose it to Vercel.

## GitHub Actions

Configure `CAREER_API_URL` and `SCRAPER_INGESTION_TOKEN` as GitHub Actions secrets when the Railway URL exists. The scraper workflow remains dry-run unless explicitly selected.

Before launch, confirm Railway health is green, Vercel production variables are present, Supabase redirect/storage settings are correct, both auto-apply safety variables retain the values above, and no secret file is tracked. Do not run migrations or real cleanup without a reviewed production change window.
