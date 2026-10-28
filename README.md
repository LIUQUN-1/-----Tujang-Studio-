# 图匠工坊 · Tujang Studio

> 素材制作网站：浏览模板 → 上传素材 → 提交制作任务 → 后台异步执行 → 查看历史与结果。
> 小影科技 2027 校招题 · 当前进度 **P0（骨架验证通过）**，完整开发计划见 [plan.md](plan.md)。

---

## 这个项目在做什么

制作流程不调用任何真实 AI。模板、素材与"生成结果"都是可预期的模拟数据，
真正要做出的是**后端多步骤异步处理**与**前端异步状态管理**这两件事：

```
POST /api/tasks ──> 202 + taskId（不等制作完成）
                       │
                       └─ 后台线程池：ASSET_CHECK → GENERATE → FINALIZE
                          每步把「阶段 + 状态」写入 MySQL，失败即中断
前端轮询 GET /api/tasks/{id}（纯只读）──> 阶段推进 / 成功结果 / 失败原因
刷新页面 ──> 从 MySQL 恢复历史，未完成任务继续跟踪
```

接口契约见 [docs/api.md](docs/api.md)，它是前后端的唯一真源。

---

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 仓库骨架、接口契约、数据库与种子数据、Compose、前后端空工程（链路打通） | ✅ 完成 |
| P1 | 模板列表 / 分类筛选 / 详情页 | ⬜ 待开始 |
| P2 | 素材上传（预览、替换、移除、校验） | ⬜ 待开始 |
| P3 | 提交受理 + 三步异步执行 + 状态查询 | ⬜ 待开始 |
| P4 | 前端任务状态机、轮询生命周期、历史与刷新恢复 | ⬜ 待开始 |
| P5 | 失败注入与耗时调节 | ⬜ 待开始 |
| P6 | 用户身份与个人记录 | ⬜ 待开始 |
| P7 | 测试、CI、容器交付、README 定稿 | ⬜ 待开始 |
| P8 | 冷启动验收与收尾 | ⬜ 待开始 |

> P0 已验证：`/api/health` 与 `/files/seed/**` 经 Next 代理可达；页面服务端直连 Java 显示"已连通"；
> MySQL 初始化出 9 个模板 / 4 个分类。

---

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 前端 | Next.js（App Router）/ React / TypeScript | 15.1.6 / 19.0.0 / 5.7.3 |
| 样式 | Tailwind CSS | 3.4.17 |
| 客户端状态 | Zustand | 5.0.3 |
| 后端 | Java + Spring Boot + MyBatis | 21 / 3.3.5 / 3.0.3 |
| 数据库 | MySQL | 8.0 |
| 测试 | Vitest + Testing Library（前端）、JUnit 5（后端） | 2.1.8 / 5.10.5 |
| 运行时 | Node / pnpm / Maven | 22 / 12.15.0 / 3.9.12 |

> **后端必须用 JDK 21**。本机 PATH 上可能装着更高的 JDK，务必显式指定 `JAVA_HOME`，
> 否则 Spring Boot 3.3 的字节码增强可能报错。

---

## 目录结构

```
.
├─ docs/api.md              接口契约（前后端唯一真源）
├─ db/                      01-schema.sql / 02-seed.sql（MySQL 容器首次启动自动执行）
├─ seed/                    程序化生成的种子素材：covers / results / samples
├─ tools/gen_seed_assets.py 素材生成脚本（可复现，无第三方版权素材）
├─ backend/                 Spring Boot（Maven）
├─ frontend/                Next.js（App Router）
├─ docker-compose.yml       mysql / api / web
└─ plan.md                  分阶段实施计划
```

---

## 快速开始

### 方式一：Docker Compose（推荐，评审用）

```bash
cp .env.example .env      # 端口冲突时改这里的 MYSQL_PORT / API_PORT / WEB_PORT
docker compose up --build
```

- Web: <http://localhost:3000>
- API: <http://localhost:8080>
- MySQL: `localhost:3306`，库名 `tujang`

> **P0 阶段说明**：`api` / `web` 两个服务暂时位于 `profiles: ["app"]` 下，
> 所以 `up --build` 目前只会启动 MySQL。P7 会移除该 profile 并补齐两个 Dockerfile。
> 现阶段请用下面的"方式二"。

### 方式二：本地开发

前置：JDK 21、Node 22、pnpm、Docker。

```bash
# 1) 数据库（首次启动会自动建表 + 灌种子数据）
cp .env.example .env
docker compose up -d mysql

# 2) 后端（二选一）
cd backend
mvn -DskipTests package && java -jar target/tujang-studio-api-0.1.0.jar
# 或：mvn spring-boot:run

# 3) 前端（另开一个终端）
cd frontend
pnpm install
pnpm dev
```

> ⚠️ **Windows + 含中文/括号的项目路径**下，`mvn spring-boot:run` 会报
> `ClassNotFoundException: ...TujangStudioApplication`。原因是 Spring Boot Maven 插件
> 为 fork 出的 JVM 组织 classpath 时无法正确处理非 ASCII 路径（`mvn test` 不受影响，
> 它走 surefire）。此时请改用上面的 `java -jar`，或把项目放到纯 ASCII 路径。
> 容器内路径为 `/app`，不存在该问题。

本机若 `3306` / `8080` 已被占用，用环境变量改端口（参见 `.env.example`）：

| 变量 | 作用 | 本机示例 |
|---|---|---|
| `MYSQL_PORT` | MySQL 映射到宿主机的端口 | `3307` |
| `API_PORT` | 后端映射到宿主机的端口 | `8090` |
| `WEB_PORT` | 前端映射到宿主机的端口 | `3000` |

后端连数据库用 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`，
本地跑 jar 时要相应设置，例如：

```powershell
$env:DB_PORT='3307'; $env:SERVER_PORT='8090'
java -jar target/tujang-studio-api-0.1.0.jar
```

前端只认一个变量 `BACKEND_URL`（写在 `frontend/.env.local`）：

```
BACKEND_URL=http://localhost:8090
```

浏览器**始终**访问同源的 `/api/**` 与 `/files/**`，由 Next 的 rewrite 转发到 `BACKEND_URL`，
因此不存在 CORS 配置，容器内外行为一致。

---

## 数据库

`db/01-schema.sql`（结构）与 `db/02-seed.sql`（种子）会被 MySQL 官方镜像在
**数据卷为空时**自动执行。需要重来一遍时：

```bash
docker compose down -v && docker compose up -d mysql
```

手动初始化：

```bash
mysql -u root -p < db/01-schema.sql
mysql -u root -p < db/02-seed.sql
```

9 个模板刻意覆盖三类演示差异：1/2/3 张素材要求、仅接受 JPG 的模板、限 1MB 的模板。

---

## 素材

`seed/` 下所有图片都由 `tools/gen_seed_assets.py` 程序化生成，**不使用任何第三方素材**，
因此没有版权问题。重新生成：

```bash
python tools/gen_seed_assets.py     # 需要 Pillow
```

| 目录 | 用途 | 访问路径 |
|---|---|---|
| `seed/covers/` | 模板封面 | `/files/seed/covers/{templateId}.jpg` |
| `seed/results/` | 预置结果图 | 由 `FINALIZE` 阶段复制到 `data/results/` |
| `seed/samples/` | 演示时可直接上传的示例照片 | 手动上传用 |

运行时产物（用户上传、制作结果）落在 `backend/data/`，已被 `.gitignore` 忽略。

---

## 已知限制（P0 时点）

- `docker compose up --build` 尚未拉起前后端（P7 完成），当前只有 MySQL 可用。
- 除 `/api/health` 外的接口尚未实现。
- `frontend/pnpm test` 目前没有测试文件，脚本带 `--passWithNoTests`；P4 起会补真实用例。
- AI 工作流说明、部署与回滚步骤将在 P7 补入。

---

## 文档

- [plan.md](plan.md) — 分阶段实施计划与需求追溯矩阵
- [docs/api.md](docs/api.md) — 接口契约、状态机、轮询约定
