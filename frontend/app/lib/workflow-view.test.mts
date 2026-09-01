import assert from "node:assert/strict";
import test from "node:test";
import {applicationActions, csvSkills, workflowStatuses} from "./workflow-view.ts";

test("pending review retains rejected packages for an explicit return action", () => {
  assert.deepEqual(workflowStatuses["Pending Review"], ["PENDING_REVIEW", "REJECTED"]);
  assert.deepEqual(applicationActions("REJECTED"), ["Redacted dry-run preview", "Return to review"]);
});

test("ready packages expose manual-only actions", () => {
  assert.deepEqual(applicationActions("READY_TO_APPLY"), ["Redacted dry-run preview", "Validate dry run", "Return to review", "Mark manually applied"]);
});

test("score skill lists preserve only supplied evidence", () => {
  assert.deepEqual(csvSkills("AWS, Terraform, "), ["AWS", "Terraform"]);
  assert.deepEqual(csvSkills(null), []);
});
