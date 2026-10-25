import type { Metadata, Viewport } from "next";
import "./globals.css";
import { ProvedorApp } from "@/lib/sessao";
import { Toasts } from "@/components/ui";

export const metadata: Metadata = {
  title: { default: "CivilConstructorAI", template: "%s · CivilConstructorAI" },
  description: "CAD/BIM generativo com Notação VI e auditoria paramétrica",
  icons: { icon: "/marca/civilconstructorai.webp" },
};
export const viewport: Viewport = { themeColor: "#1D4ED8", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet" />
      </head>
      <body>
        <ProvedorApp>
          {children}
          <Toasts />
        </ProvedorApp>
      </body>
    </html>
  );
}
