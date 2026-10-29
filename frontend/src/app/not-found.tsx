import Link from "next/link";

export default function NotFound() {
  return (
    <div className="card mx-auto mt-6 flex max-w-xl flex-col items-center px-6 py-14 text-center">
      <span aria-hidden className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-white/[0.04] text-xl">
        🔍
      </span>
      <h1 className="text-base font-medium text-zinc-100">这个页面不存在</h1>
      <p className="mt-2 max-w-md text-sm leading-relaxed text-zinc-400">
        链接可能已经失效，或者模板已下线。
      </p>
      <Link href="/" className="btn-primary mt-6">
        回到模板列表
      </Link>
    </div>
  );
}
