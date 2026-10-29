"use client";

import { useEffect } from "react";

import { listTasks } from "@/lib/tasks";
import { useTaskPoller } from "@/hooks/use-task-poller";
import { useTaskStore } from "@/store/task-store";

/**
 * 全局任务观察者：挂在 layout 上，随整个站点存活。
 *
 * <p>职责有两件，正好对应两条要求：
 * <ol>
 *   <li><b>刷新恢复</b>——挂载时拉一次 `GET /api/tasks`，把未完成的任务重新纳入跟踪；
 *       因为它在 layout 里，所以刷新任意页面都能恢复，不必回到模板详情页。</li>
 *   <li><b>持续跟踪</b>——轮询只针对未完成的任务；全部结束后定时器自动停。</li>
 * </ol>
 *
 * <p>它不渲染任何东西。**未登录时完全不发请求**：任务按用户隔离，匿名访客
 * 拉列表只会拿到 401，没有意义还白打一次接口。
 * 退出登录会整页跳转（layout 重新挂载），上一个身份的轮询随卸载停止。
 */
export function TaskWatcher({ authed }: { authed: boolean }) {
  const hydrate = useTaskStore((state) => state.upsertMany);

  useEffect(() => {
    if (!authed) {
      return;
    }
    let alive = true;
    listTasks()
      .then((tasks) => {
        if (alive) {
          hydrate(tasks);
        }
      })
      .catch(() => {
        // 首屏恢复失败不打扰用户：页面本身还有服务端渲染的数据，轮询也会自然重试
      });
    return () => {
      alive = false;
    };
  }, [authed, hydrate]);

  useTaskPoller(authed);

  return null;
}
