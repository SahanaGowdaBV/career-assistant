import type { Metadata } from "next";
import "./globals.css";
import "./resume.css";

export const metadata: Metadata = { title: "Career Assistant | UAE DevOps", description: "A safe, dry-run UAE job discovery and application workspace." };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
