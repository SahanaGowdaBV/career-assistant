"use client";

import {useCallback, useEffect, useMemo, useState} from "react";

type SourceHealth = {
  sourceKey:string; sourceName:string; sourceKind:string; sourceStatus:"ACTIVE"|"CATALOG_ONLY"|"LINK_ONLY"|"FAILED";
  careerUrl:string|null; discovered:number; fetched:number; accepted:number; rejected:number; duplicates:number;
  elapsedMs:number; errorType:string|null; lastRunAt:string|null; lastSuccessAt:string|null;
};

const statusLabel = (status:SourceHealth["sourceStatus"]) => ({ACTIVE:"Active", CATALOG_ONLY:"Catalog only", LINK_ONLY:"Link only", FAILED:"Failed"}[status]);

export default function Companies({apiBase, apiFetch}:{apiBase:string; apiFetch:(input:RequestInfo|URL, init?:RequestInit)=>Promise<Response>}) {
  const [sources,setSources] = useState<SourceHealth[]>([]);
  const [loading,setLoading] = useState(true);
  const [error,setError] = useState<string|null>(null);
  const load = useCallback(async()=>{
    setError(null);
    try {
      const response = await apiFetch(`${apiBase}/scraper/sources`, {cache:"no-store"});
      if (!response.ok) throw new Error(`Source health unavailable (${response.status})`);
      const data = await response.json();
      setSources(Array.isArray(data) ? data as SourceHealth[] : []);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Source health unavailable"); }
    finally { setLoading(false); }
  }, [apiBase, apiFetch]);
  useEffect(()=>{const timer=window.setTimeout(()=>{void load();},0); return()=>window.clearTimeout(timer);},[load]);
  const counts = useMemo(()=>({active:sources.filter(s=>s.sourceStatus==="ACTIVE").length,catalog:sources.filter(s=>s.sourceStatus==="CATALOG_ONLY").length,link:sources.filter(s=>s.sourceStatus==="LINK_ONLY").length,failed:sources.filter(s=>s.sourceStatus==="FAILED").length}),[sources]);
  return <section className="companiesPage">
    <style>{`.companiesPage{background:#fff;border:1px solid var(--line);border-radius:13px;overflow:hidden}.sourceSummary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:0 22px 20px}.sourceSummary article{border:1px solid var(--line);border-radius:9px;padding:12px 14px}.sourceSummary strong,.sourceSummary span{display:block}.sourceSummary strong{font-size:23px;color:var(--green)}.sourceSummary span{font-size:11px;color:var(--muted);margin-top:3px}.sourceStatus{display:inline-block;font-size:10px;font-weight:700;padding:5px 8px;border-radius:12px;background:#eef1ef}.sourceStatus.active{color:#176340;background:#e4f4ea}.sourceStatus.catalog_only{color:#66716b;background:#f0f2f1}.sourceStatus.link_only{color:#805c15;background:#fff4d7}.sourceStatus.failed{color:#a43f39;background:#fde9e7}.acceptedCount{font-weight:800;color:var(--green)}.companiesPage a{color:var(--green);text-decoration:none}@media(max-width:620px){.sourceSummary{grid-template-columns:repeat(2,1fr)}}`}</style>
    <div className="panelHead"><div><h2>Career sources</h2><p>Automated feeds use permitted public employer ATS endpoints. Board links remain available without copying restricted content.</p></div><button className="ghost" onClick={()=>{setLoading(true);void load();}}>Refresh health ↻</button></div>
    <div className="sourceSummary"><article><strong>{counts.active}</strong><span>Active</span></article><article><strong>{counts.catalog}</strong><span>Catalog only</span></article><article><strong>{counts.link}</strong><span>Link only</span></article><article><strong>{counts.failed}</strong><span>Failed</span></article></div>
    {error && <div className="notice">{error}</div>}
    {loading ? <div className="state">Loading source health…</div> : sources.length === 0 ? <div className="state">No live scan has reported source health yet.</div> : <div className="tableWrap"><table><thead><tr><th>SOURCE</th><th>STATUS</th><th>FETCHED</th><th>ACCEPTED</th><th>REJECTED</th><th>DUPLICATES</th><th>LAST RUN</th></tr></thead><tbody>{sources.map(source=><tr key={source.sourceKey}><td><b>{source.sourceName}</b><small>{source.sourceKind.replaceAll("_"," ")}{source.careerUrl&&<> · <a href={source.careerUrl} target="_blank" rel="noreferrer">career page ↗</a></>}</small></td><td><span className={`sourceStatus ${source.sourceStatus.toLowerCase()}`}>{statusLabel(source.sourceStatus)}</span>{source.errorType&&<small>{source.errorType}</small>}</td><td>{source.fetched.toLocaleString()}</td><td className="acceptedCount">{source.accepted.toLocaleString()}</td><td>{source.rejected.toLocaleString()}</td><td>{source.duplicates.toLocaleString()}</td><td>{source.lastRunAt?new Date(source.lastRunAt).toLocaleString("en-AE",{dateStyle:"medium",timeStyle:"short"}):"Not run"}</td></tr>)}</tbody></table></div>}
  </section>;
}

export {statusLabel};
