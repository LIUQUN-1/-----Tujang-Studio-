# 图匠工坊 Tujang Studio — 实施计划（plan.md）

> 目标题：小影科技 2027 校招 · 素材制作网站
> 本文件最初是**动工前的唯一计划依据**（先写判据再写代码）；实施过程中每个阶段的
> 「实际与计划的偏差」都回填在这里，因此现在它同时是**计划 + 决策与踩坑记录**。
> 需求原文见 `项目说明文件/小影科技2027校招.md`，本计划不重复需求，只回答"怎么做、按什么顺序做、做到什么算完"。

---

## 0. 一句话定位

做的是一个**能 `docker compose up --build` 一键跑起来的全栈网站**：`模板列表 → 模板详情 → 上传素材 → 提交制作任务 → 轮询看阶段 → 看历史结果`。

真正被打分的是两条主线，其余都是支撑：

- **后端**：一次提交如何变成一个"受理即返回、后台按三步推进、状态落 MySQL、失败即中断"的异步任务；
- **前端**：如何让 N 个任务各自推进互不串扰，如何把"查询失败"和"任务失败"区分开，如何刷新后接着跟踪、没活儿就停。

> 判断标准来自题面原话：「实现复杂度本身不代表质量；完整、可靠且能解释的方案优先」。因此本计划**主动砍掉一切非必需技术**（不引入 MQ / Redis / SSE / WebSocket / 对象存储），把省下的时间全投到状态正确性、测试与交付说明上。

---

## 1. 技术选型

### 1.1 目标栈

| 层 | 选型 | 说明 |
|---|---|---|
| 前端 | Next.js **15.x** App Router + React 19 + TypeScript 5.x | 题面硬要求 App Router |
| 样式 | Tailwind CSS **3.4** | 不用 v4，避免配置范式差异带来的返工 |
| 客户端状态 | **Zustand**（约 1KB） | 任务是"按 id 索引的 Map"，Zustand 天然适配；比 Context 少一半样板，比 Redux 轻 |
| 异步更新 | **HTTP 轮询**（1.5s，可配） | 题面不强制 SSE/WS；轮询最容易把"停止条件/清理"讲清楚 |
| 后端 | Java **21** + Spring Boot **3.3.x** + MyBatis-Spring-Boot-Starter **3.0.x** | 见 §1.3 版本风险 |
| 数据库 | MySQL **8.0** | 官方镜像 |
| 容器 | Docker Compose（web / api / mysql 三服务） | R-14 |
| 后端测试 | JUnit 5 + Mockito（纯单测，不依赖 DB） | 保证 CI 稳定、快 |
| 前端测试 | Vitest + React Testing Library | 只测状态机与轮询，不做 E2E |
| CI | GitHub Actions（两 job：web / api） | R-12 |

> 具体小版本在 P0 脚手架时锁定，并以 `README` 记录的最终版本为准；本文件只定大方向和兼容边界。

### 1.2 本机环境（已实测）

| 项 | 现状 | 影响 |
|---|---|---|
| Node | v22.23.2，pnpm 12.15.0 | 满足 Next 15 |
| JDK | `JAVA_HOME=E:\JDK21.0.10`（21.0.10）；**PATH 上的 `java` 是 25.0.1** | 必须显式用 JDK 21，见下 |
| Maven | 3.9.12 | 可用 |
| Docker | 29.1.3 + Compose v2.40.3，linux 容器正常 | 可用 |
| 已占用端口 | **3306**（`mysql_server_1`）、**8080**、**8081** | 本地开发必须换端口，见 §9 |

### 1.3 三个必须现在就规避的坑

1. **JDK 25 在 PATH 上**。Spring Boot 3.3 + Spring Framework 6.1 的字节码增强对 JDK 25 有风险。对策：所有 Maven 命令强制 `JAVA_HOME=E:\JDK21.0.10`，`pom.xml` 里写 `maven.compiler.release=21`，Dockerfile 用 `eclipse-temurin:21-jre`。README 明确写"需要 JDK 21"。
2. **端口冲突**。本机 3306/8080 已被其他项目容器占用。对策：compose 端口全部走变量（`${MYSQL_PORT:-3306}`），本地 `.env` 覆盖为 `3307/8082/3000`；**绝不**碰已有的 `mysql_server_1`，我们的容器统一命名 `tujang-*`。
3. **跨容器访问后端**。浏览器里写 `http://backend:8080` 不通，写 `http://localhost:8082` 在容器里又不对。对策：**前端不直连后端**，统一走 Next.js rewrite 代理：浏览器请求 `/api/backend/**` → Next 服务端转发到 `BACKEND_URL`（本地 `http://localhost:8082`，compose 内 `http://backend:8080`）。好处：零 CORS 配置、内外网一致、`<img src>` 也能直接用代理路径加载结果图。

---

## 2. 目录结构

```
.
├─ plan.md                      ← 本文件
├─ README.md                    ← 交付主文档（R-13 七项）
├─ docker-compose.yml
├─ .env.example                 ← 非敏感配置示例；真实 .env 不入库
├─ .github/workflows/ci.yml
├─ docs/
│  ├─ api.md                    ← 接口契约（字段级）
│  ├─ schema.md                 ← 表结构与状态机说明
│  ├─ demo.md                   ← 演示脚本（成功/指定失败/调耗时/多任务/刷新恢复/换用户）
│  └─ ai-workflow.md            ← AI 工作流说明（R-13 末项）
├─ db/
│  ├─ 01-schema.sql
│  └─ 02-seed.sql               ← 模板种子数据
├─ backend/                     ← Spring Boot（Maven）
│  ├─ Dockerfile
│  ├─ pom.xml
│  └─ src/{main,test}/...
├─ frontend/                    ← Next.js App Router
│  ├─ Dockerfile
│  ├─ package.json / pnpm-lock.yaml
│  └─ src/...
├─ seed/                        ← 本地可访问的示例素材（R-02 要求）
│  ├─ covers/                   ← 模板封面
│  └─ results/                  ← 预置结果图
└─ 项目说明文件/                 ← 题面与参考截图（原样保留）
```

后端运行期写入的文件统一落 `backend/data/files/{assets,results}`，compose 里挂 named volume；`seed/` 由启动时复制进去，保证"素材不能只靠浏览器临时地址"。

---

## 3. 数据模型

### 3.1 表

```sql
app_user(id PK, display_name, created_at)                     -- 身份极简方案，见 §8.3
template(id PK, title, category, cover_url, description,
         required_image_count, accepted_mime_types,          -- 逗号分隔 jpeg,png
         max_file_size_bytes, sort_order, created_at)
asset(id PK, user_id, template_id, stored_path, original_filename,
      mime_type, size_bytes, width, height, created_at)
task(id PK, user_id, template_id,
     status, current_step, failed_step, error_code, error_message,
     result_url, result_width, result_height,
     sim_fail_step, sim_durations_json,                       -- 模拟配置快照，见 §8.4
     created_at, started_at, finished_at, updated_at)
task_asset(task_id, asset_id, position, PK(task_id, asset_id))
```

索引：`task(user_id, created_at DESC)`（历史列表）、`task(status)`（恢复跟踪）、`asset(user_id, created_at)`。

### 3.2 状态机（二维）

```
status      : PENDING | PROCESSING | SUCCEEDED | FAILED        （整体状态）
current_step: ASSET_CHECK | GENERATE | FINALIZE                （当前阶段，独立于 status）
failed_step : 上面三个之一，仅 FAILED 时有值
```

合法迁移只有：

```
PENDING ──> PROCESSING(ASSET_CHECK) ──> PROCESSING(GENERATE) ──> PROCESSING(FINALIZE) ──> SUCCEEDED
                     │                        │                          │
                     └────────────────────────┴──────────────────────────┴──> FAILED(failed_step=当前步)
```

**不变式（要写单测守住）**：

- `SUCCEEDED` / `FAILED` 是吸收态，任何后续查询、任何重复执行都不能改回 `PROCESSING`；
- `FAILED` 时 `result_url IS NULL`，且失败步之后的步骤绝不执行；
- 每条状态写入都是"带条件的 UPDATE"（`WHERE id=? AND status NOT IN ('SUCCEEDED','FAILED')`），从数据库层面保证幂等。

---

## 4. 接口契约（最小集）

| 方法 | 路径 | 作用 | 关键点 |
|---|---|---|---|
| GET | `/api/templates?category=` | 模板列表 | 数据来自 DB，不在前端写死 |
| GET | `/api/templates/categories` | 分类清单 | 供 Tab 渲染 |
| GET | `/api/templates/{id}` | 模板详情 | 不存在 → 404 + 可读错误体 |
| POST | `/api/uploads` | 上传单张素材（multipart） | 服务端复校 mime/大小/可解码；返回 `assetId` + 可访问 `url` |
| DELETE | `/api/uploads/{assetId}` | 移除上传位时清临时素材 | 仅本人、未绑定任务时可删 |
| POST | `/api/tasks` | **提交制作任务** | 校验后建记录 → **202 + taskId**，不等制作完成 |
| GET | `/api/tasks/{id}` | 查单个任务 | **纯只读，绝不能推进任务** |
| GET | `/api/tasks?scope=history` | 个人历史（含未完成，用于刷新恢复） | 按 `user_id` 隔离，倒序 |
| GET | `/api/me` | 当前身份 | 供前端显示/切换 |
| POST | `/api/dev/simulation` | 设置模拟参数（仅 dev profile） | 见 §8.4 |
| GET | `/files/**` | 静态素材/结果图 | 也经 Next 代理暴露 |

> 详细字段（含成功/处理中/失败三种响应体）在 P0 落地为 `docs/api.md`，作为前后端唯一真源。字段命名直接沿用前一轮讨论的形态：任务的 `result.url` 就是"制作出来的东西"。

---

## 5. 分阶段计划

每个阶段都给出**产出物**和**可验证的完成判据**——判据必须是"跑一下就能看到"的，不写"完成了 X 功能"这种自欺的话。

### P0 · 地基与契约（约 0.5 天）· 里程碑 M1 起点 ✅ 已完成 2026-10-28

- [x] `git init` + `.gitignore`（`node_modules`、`target`、`.next`、`data/`、`.env`）+ 首次 commit
- [x] 目录骨架按 §2 建好
- [x] `docs/api.md` 写完（**先写契约再写代码**，前后端才能并行）
- [x] `db/01-schema.sql` + `02-seed.sql`（9 个模板 / 4 个分类，覆盖 1/2/3 张图、只收 jpeg、1MB 上限）
- [x] `docker-compose.yml` 骨架（mysql 起得来，web/api 在 `profiles: ["app"]` 下待 P7 补 Dockerfile）
- [x] 后端空工程：`/api/health` 返回 200；`mvn -B test` 能跑
- [x] 前端空工程：`pnpm build` / `lint` / `typecheck` 能过；`/api/**` 与 `/files/**` rewrite 打通
- [x] 种子素材：`tools/gen_seed_assets.py` 程序化生成 9 封面 + 9 结果图 + 3 示例照片

**完成判据**：`docker compose up -d mysql` 健康（✅ 9 templates / 4 categories）；
`pnpm start` 后首页显示"Java 已连通"，且 `/api/health`、`/files/seed/covers/*.jpg` 经 3000 端口代理均 200（✅）。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **代理路径**：原计划 `/api/backend/**` 前缀，实际改为 `/api/**` 直通——项目里没有也不需要 Next 的 route handler，不会冲突，URL 更干净。
2. **本机端口**：8082 被 RocketMQ 容器（`8080-8082`）占用，本地 API 改用 **8090**（`spring-boot:run` 时代为 8091 验证）。
3. **`mvn spring-boot:run` 在中文路径下不可用**：Spring Boot 插件的 fork 无法正确处理非 ASCII 路径，报 `ClassNotFoundException`。本地统一用 `java -jar`；已在 README 写明。
4. **pnpm 12 的构建脚本拦截**：需 `frontend/pnpm-workspace.yaml` 的 `allowBuilds`（由 `pnpm approve-builds --all` 生成），否则 `esbuild`/`sharp`/`unrs-resolver` 装不出二进制。
5. **不开 `output: "standalone"`**：它要求 Windows 符号链接权限，且会让本地与 Docker 产物走两条路径。Docker 侧改用常规多阶段构建。

### P1 · 模板域（约 0.75 天）✅ 已完成 2026-10-29

- [x] 后端：`TemplateMapper` + 列表/分类/详情三个接口 + 统一响应体与错误码
- [x] 前端：模板列表页（分类 Tab + 卡片网格 + 封面懒加载）、详情页骨架
- [x] 三态 UI：加载骨架 / 空数据 / 请求失败可重试；模板不存在 → 404 页
- [x] 小屏（≤640px）可操作：Tab 横向滚动、卡片两列
- [x] 测试：后端 `TemplateServiceTest`（5 例：分类切换/空分类/映射/404/计数）、前端 `format.test.ts`（7 例）

**完成判据**：切分类结果变化（✅ `?category=pet` 只出 2 个宠物模板）；点卡片进详情（✅ 要求/格式/上限正确渲染）；
后端不可用时报可读错误而非白屏（✅ `error.tsx` + `ApiError` 携带原因）；不存在的模板返回**真 404**
（✅ 生产模式实测 `/templates/nope` → 404，未知路径 → 404）。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **种子数据中文乱码**：MySQL 容器内 `LANG` 为空，`mysql` 客户端退化成 `latin1`，
   UTF-8 字节被当 latin1 再转 utf8mb4，入库即双重编码。修复：两个 SQL 文件开头 `SET NAMES utf8mb4`，
   并重建数据卷。校验方式：`HEX(LEFT(title,2))` 应为 `E69785`（旅）而非 `C3A6E28094`。
2. **`pnpm build` 后 `pnpm dev` 崩溃**（`Cannot find module './vendor-chunks/...'`）：两者共用 `.next`。
   修复：`distDir` 按 `NODE_ENV` 分目录，dev 用 `.next-dev`。
3. **`app/loading.tsx` 会吞掉 404 状态码**：它的 Suspense 边界让页面在数据到达前就 flush 出 200，
   `notFound()` 无法回写状态。修复：骨架屏下沉到列表页内部（只包网格），详情页不设边界。
4. **封面图烘焙了标题文字**：与卡片叠加标题重复。修复：`gen_seed_assets.py` 中封面/结果图不再绘制文字，
   仅示例照片保留。

### P2 · 上传域（约 0.75 天）✅ 已完成 2026-10-29

- [x] 后端：multipart 接收 → 落盘 → 读宽高 → 写 `asset` → 返回 `{assetId,url,mimeType,sizeBytes}`；`/files/**` 静态映射
- [x] 后端：服务端校验 mime 白名单、大小上限、**能否真正解码**（防伪造扩展名）
- [x] 前端：`UploadSlot` 组件——按 `requiredImageCount` 渲染 N 个位；选择 / 预览 / 替换 / 移除；逐位独立的"上传中 / 失败可重试"
- [x] 前端：数量、类型、大小三重前端校验，错误文案具体（"第 2 张需为 JPG，当前为 PNG"）
- [x] 未传够时提交按钮禁用并说明缺几张
- [x] 测试：后端 `AssetServiceTest` 9 例 + `ImageInspectorTest` 5 例；前端 `upload.test.ts` 8 例

**完成判据**：三类非法文件都被拦且提示准确——实测
超大（4.8 MB 喂 1 MB 上限模板）→ `413 FILE_TOO_LARGE`"文件 4.8 MB，超过上限 1 MB"；
改扩展名假图 → `415 UNSUPPORTED_MEDIA_TYPE`；
魔数正确但内容损坏 → `400 FILE_NOT_DECODABLE`；
类型不符模板 → `415`"该模板仅接受 image/jpeg，当前为 image/png"。
关掉后端时上传会失败并给出可重试入口（前端 `ApiError` 文案直接展示）。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **提前引入 `CurrentUserProvider`**：`asset.user_id` 有外键非空，P2 就得有归属。做法是抽出接口 + `DemoCurrentUserProvider`（固定种子用户），
   P6 只替换实现，业务代码不动。
2. **删除素材的两道保护**：他人的 → `403 ASSET_FORBIDDEN`；已被任务引用 → `409 ASSET_IN_USE`（先查 `task_asset` 再删，
   比撞外键报 500 更可读）。
3. **校验顺序固定为"大小 → 魔数类型 → 解码"**，越贵的检查越靠后；三种失败给三种错误码（413/415/400），
   前端不需要猜是哪类问题。
4. **前端校验不作为安全边界**：`validateFile` 只为即时反馈，服务端一律用文件魔数重新判定内容类型。
5. **不做浏览器 E2E**（题面也不要求）：上传链路用 curl 走完整代理路径验证 + 单测覆盖校验分支，UI 交互人工确认。
   截图见 README 的 `docs/p9-detail.png`（P9 接入登录后统一重拍，旧图已删）。

### P3 · 提交与异步执行（约 1.5 天）✅ 已完成 2026-10-29 · 后端核心

- [x] `POST /api/tasks`：复校（数量/重复/归属/类型/大小/文件在盘）→ 建 task + task_asset → **202 立即返回**
- [x] `TaskPipeline`：`taskExecutor` 线程池跑 `ASSET_CHECK → GENERATE → FINALIZE`
- [x] 每步写库（`current_step`），失败即中断并落 `failed_step` + `error_code` + 可读文案
- [x] `GET /api/tasks/{id}` 与历史接口**只读**；终态靠条件 UPDATE 守住
- [x] 幂等：已终态直接跳过；抢不到 `PENDING→PROCESSING` 就安静退出
- [x] **单测**：`TaskPipelineTest` 8 例（顺序/三处中断/终态跳过/抢不到/队列满）、`TaskServiceTest` 8 例、`SeedResultProducerTest` 3 例

**完成判据**：实测 `POST` 返回 202 + PENDING；轮询看到
`PROCESSING/ASSET_CHECK`(0–0.8s) → `GENERATE`(0.8–3.2s) → `FINALIZE`(3.2–4.0s) → `SUCCEEDED`，
结果图 `HTTP 200 image/jpeg 1200×900`；连续查 10 次，`status` 与 `updated_at` 不变（查询不推进任务）；
数量不符 → 400 字段级错误；他人素材 → 403。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **提交接口返回完整 `TaskDto`**（契约原写只返回 3 个字段）：前端对"刚受理"和"查询到"的任务用同一套渲染，
   卡片能立刻出现，不必先等一次轮询。已同步更新 `docs/api.md §4.1`。
2. **派发必须在事务提交之后**：用 `TransactionSynchronization.afterCommit` 投递，
   否则后台线程可能查不到还没提交的 task 行（典型竞态，同步测试也测不出来）。
3. **队列满 = 明确失败**：拒绝策略用 `AbortPolicy`，捕获后把任务标成 `FAILED(QUEUE_FULL)`。
   不用 `CallerRunsPolicy`——那会让提交请求被就地执行拖住，破坏"受理即返回"。
4. **模拟配置在 P3 就先读了**（`sim_fail_step` / `sim_durations` 快照 → `SimulationProfile`），
   P5 只需要补一个写这两列的开发接口，执行器不动。
5. **`FAILED` 允许从 `PENDING` 进入**：队列满时任务还没开始跑，`WHERE status NOT IN ('SUCCEEDED','FAILED')` 覆盖这种情况。
6. **提交后清空上传位只清本地**，不调服务端 `DELETE`：素材已绑定任务，删掉会让「素材检查」失败。
7. `TaskServiceTest` 用 `@MockitoSettings(LENIENT)`：多次断言"在校验早期就抛错，后续 stub 用不上"，
   严格模式会把这种**正确行为**报成 UnnecessaryStubbing。

### P4 · 前端异步状态（约 1.5 天）✅ 已完成 2026-10-29 · 前端核心

- [x] `useTaskStore`（Zustand）：`entries` 按 **taskId 索引**，天然的互不串扰
- [x] `useTaskPoller`：只轮询"未完成"的 id 集合；集合空 → 清定时器；集合变 → 先 abort 上一轮
- [x] 状态分离：`upload`（slot.status）/ `submit`（composer.submitting）/ `task`（store）/ `query`（store.queryState）
- [x] 详情页 History 卡片区与历史页共用 `TaskList`；同屏可多个"处理中" + 失败/成功
- [x] 受理成功瞬间：按钮从"提交中"恢复、清空上传位、新卡片入场并开始轮询
- [x] 历史页：按创建时间倒序、模板信息、状态徽标、结果缩略图、失败原因
- [x] 查询异常：卡片单独一条黄色提示 + 重试按钮，与红色的"任务失败"分开
- [x] **前端单测**：`task-store.test.ts` 10 例 + `use-task-poller.test.tsx` 5 例（共 30 例）

**完成判据**：连提 3 个任务 → 三张卡各自推进（实测 t≈1s 时三条都 `PROCESSING/GENERATE`，t≈7s 全部 `SUCCEEDED`）；
刷新页面后未完成任务仍在并继续跟踪（服务端首屏直接渲染在途任务，实测截图即刷新后状态）；
单测覆盖"任务 A 更新不影响 B""查询失败不改 status""无待处理任务时不发请求"。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **轮询器提到 layout（`TaskWatcher`）**，而不是页面级：这样刷新任意页面都能恢复跟踪，
   也避免"从详情页跳到历史页就断更"。离开站点（layout 卸载）才清理；切用户的清理留到 P6。
2. **`useMergedTasks` 用"服务端首屏 + store 覆盖"**，首帧不空白；并且 `hydrateMissing`
   只补缺失的 id，避免服务端旧数据把轮询到的新状态盖回去。
3. **结果图从大图改为 128×84 缩略图 + 尺寸信息**：历史列表里把 4:3 图拉满宽会变成一条横幅，信息密度反而更低。
4. 详情页与历史页共用 `TaskList`，差别只有 `showTemplate` 与空态文案。

### P5 · 失败与耗时模拟（约 0.5 天）✅ 已完成 2026-10-29

- [x] `SimulationRegistry`（内存态）+ `SimulationSource` 快照：受理任务时把配置写进 task 行
- [x] `POST/GET /api/dev/simulation`：`{failStep, perStepDurations, applyTo}`，仅 `@Profile("dev")`
- [x] 生产 profile 下 bean 不存在 → 路径自然 404，业务代码里没有 profile 判断
- [x] `docs/demo.md`：三步失败、调快/调慢、多任务同屏、刷新恢复、生产 404 的逐条命令
- [x] 单测 `SimulationRegistryTest` 6 例（NEXT 一次性 / ALL 持续 / 仅耗时 / 清空）

**完成判据**：实测
`NEXT + GENERATE` → 任务 1 失败(`GENERATE_TIMEOUT`)、任务 2 自动恢复正常成功（一次性生效）；
`ALL + ASSET_CHECK` → 连续两个任务都失败在同一阶段（持续生效）；
`FINALIZE` → 前两步成功、最后一步失败且无结果；
耗时 `1200/4000/1200` → 阶段切换发生在 0.0s / 1.2s / 5.3s / 6.5s；
`SPRING_PROFILES_ACTIVE=prod` → `GET|POST /api/dev/simulation` 均 404，`/api/templates` 仍 200。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **`spring.profiles.default: dev`**：不显式指定 profile 时按 dev 跑，这样 `java -jar` 和 IDE 启动都能用模拟接口；
   要验证"生产没有这些接口"就显式 `SPRING_PROFILES_ACTIVE=prod`。已在 `docs/demo.md` 写明。
2. **`applyTo` 默认 `NEXT`**（不是 ALL）：默认一次性可以避免"设了一次失败之后全失败"这种难排查的演示事故。
3. **配置只存在内存**，进程重启即失效——它是开发工具不是业务状态，已在文档说明。

### P6 · 身份与个人记录（约 0.5 天）✅ 已完成 2026-10-29 · **P9 已替换为账号登录**

- [x] `tujang_uid` Cookie（HttpOnly / SameSite=Lax / 30 天）+ `UserService` 三条解析规则
- [x] `CookieCurrentUserProvider` 替换 P2 的演示实现，业务代码零改动
- [x] `CurrentUserInterceptor` + `CurrentUserHolder`（ThreadLocal，请求结束必清）
- [x] `GET /api/me`（含 `devTools`）、`POST /api/dev/users/switch`（仅 dev，支持切回）
- [x] 顶栏显示当前用户；「切换用户 / 切回上一个」仅 dev 可见；切换后整页跳转
- [x] 单测 `UserServiceTest` 7 例（认领已有 / 采用预置 id / 无 Cookie / 非法 Cookie / 显示名 / last_seen / 换新）
- [x] 文档：`docs/api.md §5`、README「身份与个人记录」

**完成判据**：实测 A 建 1 个任务 → 切到 B 看到 0 条 → 切回 A 又看到 1 条；
B 的 Cookie 访问 A 的任务 → 403 `TASK_FORBIDDEN`；连续 5 次"全新用户首次打开历史页"均 200 且带头像；
`SPRING_PROFILES_ACTIVE=prod` → 切换接口与模拟接口都 404，`/api/me` 的 `devTools=false`。

**实际与计划的偏差（两个并发 bug，都是实测才暴露的）**：

1. **首屏并发建用户撞主键**：layout 取 `/api/me` 与页面取 `/api/tasks` 同时发现"新 Cookie 的用户不存在"，
   两边都 INSERT，其中一个 `Duplicate entry` → 500。修复：`INSERT ... ON DUPLICATE KEY UPDATE id = id` 幂等化。
2. **REPEATABLE READ 快照导致"写入后读不到"**：即使插入幂等，`@Transactional` 里"插入后再查"
   读到的仍是事务开始时的快照，看不到并发事务刚提交的行。修复：`resolveOrCreate` **不加事务**，
   每条语句各自提交。这两个 bug 单测都测不出来，只有真实并发首屏才会复现。
3. **中间件先发 Cookie**：服务端组件不能设 Cookie，所以由 `middleware.ts` 生成并同时写进请求与响应；
   后端采用前端预生成的 id，避免每次 SSR 多建一个空用户。
4. **服务端取数要转发 Cookie**：抽了 `lib/server-api.ts`（`cookies()` → `Cookie` 头），
   否则 SSR 请求在 Java 看来都是新访客，历史页永远是空的。
5. **可切回上一个**：切换后原身份不可达会让"隔离演示"很难讲，于是给 `switch` 加了可选 `userId`，
   前端记住上一个用户并提供「切回上一个」。生产环境这个入口整体不存在。

### P7 · 工程交付（约 1.0 天）✅ 已完成 2026-10-29

- [x] 补齐测试：后端 53 例（步骤顺序/中断/幂等/校验）；前端 38 例（含新增 `TaskCard` 8 例：四态呈现、查询异常≠任务失败、重试成功/失败）
- [x] `.github/workflows/ci.yml`：`web`（install --frozen-lockfile → lint → `tsc --noEmit` → test → build）+ `api`（`mvn -B verify`）+ `images`（buildx 构建两个镜像）
- [x] `backend/Dockerfile`（maven build → JRE 运行，非 root，自带 HEALTHCHECK）、`frontend/Dockerfile`（deps → build → runner）
- [x] `docker-compose.yml` 完整：mysql（`db/` 自动初始化）+ api（等 mysql healthy）+ web（等 api healthy）；`up --build` 冷启动即用
- [x] `README.md` 按 R-13 七项重写（版本/结构/命令、配置与初始化、接口与状态机、演示、身份、已完成与限制、部署回滚）+ AI 工作流 + 引用来源
- [x] `docs/ai-workflow.md` 用本次实施过程本身做例子（含被真实验证抓出来的 5 个问题）

**完成判据**：`docker compose down -v` 清空数据卷后 `docker compose up -d --build`，三个服务全部 healthy；
经 3001（web）走完整链路：模板 9 个、上传 2 张、提交 202、阶段 0.55/1.00/3.64s、4.08s SUCCEEDED、结果图 200 image/jpeg；
注入 `GENERATE` 失败 → `FAILED/GENERATE_TIMEOUT` 且 `result=null`，`NEXT` 一次性用完自动清空；
连查 10 次 `status|updatedAt` 只有 1 种取值；`prod` profile 下三个 dev 接口全 404、`devTools=false`、`/api/templates` 仍 200。

**实际与计划的偏差（已修正，后续阶段沿用）**：

1. **中间件不能给 `/api/**` 发 Cookie**。rewrite 的响应来自 Java，Next 不会把中间件设在
   `NextResponse` 上的 `Set-Cookie` 合并进这个代理响应（实测：页面响应有，`/api/me` 没有）。
   结果纯 API 客户端（curl、脚本）永远拿不到身份，上传与提交分属两个用户 → `403 ASSET_FORBIDDEN`。
   修复：中间件 matcher 排除 `api`/`files`，**页面请求由中间件发 id，API 请求由 Java 发**，
   Java 的 `Set-Cookie` 经代理原样透传（实测有效）。`docs/demo.md` 的 curl 示例随之加上 Cookie jar。
2. **`/_not-found` 预渲染噪音**：根布局调 `cookies()`，构建时会打一行红色
   "couldn't be rendered statically because it used `cookies`"（退出码仍是 0）。
   修复：根布局声明 `export const dynamic = "force-dynamic"`——所有页面本来就带身份、无静态页。
3. **`web` 的健康检查经 Next 代理打后端 `/api/health`**：一条检查同时覆盖"web 起来了"和
   "rewrite 指向对了"。指向错时容器直接 unhealthy，而不是等人打开页面才发现 502。
4. **api 构建上下文改成 `./backend`**：seed 走 volume 只读挂载，镜像里不需要它，上下文更小。
5. **冷启动用映射端口验证**（3307/8092/3001）：本机 3306/8080/3000 被其他项目占用。
   端口映射是纯配置，已用同一份 compose 的默认值做静态核对。
6. **CI 的 Actions 未在 GitHub 上真实跑过**（本仓库没有远端）。CI 三个 job 的命令已在本机
   逐条等价执行（`lint`/`typecheck`/`test`/`build`/`mvn verify` 全绿），但"工作流文件本身能被
   GitHub 正确调度"记为**未验证**，已在 README 的 R-12 行如实标注。

### P8 · 收尾验证（约 0.5 天）✅ 已完成 2026-10-29

- [x] 按"验收者视角"重走：`git clone` 到空目录 → `cp .env.example .env` → `up -d --build` → 五段演示全跑
- [x] 清点 §6 矩阵每一行为"已验证 / 部分验证 / 未验证"，结论写进 README §7.1（含两处没做到满分的地方）
- [x] 打 tag `v1.0.0`，README 顶部指向该 tag（hash 用 `git rev-list -n 1 v1.0.0` 取，见下方偏差 1）
- [x] 检查无密钥、无大文件、无 `data/`/`target/`/`node_modules` 残留入库

**完成判据**：干净目录克隆 → compose 起来 → 五个演示全通（✅ 实测记录见 README §7.1.2）。

**实际与计划的偏差**：

1. **验收 commit 用 tag 表示，而不是把 hash 写死在 README**。写 hash 这个动作本身会产生新提交，
   写进去的 hash 立刻过期；改完再 amend 又会变。用 annotated tag `v1.0.0` 指向最终提交，
   README 写 `git rev-list -n 1 v1.0.0`，任何时候都能取到正确 hash，也不会自己过期。
   （顺带记一笔：annotated tag 用 `git rev-parse` 取到的是 tag 对象而不是 commit，
   要用 `rev-list -n 1` 或 `rev-parse v1.0.0^{commit}`——这就是上面把命令写成 `rev-list` 的原因。）
2. **干净目录里必须改端口**：本机 3306/8080/3000 都被别的项目/进程占着，所以克隆后
   `cp .env.example .env` 之后改了 `MYSQL_PORT/API_PORT/WEB_PORT`。README 里本来就写了这一步，
   但这意味着"默认端口零配置即用"这条**没有在真机上被完整执行过**——端口映射是纯配置，
   同样一份 compose 在 P7 与 P8 各起过一次，映射关系逐行核对过。
3. **仓库里的 `docs/*.png` 共约 3 MB**（最大 850 KB）。它们是交付证据（UI 截图），不是构建产物，
   保留在仓库里；构建缓存、依赖目录、`data/` 均未入库（`git ls-files` 已核对）。
4. **密钥扫描**：`git grep -i 'password|secret|api_key'` 只命中示例口令（`tujang_pw` 之类），
   没有真实凭据；`.env` / `.env.local` / `.vscode/` 都在 `.gitignore` 里。

### P9 · 账号登录（题目之外的返工，约 0.5 天）✅ 已完成 2026-10-29

**为什么加**：P6 的身份方案是"首次访问发一个随机 UUID Cookie"，`app_user` 只是访问者标识。
交付后回看，这个设计有两个问题：一是它撑不起"用户"两个字（没有账号、没有密码），
二是 dev 下的「切换用户」按钮长得像产品功能，容易被读成"谁都能随便变人"。
所以把身份换成真登录。

- [x] `app_user` 加 `username`（唯一）+ `password_hash`（BCrypt）；新增 `user_session`
- [x] `POST /api/auth/register|login|logout`、`GET /api/me`（未登录 401）
- [x] 会话 Cookie `tujang_sid`，库里只存 `sha256(token)`；30 天过期，退出即删
- [x] 权限边界：浏览公开；上传/提交/记录需要登录；未登录 `/history` 跳登录页并带回跳地址
- [x] 前端：`/login`、`/register` 页面；顶栏改成"账号 + 退出登录"；未登录时详情页上传区换成登录入口
- [x] 删除 P6 的「切换用户 / 切回上一个」与 `DevUserController`，删掉已无用的 `middleware.ts`
- [x] 单测：`AuthServiceTest` 12 例 + `SessionTokensTest` 3 例；前端 `auth-rules.test.ts` 8 例 + "未登录不轮询" 1 例
- [x] 文档：`docs/api.md §5`、README §4/§5/§7、`docs/demo.md` 全文重写

**完成判据**：注册即登录并下发 `tujang_sid`；未登录 `/api/me`、`/api/tasks`、`/api/uploads` 全 401、
`/api/templates` 仍 200、`/history` 307 → `/login?next=/history`；A 有 1 条 / B 有 0 条 /
用密码重新登录 A 仍有 1 条；B 访问 A 的任务 403；错误密码与不存在的用户返回同一个
`401 INVALID_CREDENTIALS`；重名注册 409；退出后 204 且 `/api/me` 变 401。（全部实测）

**实际与计划的偏差**：

1. **没有用 Spring Security 全家桶**，只引 `spring-security-crypto` 拿 BCrypt。
   引入 starter 会带来一整套过滤器链与默认配置（CSRF、登录页、拦截规则），
   和"拦截器 + ThreadLocal"的现有结构冲突，而我们要的只是密码哈希，不划算。
2. **会话用数据库表 + 随机 token，不用 JWT**。JWT 的好处是无状态，代价是**无法撤销**——
   而这个项目"退出登录"必须立刻生效；DB 会话多一次查询，但删一行就是下线，语义最直白。
3. **库里存 token 的 SHA-256，不存原值**。多一层哈希几乎零成本，换来"数据库泄露也换不出可用 Cookie"。
4. **登录失败不区分"用户不存在"与"密码错"**，并且用户不存在时也跑一次 BCrypt 比对——
   否则响应时间会泄露账号是否存在。这条有单测盯着。
5. **取消 `middleware.ts`**。它原本的作用是给无身份访客预生成 Cookie；有了登录之后，
   Cookie 只应该由登录产生，继续预生成反而会造出"没有账号的会话"，删掉更干净。
6. **表结构变了，老数据卷必须重建**（`docker compose down -v`）。`db/*.sql` 只在空卷时执行，
   升级路径写在 README §8.2（手工 `ALTER` + 给老用户补 `username`）。这是没有迁移框架的代价，
   如实记在「已知限制」里，不假装支持平滑升级。
7. **演示账号由代码创建，不写 SQL**：BCrypt 哈希带盐，写死在 seed 里既不可校验也不可读；
   交给与注册完全相同的 `AuthService.register` 创建，行为和真实注册一致。
   `@Profile("dev")` 保证生产不会凭空出现一个 demo 账号。
8. **上线时踩到自己埋的坑**：`AuthService.register` 里 `userMapper.insert` 的参数顺序写错
   （哈希与展示名互换），单测直接抓到——这类错误肉眼看代码很难发现，是"把参数按名字写进
   ArgumentCaptor 断言"救回来的。

---

## 6. 需求追溯矩阵（防止漏项）

状态口径与 README §7.1 一致：✅ 已验证（跑过并留证据）／⚠️ 部分验证（有一块没在真实环境跑）／⬜ 未验证。

| 需求 | 落点 | 验证证据 | 状态 |
|---|---|---|---|
| R-01 栈与职责 | P0/P3/P4 | compose 三服务；接口清单；DB 有 task 表 | ✅ |
| R-02 列表与数据 | P1 | 分类切换截图；seed.sql | ✅ |
| R-03 详情/上传/校验 | P2 | 三类非法文件被拦；服务端校验单测 | ✅ |
| R-04 受理与异步 | P3 | curl 202 立刻返回；关页面任务照跑 | ✅ |
| R-05 多步骤与查询 | P3 | 单测断言顺序；查接口不改状态（连查 10 次） | ✅ |
| R-06 失败与模拟 | P5 | demo.md 逐条复现，三段失败 + 耗时调节 | ✅ |
| R-07 状态表达与异常 | P4 | 前端单测：查询失败 ≠ 任务失败 | ✅ |
| R-08 多任务与生命周期 | P4 | 三任务同屏独立；停止轮询单测 | ✅ |
| R-09 刷新恢复与记录 | P4 | 刷新后继续跟踪；历史页含结果 | ✅ |
| R-10 身份与个人记录 | P9 | 账号登录；两账号记录隔离；越权 403；退出后 401 | ✅ |
| R-11 视觉与可用性 | P1/P2/P4 | label、`aria-label`、全局 `:focus-visible`、390px 截图 | ⚠️ Tab 顺序未逐键走查 |
| R-12 CI 与测试 | P7 | 本地 lint/tsc/test/build + `mvn verify` 全绿 | ⚠️ Actions 未在 GitHub 调度 |
| R-13 材料与说明 | P7/P8 | README + `docs/` | ✅ |
| R-14 容器交付 | P7/P8 | 两次冷启动（含干净目录克隆） | ✅ |

---

## 7. 关键设计决策（会被问"为什么这么设计"）

1. **异步用进程内线程池，不用 MQ**。题面明确不强制 MQ；单机场景下线程池足够，且能把"受理 / 执行 / 落库"三件事的边界讲清楚。代价：进程重启会丢在跑的任务 —— 这正是题面允许不做的"重启恢复"，写进已知限制。
2. **查询接口绝对只读**。任务的推进只由 `TaskPipeline` 写库，查询路径上不出现任何写操作。这条是 R-05 的明文要求，也是很多实现翻车的地方。
3. **状态用"条件 UPDATE"保证终态不可回退**，而不是只靠代码里 if 判断——数据库层面兜底，单测更容易写。
4. **前端不直连后端**，走 Next rewrite 代理：零 CORS，容器内外一致，`<img>` 也能走同一路径。
5. **任务状态按 id 存在 Map 里，且拆成四维**。一个 `status` 字段同时表示"上传中/提交中/处理中/查询失败"必然串味，这是 R-07 的核心考点。
6. **模拟配置在任务受理时快照入行**，而不是全局单例——否则无法做到"同时演示一个成功、一个失败"，而这恰好是 UI-03 的画面。
7. **结果图先用预置映射**（方案 A），主干通了再考虑在 FINALIZE 里真合成（方案 B）。先保证闭环可交付，再谈说服力。
8. **会话用数据库表 + 随机 token，不用 JWT**。JWT 无状态但不可撤销，而"退出登录必须立刻生效"是产品底线；删一行就是下线，比维护黑名单简单。
9. **账号只引 BCrypt，不引 Spring Security**。要的只是密码哈希；引入 starter 会带来过滤器链、默认登录页、CSRF 等一整套需要解释的东西，与本项目"拦截器 + ThreadLocal"的结构冲突。
10. **权限边界按接口分，不按页面分**。后端对上传/提交/记录返回 401，前端据此决定跳转或显示登录入口；页面级拦截容易被绕过，而接口级是真正的边界。

---

## 8. 演示脚本草案（P5/P7 落地为 docs/demo.md）

### 8.1 成功路径
列表 → 选「旅行明信片」（2 张图）→ 传 2 张 → 提交 → 3 张卡里新卡从"等待"走到"处理中·素材检查→生成→结果整理"→ 出结果图。

### 8.2 指定阶段失败
```bash
curl -X POST localhost:8082/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":"GENERATE","durationMsPerStep":3000,"applyTo":"NEXT"}'
```
下一个任务必在"模拟生成"失败，卡片显示"生成服务响应超时"，且**没有结果图**。

### 8.3 调耗时
同一接口传 `durationMsPerStep: 10000`，便于观察"处理中"与排队；传 `200` 便于快速跑通。

### 8.4 多任务与刷新
设置 `applyTo: NEXT` 失败一次 → 立刻连提 3 次 → 前两个成功、第三个失败同屏 → 中途 `F5` → 未完成者继续跟踪。

### 8.5 身份
注册 `alice` 提交一个任务 → 退出 → 注册 `bob`：记录为空、详情页看不到 alice 的任务 → 退出、用 `alice` 登录：记录与结果都还在。

---

## 9. 本地运行约定

compose 里三个端口一律写成 `${VAR:-默认值}`，**默认值就是标准端口**（别人机器零配置即用），本机靠不提交的 `.env` 覆盖：

| 用途 | compose 写法 | 默认 | 本机 `.env` 覆盖（3306/8080 已被占） |
|---|---|---|---|
| MySQL | `${MYSQL_PORT:-3306}:3306` | 3306 | `MYSQL_PORT=3307` |
| API | `${API_PORT:-8080}:8080` | 8080 | `API_PORT=8082` |
| Web | `${WEB_PORT:-3000}:3000` | 3000 | 不用改 |

- 本地起后端：`$env:JAVA_HOME='E:\JDK21.0.10'; mvn -f backend/pom.xml spring-boot:run`
- 本地起前端：`pnpm --dir frontend dev`，`.env.local` 写 `BACKEND_URL=http://localhost:8082`
- `.env.example` 入库（全默认值 + 注释），`.env` 与 `.env.local` 进 `.gitignore`。

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| PATH 上是 JDK 25 | 编译/运行诡异错误 | 全局固定 `JAVA_HOME`，`release=21`，Dockerfile 用 temurin 21 |
| 3306/8080 被占 | 本地起不来 | 端口变量化；本地 `.env` 走 3307/8082；容器命名加 `tujang-` 前缀 |
| pnpm 12 / Next 15 版本漂移 | 装不上或 API 变化 | P0 一次锁定并提交 lockfile，之后不随意升级 |
| 前端状态越写越乱 | R-07/R-08 丢分 | 先写测试骨架（任务隔离、查询异常、停止轮询）再写 hook |
| 只在本地跑通、容器里跑不通 | 运行验收记"未验证" | **P3 结束就开始做 compose 雏形**，不等 P7 |
| 演示靠手速、不可复现 | 面试演示翻车 | 模拟配置落库 + demo.md 逐条命令 |
| 想做的东西太多 | 交付不完整 | 严格执行 §11 不做清单 |

---

## 11. 范围外（明确不做，写进 README）

- 真实 AI / 图像处理 / 视频；真实对象存储（本地目录 + volume）
- MQ / Redis / 集群 / 分布式调度；SSE / WebSocket
- 模板 CRUD 后台、模板可视化编辑
- 积分 / 会员 / 支付 / 第三方登录 / 国际化 / 多区域部署
- **找回密码 / 邮箱验证 / 登录限流 / 多设备管理**（做了账号密码登录，但这些没做，README §7.3 列明）
- 失败任务重试、任务删除、结果下载、任务受理幂等与断点续跑（README 讨论取舍即可）
- 服务重启后自动恢复执行（README 明说边界）
- E2E 浏览器测试、真实公网部署、自动发布

---

## 12. 里程碑与提交约定

| 里程碑 | 含阶段 | 状态 | 验收点 |
|---|---|---|---|
| M1 骨架通 | P0–P1 | ✅ 2026-10-28 | 首页展示 Java 数据，代理链路通 |
| M2 能提交 | P2–P3 | ✅ 2026-10-29 | curl 提交 + 阶段推进 + 单测绿 |
| M3 闭环完整 | P4–P6 | ✅ 2026-10-29 | 三任务并行、刷新恢复、切用户隔离 |
| M4 可交付 | P7–P8 | ✅ 2026-10-29 | 冷启动 compose 一次通过 + README 齐全 |
| M5 真账号 | P9 | ✅ 2026-10-29 | 注册/登录/退出、未登录 401、两账号隔离 |

约定：每个里程碑结束打 tag（`m1`…`m5`）、更新本文件状态列、commit message 用 `feat(scope): ...` 格式，便于 README 记录验收 commit。

**总预估：约 7–7.5 个工作日**（P0 0.5 + P1 0.75 + P2 0.75 + P3 1.5 + P4 1.5 + P5 0.5 + P6 0.5 + P7 1.0 + P8 0.5）。

---

## 13. 交付状态与后续可选工作

P0–P9 全部完成，交付物见 README，验收版本为 tag `v1.1.0`。

如果还有时间，按"性价比"排序可以做：

1. **重启恢复**（题面不要求）：启动时扫 `PROCESSING` 行重新派发，或把它们标成
   `FAILED(INTERRUPTED)`。难点不在扫表，而在"执行中的任务无法判断是真卡住还是正在跑"。
2. **任务受理幂等**：加 `Idempotency-Key`，避免用户手抖重复提交（现在前端靠禁用按钮挡）。
3. **真合成结果图**：FINALIZE 里用 Java 把用户素材拼成一张成品，而不是复制预置图——
   能让"制作"更有说服力，但不影响本题的评分重点。
4. **浏览器 E2E**：Playwright 跑一遍"上传 → 提交 → 轮询 → 刷新"，补上现在靠人眼确认的那段。

不做清单见 §11；每一条都不是遗漏，而是明确的范围决定。





