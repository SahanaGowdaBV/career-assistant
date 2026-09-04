"""Verified public, credential-free company career sources.

Each ATS endpoint below was resolved from an official employer career page and
returned HTTP 200 before inclusion. No session, login, cookie, private API, or
browser automation is used.
"""

SOURCES = [
    {
        "kind": "amazon",
        "name": "Amazon",
    },
    {
        "kind": "workday",
        "name": "Accenture",
        "host": "accenture.wd103.myworkdayjobs.com",
        "tenant": "accenture",
        "site": "AccentureCareers",
    },
    {
        "kind": "oracle",
        "name": "e&",
        "host": "iaayey.fa.ocs.oraclecloud26.com",
        "site": "CX_1",
    },
    {
        "kind": "ashby",
        "name": "Ziina",
        "slug": "ziina",
    },
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
    "Senior DevOps Engineer",
    "DevSecOps Engineer",
    "Site Reliability Engineer",
    "SRE",
    "Platform Engineer",
    "Cloud DevOps Engineer",
    "Cloud Infrastructure Engineer",
    "Infrastructure Engineer",
)

PRIORITY_KEYWORDS = (
    "AWS", "Kubernetes", "Docker", "Terraform", "Helm", "GitHub Actions",
    "Jenkins", "CI/CD", "Linux", "Grafana", "Prometheus", "Ansible",
    "CloudWatch", "EKS",
)


def validate_source(source: dict) -> None:
    """Fail closed before contacting anything except configured public career providers."""
    kind = source.get("kind")
    if kind not in {"amazon", "ashby", "greenhouse", "lever", "workable", "smartrecruiters", "workday", "oracle", "phenom", "official_html"}:
        raise ValueError("Unsupported public source kind")
    if not str(source.get("name") or "").strip():
        raise ValueError("Official source name is required")
    if kind == "workday" and not str(source.get("host") or "").lower().endswith(".myworkdayjobs.com"):
        raise ValueError("Workday source host is not allowlisted")
    if kind == "oracle" and ".oraclecloud" not in str(source.get("host") or "").lower():
        raise ValueError("Oracle source host is not allowlisted")
    for key in ("base_url", "list_url"):
        value = source.get(key)
        if value and not str(value).startswith("https://"):
            raise ValueError("Official career source URLs must use HTTPS")
