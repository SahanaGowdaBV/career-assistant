import type { NextConfig } from "next";

if (process.env.NODE_ENV === "production") {
  const required = ["NEXT_PUBLIC_API_URL", "NEXT_PUBLIC_SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_ANON_KEY"];
  const missing = required.filter(name => !process.env[name]?.trim());
  if (missing.length > 0) {
    throw new Error(`Missing required production environment variables: ${missing.join(", ")}`);
  }
}

const nextConfig: NextConfig = {
  /* config options here */
};

export default nextConfig;
