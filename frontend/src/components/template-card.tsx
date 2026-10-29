import Link from "next/link";

import type { TemplateSummary } from "@/lib/types";

export function TemplateCard({ template }: { template: TemplateSummary }) {
  return (
    <li>
      <Link
        href={`/templates/${template.id}`}
        className="group relative block overflow-hidden rounded-2xl border border-white/[0.07] bg-ink-900 shadow-card transition-colors hover:border-white/20"
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={template.coverUrl}
          alt={`${template.title} 模板封面`}
          width={800}
          height={1000}
          loading="lazy"
          className="aspect-[4/5] w-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
        />

        <span className="absolute left-3 top-3 rounded-full bg-black/55 px-2.5 py-1 text-[11px] text-zinc-100 backdrop-blur">
          {template.categoryName}
        </span>

        <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 via-black/45 to-transparent px-4 pb-3.5 pt-10">
          <h3 className="truncate text-[15px] font-medium text-white">{template.title}</h3>
          <p className="mt-0.5 text-xs text-zinc-400">
            需 {template.requiredImageCount} 张素材
          </p>
        </div>
      </Link>
    </li>
  );
}

/** 加载/空态共用的骨架卡片，避免布局跳动。 */
export function TemplateCardSkeleton() {
  return (
    <li className="overflow-hidden rounded-2xl border border-white/[0.05] bg-ink-900">
      <div className="aspect-[4/5] w-full animate-pulse bg-white/[0.04]" />
    </li>
  );
}
