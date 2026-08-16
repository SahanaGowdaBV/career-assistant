"use client";

import type {Session} from "@supabase/supabase-js";
import {usePathname, useRouter} from "next/navigation";
import {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState} from "react";
import {AUTH_CALLBACK_PATH, isPublicAuthRoute} from "./lib/auth-callback";
import {getSupabaseBrowserClient} from "./lib/supabase";

type AuthContextValue = {
  session: Session | null;
  apiFetch: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
  downloadFile: (url: string, options?: {filename?: string; inline?: boolean}) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);
export class AuthenticationRequiredError extends Error {
  constructor() {
    super("Your session has expired. Please sign in again.");
    this.name = "AuthenticationRequiredError";
  }
}

export function AuthProvider({children}: {children: ReactNode}) {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const supabase = getSupabaseBrowserClient();
    let active = true;
    void supabase.auth.getSession().then(({data}) => {
      if (active) {
        setSession(data.session);
        setLoading(false);
      }
    });
    const {data: {subscription}} = supabase.auth.onAuthStateChange((_event, nextSession) => {
      if (active) {
        setSession(nextSession);
        setLoading(false);
      }
    });
    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!loading && !session && !isPublicAuthRoute(pathname)) {
      router.replace("/login");
    }
  }, [loading, pathname, router, session]);

  const expireSession = useCallback(async (reason: "session-missing" | "session-refresh-failed" | "backend-unauthorized") => {
    if (pathname === AUTH_CALLBACK_PATH) return;
    setSession(null);
    await getSupabaseBrowserClient().auth.signOut({scope: "local"});
    router.replace(`/login?reason=${reason}`);
  }, [pathname, router]);

  const apiFetch = useCallback(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const supabase = getSupabaseBrowserClient();
    const {data: {session: currentSession}} = await supabase.auth.getSession();
    if (!currentSession) {
      await expireSession("session-missing");
      throw new AuthenticationRequiredError();
    }

    const authenticatedRequest = (accessToken: string) => {
      const headers = new Headers(init.headers);
      headers.set("Authorization", `Bearer ${accessToken}`);
      return fetch(input, {...init, headers});
    };

    let response = await authenticatedRequest(currentSession.access_token);
    if (response.status !== 401) return response;

    const {data: {session: refreshedSession}, error} = await supabase.auth.refreshSession();
    if (!error && refreshedSession) {
      response = await authenticatedRequest(refreshedSession.access_token);
      if (response.status !== 401) return response;
    } else {
      await expireSession("session-refresh-failed");
      throw new AuthenticationRequiredError();
    }

    await expireSession("backend-unauthorized");
    throw new AuthenticationRequiredError();
  }, [expireSession]);

  const downloadFile = useCallback(async (url: string, options: {filename?: string; inline?: boolean} = {}) => {
    const previewWindow = options.inline ? window.open("about:blank", "_blank") : null;
    if (previewWindow) previewWindow.opener = null;
    try {
      const response = await apiFetch(url, {cache: "no-store"});
      if (!response.ok) throw new Error(`Download failed (${response.status})`);
      const objectUrl = URL.createObjectURL(await response.blob());
      if (options.inline && previewWindow) {
        previewWindow.location.href = objectUrl;
      } else {
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = options.filename || "download";
        link.rel = "noreferrer";
        link.click();
      }
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      previewWindow?.close();
      throw error;
    }
  }, [apiFetch]);

  const signOut = useCallback(async () => {
    await getSupabaseBrowserClient().auth.signOut({scope: "local"});
    setSession(null);
    router.replace("/login");
  }, [router]);

  const value = useMemo(() => ({session, apiFetch, downloadFile, signOut}), [apiFetch, downloadFile, session, signOut]);
  if (loading || (!session && !isPublicAuthRoute(pathname))) {
    return <div className="authLoading" role="status">Loading secure workspace…</div>;
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
