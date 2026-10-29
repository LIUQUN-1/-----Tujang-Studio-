import type { Metadata, Viewport } from "next";

import { SiteHeader } from "@/components/site-header";
import { TaskWatcher } from "@/components/task-watcher";
import { getMe } from "@/lib/server-api";
import type { Me } from "@/lib/types";

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

/**
 * 每个页面的首屏都带身份（顶栏、个人记录），没有可以静态化的页面。
 *
 * 显式声明成动态渲染，除了表达这一点，还顺手消掉了构建期的一条噪音：
 * 生成 /_not-found 时根布局会调 `cookies()`，Next 会打一行
 * "Route /_not-found couldn't be rendered statically because it used `cookies`"。
 * 构建结果本来就没错（退出码 0），但日志里一条红色错误容易让人误判。
 */
export const dynamic = "force-dynamic";

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // 身份由中间件保证存在；后端不可用时降级为"未登录"，不让整页报错
  let me: Me | null = null;
  try {
    me = await getMe();
  } catch (err) {
    console.error("[layout] getMe failed", err);
    me = null;
  }

  return (
    <html lang="zh-CN">
      <body className="min-h-screen">
        {/* 全局任务观察者：刷新恢复 + 持续跟踪未完成任务，不渲染任何内容 */}
        <TaskWatcher authed={me !== null} />
        <SiteHeader user={me} />
        <main className="mx-auto w-full max-w-[1400px] px-5 py-8 sm:px-8 sm:py-10">{children}</main>
      </body>
    </html>
  );
}
