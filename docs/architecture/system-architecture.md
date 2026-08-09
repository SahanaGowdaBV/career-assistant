# Career Assistant — System Architecture

## Architecture

```text
                         GitHub
                           |
                   GitHub Actions
                           |
              +------------+------------+
              |                         |
           Vercel                    Railway
       Next.js Frontend          Spring Boot API
              |                         |
              +------------+------------+
                           |
                        Supabase
              PostgreSQL + Auth + Storage

Components
GitHub

Source control and project management.

GitHub Actions

Used for:

CI
Scheduled job scraping
Automated processing
Testing
Deployment workflows
Vercel

Hosts the Next.js frontend.

Railway

Hosts the Spring Boot backend API.

Supabase

Provides:

PostgreSQL
Authentication
Storage
Python Scraper

Collects UAE job postings and sends normalized job data to the backend.

