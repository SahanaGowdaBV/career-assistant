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
        "kind": "phenom",
        "name": "Core42",
        "base_url": "https://careers.g42.ai",
        "site_path": "core42/global/en",
        "career_url": "https://careers.g42.ai/core42/global/en",
    },
    {
        "kind": "phenom",
        "name": "Presight",
        "base_url": "https://careers.g42.ai",
        "site_path": "presight/global/en",
        "career_url": "https://careers.g42.ai/presight/global/en",
    },
    {
        "kind": "phenom",
        "name": "Technology Innovation Institute",
        "base_url": "https://careers.tii.ae",
        "site_path": "us/en",
        "career_url": "https://careers.tii.ae/us/en",
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
    {
        "kind": "workday",
        "name": "Cisco",
        "host": "cisco.wd5.myworkdayjobs.com",
        "tenant": "cisco",
        "site": "Cisco_Careers",
        "career_url": "https://cisco.wd5.myworkdayjobs.com/Cisco_Careers",
    },
    {
        "kind": "workday",
        "name": "PwC",
        "host": "pwc.wd3.myworkdayjobs.com",
        "tenant": "pwc",
        "site": "Global_Experienced_Careers",
        "career_url": "https://pwc.wd3.myworkdayjobs.com/Global_Experienced_Careers",
    },
    {
        "kind": "workday",
        "name": "Autodesk",
        "host": "autodesk.wd1.myworkdayjobs.com",
        "tenant": "autodesk",
        "site": "Ext",
        "career_url": "https://autodesk.wd1.myworkdayjobs.com/Ext",
    },
    {
        "kind": "workday",
        "name": "Visa",
        "host": "visa.wd5.myworkdayjobs.com",
        "tenant": "visa",
        "site": "Visa",
        "career_url": "https://visa.wd5.myworkdayjobs.com/Visa",
    },
    {
        "kind": "workday",
        "name": "Unilever",
        "host": "unilever.wd3.myworkdayjobs.com",
        "tenant": "unilever",
        "site": "Unilever_Experienced_Professionals",
        "career_url": "https://unilever.wd3.myworkdayjobs.com/Unilever_Experienced_Professionals",
    },
    {
        "kind": "workday",
        "name": "NVIDIA",
        "host": "nvidia.wd5.myworkdayjobs.com",
        "tenant": "nvidia",
        "site": "NVIDIAExternalCareerSite",
        "career_url": "https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite",
    },
    {
        "kind": "workday",
        "name": "Intel",
        "host": "intel.wd1.myworkdayjobs.com",
        "tenant": "intel",
        "site": "External",
        "career_url": "https://intel.wd1.myworkdayjobs.com/External",
    },
    {
        "kind": "workday",
        "name": "Salesforce",
        "host": "salesforce.wd12.myworkdayjobs.com",
        "tenant": "salesforce",
        "site": "External_Career_Site",
        "career_url": "https://salesforce.wd12.myworkdayjobs.com/External_Career_Site",
    },
    {
        "kind": "workday",
        "name": "Red Hat",
        "host": "redhat.wd5.myworkdayjobs.com",
        "tenant": "redhat",
        "site": "Jobs",
        "career_url": "https://redhat.wd5.myworkdayjobs.com/Jobs",
    },
    {"kind": "greenhouse", "name": "Canonical", "slug": "canonical"},
    {"kind": "greenhouse", "name": "Cloudflare", "slug": "cloudflare"},
    {"kind": "lever", "name": "Binance", "slug": "binance"},
    {"kind": "lever", "name": "Palantir", "slug": "palantir"},

    # Career-page catalog entries. These are intentionally not scraped until
    # their public ATS contract is verified and an adapter is enabled.
    {"kind": "catalog_only", "name": "Oracle", "career_url": "https://careers.oracle.com/"},
    {"kind": "catalog_only", "name": "du", "career_url": "https://www.du.ae/careers-join-us"},
    {"kind": "catalog_only", "name": "Khazna Data Centers", "career_url": "https://khaznadatacenters.com/careers/"},
    {"kind": "catalog_only", "name": "Emirates Group", "career_url": "https://www.emiratesgroupcareers.com/search-and-apply/"},
    {"kind": "catalog_only", "name": "Microsoft", "career_url": "https://careers.microsoft.com/"},
    {"kind": "catalog_only", "name": "Google", "career_url": "https://www.google.com/about/careers/applications/"},
    {"kind": "catalog_only", "name": "IBM", "career_url": "https://www.ibm.com/careers"},
    {"kind": "catalog_only", "name": "Dell Technologies", "career_url": "https://jobs.dell.com/"},
    {"kind": "catalog_only", "name": "SAP", "career_url": "https://jobs.sap.com/"},
    {"kind": "catalog_only", "name": "ServiceNow", "career_url": "https://careers.servicenow.com/jobs/"},
    {"kind": "catalog_only", "name": "Hewlett Packard Enterprise", "career_url": "https://careers.hpe.com/"},
    {"kind": "catalog_only", "name": "Broadcom", "career_url": "https://www.broadcom.com/company/careers"},
    {"kind": "catalog_only", "name": "VMware", "career_url": "https://www.broadcom.com/company/careers"},
    {"kind": "catalog_only", "name": "Equinix", "career_url": "https://careers.equinix.com/"},
    {"kind": "catalog_only", "name": "DXC Technology", "career_url": "https://www.dxc.com/us/en/careers"},
    {"kind": "catalog_only", "name": "NTT DATA", "career_url": "https://us.nttdata.com/en/careers"},
    {"kind": "catalog_only", "name": "Rackspace Technology", "career_url": "https://rackspace.jobs/"},
    {"kind": "catalog_only", "name": "Huawei", "career_url": "https://career.huawei.com/"},
    {"kind": "catalog_only", "name": "Ericsson", "career_url": "https://www.ericsson.com/en/careers"},
    {"kind": "catalog_only", "name": "Nokia", "career_url": "https://www.nokia.com/careers/"},
    {"kind": "catalog_only", "name": "Capgemini", "career_url": "https://www.capgemini.com/careers/"},
    {"kind": "catalog_only", "name": "Cognizant", "career_url": "https://careers.cognizant.com/"},
    {"kind": "catalog_only", "name": "Tata Consultancy Services", "career_url": "https://www.tcs.com/careers"},
    {"kind": "catalog_only", "name": "Wipro", "career_url": "https://careers.wipro.com/"},
    {"kind": "catalog_only", "name": "HCLTech", "career_url": "https://www.hcltech.com/careers"},
    {"kind": "catalog_only", "name": "Deloitte", "career_url": "https://www.deloitte.com/global/en/careers.html"},
    {"kind": "catalog_only", "name": "EY", "career_url": "https://www.ey.com/en_gl/careers"},
    {"kind": "catalog_only", "name": "KPMG", "career_url": "https://kpmg.com/xx/en/home/careers.html"},
    {"kind": "catalog_only", "name": "Emirates NBD", "career_url": "https://www.emiratesnbd.com/en/careers"},
    {"kind": "catalog_only", "name": "Mashreq", "career_url": "https://www.mashreq.com/en/uae/about-us/careers/"},
    {"kind": "catalog_only", "name": "Standard Chartered", "career_url": "https://www.sc.com/en/global-careers/"},
    {"kind": "catalog_only", "name": "HSBC", "career_url": "https://www.hsbc.com/careers"},
    {"kind": "catalog_only", "name": "Citi", "career_url": "https://jobs.citi.com/"},
    {"kind": "catalog_only", "name": "Mastercard", "career_url": "https://careers.mastercard.com/"},
    {"kind": "catalog_only", "name": "Noon", "career_url": "https://careers.noon.com/"},
    {"kind": "catalog_only", "name": "Talabat", "career_url": "https://careers.talabat.com/"},
    {"kind": "catalog_only", "name": "Property Finder", "career_url": "https://www.propertyfinder.ae/careers/"},
    {"kind": "catalog_only", "name": "Kitopi", "career_url": "https://www.kitopi.com/careers"},
    {"kind": "catalog_only", "name": "Tabby", "career_url": "https://www.tabby.ai/careers"},
    {"kind": "catalog_only", "name": "Network International", "career_url": "https://www.network.global/careers"},
    {"kind": "catalog_only", "name": "Checkout.com", "career_url": "https://www.checkout.com/careers"},
    {"kind": "catalog_only", "name": "M42", "career_url": "https://m42.ae/careers"},
    {"kind": "catalog_only", "name": "Injazat", "career_url": "https://www.injazat.com/careers/"},
    {"kind": "catalog_only", "name": "Moro Hub", "career_url": "https://www.morohub.com/careers/"},
    {"kind": "catalog_only", "name": "Bayanat", "career_url": "https://www.bayanat.ai/careers/"},
    {"kind": "catalog_only", "name": "Maersk", "career_url": "https://careers.maersk.com/"},
    {"kind": "catalog_only", "name": "DHL", "career_url": "https://careers.dhl.com/"},
    {"kind": "catalog_only", "name": "FedEx", "career_url": "https://careers.fedex.com/"},
    {"kind": "catalog_only", "name": "Siemens", "career_url": "https://www.siemens.com/global/en/company/jobs.html"},
    {"kind": "catalog_only", "name": "Schneider Electric", "career_url": "https://www.se.com/ww/en/about-us/careers/"},
    {"kind": "catalog_only", "name": "Honeywell", "career_url": "https://careers.honeywell.com/"},
    {"kind": "catalog_only", "name": "Johnson Controls", "career_url": "https://jobs.johnsoncontrols.com/"},
    {"kind": "catalog_only", "name": "Baker Hughes", "career_url": "https://careers.bakerhughes.com/"},
    {"kind": "catalog_only", "name": "Halliburton", "career_url": "https://jobs.halliburton.com/"},
    {"kind": "catalog_only", "name": "Shell", "career_url": "https://www.shell.com/careers.html"},
    {"kind": "catalog_only", "name": "bp", "career_url": "https://www.bp.com/en/global/corporate/careers.html"},
    {"kind": "catalog_only", "name": "TotalEnergies", "career_url": "https://careers.totalenergies.com/"},
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
    if kind not in {"amazon", "ashby", "greenhouse", "lever", "workable", "smartrecruiters", "workday", "oracle", "phenom", "official_html", "catalog_only"}:
        raise ValueError("Unsupported public source kind")
    if not str(source.get("name") or "").strip():
        raise ValueError("Official source name is required")
    if kind == "workday" and not str(source.get("host") or "").lower().endswith(".myworkdayjobs.com"):
        raise ValueError("Workday source host is not allowlisted")
    if kind == "oracle" and ".oraclecloud" not in str(source.get("host") or "").lower():
        raise ValueError("Oracle source host is not allowlisted")
    for key in ("base_url", "list_url", "career_url"):
        value = source.get(key)
        if value and not str(value).startswith("https://"):
            raise ValueError("Official career source URLs must use HTTPS")
