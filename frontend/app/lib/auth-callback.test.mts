import assert from "node:assert/strict";
import test from "node:test";

import {completePkceCallback, isPublicAuthRoute, type PkceExchangeClient} from "./auth-callback.ts";

test("public auth routes are excluded from protected redirects", () => {
  assert.equal(isPublicAuthRoute("/login"), true);
  assert.equal(isPublicAuthRoute("/auth/callback"), true);
  assert.equal(isPublicAuthRoute("/"), false);
});

test("callback exchanges the PKCE code exactly once before succeeding", async () => {
  const calls: string[] = [];
  const client: PkceExchangeClient = {
    auth: {
      exchangeCodeForSession: async code => {
        calls.push(code);
        return {data: {session: {}}, error: null};
      },
    },
  };

  const result = await completePkceCallback(client, "http://localhost:3000/auth/callback?code=test-code");

  assert.deepEqual(result, {ok: true});
  assert.deepEqual(calls, ["test-code"]);
});

test("callback reports a safe error without attempting exchange when code is absent", async () => {
  let called = false;
  const client: PkceExchangeClient = {
    auth: {
      exchangeCodeForSession: async () => {
        called = true;
        return {data: {session: {}}, error: null};
      },
    },
  };

  const result = await completePkceCallback(client, "http://localhost:3000/auth/callback");

  assert.equal(result.ok, false);
  assert.equal(called, false);
});

test("callback hides provider error details when exchange fails", async () => {
  const client: PkceExchangeClient = {
    auth: {
      exchangeCodeForSession: async () => ({
        data: {session: null},
        error: new Error("sensitive-provider-detail"),
      }),
    },
  };

  const result = await completePkceCallback(client, "http://localhost:3000/auth/callback?code=test-code");

  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.message.includes("sensitive-provider-detail"), false);
});

test("callback converts thrown exchange failures to the same safe error", async () => {
  const client: PkceExchangeClient = {
    auth: {
      exchangeCodeForSession: async () => {
        throw new Error("sensitive-network-detail");
      },
    },
  };

  const result = await completePkceCallback(client, "http://localhost:3000/auth/callback?code=test-code");

  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.message.includes("sensitive-network-detail"), false);
});
