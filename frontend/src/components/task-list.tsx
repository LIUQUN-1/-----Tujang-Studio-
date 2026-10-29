"use client";

import type { ReactNode } from "react";

import { TaskCard } from "@/components/task-card";
import { useMergedTasks } from "@/hooks/use-merged-tasks";
import type { TaskDto } from "@/lib/types";

type Props = {
  initialTasks: TaskDto[];
  /** 只看某个模板的任务（模板详情页传，历史页不传） */
  templateId?: string;
  showTemplate?: boolean;
  empty: ReactNode;
};

/** 任务列表：详情页与历史页共用，差别只在是否展示模板信息与空态文案。 */
export function TaskList({ initialTasks, templateId, showTemplate, empty }: Props) {
  const tasks = useMergedTasks(initialTasks, templateId);

  if (tasks.length === 0) {
    return <>{empty}</>;
  }

  return (
    <ul className="mt-4 space-y-2.5">
      {tasks.map(({ task, entry }) => (
        <li key={task.id}>
          <TaskCard entry={entry} showTemplate={showTemplate} />
        </li>
      ))}
    </ul>
  );
}
