import assert from "node:assert/strict";
import test from "node:test";
import {applicationActions, csvSkills, visibleWorkflowError, workflowStatuses} from "./workflow-view.ts";

test("pending review retains rejected packages for an explicit return action", () => {
  assert.deepEqual(workflowStatuses["Pending Review"], ["PENDING_REVIEW", "REJECTED"]);
  assert.deepEqual(applicationActions("REJECTED"), ["Return to review"]);
  assert.ok(applicationActions("PENDING_REVIEW").includes("Regenerate résumé + cover letter"));
});

test("ready packages expose manual-only actions", () => {
  assert.deepEqual(applicationActions("READY_TO_APPLY"), ["Return to review", "Mark manually applied"]);
});

test("score skill lists preserve only supplied evidence", () => {
  assert.deepEqual(csvSkills("AWS, Terraform, "), ["AWS", "Terraform"]);
  assert.deepEqual(csvSkills(null), []);
});

test("regeneration failures always produce a visible safe message", () => {
  assert.equal(visibleWorkflowError(new Error("Document quality gate blocked package completion"), "Regeneration failed"), "Document quality gate blocked package completion");
  assert.equal(visibleWorkflowError("unexpected", "Regeneration failed"), "Regeneration failed");
});
