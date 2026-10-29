import Link from "next/link";

import type { Category } from "@/lib/types";

type Tab = { code: string | undefined; name: string; count: number };

/**
 * 分类筛选。用链接（?category=）而不是本地 state：
 * 刷新、分享、浏览器前进后退都能保持筛选结果，也省掉一份客户端状态。
 */
export function CategoryTabs({ categories, active }: { categories: Category[]; active?: string }) {
  const total = categories.reduce((sum, c) => sum + c.count, 0);
  const tabs: Tab[] = [{ code: undefined, name: "全部", count: total }, ...categories];

  return (
    <nav aria-label="模板分类" className="-mx-1 overflow-x-auto pb-1">
      <ul className="flex min-w-max items-center gap-2 px-1">
        {tabs.map((tab) => {
          const isActive = tab.code === active || (!tab.code && !active);
          return (
            <li key={tab.code ?? "all"}>
              <Link
                href={tab.code ? `/?category=${tab.code}` : "/"}
                aria-current={isActive ? "page" : undefined}
                className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-3.5 py-1.5 text-sm transition-colors ${
                  isActive
                    ? "border-accent/60 bg-accent/10 text-accent-soft"
                    : "border-white/10 text-zinc-400 hover:border-white/20 hover:text-white"
                }`}
              >
                {tab.name}
                <span className={`text-[11px] ${isActive ? "text-accent/80" : "text-zinc-600"}`}>
                  {tab.count}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
