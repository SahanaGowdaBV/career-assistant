"use client";

import {FormEvent, useEffect, useMemo, useState} from "react";
import {usePathname, useRouter} from "next/navigation";
import {getSupabaseBrowserClient} from "../lib/supabase";

export default function LoginPage() {
  const router = useRouter();
  const pathname = usePathname();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const reasonNotice = useMemo(() => {
    if (!pathname || typeof window === "undefined") return null;
    const reason = new URLSearchParams(window.location.search).get("reason");
    const messages: Record<string, string> = {
      "session-missing": "No persisted browser session was available after sign-in.",
      "session-refresh-failed": "The saved sign-in session could not be refreshed.",
      "backend-unauthorized": "The backend rejected the authenticated request. Check its JWT configuration.",
    };
    return reason && messages[reason] ? messages[reason] : null;
  }, [pathname]);

  useEffect(() => {
    void getSupabaseBrowserClient().auth.getSession().then(({data}) => {
      if (data.session) router.replace("/");
    });
  }, [router]);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    const {error} = await getSupabaseBrowserClient().auth.signInWithOtp({
      email: email.trim(),
      options: {
        emailRedirectTo: `${window.location.origin}/auth/callback`,
        shouldCreateUser: false,
      },
    });
    setBusy(false);
    setNotice(error ? error.message : "Magic link sent. Check your inbox and open it in this browser.");
  };

  return <main className="loginPage">
    <section className="loginCard">
      <div className="brand loginBrand"><span>CA</span><div>Career Assistant<small>Secure workspace</small></div></div>
      <p className="eyebrow">SUPABASE AUTH</p>
      <h1>Sign in with a magic link</h1>
      <p>Use the single email address authorized for this workspace.</p>
      <form onSubmit={submit}>
        <label htmlFor="email">Email address</label>
        <input id="email" type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com"/>
        <button className="primary" disabled={busy}>{busy ? "Sending…" : "Send magic link"}</button>
      </form>
      {(notice || reasonNotice) && <div className="notice" role="status">{notice || reasonNotice}</div>}
    </section>
  </main>;
}
