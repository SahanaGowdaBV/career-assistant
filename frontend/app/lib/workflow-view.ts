export type WorkflowStatus = "PENDING_REVIEW"|"READY_TO_APPLY"|"AUTO_APPLIED"|"MANUALLY_APPLIED"|"FAILED"|"REJECTED";

export const workflowStatuses: Partial<Record<string, WorkflowStatus[]>> = {
  "Pending Review": ["PENDING_REVIEW", "REJECTED"],
  "Ready to Apply": ["READY_TO_APPLY"],
  "Auto Applied": ["AUTO_APPLIED"],
  "Failed Applications": ["FAILED"],
  "Manually Applied": ["MANUALLY_APPLIED"],
};

export const jobStatusesForView: Partial<Record<string, string[]>> = {
  "New Jobs": ["NEW", "HIGH_SCORE"],
  "High Score Jobs": ["HIGH_SCORE"],
};

export function isActiveOpportunityStatus(status: string): boolean {
  return !["MANUALLY_APPLIED", "AUTO_APPLIED", "REJECTED", "ARCHIVED"].includes(status);
}

export function applicationActions(status: WorkflowStatus): string[] {
  if (status === "PENDING_REVIEW") return ["Regenerate résumé + cover letter", "Documents reviewed", "Mark manually applied", "Reject"];
  if (status === "REJECTED") return ["Return to review"];
  if (status === "READY_TO_APPLY") return ["Return to review", "Mark manually applied"];
  return [];
}

export function csvSkills(value: string | null | undefined): string[] {
  return value ? value.split(",").map(skill => skill.trim()).filter(Boolean) : [];
}

export function visibleWorkflowError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
