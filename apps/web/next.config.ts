import type { NextConfig } from "next";

/** O backend (Spring) responde em 8080; em desenvolvimento o Next faz proxy de /api para manter o cookie de sessão same-origin. */
const backend = process.env.CCA_BACKEND_URL ?? "http://localhost:8080";

const config: NextConfig = {
  reactStrictMode: true,
  transpilePackages: ["@cca/notacao-vi", "@cca/tokens", "three"],
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
        ],
      },
    ];
  },
};

export default config;
