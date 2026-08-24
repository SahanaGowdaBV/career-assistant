import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import "./resume.css";
import "./auth.css";
import {AuthProvider} from "./auth-provider";

export const metadata: Metadata = { title: "Career Assistant | UAE DevOps", description: "A safe, dry-run UAE job discovery and application workspace." };

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col"><AuthProvider>{children}</AuthProvider></body>
    </html>
  );
}
