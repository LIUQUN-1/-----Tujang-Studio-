import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";

import { ApiError } from "@/lib/api";
import { formatBytes, formatMimeTypes } from "@/lib/format";
import { getMe, getTemplate, listTasksServer } from "@/lib/server-api";
import { TemplateComposer } from "@/components/template-composer";
import { TaskList } from "@/components/task-list";

export const dynamic = "force-dynamic";

type Params = Promise<{ id: string }>;

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  try {
    const template = await getTemplate(id);
    return { title: template.title, description: template.description };
  } catch {
    // 标题取不到不阻塞渲染，页面自己会处理 404
    return { title: "模板" };
  }
}

export default async function TemplateDetailPage({ params }: { params: Params }) {
  const { id } = await params;

  // 只有"模板不存在"才是 404；后端不可用应当交给错误边界，别伪装成 404
  const template = await getTemplate(id).catch((err: unknown) => {
    if (err instanceof ApiError && err.status === 404) {
      notFound();
    }
    throw err;
  });

  // 该模板下的历史任务：服务端先给一版，刷新后不必等轮询就能看到。
  // 未登录不发这个请求——任务按用户隔离，匿名拉只会拿到 401
  const me = await getMe();
  const authed = me !== null;
  const allTasks = authed ? await listTasksServer(50) : [];
  const initialTasks = allTasks.filter((task) => task.templateId === template.id);

  return (
    <div>
      <Link
        href="/"
        className="mb-5 inline-flex items-center gap-1.5 rounded-lg text-sm text-zinc-400 hover:text-white"
      >
        <span aria-hidden>←</span> 返回模板列表
      </Link>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,420px)_minmax(0,1fr)] lg:items-start">
        {/* 左：模板信息与制作区 */}
        <section className="card p-5" aria-labelledby="tpl-title">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={template.coverUrl}
            alt={`${template.title} 示例封面`}
            width={800}
            height={1000}
            className="aspect-[4/3] w-full rounded-xl object-cover"
          />

          <div className="mt-4 flex items-center gap-2">
            <span className="rounded-full border border-white/10 px-2.5 py-0.5 text-[11px] text-zinc-400">
              {template.categoryName}
            </span>
            <span className="text-[11px] text-zinc-500">需 {template.requiredImageCount} 张素材</span>
          </div>

          <h1 id="tpl-title" className="mt-3 text-xl font-semibold tracking-tight text-white">
            {template.title}
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-zinc-400">{template.description}</p>

          <dl className="mt-5 grid grid-cols-3 gap-2 rounded-xl border border-white/[0.06] bg-white/[0.02] p-3 text-center">
            <div>
              <dt className="text-[11px] text-zinc-500">素材数量</dt>
              <dd className="mt-0.5 text-sm text-zinc-200">{template.requiredImageCount} 张</dd>
            </div>
            <div>
              <dt className="text-[11px] text-zinc-500">支持格式</dt>
              <dd className="mt-0.5 text-sm text-zinc-200">
                {formatMimeTypes(template.acceptedMimeTypes)}
              </dd>
            </div>
            <div>
              <dt className="text-[11px] text-zinc-500">单张上限</dt>
              <dd className="mt-0.5 text-sm text-zinc-200">
                {formatBytes(template.maxFileSizeBytes)}
              </dd>
            </div>
          </dl>

          <TemplateComposer template={template} authed={authed} />
        </section>

        {/* 右：该模板下的制作记录 */}
        <section className="card p-5" aria-labelledby="tpl-history">
          <div className="flex items-baseline justify-between">
            <h2 id="tpl-history" className="text-sm font-medium text-zinc-300">
              制作记录
            </h2>
            <span className="text-[11px] text-zinc-600">{authed ? "按创建时间倒序" : "登录后可见"}</span>
          </div>

          <TaskList
            initialTasks={initialTasks}
            templateId={template.id}
            empty={
              <div className="mt-4 rounded-xl border border-dashed border-white/10 px-4 py-14 text-center">
                <p className="text-sm text-zinc-400">{authed ? "还没有制作记录" : "登录后查看你的制作记录"}</p>
                <p className="mx-auto mt-1 max-w-xs text-xs leading-relaxed text-zinc-600">
                  {authed
                    ? "提交任务后，处理进度和结果会出现在这里；离开页面或刷新都不会中断后台处理。"
                    : "记录按账号隔离，登录后这里会显示你提交过的任务。"}
                </p>
              </div>
            }
          />
        </section>
      </div>
    </div>
  );
}
