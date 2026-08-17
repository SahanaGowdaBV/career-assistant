export const AUTH_CALLBACK_PATH = "/auth/callback";

const publicAuthRoutes = new Set(["/login", AUTH_CALLBACK_PATH]);

type ExchangeResult = {
  data: {session: unknown | null};
  error: unknown | null;
};

type SessionResult = {data: {session: unknown | null}; error: unknown | null};
type UserResult = {data: {user: unknown | null}; error: unknown | null};

export type PkceExchangeClient = {
  auth: {
    exchangeCodeForSession: (code: string) => Promise<ExchangeResult>;
    getSession: () => Promise<SessionResult>;
    getUser: () => Promise<UserResult>;
  };
};

export type CallbackResult =
  | {ok: true}
  | {ok: false; stage: "missing-code" | "exchange-failed" | "session-persistence-failed" | "user-fetch-failed"; code: string; message: string};

export function isPublicAuthRoute(pathname: string): boolean {
  return publicAuthRoutes.has(pathname);
}

export async function completePkceCallback(
  client: PkceExchangeClient,
  callbackUrl: string,
): Promise<CallbackResult> {
  const code = new URL(callbackUrl).searchParams.get("code");
  if (!code) {
    return {ok: false, stage: "missing-code", code: "missing_code", message: "This sign-in link is missing its authorization code. Request a new magic link."};
  }

  try {
    const {data, error} = await client.auth.exchangeCodeForSession(code);
    if (error) return {ok: false, stage: "exchange-failed", code: safeErrorCode(error), message: exchangeMessage(safeErrorCode(error))};
    if (!data.session) return {ok: false, stage: "session-persistence-failed", code: "missing_session", message: "Sign-in completed without a persisted session. Try the magic link again in this browser."};

    const persisted = await client.auth.getSession();
    if (persisted.error || !persisted.data.session) {
      return {ok: false, stage: "session-persistence-failed", code: safeErrorCode(persisted.error), message: "Sign-in completed, but the browser could not persist the session. Enable site storage and try again."};
    }

    const user = await client.auth.getUser();
    if (user.error || !user.data.user) {
      return {ok: false, stage: "user-fetch-failed", code: safeErrorCode(user.error), message: "The authenticated user could not be verified. Try the magic link again."};
    }
    return {ok: true};
  } catch {
    // Provider and network details must not be exposed on the public callback page.
    return {ok: false, stage: "exchange-failed", code: "exchange_exception", message: "The magic link could not be completed. Request a new one."};
  }
}

function safeErrorCode(error: unknown): string {
  if (error && typeof error === "object" && "code" in error) {
    const code = (error as {code?: unknown}).code;
    if (typeof code === "string" && /^[a-z0-9_-]{1,64}$/i.test(code)) return code;
  }
  return "unknown";
}

function exchangeMessage(code: string): string {
  if (code === "bad_code_verifier") return "This link was opened in a different browser profile or its sign-in state was cleared. Request a new magic link in this browser.";
  if (code === "flow_state_expired" || code === "otp_expired" || code === "invalid_grant") return "This magic link is expired or already used. Request a new one.";
  return "The magic link could not be completed. Request a new one.";
}
