import { redirect } from "next/navigation";

import { AuthForm } from "@/components/auth-form";
import { getMe } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export const metadata = { title: "登录" };

type SearchParams = Promise<{ next?: string }>;

export default async function LoginPage({ searchParams }: { searchParams: SearchParams }) {
  const { next } = await searchParams;

  // 已登录就别再显示了：直接回到目标页
  if (await getMe()) {
    redirect(next && next.startsWith("/") ? next : "/");
  }

  return <AuthForm mode="login" next={next} />;
}
