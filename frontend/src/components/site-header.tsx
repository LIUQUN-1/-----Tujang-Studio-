import Link from "next/link";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-white/[0.06] bg-ink-950/80 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-[1400px] items-center justify-between px-5 sm:px-8">
        <Link href="/" className="flex items-center gap-2.5 rounded-lg">
          <span
            aria-hidden
            className="grid h-8 w-8 place-items-center rounded-[10px] bg-gradient-to-br from-accent to-accent-deep text-sm font-bold text-ink-950"
          >
            图
          </span>
          <span className="text-[15px] font-semibold tracking-tight text-white">
            图匠工坊
            <span className="ml-1.5 text-xs font-normal text-zinc-500">Tujang Studio</span>
          </span>
        </Link>

        <nav aria-label="主导航" className="flex items-center gap-1 text-sm text-zinc-400">
          <Link href="/" className="rounded-lg px-3 py-2 hover:bg-white/[0.06] hover:text-white">
            模板
          </Link>
          <Link href="/history" className="rounded-lg px-3 py-2 hover:bg-white/[0.06] hover:text-white">
            我的记录
          </Link>
        </nav>
      </div>
    </header>
  );
}
