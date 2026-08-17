"""Verified public, credential-free company career sources.

Each ATS endpoint below was resolved from an official employer career page and
returned HTTP 200 on 2026-08-14. No session, login, cookie, private API, or
browser automation is used.
"""

SOURCES = [
    {
        "kind": "phenom",
        "name": "G42",
        "base_url": "https://careers.g42.ai",
        "site_path": "global/en",
    },
    {
        "kind": "oracle",
        "name": "AD Ports Group",
        "host": "fa-ewzx-saasfaprod1.fa.ocs.oraclecloud.com",
        "site": "CX_1",
    },
    {
        "kind": "oracle",
        "name": "First Abu Dhabi Bank",
        "host": "ehjd.fa.em2.oraclecloud.com",
        "site": "fabCareers",
    },
    {
        "kind": "oracle",
        "name": "DP World",
        "host": "ehpv.fa.em2.oraclecloud.com",
        "site": "CX_1",
    },
    {
        "kind": "oracle",
        "name": "Emaar Hospitality Group",
        "host": "emhm.fa.em2.oraclecloud.com",
        "site": "CX_1001",
    },
    {"kind": "smartrecruiters", "name": "Etihad Airways", "slug": "EtihadAirways5"},
    {"kind": "smartrecruiters", "name": "Masdar", "slug": "masdar"},
    {"kind": "lever", "name": "Aldar", "slug": "aldar"},
    {"kind": "greenhouse", "name": "Careem", "slug": "careem"},
    {"kind": "workable", "name": "Dubizzle Group", "slug": "bayutdubizzle"},
    {
        "kind": "official_html",
        "name": "Chalhoub Group",
        "slug": "chalhoub",
        "base_url": "https://careers.chalhoubgroup.com",
        "list_url": "https://careers.chalhoubgroup.com/jobs",
    },
    {
        "kind": "workday",
        "name": "Kyndryl",
        "host": "kyndryl.wd5.myworkdayjobs.com",
        "tenant": "kyndryl",
        "site": "KyndrylProfessionalCareers",
    },
    {
        "kind": "workday",
        "name": "Johnson & Johnson",
        "host": "jj.wd5.myworkdayjobs.com",
        "tenant": "jj",
        "site": "JJ",
    },
    {
        "kind": "workday",
        "name": "GE HealthCare",
        "host": "gehc.wd5.myworkdayjobs.com",
        "tenant": "gehc",
        "site": "GEHC_ExternalSite",
    },
    {
        "kind": "workday",
        "name": "AstraZeneca",
        "host": "astrazeneca.wd3.myworkdayjobs.com",
        "tenant": "astrazeneca",
        "site": "Careers",
    },
    {"kind": "greenhouse", "name": "Canonical", "slug": "canonical"},
    {"kind": "greenhouse", "name": "Cloudflare", "slug": "cloudflare"},
    {"kind": "lever", "name": "Binance", "slug": "binance"},
    {"kind": "lever", "name": "Palantir", "slug": "palantir"},
]

SEARCH_TERMS = (
    "DevOps Engineer",
    "Site Reliability Engineer",
    "SRE",
    "Cloud Engineer",
    "Cloud Architect",
    "Platform Engineer",
)

