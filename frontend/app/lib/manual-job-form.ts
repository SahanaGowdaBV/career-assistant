export type ManualJobDraft = {
  title: string;
  company: string;
  location: string;
  experienceText: string;
  description: string;
  applicationUrl: string;
  sourcePortal: string;
};

export const manualJobFields = [
  "Job title", "Company", "Location", "Experience text",
  "Full job description", "Official application URL", "Source portal",
] as const;

export function validateManualJob(draft: ManualJobDraft): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of ["title", "company", "location", "description", "applicationUrl", "sourcePortal"] as const) {
    if (!draft[field].trim()) errors[field] = "Required";
  }
  try {
    const url = new URL(draft.applicationUrl);
    const host = url.hostname.toLowerCase();
    const privateHost = host === "localhost" || host.endsWith(".local") || host.endsWith(".internal")
      || /^\d+(?:\.\d+){3}$/.test(host) || !host.includes(".");
    if (url.protocol !== "https:" || privateHost || url.username || url.password) {
      errors.applicationUrl = "Use a public HTTPS application URL";
    }
  } catch {
    errors.applicationUrl = "Enter a valid public HTTPS application URL";
  }
  return errors;
}

export function manualJobPayload(draft: ManualJobDraft): ManualJobDraft {
  return Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, value.trim()])) as ManualJobDraft;
}
