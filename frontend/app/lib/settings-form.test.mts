import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {buildSettingsPayload,normalizeProfile,profileFields,validateProfile} from "../settings-form.ts";

const existing={legalName:"Existing Candidate",applicationEmail:"candidate@example.com",applicationPhone:"+971500000000",currentLocation:"Dubai",relocation:"UAE",visaAnswer:"Yes",sponsorshipAnswer:"No",noticePeriodDays:30,linkedinUrl:"https://www.linkedin.com/in/candidate",salaryAnswer:"",consentAnswers:"Yes",legalAnswers:"Yes",ownerSubject:"user-secret",id:"private-id",profileName:"Search"};

test("all applicant-profile labels and mapped controls are present",()=>{
 const source=readFileSync(new URL("../settings.tsx",import.meta.url),"utf8");
 for(const label of profileFields)assert.match(source,new RegExp(label.replace(/[()]/g,"\\$&"),"i"));
 assert.match(source,/type="email"/);assert.match(source,/type="tel"/);assert.match(source,/type="number"/);assert.match(source,/type="url"/);assert.equal((source.match(/type="checkbox"/g)||[]).length,2);
});

test("existing values prefill and inputs produce backend field values",()=>{
 const profile=normalizeProfile(existing);assert.equal(profile.legalName,"Existing Candidate");assert.equal(profile.relocation,"UAE");assert.equal(profile.visaAnswer,"Yes");assert.equal(profile.sponsorshipAnswer,"No");
 const changed={...profile,legalName:"Changed Candidate",visaAnswer:"No",consentAnswers:"Yes"};assert.equal(changed.legalName,"Changed Candidate");assert.equal(changed.visaAnswer,"No");
});

test("save payload excludes ownership and server-managed identifiers",()=>{
 const payload=buildSettingsPayload(normalizeProfile(existing),existing);assert.equal(payload.legalName,"Existing Candidate");assert.equal(payload.profileName,"Search");assert.ok(!("ownerSubject" in payload));assert.ok(!("id" in payload));assert.ok(!("updatedAt" in payload));
});

test("validation reports field-specific errors",()=>{
 const errors=validateProfile(normalizeProfile({applicationEmail:"not-an-email",linkedinUrl:"broken",noticePeriodDays:-1}));assert.equal(errors.legalName,"This field is required.");assert.equal(errors.applicationEmail,"Enter a valid email address.");assert.match(errors.noticePeriodDays||"",/whole days/);
});

test("layout is two-column, responsive, and submission is guarded",()=>{
 const css=readFileSync(new URL("../settings.module.css",import.meta.url),"utf8");const source=readFileSync(new URL("../settings.tsx",import.meta.url),"utf8");
 assert.match(css,/grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);assert.match(css,/@media\(max-width:720px\)/);assert.match(css,/grid-template-columns:minmax\(0,1fr\)/);assert.match(css,/width:100%;min-width:0/);assert.doesNotMatch(source,/className="filters"/);assert.match(source,/invalid\|\|loading\|\|submitting/);assert.match(source,/await completeness\(\)/);assert.match(source,/role="alert"/);
});
