"use client";

import {useEffect, useRef, useState} from "react";
import {useRouter} from "next/navigation";
import {getSupabaseBrowserClient} from "../../lib/supabase";

export default function AuthCallbackPage() {
  const router = useRouter();
  const started = useRef(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const finishSignIn = async () => {
      const supabase = getSupabaseBrowserClient();
      const {data, error: sessionError} = await supabase.auth.getSession();
      if (sessionError || !data.session) {
        setError("A valid session could not be created. Request a new magic link.");
        return;
      }
      router.replace("/");
    };
    void finishSignIn();
  }, [router]);

  return <main className="loginPage"><section className="loginCard">
    <p className="eyebrow">SECURE SIGN-IN</p>
    <h1>{error ? "Sign-in failed" : "Completing sign-in…"}</h1>
    <p>{error || "Validating your magic link and restoring the workspace session."}</p>
    {error && <a className="primary loginLink" href="/login">Return to login</a>}
  </section></main>;
}
