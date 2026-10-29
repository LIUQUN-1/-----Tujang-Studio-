import type { NextConfig } from "next";

/**
 * 浏览器只访问同源路径，由 Next 服务端转发到 Java：
 *   /api/**   → ${BACKEND_URL}/api/**
 *   /files/** → ${BACKEND_URL}/files/**
 *
 * 这样零 CORS、容器内外地址一致、<img src="/files/..."> 直接可用。
 *
 * 注意：rewrites 在 `next build` 时被编进 routes-manifest，
 * 所以生产镜像里 BACKEND_URL 是**构建期**变量，Dockerfile 用 ARG 注入。
 * `next dev` 则是运行期读取，本地改 .env.local 即时生效。
 */
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  // 刻意不开 output:"standalone"：它在 Windows 上要创建符号链接（需额外权限），
  // 且会让本地构建与 Docker 构建走两条不同的产物路径。Docker 侧用常规多阶段构建。
  reactStrictMode: true,
  poweredByHeader: false,

  // 开发与生产产物分目录存放。
  // 默认两者都用 .next，`pnpm build` 之后再 `pnpm dev` 会读到生产的 vendor-chunks，
  // 报 "Cannot find module './vendor-chunks/...'"，且必须手动删 .next 才能恢复。
  distDir: process.env.NODE_ENV === "development" ? ".next-dev" : ".next",
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
      { source: "/files/:path*", destination: `${backendUrl}/files/:path*` },
    ];
  },
};

export default nextConfig;
