import { redirect } from "next/navigation";

import { AuthForm } from "@/components/auth-form";
import { getMe } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export const metadata = { title: "注册" };

type SearchParams = Promise<{ next?: string }>;

export default async function RegisterPage({ searchParams }: { searchParams: SearchParams }) {
  const { next } = await searchParams;

  if (await getMe()) {
    redirect(next && next.startsWith("/") ? next : "/");
  }

  return <AuthForm mode="register" next={next} />;
}
