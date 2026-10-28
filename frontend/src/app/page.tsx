import { BackendProbe } from "@/components/backend-probe";
import { SiteHeader } from "@/components/site-header";

// 骨架阶段需要实时读取后端状态，禁用静态预渲染（也让 `pnpm build` 不依赖后端在跑）
export const dynamic = "force-dynamic";

type Health = { status: string; app: string; time: string };
type LoadResult = { ok: true; data: Health } | { ok: false; error: string };

async function loadHealth(): Promise<LoadResult> {
  const base = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
  try {
    const res = await fetch(`${base}/api/health`, { cache: "no-store" });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };
    return { ok: true, data: (await res.json()) as Health };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "连接失败" };
  }
}

const SEED_PREVIEW = [
  { title: "旅行明信片", id: "demo-postcard" },
  { title: "油画肖像", id: "oil-portrait" },
  { title: "萌宠头像", id: "pet-avatar" },
  { title: "圣诞明信片", id: "santa-postcard" },
];

const P0_ITEMS = [
  { label: "接口契约 docs/api.md 先行", done: true },
  { label: "数据库结构与 9 个模板种子数据", done: true },
  { label: "种子素材（封面 / 预置结果 / 示例照片）", done: true },
  { label: "Docker Compose + MySQL 初始化", done: true },
  { label: "Java 后端骨架与 /api/health", done: true },
  { label: "Next 骨架与 /api、/files 同源代理", done: true },
];

export default async function HomePage() {
  const health = await loadHealth();

  return (
    <div className="min-h-screen">
      <SiteHeader />

      <main className="mx-auto w-full max-w-[1400px] px-5 py-10 sm:px-8">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-accent">P0 · 骨架验证</p>
            <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
              素材制作，跑在后台
            </h1>
            <p className="mt-3 max-w-xl text-sm leading-relaxed text-zinc-400">
              浏览模板、上传素材、提交制作任务，处理期间继续使用网站，回来后查看历史结果。
              本阶段只验证链路：Next 页面 → Java 接口 → MySQL 与静态素材。
            </p>
          </div>

          <div className="flex items-center gap-3">
            <span
              className={`chip ${health.ok ? "chip-active" : "border-red-400/40 text-red-300"}`}
              role="status"
            >
              {health.ok ? "Java 已连通" : `Java 未连通：${health.error}`}
            </span>
          </div>
        </div>

        <section className="mt-10 grid gap-4 lg:grid-cols-3">
          <div className="card p-5 lg:col-span-2">
            <h2 className="text-sm font-medium text-zinc-300">链路自检</h2>
            <div className="mt-4 space-y-4">
              <div className="flex items-center gap-3 text-sm">
                <span
                  aria-hidden
                  className={`h-2.5 w-2.5 shrink-0 rounded-full ${health.ok ? "bg-accent" : "bg-red-400"}`}
                />
                <div>
                  <p className="font-medium text-zinc-200">服务端 → Java（直连 BACKEND_URL）</p>
                  <p className="text-xs text-zinc-500">
                    {health.ok
                      ? `HTTP 200 · ${health.data.app} · ${health.data.time}`
                      : `失败：${health.error}`}
                  </p>
                </div>
              </div>

              <BackendProbe />
            </div>
          </div>

          <div className="card p-5">
            <h2 className="text-sm font-medium text-zinc-300">P0 完成项</h2>
            <ul className="mt-4 space-y-2.5 text-sm">
              {P0_ITEMS.map((item) => (
                <li key={item.label} className="flex items-start gap-2.5 text-zinc-400">
                  <span
                    aria-hidden
                    className={`mt-0.5 grid h-4 w-4 shrink-0 place-items-center rounded-full text-[10px] ${
                      item.done ? "bg-accent/20 text-accent" : "bg-white/10 text-zinc-500"
                    }`}
                  >
                    ✓
                  </span>
                  {item.label}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="mt-10">
          <div className="flex items-baseline justify-between">
            <h2 className="text-sm font-medium text-zinc-300">种子素材（经 /files/seed 提供）</h2>
            <p className="text-xs text-zinc-500">P1 会替换为数据库读出的模板列表</p>
          </div>
          <ul className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
            {SEED_PREVIEW.map((tpl) => (
              <li key={tpl.id} className="card overflow-hidden">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={`/files/seed/covers/${tpl.id}.jpg`}
                  alt={`${tpl.title} 模板封面`}
                  width={800}
                  height={1000}
                  className="aspect-[4/5] w-full object-cover"
                />
                <div className="px-3 py-2.5 text-xs text-zinc-400">{tpl.title}</div>
              </li>
            ))}
          </ul>
        </section>
      </main>
    </div>
  );
}
