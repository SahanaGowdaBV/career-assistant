import assert from "node:assert/strict";
import test from "node:test";
import {manualJobFields,manualJobPayload,validateManualJob} from "./manual-job-form.ts";

const valid={title:" Platform Engineer ",company:" Example ",location:" Dubai, UAE ",experienceText:" 5+ years ",description:" Full public description ",applicationUrl:"https://careers.example.com/jobs/123",sourcePortal:" Bayt "};

test("manual form exposes every required product field",()=>assert.deepEqual(manualJobFields,["Job title","Company","Location","Experience text","Full job description","Official application URL","Source portal"]));
test("manual payload trims fields and never includes owner data",()=>{const payload=manualJobPayload(valid);assert.equal(payload.title,"Platform Engineer");assert.equal(payload.sourcePortal,"Bayt");assert.equal("owner_subject" in payload,false);});
test("manual validation rejects missing fields and non-HTTPS URLs",()=>{const errors=validateManualJob({...valid,title:"",applicationUrl:"http://example.com/job"});assert.equal(errors.title,"Required");assert.match(errors.applicationUrl,/HTTPS/);});
test("manual validation accepts a complete public HTTPS posting",()=>assert.deepEqual(validateManualJob(valid),{}));
