# 接口契约 · v1

> 本文件是**前后端唯一真源**。任何字段变更先改这里，再改代码。
> 实现进度见 `plan.md`；本文件描述的是**最终形态**，P0 阶段只实现 `/api/health`。

---

## 0. 全局约定

### 0.1 路径与代理

浏览器**只访问同源相对路径**，不直连 Java：

| 浏览器请求 | Next.js rewrite 转发到 | 由谁处理 |
|---|---|---|
| `/api/**` | `${BACKEND_URL}/api/**` | Java |
| `/files/**` | `${BACKEND_URL}/files/**` | Java 静态资源 |

`BACKEND_URL` 是**服务端**环境变量：本地 `http://localhost:8082`，compose 内 `http://backend:8080`。
好处：零 CORS、`<img src>` 直接用 `/files/...`、容器内外地址一致。

### 0.2 内容类型

- 请求/响应：`application/json; charset=utf-8`
- 上传：`multipart/form-data`
- 文本为 UTF-8，中文不加转义

### 0.3 时间

- 存储：MySQL `DATETIME(3)`，**一律 UTC**
- 传输：ISO-8601 UTC，例如 `2026-10-28T13:30:15.123Z`
- 展示：前端按浏览器时区格式化，后端不做本地化

### 0.4 身份

- Cookie `tujang_uid`（`HttpOnly; SameSite=Lax; Path=/`），值为 `app_user.id`（UUID）
- 首次访问：后端新建用户 → `Set-Cookie`
- 所有业务接口都从 Cookie 解析 `userId`，**不信任任何请求体里的 userId**
- 越权访问他人资源统一返回 `403 ASSET_FORBIDDEN` / `403 TASK_FORBIDDEN`

### 0.5 错误响应

非 2xx 一律返回同一形状（不套 `data` 信封）：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "素材数量不符合模板要求",
  "fields": { "assetIds": "该模板需要 2 张图片，当前 1 张" }
}
```

| 字段 | 说明 |
|---|---|
| `code` | 稳定的大写蛇形错误码，前端可据此分支 |
| `message` | 面向用户的中文说明，**可直接展示** |
| `fields` | 可选。字段级校验错误，key 为请求字段名 |

### 0.6 错误码表

| HTTP | code | 场景 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 参数缺失/格式错/数量不符 |
| 400 | `FILE_NOT_DECODABLE` | 文件无法解码为图片 |
| 403 | `ASSET_FORBIDDEN` | 素材不属于当前用户 |
| 403 | `TASK_FORBIDDEN` | 任务不属于当前用户 |
| 404 | `TEMPLATE_NOT_FOUND` | 模板不存在 |
| 404 | `TASK_NOT_FOUND` | 任务不存在 |
| 404 | `ASSET_NOT_FOUND` | 素材不存在 |
| 413 | `FILE_TOO_LARGE` | 超过模板 `maxFileSizeBytes` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 不在模板 `acceptedMimeTypes` 内 |
| 500 | `INTERNAL_ERROR` | 兜底，**不返回堆栈** |

---

## 1. 健康检查

`GET /api/health` → `200`

```json
{ "status": "ok", "app": "tujang-studio", "time": "2026-10-28T13:30:15.123Z" }
```

---

## 2. 模板

### 2.1 `GET /api/categories`

```json
[
  { "code": "creative", "name": "创意图片", "count": 3 },
  { "code": "pet",      "name": "宠物",     "count": 2 },
  { "code": "wedding",  "name": "婚纱",     "count": 2 },
  { "code": "festival", "name": "节日",     "count": 2 }
]
```

`count` 为该分类下模板数；前端「全部」Tab 自行汇总。

### 2.2 `GET /api/templates?category={code}`

`category` 可省略（= 全部）。未知 code 返回空数组而非报错。

```json
[
  {
    "id": "demo-postcard",
    "title": "旅行明信片",
    "categoryCode": "creative",
    "categoryName": "创意图片",
    "coverUrl": "/files/seed/covers/demo-postcard.jpg",
    "description": "上传两张照片，模拟生成一张旅行明信片",
    "requiredImageCount": 2
  }
]
```

### 2.3 `GET /api/templates/{id}`

在 2.2 单条基础上追加两个制作约束字段：

```json
{
  "id": "demo-postcard",
  "title": "旅行明信片",
  "categoryCode": "creative",
  "categoryName": "创意图片",
  "coverUrl": "/files/seed/covers/demo-postcard.jpg",
  "description": "上传两张照片，模拟生成一张旅行明信片",
  "requiredImageCount": 2,
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "maxFileSizeBytes": 5242880
}
```

不存在 → `404 TEMPLATE_NOT_FOUND`。

---

## 3. 素材上传

### 3.1 `POST /api/uploads`

`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | 文件 | 是 | 单张图片 |
| `templateId` | string | 否 | 带上则按该模板的 mime/大小约束校验 |

成功 `201`：

```json
{
  "assetId": "01JBQ2X9K8R4T7V3M5N1P6D0ZA",
  "url": "/files/assets/01JBQ2X9K8R4T7V3M5N1P6D0ZA.jpg",
  "originalFilename": "beach.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 348210,
  "width": 1200,
  "height": 800,
  "createdAt": "2026-10-28T13:30:15.123Z"
}
```

服务端必须校验：mime 白名单 → 大小上限 → **能否真正解码**（防改扩展名）→ 读宽高。
失败：`413 FILE_TOO_LARGE` / `415 UNSUPPORTED_MEDIA_TYPE` / `400 FILE_NOT_DECODABLE`。

### 3.2 `DELETE /api/uploads/{assetId}`

移除上传位时清理临时素材。成功 `204`。
仅允许删除**属于当前用户且尚未绑定任何任务**的素材；否则 `403`/`409 ASSET_IN_USE`。

---

## 4. 制作任务

### 4.1 `POST /api/tasks` — 提交（受理即返回）

```json
{ "templateId": "demo-postcard", "assetIds": ["01JB...", "01JB..."] }
```

校验内容（顺序即报错优先级）：

1. `templateId` 存在 → `404 TEMPLATE_NOT_FOUND`
2. `assetIds` 数量 == `requiredImageCount` → `400 VALIDATION_FAILED`
3. 每个 asset 属于当前用户 → `403 ASSET_FORBIDDEN`
4. 每个 asset 文件仍在磁盘、mime/大小符合模板 → `400 VALIDATION_FAILED`

成功 `202 Accepted`，**不等待制作完成**。响应体是**完整的 TaskDto**（与 4.2 同形状），
`status` 恒为 `PENDING`、`currentStep`/`result` 为 `null`：

```json
{
  "id": "01JBQ3A2M4T8K1R6V9N5P7D3XY",
  "status": "PENDING",
  "currentStep": null,
  "failedStep": null,
  "errorCode": null,
  "errorMessage": null,
  "templateId": "demo-postcard",
  "templateTitle": "旅行明信片",
  "templateCoverUrl": "/files/seed/covers/demo-postcard.jpg",
  "result": null,
  "inputs": [
    { "assetId": "01JBQ2X9K8R4T7V3M5N1P6D0ZA", "url": "/files/assets/01JBQ2X9K8R4T7V3M5N1P6D0ZA.jpg" }
  ],
  "simulation": null,
  "createdAt": "2026-10-28T13:30:20.000Z",
  "startedAt": null,
  "finishedAt": null,
  "updatedAt": "2026-10-28T13:30:20.000Z"
}
```

> 为什么不只返回 `{id}`：前端对"刚受理的任务"和"查询到的任务"用同一套渲染逻辑，
> 卡片可以立刻出现，不用先等一次轮询。
>
> 202 而非 200：语义上"已受理、处理中"。

### 4.2 `GET /api/tasks/{id}` — 查询（**纯只读**）

```json
{
  "id": "01JBQ3A2M4T8K1R6V9N5P7D3XY",
  "status": "SUCCEEDED",
  "currentStep": "FINALIZE",
  "failedStep": null,
  "errorCode": null,
  "errorMessage": null,
  "templateId": "demo-postcard",
  "templateTitle": "旅行明信片",
  "templateCoverUrl": "/files/seed/covers/demo-postcard.jpg",
  "result": {
    "url": "/files/results/01JBQ3A2M4T8K1R6V9N5P7D3XY.png",
    "width": 1200,
    "height": 900,
    "createdAt": "2026-10-28T13:30:27.000Z"
  },
  "inputs": [
    { "assetId": "01JBQ2X9K8R4T7V3M5N1P6D0ZA", "url": "/files/assets/01JBQ2X9K8R4T7V3M5N1P6D0ZA.jpg" }
  ],
  "simulation": { "failStep": "GENERATE", "perStepDurations": { "GENERATE": 3000 } },
  "createdAt": "2026-10-28T13:30:20.000Z",
  "startedAt": "2026-10-28T13:30:20.100Z",
  "finishedAt": "2026-10-28T13:30:27.000Z",
  "updatedAt": "2026-10-28T13:30:27.000Z"
}
```

| 字段 | 说明 |
|---|---|
| `status` | `PENDING` / `PROCESSING` / `SUCCEEDED` / `FAILED` |
| `currentStep` | `ASSET_CHECK` / `GENERATE` / `FINALIZE`；`PENDING` 时为 `null` |
| `failedStep` | 仅 `FAILED` 有值，表示**死在哪一步** |
| `errorCode` | 稳定错误码，如 `GENERATE_TIMEOUT`、`ASSET_CORRUPTED` |
| `errorMessage` | 面向用户的中文原因 |
| `result` | 仅 `SUCCEEDED` 有值；`FAILED`/进行中为 `null` |
| `inputs` | 本次任务用到的素材，回显用 |
| `simulation` | `dev` 下回显当次生效的模拟配置（受理时的快照）；生产为 `null` |

**三种形态的要点**：

- 进行中：`status=PROCESSING`、`currentStep=GENERATE`、`result=null`
- 成功：`result.url` 即"做出来的东西"，前端直接 `<img src>` 预览
- 失败：`failedStep` + `errorMessage` 有值、`result=null`，**后续步骤不再执行**

不存在 → `404 TASK_NOT_FOUND`；他人的任务 → `403 TASK_FORBIDDEN`。

> **不变式**：查询接口不写库。任务的推进只由后台执行器写。任何查询都不能让任务前进或后退。

### 4.3 `GET /api/tasks?limit=50` — 个人历史

返回 `TaskDto[]`，按 `createdAt DESC`。包含未完成任务（用于刷新后恢复跟踪）。
`limit` 默认 50，上限 200。

```json
[ { "id": "...", "status": "PROCESSING", ... }, { "id": "...", "status": "FAILED", ... } ]
```

---

## 5. 身份

身份就是一个 Cookie，没有账号密码：

| 项 | 值 |
|---|---|
| 名字 | `tujang_uid` |
| 值 | `app_user.id`（UUID） |
| 属性 | `HttpOnly; SameSite=Lax; Path=/; Max-Age=30d`（生产应再加 `Secure`） |
| 谁发放 | Next 中间件在首次访问时生成；后端在"Cookie 缺失/格式非法"时兜底生成 |

服务端解析规则（`UserService`）：

1. Cookie 是合法 UUID 且库里有 → 就是这个用户；
2. Cookie 是合法 UUID 但库里没有 → **采用该 id 建行**（前端预生成的场景，避免多建空用户）；
3. 没有或格式非法 → 服务端新建一个，并通过 `Set-Cookie` 发回。

> **这不是认证**：知道对方 UUID 就能看到对方的记录。本题不要求登录体系，
> 这里只保证"记录按人隔离 + id 不可猜"，边界在 README 里写明。

### 5.1 `GET /api/me`

```json
{
  "userId": "0f0c1b3e-0000-4000-8000-000000000001",
  "displayName": "设计师 7F2A",
  "createdAt": "2026-10-28T13:30:15.123Z",
  "devTools": true
}
```

`devTools` 表示当前后端是否跑在 `dev` profile——前端据此显示「切换用户」这类开发入口；
生产环境为 `false`，入口不出现。

### 5.2 `POST /api/dev/users/switch`（仅 dev）

请求体可选：

```json
{ "userId": "0f0c1b3e-..." }
```

- 不传或目标不存在 → 新建一个用户并下发新 Cookie（用于演示"换一个人用"）；
- 传了已存在的 `userId` → 切回该用户（前端「切回上一个」用它）。

响应为 5.1 结构，同时 `Set-Cookie` 新身份。生产 profile 下该路径 404。

---

## 6. 模拟控制（仅 `dev` profile）

### 6.1 `GET /api/dev/simulation`

```json
{
  "failStep": null,
  "perStepDurations": { "ASSET_CHECK": 600, "GENERATE": 2500, "FINALIZE": 500 },
  "applyTo": "NEXT"
}
```

### 6.2 `POST /api/dev/simulation`

```json
{
  "failStep": "GENERATE",
  "perStepDurations": { "ASSET_CHECK": 600, "GENERATE": 3000, "FINALIZE": 500 },
  "applyTo": "NEXT"
}
```

| 字段 | 取值 | 说明 |
|---|---|---|
| `failStep` | `ASSET_CHECK` / `GENERATE` / `FINALIZE` / `null` | `null` = 不注入失败 |
| `perStepDurations` | `{"GENERATE":3000,...}` / `null` | 覆盖单步耗时；缺省走 `application.yml` 的默认值 |
| `applyTo` | `"NEXT"` / `"ALL"` | 仅影响下一个任务 / 持续生效 |

**关键设计**：受理任务时把当时生效的配置**快照写入 `task` 行**（`sim_fail_step`、`sim_durations`），
因此并发任务各按自己的配置推进 —— 这正是"同屏两个处理中 + 一个失败"能够稳定复现的前提。
生产 profile 下这些接口返回 `404`。

---

## 7. 状态机

```
                 ┌──────────── PENDING ────────────┐
                 │                                  │
                 ▼                                  │
        PROCESSING / ASSET_CHECK                    │
                 │  ├── 失败 ──> FAILED(ASSET_CHECK)┤
                 ▼                                  │
        PROCESSING / GENERATE                       │
                 │  ├── 失败 ──> FAILED(GENERATE)   │
                 ▼                                  │
        PROCESSING / FINALIZE                       │
                 │  ├── 失败 ──> FAILED(FINALIZE)   │
                 ▼                                  │
            SUCCEEDED  (result.url 落库)
```

**不变式（单测必须守住）**：

1. `SUCCEEDED` / `FAILED` 是吸收态，任何后续写库都不能改回 `PROCESSING`
2. `FAILED` 时失败步之后的步骤绝不执行，`result_url IS NULL`
3. 状态写入用条件 UPDATE：`WHERE id = ? AND status NOT IN ('SUCCEEDED','FAILED')`
4. `SUCCEEDED` 时 `result_url IS NOT NULL` 且 `failed_step IS NULL`

---

## 8. 前端轮询约定（非接口，但属于契约）

| 项 | 约定 |
|---|---|
| 间隔 | 1500ms（可配） |
| 范围 | 仅轮询"未完成任务"的 id 集合 |
| 停止 | 集合为空 → 立即清定时器；任务进入终态 → 移出集合 |
| 竞态 | 每次请求带 `AbortController`；切用户/卸载时 abort 并清空 |
| 查询失败 | **只标记 `queryState='error'`，绝不改写 `task.status`** |
| 恢复 | `GET /api/tasks` 拉回历史 → 未完成者重新加入轮询集合 |
