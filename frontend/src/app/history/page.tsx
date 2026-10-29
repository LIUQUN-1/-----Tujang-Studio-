import Link from "next/link";

export const metadata = { title: "我的记录" };

/** P1 只放空态；P4 接入任务列表、轮询与刷新恢复。 */
export default function HistoryPage() {
  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">我的记录</h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-zinc-400">
          这里会列出你提交过的制作任务：创建时间、当前进度、成功的结果图或失败原因。
        </p>
      </header>

      <div className="card flex flex-col items-center justify-center px-6 py-16 text-center">
        <span aria-hidden className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-white/[0.04] text-xl">
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
    </div>
  );
}
