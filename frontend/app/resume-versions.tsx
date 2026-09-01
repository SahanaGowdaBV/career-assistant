"use client";

import {FormEvent, useCallback, useEffect, useState} from "react";
import {useAuth} from "./auth-provider";

type ResumeSummary = {
  id: string;
  filename: string;
  uploadedAt: string;
  version: number;
  status: "MASTER" | "CUSTOMIZED" | "READY" | "UPLOADED";
  master: boolean;
  customized: boolean;
  parsedSkills: string[];
  contentType: string;
  fileSize: number;
};

type Experience = {employer: string | null; jobTitle: string | null; employmentDates: string | null; highlights: string[]};
type ResumeDetails = ResumeSummary & {
  checksum: string | null;
  parsedText: string | null;
  parsed: {
    name: string | null;
    contact: {email: string | null; phone: string | null; linkedin: string | null; location: string | null};
    professionalSummary: string | null;
    experience: Experience[];
    skills: string[];
    certifications: string[];
    education: string[];
    achievements: string[];
  };
  customizationSummary: string | null;
};
type ApiError = {message?: string};
type Notice = {kind: "success" | "error"; text: string} | null;

const errorMessage = async (response: Response) => {
  const body = await response.json().catch(() => null) as ApiError | null;
  return body?.message || `Request failed (${response.status})`;
};

const formatDate = (value: string) => {
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? "Date unavailable" : new Date(parsed).toLocaleString("en-AE", {dateStyle: "medium", timeStyle: "short"});
};

const formatSize = (bytes: number) => bytes < 1024 * 1024
  ? `${Math.max(1, Math.round(bytes / 1024))} KB`
  : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

export default function ResumeVersions({apiBase}: {apiBase: string}) {
  const {apiFetch, downloadFile} = useAuth();
  const [resumes, setResumes] = useState<ResumeSummary[]>([]);
  const [selected, setSelected] = useState<ResumeDetails | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [makeMaster, setMakeMaster] = useState(false);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await apiFetch(`${apiBase}/resumes`, {cache: "no-store"});
      if (!response.ok) throw new Error(await errorMessage(response));
      setResumes(await response.json() as ResumeSummary[]);
    } catch (error) {
      setNotice({kind: "error", text: error instanceof Error ? error.message : "Resume versions could not be loaded"});
    } finally {
      setLoading(false);
    }
  }, [apiBase, apiFetch]);

  useEffect(() => {
    let ignore = false;
    apiFetch(`${apiBase}/resumes`, {cache: "no-store"})
      .then(async response => {
        if (!response.ok) throw new Error(await errorMessage(response));
        return response.json() as Promise<ResumeSummary[]>;
      })
      .then(data => {if (!ignore) setResumes(data);})
      .catch(error => {if (!ignore) setNotice({kind: "error", text: error instanceof Error ? error.message : "Resume versions could not be loaded"});})
      .finally(() => {if (!ignore) setLoading(false);});
    return () => {ignore = true;};
  }, [apiBase, apiFetch]);

  const upload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!file) return setNotice({kind: "error", text: "Choose a PDF or DOCX file first."});
    setAction("upload");
    setNotice(null);
    const data = new FormData();
    data.append("file", file);
    data.append("master", String(makeMaster));
    try {
      const response = await apiFetch(`${apiBase}/resumes`, {method: "POST", body: data});
      if (!response.ok) throw new Error(await errorMessage(response));
      const created = await response.json() as ResumeDetails;
      setFile(null);
      setMakeMaster(false);
      setSelected(created);
      setNotice({kind: "success", text: `${created.filename} uploaded and parsed successfully.`});
      await load();
      form.reset();
    } catch (error) {
      setNotice({kind: "error", text: error instanceof Error ? error.message : "Upload failed"});
    } finally {
      setAction(null);
    }
  };

  const details = async (id: string) => {
    setAction(id + ":details");
    setNotice(null);
    try {
      const response = await apiFetch(`${apiBase}/resumes/${id}`, {cache: "no-store"});
      if (!response.ok) throw new Error(await errorMessage(response));
      setSelected(await response.json() as ResumeDetails);
    } catch (error) {
      setNotice({kind: "error", text: error instanceof Error ? error.message : "Resume details could not be loaded"});
    } finally {
      setAction(null);
    }
  };

  const mutate = async (resume: ResumeSummary, operation: "activate" | "parse" | "delete") => {
    if (operation === "delete" && !window.confirm(`Delete ${resume.filename}? This cannot be undone.`)) return;
    setAction(resume.id + ":" + operation);
    setNotice(null);
    try {
      const response = await apiFetch(`${apiBase}/resumes/${resume.id}${operation === "activate" ? "/activate" : operation === "parse" ? "/parse" : ""}`, {
        method: operation === "delete" ? "DELETE" : "POST",
      });
      if (!response.ok) throw new Error(await errorMessage(response));
      if (operation !== "delete") setSelected(await response.json() as ResumeDetails);
      else if (selected?.id === resume.id) setSelected(null);
      setNotice({kind: "success", text: operation === "activate" ? `${resume.filename} is now the active master.` : operation === "parse" ? `${resume.filename} was reparsed.` : `${resume.filename} was deleted.`});
      await load();
    } catch (error) {
      setNotice({kind: "error", text: error instanceof Error ? error.message : "Resume action failed"});
    } finally {
      setAction(null);
    }
  };

  return <div className="resumePage">
    <section className="uploadCard">
      <div>
        <p className="eyebrow">VERIFIED SOURCE</p>
        <h2>Upload a resume version</h2>
        <p>PDF or DOCX, up to the backend&apos;s configured limit (5 MB by default). Files are parsed locally and stored privately.</p>
      </div>
      <form onSubmit={upload}>
        <label className="filePicker">
          <span>{file ? file.name : "Choose PDF or DOCX"}</span>
          <input type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={event => setFile(event.target.files?.[0] || null)}/>
        </label>
        <label className="masterCheck"><input type="checkbox" checked={makeMaster} onChange={event => setMakeMaster(event.target.checked)}/> Activate as master after upload</label>
        <button className="primary" disabled={action === "upload"}>{action === "upload" ? "Uploading and parsing…" : "Upload resume"}</button>
      </form>
    </section>

    {notice && <div className={`resumeNotice ${notice.kind}`} role={notice.kind === "error" ? "alert" : "status"}>{notice.text}</div>}

    <section className="panel resumePanel">
      <div className="panelHead"><div><h2>Resume versions</h2><p>The active master is protected from deletion.</p></div><button className="ghost" onClick={() => void load()} disabled={loading}>Refresh</button></div>
      {loading ? <div className="state">Loading resume versions…</div> : resumes.length === 0 ? <div className="state"><b>No resumes yet</b><span>Upload a verified PDF or DOCX to create version 1.</span></div> :
        <div className="resumeList">{resumes.map(resume => <article key={resume.id} className={resume.master ? "masterResume" : ""}>
          <div className="fileIcon">{resume.contentType === "application/pdf" ? "PDF" : "DOCX"}</div>
          <div className="resumeMeta">
            <div><h3>{resume.filename}</h3><span className={`badge ${resume.status.toLowerCase()}`}>{resume.status}</span></div>
            <p>Version {resume.version} · {formatDate(resume.uploadedAt)} · {formatSize(resume.fileSize)}</p>
            <div className="chips good">{resume.parsedSkills.slice(0, 8).map(skill => <span key={skill}>{skill}</span>)}{resume.parsedSkills.length === 0 && <em>No structured skills found</em>}</div>
          </div>
          <div className="resumeActions">
            <button onClick={() => void details(resume.id)} disabled={action?.startsWith(resume.id)}>Details</button>
            <button onClick={() => void downloadFile(`${apiBase}/resumes/${resume.id}/download?inline=true`, {inline: true})}>View</button>
            <button onClick={() => void downloadFile(`${apiBase}/resumes/${resume.id}/download`, {filename: resume.filename})}>Download</button>
            {!resume.master && <button onClick={() => void mutate(resume, "activate")} disabled={action?.startsWith(resume.id)}>Make master</button>}
            <button onClick={() => void mutate(resume, "parse")} disabled={action?.startsWith(resume.id)}>Reparse</button>
            <button className="danger" title={resume.master ? "Activate another version before deleting the master resume" : "Delete this version"} disabled={resume.master || action?.startsWith(resume.id)} onClick={() => void mutate(resume, "delete")}>{resume.master ? "Master protected" : "Delete"}</button>
          </div>
        </article>)}</div>}
    </section>

    {selected && <div className="scrim" onClick={() => setSelected(null)}><aside className="drawer resumeDrawer" onClick={event => event.stopPropagation()}>
      <button className="close" onClick={() => setSelected(null)}>×</button>
      <p className="eyebrow">VERSION {selected.version} · {selected.status}</p>
      <h2>{selected.filename}</h2>
      <p>{formatDate(selected.uploadedAt)} · {formatSize(selected.fileSize)}</p>
      {selected.customizationSummary && <div className="detailCallout">{selected.customizationSummary}</div>}
      <h3>Verified profile</h3>
      <dl className="resumeFacts"><div><dt>Name</dt><dd>{selected.parsed.name || "Not identified"}</dd></div><div><dt>Email</dt><dd>{selected.parsed.contact?.email || "Not identified"}</dd></div><div><dt>Phone</dt><dd>{selected.parsed.contact?.phone || "Not identified"}</dd></div><div><dt>LinkedIn</dt><dd>{selected.parsed.contact?.linkedin || "Not identified"}</dd></div><div><dt>Location</dt><dd>{selected.parsed.contact?.location || "Not identified"}</dd></div><div><dt>Checksum</dt><dd title={selected.checksum || ""}>{selected.checksum?.slice(0, 16) || "Unavailable"}…</dd></div></dl>
      {selected.parsed.professionalSummary && <><h3>Professional summary</h3><p>{selected.parsed.professionalSummary}</p></>}
      <h3>Parsed skills</h3><div className="chips good">{selected.parsed.skills.map(skill => <span key={skill}>{skill}</span>)}</div>
      <h3>Experience</h3>{selected.parsed.experience.length === 0 ? <p>No structured experience identified. The original extracted text remains preserved.</p> : selected.parsed.experience.map((entry, index) => <div className="experienceItem" key={`${entry.employer}-${entry.employmentDates}-${index}`}><b>{[entry.jobTitle, entry.employer].filter(Boolean).join(" · ")}</b><small>{entry.employmentDates}</small></div>)}
      <div className="actions"><button onClick={() => void downloadFile(`${apiBase}/resumes/${selected.id}/download?inline=true`, {inline: true})}>View file</button><button className="primary" onClick={() => void downloadFile(`${apiBase}/resumes/${selected.id}/download`, {filename: selected.filename})}>Download</button></div>
    </aside></div>}
  </div>;
}
