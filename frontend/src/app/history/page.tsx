import Link from "next/link";
import { redirect } from "next/navigation";

import { TaskList } from "@/components/task-list";
import { getMe, listTasksServer } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export const metadata = { title: "我的记录" };

/**
 * 个人历史。
 *
 * <p>首屏由服务端直接把任务渲染出来，刷新后立刻可见；客户端 store 会在
 * `TaskWatcher` 里再同步一次并继续跟踪未完成的任务。
 *
 * <p>未登录直接跳登录页，并把"回来以后去哪"带上（`next`），登录成功后回到这里。
 */
export default async function HistoryPage() {
  const me = await getMe();
  if (!me) {
    redirect("/login?next=/history");
  }

  const tasks = await listTasksServer(50);
  const pending = tasks.filter((task) => task.status === "PENDING" || task.status === "PROCESSING").length;

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">我的记录</h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-zinc-400">
          这里列出 <span className="text-zinc-200">{me.username}</span> 提交过的制作任务：
          创建时间、处理进度、成功的结果图或失败原因。
          {pending > 0 && <span className="ml-1 text-accent-soft">其中 {pending} 个正在处理中。</span>}
        </p>
      </header>

      <TaskList
        initialTasks={tasks}
        showTemplate
        empty={
          <div className="card flex flex-col items-center justify-center px-6 py-16 text-center">
            <span
              aria-hidden
              className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-white/[0.04] text-xl"
            >
              🖼
            </span>
            <h2 className="text-base font-medium text-zinc-200">还没有制作记录</h2>
            <p className="mt-1.5 max-w-sm text-sm text-zinc-500">
              去挑一个模板，上传素材并提交制作任务吧。
            </p>
            <Link href="/" className="btn-primary mt-5">
              浏览模板
            </Link>
          </div>
        }
      />
    </div>
  );
}
