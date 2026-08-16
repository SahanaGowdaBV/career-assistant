export const AUTH_CALLBACK_PATH = "/auth/callback";

const publicAuthRoutes = new Set(["/login", AUTH_CALLBACK_PATH]);

type ExchangeResult = {
  data: {session: unknown | null};
  error: unknown | null;
};

export type PkceExchangeClient = {
  auth: {
    exchangeCodeForSession: (code: string) => Promise<ExchangeResult>;
  };
};

export type CallbackResult =
  | {ok: true}
  | {ok: false; message: string};

export function isPublicAuthRoute(pathname: string): boolean {
  return publicAuthRoutes.has(pathname);
}

export async function completePkceCallback(
  client: PkceExchangeClient,
  callbackUrl: string,
): Promise<CallbackResult> {
  const code = new URL(callbackUrl).searchParams.get("code");
  if (!code) {
    return {ok: false, message: "This sign-in link is missing its authorization code. Request a new magic link."};
  }

  try {
    const {data, error} = await client.auth.exchangeCodeForSession(code);
    if (!error && data.session) return {ok: true};
  } catch {
    // Provider and network details must not be exposed on the public callback page.
  }
  return {ok: false, message: "This magic link is invalid or expired. Request a new one."};
}
