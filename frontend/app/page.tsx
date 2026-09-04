"use client";

import {useEffect, useMemo, useState} from "react";
import ResumeVersions from "./resume-versions";
import ApplicationWorkflow from "./application-workflow";
import Settings from "./settings";
import ManualJobForm from "./manual-job-form";
import {AuthenticationRequiredError, useAuth} from "./auth-provider";

type Job = {id:string; title:string|null; companyId:string|null; companyName?:string|null; description:string|null; location:string|null; country:string|null; city:string|null; employmentType:string|null; experienceMin:number|null; experienceMax:number|null; salaryMin:number|null; salaryMax:number|null; salaryCurrency:string|null; source:string|null; sourceJobId:string|null; sourcePortal:string|null;experienceText:string|null;experienceStatus:string|null;jobUrl:string|null; postedAt:string|null; scrapedAt:string|null; status:string|null; createdAt:string|null; updatedAt:string|null};
type NormalizedJob = {id:string; title:string; companyId:string; companyName:string; location:string; city:string; source:string; sourcePortal:string;sourceJobId:string; status:string; postedAt:string; description:string; experienceMin:number|null; experienceMax:number|null;experienceText:string;experienceStatus:string;jobUrl:string};
type JobPage = {content?: Job[] | null};
type JobScore = {score:number;scoringConfidence:string;experienceScore:number|null;locationScore:number|null;matchedKeywords:string|null;missingKeywords:string|null;scoringReason:string|null};
type ExistingApplication = {id:string;jobId:string;resumeVersionId:string|null;coverLetterId:string|null;status:string};

const api = process.env.NEXT_PUBLIC_API_URL || "";
const nav = ["Overview", "New Jobs", "Add Job Manually", "High Score Jobs", "Pending Review", "Ready to Apply", "Failed Applications", "Successfully Applied", "Resume Versions", "Cover Letters", "Companies", "Analytics", "Settings"];
const navIcons = ["⌂", "＋", "✎", "★", "◷", "→", "!", "✓", "▤", "✉", "◇", "↗", "⚙"];
const text = (value:string|null|undefined, fallback="") => typeof value === "string" ? value : fallback;
const normalizeJob = (job:Job, index:number):NormalizedJob => ({id:text(job.id, `unknown-job-${index}`), title:text(job.title, "Untitled role"), companyId:text(job.companyId), companyName:text(job.companyName), location:text(job.location, "Location unavailable"), city:text(job.city), source:text(job.source, "UNKNOWN"), sourcePortal:text(job.sourcePortal),sourceJobId:text(job.sourceJobId), status:text(job.status, "UNKNOWN"), postedAt:text(job.postedAt), description:text(job.description), experienceMin:typeof job.experienceMin === "number" ? job.experienceMin : null, experienceMax:typeof job.experienceMax === "number" ? job.experienceMax : null,experienceText:text(job.experienceText),experienceStatus:text(job.experienceStatus,"EXPERIENCE_UNKNOWN"),jobUrl:text(job.jobUrl)});
const dateValue = (value:string) => {const parsed=Date.parse(value); return Number.isNaN(parsed) ? 0 : parsed;};
const formatDate = (value:string) => {const parsed=Date.parse(value); return Number.isNaN(parsed) ? "Date unavailable" : new Date(parsed).toLocaleDateString("en-AE", {month:"short", day:"numeric"});};
const formatExperience = (minimum:number|null, maximum:number|null) => minimum !== null && maximum !== null ? `${minimum}–${maximum} years` : minimum !== null ? `${minimum}+ years` : maximum !== null ? `Up to ${maximum} years` : "Experience not specified";
const demo:NormalizedJob[] = [
  {id:"demo-1", title:"Senior DevOps Engineer", companyId:"", companyName:"", location:"Dubai, UAE", city:"Dubai", source:"COMPANY_CAREER_PAGE",sourcePortal:"", sourceJobId:"", status:"HIGH_SCORE", postedAt:"2026-08-14T08:00:00Z", description:"Build AWS and Kubernetes platforms using Terraform, Helm, Docker, GitHub Actions, Linux and Grafana.", experienceMin:5, experienceMax:8,experienceText:"5–8 years",experienceStatus:"EXPERIENCE_MATCH", jobUrl:"https://example.com/jobs/devops"},
  {id:"demo-2", title:"Site Reliability Engineer", companyId:"", companyName:"", location:"Abu Dhabi, UAE", city:"Abu Dhabi", source:"COMPANY_CAREER_PAGE",sourcePortal:"", sourceJobId:"", status:"NEW", postedAt:"2026-08-13T09:00:00Z", description:"Own SRE, monitoring, CI/CD and cloud reliability practices.", experienceMin:4, experienceMax:7,experienceText:"4–7 years",experienceStatus:"EXPERIENCE_MATCH", jobUrl:"https://example.com/jobs/sre"},
  {id:"demo-3", title:"Platform Engineer", companyId:"", companyName:"", location:"UAE Remote", city:"Remote", source:"FIXTURE",sourcePortal:"", sourceJobId:"", status:"PENDING_REVIEW", postedAt:"2026-08-12T09:00:00Z", description:"Platform engineering with Kubernetes, AWS and Terraform.", experienceMin:null, experienceMax:null,experienceText:"",experienceStatus:"EXPERIENCE_UNKNOWN", jobUrl:"https://example.com/jobs/platform"},
];

const portalSearches=[
  ["LinkedIn UAE jobs","https://www.linkedin.com/jobs/search/?keywords=DevOps&location=United%20Arab%20Emirates"],
  ["NaukriGulf UAE jobs","https://www.naukrigulf.com/devops-jobs-in-uae"],
  ["Bayt UAE jobs","https://www.bayt.com/en/uae/jobs/devops-jobs/"],
  ["GulfTalent UAE jobs","https://www.gulftalent.com/uae/jobs?keywords=devops"],
  ["Indeed UAE jobs","https://ae.indeed.com/jobs?q=devops&l=United+Arab+Emirates"],
] as const;

export default function Home() {
  const {apiFetch, session, signOut} = useAuth();
  const [jobs, setJobs] = useState<NormalizedJob[]>(demo);
  const [applications, setApplications] = useState<ExistingApplication[]>([]);
  const [active, setActive] = useState("Overview");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [sort, setSort] = useState("score");
  const [selected, setSelected] = useState<NormalizedJob|null>(null);
  const [jobScores,setJobScores]=useState<Record<string,JobScore>>({});
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);
  const [page, setPage] = useState(1);
  const [packageBusy,setPackageBusy]=useState(false);
  const [packageNotice,setPackageNotice]=useState<string|null>(null);

  useEffect(() => {
    Promise.all([apiFetch(`${api}/jobs/page?size=50`), apiFetch(`${api}/applications`,{cache:"no-store"})])
      .then(async([jobsResponse, applicationsResponse]) => {if (!jobsResponse.ok) throw new Error(); const data=await jobsResponse.json() as JobPage; setJobs(Array.isArray(data.content) ? data.content.map(normalizeJob) : demo); if (applicationsResponse.ok) setApplications(await applicationsResponse.json() as ExistingApplication[]);})
      .catch(error => {if (!(error instanceof AuthenticationRequiredError)) setOffline(true);})
      .finally(() => setLoading(false));
  }, [apiFetch]);

  const filtered = useMemo(() => jobs
    .filter(job => (job.title + " " + job.location).toLowerCase().includes(query.toLowerCase()) && (status === "ALL" || job.status === status))
    .sort((a, b) => sort === "title" ? a.title.localeCompare(b.title) : dateValue(b.postedAt) - dateValue(a.postedAt)), [jobs, query, status, sort]);
  const shown = filtered.slice((page - 1) * 8, page * 8);
  const counts = {New:jobs.filter(job => job.status === "NEW").length, "High score":jobs.filter(job => job.status === "HIGH_SCORE").length, "Pending review":jobs.filter(job => job.status === "PENDING_REVIEW").length, Applied:jobs.filter(job => job.status.includes("APPLIED")).length};

  const navigate = (name:string) => {setActive(name); setSelected(null);};
  const workflowViews=["Pending Review","Ready to Apply","Auto Applied","Failed Applications","Successfully Applied","Cover Letters"];
  const selectJob=async(job:NormalizedJob)=>{setSelected(job);if(job.id.startsWith("demo")||jobScores[job.id])return;const response=await apiFetch(`${api}/jobs/${job.id}/score`,{cache:"no-store"});if(response.ok){const result=await response.json() as JobScore;setJobScores(values=>({...values,[job.id]:result}));}};
  const generatePackage=async(job:NormalizedJob)=>{const score=jobScores[job.id];let confirmed=false;if(!score||score.scoringConfidence==="LOW"){confirmed=window.confirm("This job has LOW or unavailable scoring confidence. Confirm manual review before generating its package?");if(!confirmed)return;}setPackageBusy(true);setPackageNotice(null);try{const existing=applications.find(application=>application.jobId===job.id&&application.resumeVersionId&&application.coverLetterId);const endpoint=existing?`${api}/applications/${existing.id}/regenerate`:`${api}/applications/packages`;const options:RequestInit=existing?{method:"POST"}:{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({jobId:job.id,lowConfidenceConfirmed:confirmed})};const response=await apiFetch(endpoint,options);const body=await response.json().catch(()=>null) as (ExistingApplication&{message?:string})|null;if(!response.ok)throw new Error(body?.message||`Request failed (${response.status})`);if(body)setApplications(values=>[...values.filter(item=>item.jobId!==body.jobId),body]);setPackageNotice("Tailored résumé and cover letter generated for manual application review.");setSelected(null);navigate("Pending Review");}catch(error){setPackageNotice(error instanceof Error?error.message:"Package generation failed");}finally{setPackageBusy(false);}};
  const updateJobStatus=async(job:NormalizedJob,next:string)=>{setPackageBusy(true);setPackageNotice(null);try{const response=await apiFetch(`${api}/jobs/${job.id}/status`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify({status:next})});if(!response.ok)throw new Error(`Status update failed (${response.status})`);const updated=normalizeJob(await response.json() as Job,0);setJobs(values=>values.map(value=>value.id===updated.id?updated:value));setSelected(null);}catch(error){setPackageNotice(error instanceof Error?error.message:"Status update failed");}finally{setPackageBusy(false);}};
  const markManual=async(job:NormalizedJob)=>{const application=applications.find(item=>item.jobId===job.id);if(!application||application.status!=="READY_TO_APPLY")return;if(!window.confirm("Confirm that you personally submitted this application on the official site?"))return;setPackageBusy(true);try{const response=await apiFetch(`${api}/applications/${application.id}/manually-applied`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify({confirmed:true,confirmation:"Marked manually applied by authenticated user"})});if(!response.ok)throw new Error(`Manual status update failed (${response.status})`);setJobs(values=>values.map(value=>value.id===job.id?{...value,status:"MANUALLY_APPLIED"}:value));setSelected(null);}catch(error){setPackageNotice(error instanceof Error?error.message:"Manual status update failed");}finally{setPackageBusy(false);}};

  return <div className="shell">
    <aside>
      <div className="brand"><span>CA</span><div>Career Assistant<small>UAE DevOps</small></div></div>
      <nav>{nav.map((name, index) => <button key={name} className={active === name ? "active" : ""} onClick={() => navigate(name)}><i>{navIcons[index]}</i>{name}</button>)}</nav>
      <div className="safety"><b>Manual applications only</b><small>{session?.user.email || "Authenticated user"}</small><button onClick={() => void signOut()}>Log out</button></div>
    </aside>
    <main>
      <header><div><p className="eyebrow">CAREER WORKSPACE</p><h1>{active}</h1><p>UAE roles matched to your verified experience and skills.</p></div>{active !== "Resume Versions" && <button className="primary" onClick={() => navigate("New Jobs")}>＋ Review new jobs</button>}</header>
      <div className="notice">Applications are submitted manually. No portal credentials are stored.</div>
      {active === "Resume Versions" ? <ResumeVersions apiBase={api}/> : active === "Settings" ? <Settings apiBase={api} apiFetch={apiFetch}/> : active === "Add Job Manually" ? <ManualJobForm apiBase={api} apiFetch={apiFetch} onCreated={value=>{const job=normalizeJob(value as Job,0);setJobs(items=>[job,...items]);setActive("New Jobs");void selectJob(job);}}/> : workflowViews.includes(active) ? <ApplicationWorkflow apiBase={api} view={active}/> : <>
        {offline && <div className="notice">Demo mode · Backend unavailable. Showing safe UAE fixtures.</div>}
        <section className="cards">{Object.entries(counts).map(([name, value], index) => <article key={name}><span className={`dot d${index}`}/><div><small>{name}</small><strong>{value}</strong><em>{index === 1 ? "Ranked by evidence" : "Current pipeline"}</em></div></article>)}</section>
        <section className="panel">
          <div className="panelHead"><div><h2>Recent opportunities</h2><p>Dubai, Abu Dhabi, Sharjah and UAE remote · 4–8 years</p></div><button className="ghost" onClick={() => navigate("Analytics")}>View analytics ↗</button></div>
          <div className="chips">{portalSearches.map(([label,url])=><a key={label} href={url} target="_blank" rel="noreferrer">{label} ↗</a>)}</div>
          <div className="filters"><label>⌕<input aria-label="Search jobs" placeholder="Search role or location" value={query} onChange={event => {setQuery(event.target.value); setPage(1);}}/></label><select aria-label="Status" value={status} onChange={event => setStatus(event.target.value)}><option>ALL</option><option>NEW</option><option>HIGH_SCORE</option><option>PENDING_REVIEW</option><option>FAILED</option></select><select aria-label="Sort" value={sort} onChange={event => setSort(event.target.value)}><option value="score">Newest first</option><option value="title">Title A–Z</option></select></div>
          {loading ? <div className="state">Loading opportunities…</div> : shown.length === 0 ? <div className="state">No jobs match these filters.</div> : <div className="tableWrap"><table><thead><tr><th>ROLE</th><th>LOCATION</th><th>MATCH</th><th>STATUS</th><th>POSTED</th><th/></tr></thead><tbody>{shown.map(job => <tr key={job.id} onClick={() => void selectJob(job)}><td><b>{job.title}</b><small>{job.companyName||"Company unavailable"} · {(job.sourcePortal||job.source).replaceAll("_", " ")}</small></td><td>{job.location}</td><td><span className="score">{jobScores[job.id]?`${Math.round(jobScores[job.id].score)}%`:"Review"}</span></td><td><span className={`badge ${job.status.toLowerCase()}`}>{job.status.replaceAll("_", " ")}</span></td><td>{formatDate(job.postedAt)}</td><td>›</td></tr>)}</tbody></table></div>}
          <footer><span>Showing {shown.length} of {filtered.length} jobs</span><div><button disabled={page === 1} onClick={() => setPage(value => value - 1)}>‹</button><b>{page}</b><button disabled={page * 8 >= filtered.length} onClick={() => setPage(value => value + 1)}>›</button></div></footer>
        </section>
      </>}
    </main>
    {packageNotice&&<div className="notice">{packageNotice}</div>}{selected && <div className="scrim" onClick={() => setSelected(null)}><aside className="drawer" onClick={event => event.stopPropagation()}><button className="close" onClick={() => setSelected(null)}>×</button><p className="eyebrow">{selected.sourcePortal||selected.source}</p><h2>{selected.title}</h2><p>{selected.companyName||"Company unavailable"} · {selected.location} · {selected.experienceStatus==="EXPERIENCE_UNKNOWN"?"EXPERIENCE_UNKNOWN":formatExperience(selected.experienceMin, selected.experienceMax)}</p><p>Posted {formatDate(selected.postedAt)} · Source ID {selected.sourceJobId||"unavailable"}</p><div className="bigScore"><strong>{jobScores[selected.id]?`${Math.round(jobScores[selected.id].score)}%`:"…"}</strong><span>Résumé-backed match<small>Confidence: {jobScores[selected.id]?.scoringConfidence||"loading"}</small></span></div><h3>Score explanation</h3><p>{jobScores[selected.id]?.scoringReason||"Loading the evidence-based score…"}</p><p>Location fit: {jobScores[selected.id]?.locationScore??"…"}% · Experience fit: {jobScores[selected.id]?.experienceScore??"…"}%</p><h3>Matched skills</h3><div className="chips good">{(jobScores[selected.id]?.matchedKeywords||"").split(",").map(skill=>skill.trim()).filter(Boolean).map(skill => <span key={skill}>✓ {skill}</span>)}</div><h3>Missing skills</h3><div className="chips">{(jobScores[selected.id]?.missingKeywords||"").split(",").map(skill=>skill.trim()).filter(Boolean).map(skill => <span key={skill}>{skill}</span>)}</div><h3>Role description</h3><p>{selected.description||"Description unavailable"}</p><div className="actions"><button className="primary" disabled={packageBusy||selected.id.startsWith("demo")||!jobScores[selected.id]} onClick={()=>void generatePackage(selected)}>{packageBusy?"Generating…":applications.some(application=>application.jobId===selected.id&&application.resumeVersionId&&application.coverLetterId)?"Regenerate résumé + cover letter":"Generate résumé + cover letter"}</button><button disabled={packageBusy||selected.id.startsWith("demo")} onClick={()=>void updateJobStatus(selected,"REJECTED")}>Reject</button><button disabled={packageBusy||selected.id.startsWith("demo")} onClick={()=>void updateJobStatus(selected,"ARCHIVED")}>Archive</button><button className="primary" disabled={packageBusy||applications.find(item=>item.jobId===selected.id)?.status!=="READY_TO_APPLY"} onClick={()=>void markManual(selected)}>Mark manually applied</button></div>{selected.id.startsWith("demo")&&<p>Connect the backend and select a persisted job to generate documents.</p>}{selected.jobUrl && <a className="source" href={selected.jobUrl} target="_blank" rel="noreferrer">Open official posting ↗</a>}</aside></div>}
  </div>;
}
