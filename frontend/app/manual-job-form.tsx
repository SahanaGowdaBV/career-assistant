"use client";

import {FormEvent, useState} from "react";
import {ManualJobDraft, manualJobFields, manualJobPayload, validateManualJob} from "./lib/manual-job-form";
import styles from "./manual-job.module.css";

const empty:ManualJobDraft={title:"",company:"",location:"",experienceText:"",description:"",applicationUrl:"",sourcePortal:""};
const keys:(keyof ManualJobDraft)[]=["title","company","location","experienceText","description","applicationUrl","sourcePortal"];

export default function ManualJobForm({apiBase,apiFetch,onCreated}:{apiBase:string;apiFetch:(input:RequestInfo|URL,init?:RequestInit)=>Promise<Response>;onCreated:(job:unknown)=>void}){
  const [draft,setDraft]=useState(empty);const [errors,setErrors]=useState<Record<string,string>>({});const [busy,setBusy]=useState(false);const [notice,setNotice]=useState<string|null>(null);
  const submit=async(event:FormEvent)=>{event.preventDefault();const next=validateManualJob(draft);setErrors(next);if(Object.keys(next).length)return;setBusy(true);setNotice(null);try{const response=await apiFetch(`${apiBase}/jobs/manual`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(manualJobPayload(draft))});const body=await response.json().catch(()=>null) as {message?:string}|null;if(!response.ok)throw new Error(body?.message||`Request failed (${response.status})`);onCreated(body);setDraft(empty);setNotice("Job saved. It is ready for evidence-based scoring and document generation.");}catch(error){setNotice(error instanceof Error?error.message:"Job could not be saved");}finally{setBusy(false);}};
  return <section className={`panel ${styles.panel}`}><div className="panelHead"><div><h2>Add job manually</h2><p>Copy a public posting from an unsupported portal. The backend never visits the URL or stores portal credentials.</p></div></div>{notice&&<div className={`notice ${styles.notice}`} role="status">{notice}</div>}<form className={styles.form} onSubmit={event=>void submit(event)}>{keys.map((key,index)=><label key={key} className={`${styles.field} ${key==="description"?styles.wide:""}`}><span>{manualJobFields[index]}</span>{key==="description"?<textarea rows={10} required value={draft[key]} onChange={event=>setDraft(value=>({...value,[key]:event.target.value}))}/>:<input type={key==="applicationUrl"?"url":"text"} required={key!=="experienceText"} value={draft[key]} onChange={event=>setDraft(value=>({...value,[key]:event.target.value}))}/>} {errors[key]&&<small role="alert">{errors[key]}</small>}</label>)}<button className="primary" disabled={busy} type="submit">{busy?"Saving…":"Save and score job"}</button></form></section>;
}
