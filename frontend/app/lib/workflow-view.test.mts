import assert from "node:assert/strict";
import test from "node:test";
import {applicationActions, csvSkills, isActiveOpportunityStatus, jobStatusesForView, visibleWorkflowError, workflowStatuses} from "./workflow-view.ts";

test("pending review retains rejected packages for an explicit return action", () => {
  assert.deepEqual(workflowStatuses["Pending Review"], ["PENDING_REVIEW", "REJECTED"]);
  assert.deepEqual(applicationActions("REJECTED"), ["Return to review"]);
  assert.ok(applicationActions("PENDING_REVIEW").includes("Regenerate résumé + cover letter"));
});

test("ready packages expose manual-only actions", () => {
  assert.deepEqual(applicationActions("READY_TO_APPLY"), ["Return to review", "Mark manually applied"]);
});

test("new and manually applied jobs remain in separate sections", () => {
  assert.deepEqual(jobStatusesForView["New Jobs"], ["NEW", "HIGH_SCORE"]);
  assert.deepEqual(workflowStatuses["Manually Applied"], ["MANUALLY_APPLIED"]);
  assert.equal(isActiveOpportunityStatus("MANUALLY_APPLIED"), false);
  assert.equal(isActiveOpportunityStatus("PENDING_REVIEW"), true);
});

test("score skill lists preserve only supplied evidence", () => {
  assert.deepEqual(csvSkills("AWS, Terraform, "), ["AWS", "Terraform"]);
  assert.deepEqual(csvSkills(null), []);
});

test("regeneration failures always produce a visible safe message", () => {
  assert.equal(visibleWorkflowError(new Error("Document quality gate blocked package completion"), "Regeneration failed"), "Document quality gate blocked package completion");
  assert.equal(visibleWorkflowError("unexpected", "Regeneration failed"), "Regeneration failed");
});
