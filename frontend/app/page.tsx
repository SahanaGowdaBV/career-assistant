"use client";

import {useEffect, useMemo, useState} from "react";
import ResumeVersions from "./resume-versions";

type Job = {id:string; title:string|null; companyId:string|null; companyName?:string|null; description:string|null; location:string|null; country:string|null; city:string|null; employmentType:string|null; experienceMin:number|null; experienceMax:number|null; salaryMin:number|null; salaryMax:number|null; salaryCurrency:string|null; source:string|null; sourceJobId:string|null; jobUrl:string|null; postedAt:string|null; scrapedAt:string|null; status:string|null; createdAt:string|null; updatedAt:string|null};
type NormalizedJob = {id:string; title:string; companyId:string; companyName:string; location:string; city:string; source:string; status:string; postedAt:string; description:string; experienceMin:number|null; experienceMax:number|null; jobUrl:string};
type JobPage = {content?: Job[] | null};

const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
const skills = ["AWS", "Kubernetes", "Terraform", "Docker", "GitHub Actions", "Helm", "Linux", "CI/CD", "Grafana", "Monitoring"];
const nav = ["Overview", "New Jobs", "High Score Jobs", "Auto Applied", "Pending Review", "Failed Applications", "Successfully Applied", "Resume Versions", "Cover Letters", "Companies", "Analytics", "Settings"];
const navIcons = ["⌂", "＋", "★", "⚡", "◷", "!", "✓", "▤", "✉", "◇", "↗", "⚙"];
const text = (value:string|null|undefined, fallback="") => typeof value === "string" ? value : fallback;
const normalizeJob = (job:Job, index:number):NormalizedJob => ({id:text(job.id, `unknown-job-${index}`), title:text(job.title, "Untitled role"), companyId:text(job.companyId), companyName:text(job.companyName), location:text(job.location, "Location unavailable"), city:text(job.city), source:text(job.source, "UNKNOWN"), status:text(job.status, "UNKNOWN"), postedAt:text(job.postedAt), description:text(job.description), experienceMin:typeof job.experienceMin === "number" ? job.experienceMin : null, experienceMax:typeof job.experienceMax === "number" ? job.experienceMax : null, jobUrl:text(job.jobUrl)});
const dateValue = (value:string) => {const parsed=Date.parse(value); return Number.isNaN(parsed) ? 0 : parsed;};
const formatDate = (value:string) => {const parsed=Date.parse(value); return Number.isNaN(parsed) ? "Date unavailable" : new Date(parsed).toLocaleDateString("en-AE", {month:"short", day:"numeric"});};
const formatExperience = (minimum:number|null, maximum:number|null) => minimum !== null && maximum !== null ? `${minimum}–${maximum} years` : minimum !== null ? `${minimum}+ years` : maximum !== null ? `Up to ${maximum} years` : "Experience not specified";
const demo:NormalizedJob[] = [
  {id:"demo-1", title:"Senior DevOps Engineer", companyId:"", companyName:"", location:"Dubai, UAE", city:"Dubai", source:"LINKEDIN", status:"HIGH_SCORE", postedAt:"2026-08-14T08:00:00Z", description:"Build AWS and Kubernetes platforms using Terraform, Helm, Docker, GitHub Actions, Linux and Grafana.", experienceMin:5, experienceMax:8, jobUrl:"https://example.com/jobs/devops"},
  {id:"demo-2", title:"Site Reliability Engineer", companyId:"", companyName:"", location:"Abu Dhabi, UAE", city:"Abu Dhabi", source:"COMPANY_CAREERS", status:"NEW", postedAt:"2026-08-13T09:00:00Z", description:"Own SRE, monitoring, CI/CD and cloud reliability practices.", experienceMin:4, experienceMax:7, jobUrl:"https://example.com/jobs/sre"},
  {id:"demo-3", title:"Platform Engineer", companyId:"", companyName:"", location:"UAE Remote", city:"Remote", source:"FIXTURE", status:"PENDING_REVIEW", postedAt:"2026-08-12T09:00:00Z", description:"Platform engineering with Kubernetes, AWS and Terraform.", experienceMin:4, experienceMax:6, jobUrl:"https://example.com/jobs/platform"},
];

export default function Home() {
  const [jobs, setJobs] = useState<NormalizedJob[]>(demo);
  const [active, setActive] = useState("Overview");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [sort, setSort] = useState("score");
  const [selected, setSelected] = useState<NormalizedJob|null>(null);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);
  const [page, setPage] = useState(1);

  useEffect(() => {
    fetch(`${api}/jobs/page?size=50`)
      .then(response => {if (!response.ok) throw new Error(); return response.json() as Promise<JobPage>;})
      .then(data => setJobs(Array.isArray(data.content) ? data.content.map(normalizeJob) : demo))
      .catch(() => setOffline(true))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => jobs
    .filter(job => (job.title + " " + job.location).toLowerCase().includes(query.toLowerCase()) && (status === "ALL" || job.status === status))
    .sort((a, b) => sort === "title" ? a.title.localeCompare(b.title) : dateValue(b.postedAt) - dateValue(a.postedAt)), [jobs, query, status, sort]);
  const score = (job:NormalizedJob) => Math.min(98, 45 + skills.filter(skill => job.description.toLowerCase().includes(skill.toLowerCase())).length * 6);
  const shown = filtered.slice((page - 1) * 8, page * 8);
  const counts = {New:jobs.filter(job => job.status === "NEW").length, "High score":jobs.filter(job => score(job) >= 75).length, "Pending review":jobs.filter(job => job.status === "PENDING_REVIEW").length, Applied:jobs.filter(job => job.status.includes("APPLIED")).length};

  const update = async (job:NormalizedJob, next:string) => {
    setJobs(value => value.map(item => item.id === job.id ? {...item, status:next} : item));
    setSelected({...job, status:next});
    if (!job.id.startsWith("demo")) await fetch(`${api}/jobs/${job.id}/status`, {method:"PATCH", headers:{"Content-Type":"application/json"}, body:JSON.stringify({status:next})}).catch(() => setOffline(true));
  };

  const navigate = (name:string) => {setActive(name); setSelected(null);};

  return <div className="shell">
    <aside>
      <div className="brand"><span>CA</span><div>Career Assistant<small>UAE DevOps</small></div></div>
      <nav>{nav.map((name, index) => <button key={name} className={active === name ? "active" : ""} onClick={() => navigate(name)}><i>{navIcons[index]}</i>{name}</button>)}</nav>
      <div className="safety"><b>Dry-run protected</b><small>No real applications are submitted.</small></div>
    </aside>
    <main>
      <header><div><p className="eyebrow">CAREER WORKSPACE</p><h1>{active}</h1><p>UAE roles matched to your verified experience and skills.</p></div>{active !== "Resume Versions" && <button className="primary" onClick={() => navigate("New Jobs")}>＋ Review new jobs</button>}</header>
      {active === "Resume Versions" ? <ResumeVersions apiBase={api}/> : <>
        {offline && <div className="notice">Demo mode · Backend unavailable. Showing safe UAE fixtures.</div>}
        <section className="cards">{Object.entries(counts).map(([name, value], index) => <article key={name}><span className={`dot d${index}`}/><div><small>{name}</small><strong>{value}</strong><em>{index === 1 ? "75+ match" : "Current pipeline"}</em></div></article>)}</section>
        <section className="panel">
          <div className="panelHead"><div><h2>Recent opportunities</h2><p>Dubai, Abu Dhabi, Sharjah and UAE remote · 4–8 years</p></div><button className="ghost" onClick={() => navigate("Analytics")}>View analytics ↗</button></div>
          <div className="filters"><label>⌕<input aria-label="Search jobs" placeholder="Search role or location" value={query} onChange={event => {setQuery(event.target.value); setPage(1);}}/></label><select aria-label="Status" value={status} onChange={event => setStatus(event.target.value)}><option>ALL</option><option>NEW</option><option>HIGH_SCORE</option><option>PENDING_REVIEW</option><option>FAILED</option></select><select aria-label="Sort" value={sort} onChange={event => setSort(event.target.value)}><option value="score">Newest first</option><option value="title">Title A–Z</option></select></div>
          {loading ? <div className="state">Loading opportunities…</div> : shown.length === 0 ? <div className="state">No jobs match these filters.</div> : <div className="tableWrap"><table><thead><tr><th>ROLE</th><th>LOCATION</th><th>MATCH</th><th>STATUS</th><th>POSTED</th><th/></tr></thead><tbody>{shown.map(job => <tr key={job.id} onClick={() => setSelected(job)}><td><b>{job.title}</b><small>{job.source.replaceAll("_", " ")}</small></td><td>{job.location}</td><td><span className="score">{score(job)}%</span></td><td><span className={`badge ${job.status.toLowerCase()}`}>{job.status.replaceAll("_", " ")}</span></td><td>{formatDate(job.postedAt)}</td><td>›</td></tr>)}</tbody></table></div>}
          <footer><span>Showing {shown.length} of {filtered.length} jobs</span><div><button disabled={page === 1} onClick={() => setPage(value => value - 1)}>‹</button><b>{page}</b><button disabled={page * 8 >= filtered.length} onClick={() => setPage(value => value + 1)}>›</button></div></footer>
        </section>
      </>}
    </main>
    {selected && <div className="scrim" onClick={() => setSelected(null)}><aside className="drawer" onClick={event => event.stopPropagation()}><button className="close" onClick={() => setSelected(null)}>×</button><p className="eyebrow">{selected.source}</p><h2>{selected.title}</h2><p>{selected.location} · {formatExperience(selected.experienceMin, selected.experienceMax)}</p><div className="bigScore"><strong>{score(selected)}%</strong><span>Overall match<small>Skills, location and experience</small></span></div><h3>Score explanation</h3><p>Strong UAE location and target experience overlap. Skills are matched only when present in the source description.</p><h3>Matched skills</h3><div className="chips good">{skills.filter(skill => selected.description.toLowerCase().includes(skill.toLowerCase())).map(skill => <span key={skill}>✓ {skill}</span>)}</div><h3>Missing skills</h3><div className="chips">{skills.filter(skill => !selected.description.toLowerCase().includes(skill.toLowerCase())).map(skill => <span key={skill}>{skill}</span>)}</div><div className="actions"><button onClick={() => void update(selected, "PENDING_REVIEW")}>Send to review</button><button className="primary" onClick={() => void update(selected, "READY_TO_APPLY")}>Mark ready</button></div>{selected.jobUrl && <a className="source" href={selected.jobUrl} target="_blank" rel="noreferrer">Open source posting ↗</a>}</aside></div>}
  </div>;
}
