import Link from "next/link";
import { Suspense } from "react";

import { CategoryTabs } from "@/components/category-tabs";
import { TemplateCard, TemplateCardSkeleton } from "@/components/template-card";
import { getCategories, getTemplates } from "@/lib/api";

// 数据来自 Java 且随时可能变化，不做静态预渲染
export const dynamic = "force-dynamic";

type SearchParams = Promise<{ category?: string }>;

export default async function HomePage({ searchParams }: { searchParams: SearchParams }) {
  const { category } = await searchParams;
  const categories = await getCategories();
  const activeName = categories.find((c) => c.code === category)?.name;

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
          {activeName ? `${activeName}模板` : "挑一个模板开始制作"}
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-zinc-400">
          上传素材后提交制作任务，任务在后台跑完三步；期间你可以继续浏览，结果会出现在「我的记录」里。
        </p>
      </header>

      <div className="mb-6">
        <CategoryTabs categories={categories} active={category} />
      </div>

      {/*
        骨架屏刻意只包住网格、不放在 app/loading.tsx：
        loading.tsx 会给整个 app 段加 Suspense 边界，导致 /templates/[id] 在数据到达前
        就 flush 出 200，notFound() 的 404 状态码会被吞掉。
      */}
      <Suspense key={category ?? "all"} fallback={<GridSkeleton />}>
        <TemplateGrid category={category} activeName={activeName} />
      </Suspense>
    </div>
  );
}

async function TemplateGrid({ category, activeName }: { category?: string; activeName?: string }) {
  const templates = await getTemplates(category);

  if (templates.length === 0) {
    return <EmptyTemplates category={activeName} />;
  }

  return (
    <>
      <p className="mb-3 text-xs text-zinc-500">共 {templates.length} 个模板</p>
      <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {templates.map((template) => (
          <TemplateCard key={template.id} template={template} />
        ))}
      </ul>
    </>
  );
}

function GridSkeleton() {
  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5" aria-hidden>
      {Array.from({ length: 10 }).map((_, i) => (
        <TemplateCardSkeleton key={i} />
      ))}
    </ul>
  );
}

function EmptyTemplates({ category }: { category?: string }) {
  return (
    <div className="card flex flex-col items-center justify-center px-6 py-16 text-center">
      <span aria-hidden className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-white/[0.04] text-xl">
        🗂
      </span>
      <h2 className="text-base font-medium text-zinc-200">
        {category ? `「${category}」下还没有模板` : "还没有可用的模板"}
      </h2>
      <p className="mt-1.5 max-w-sm text-sm text-zinc-500">
        {category
          ? "换一个分类看看，或者回到全部模板。"
          : "请确认数据库已初始化（db/02-seed.sql 会写入 9 个示例模板）。"}
      </p>
      <Link href="/" className="btn-ghost mt-5">
        查看全部模板
      </Link>
    </div>
  );
}
