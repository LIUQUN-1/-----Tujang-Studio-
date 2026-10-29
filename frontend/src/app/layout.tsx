import type { Metadata, Viewport } from "next";

import { SiteHeader } from "@/components/site-header";

import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "图匠工坊 · Tujang Studio",
    template: "%s · 图匠工坊",
  },
  description: "浏览模板、上传素材、提交制作任务，并在后台处理期间继续使用网站。",
};

export const viewport: Viewport = {
  themeColor: "#0b0b0c",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen">
        <SiteHeader />
        <main className="mx-auto w-full max-w-[1400px] px-5 py-8 sm:px-8 sm:py-10">{children}</main>
      </body>
    </html>
  );
}
