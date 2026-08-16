"use client";

import {FormEvent, useEffect, useState} from "react";
import {useRouter} from "next/navigation";
import {getSupabaseBrowserClient} from "../lib/supabase";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

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
      {notice && <div className="notice" role="status">{notice}</div>}
    </section>
  </main>;
}
