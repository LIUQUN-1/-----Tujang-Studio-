# 演示脚本

> 每一步都是可复制的命令，预期结果写在下面。命令用 `bash` / PowerShell 都能跑
> （Windows 上 `curl` 即 `curl.exe`）。
>
> 下面的 `API` 默认是 `http://localhost:8080`（compose 默认端口）。
> 本地开发时按你的 `API_PORT` 改，例如 `API=http://localhost:8092`。

```bash
API=http://localhost:8080
SAMPLE=seed/samples/sample-1.jpg      # 演示用示例照片，仓库里自带
JAR=/tmp/tujang-cookies.txt           # 会话 Cookie；同一套命令必须复用同一个 jar
```

> **为什么要 Cookie jar**：素材和任务都按账号隔离，未登录调用上传/提交会得到 401。
> 第一步登录时后端会 `Set-Cookie`，curl 把它存进 jar，后面自动复用。
> 想演示"两个人"，就换一个 jar 再登录一次（见第 8 节）。

---

## 0. 先登录

```bash
# 演示账号（dev profile 启动时自动创建）：
curl -s -c $JAR -b $JAR -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo1234"}' $API/api/auth/login
# {"userId":"...","username":"demo","displayName":"demo",...}

# 或者注册一个新账号（注册即登录）
curl -s -c $JAR -b $JAR -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"passw0rd123"}' $API/api/auth/register

# 确认身份
curl -s -c $JAR -b $JAR $API/api/me
```

预期：`200` + 用户信息；浏览器里顶栏会显示账号名与「退出登录」。
未登录时 `GET /api/me`、`POST /api/uploads`、`GET /api/tasks` 都是 `401 UNAUTHORIZED`，
但 `GET /api/templates` 仍然 `200`（浏览不需要登录）。

---

## 1. 看看/清空当前模拟配置

```bash
curl -s $API/api/dev/simulation
# {"failStep":null,"perStepDurations":null,"applyTo":"NEXT"}

# 清空一切注入
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":null,"perStepDurations":null}'
```

`applyTo` 有两种：

| 值 | 含义 |
|---|---|
| `NEXT`（默认） | 只影响**下一个**被受理的任务，用完即清 |
| `ALL` | 持续生效，直到显式清空 |

> 默认一次性，是为了避免"设了一次失败，后面所有任务都失败"的意外。

---

## 2. 成功路径

1. 打开 <http://localhost:3000>，登录（`demo / demo1234` 或自己注册）。
2. 选「旅行明信片」（需要 2 张素材），上传两张图片，点「提交制作」。
3. 右侧立刻出现卡片：`等待处理` → `处理中`，三阶段依次高亮。
4. 约 4 秒后变成 `已完成`，出现结果缩略图，点开可看原图。

命令行等价（不需要前端）：

```bash
# 逐个上传两张素材，记下返回的 assetId
curl -s -c $JAR -b $JAR -F "file=@$SAMPLE" -F "templateId=demo-postcard" $API/api/uploads
# {"assetId":"01...","url":"/files/assets/01....jpg", ...}

# 提交任务：立刻返回 202，不等待制作完成
curl -s -c $JAR -b $JAR -X POST $API/api/tasks -H 'Content-Type: application/json' \
  -d '{"templateId":"demo-postcard","assetIds":["<assetId1>","<assetId2>"]}'

# 反复查询，能看到 currentStep 逐步推进
curl -s -c $JAR -b $JAR $API/api/tasks/<taskId>
```

**这条链路全程没有打开页面**——任务照样从 `PENDING` 走到 `SUCCEEDED`，
这就是"关闭页面不中断后台处理"的证据。

---

## 3. 指定阶段失败

三步都可以单独注入。每条命令之后提交一个新任务，就能看到对应失败。

### 3.1 失败在「素材检查」

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":"ASSET_CHECK","applyTo":"NEXT"}'
```

预期：任务很快 `FAILED`，`failedStep=ASSET_CHECK`、`errorCode=SIMULATED_ASSET_FAILURE`，
卡片显示"在「素材检查」失败：素材检查未通过（模拟注入）"。

### 3.2 失败在「模拟生成」

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":"GENERATE","applyTo":"NEXT"}'
```

预期：先经过「素材检查」，再在「模拟生成」失败，`errorCode=GENERATE_TIMEOUT`，
**没有结果图**，后续「结果整理」不会执行。

### 3.3 失败在「结果整理」

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":"FINALIZE","applyTo":"NEXT"}'
```

预期：前两步都成功，最后一步 `failedStep=FINALIZE`、`errorCode=SIMULATED_FINALIZE_FAILURE`。

> 三种失败都满足：`result` 为 `null`、后续步骤不执行、状态停在 `FAILED` 不回退。
> 注入只生效一次（`NEXT`），下一个任务会自动恢复正常。

---

## 4. 调整处理耗时

### 4.1 调慢，便于观察"处理中"

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":3000,"GENERATE":15000,"FINALIZE":3000},"applyTo":"ALL"}'
```

提交任务后，会有约 21 秒停留在处理中，足够截图、切换页面、刷新验证。

### 4.2 调快，便于连跑多次

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":100,"GENERATE":300,"FINALIZE":100},"applyTo":"ALL"}'
```

### 4.3 恢复默认

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":null,"perStepDurations":null}'
```

默认值在 `backend/src/main/resources/application.yml` 的
`app.simulation.default-durations-ms`（800 / 2500 / 600 毫秒），也可以直接用环境变量改。

---

## 5. 多任务同屏：两个处理中 + 一个失败 + 一个成功

按下面顺序执行即可稳定复现（`docs/p9-mixed.png` 就是这么拍的）：

```bash
# ① 让任务跑得慢（生成 60 秒）
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":1500,"GENERATE":60000,"FINALIZE":1500},"applyTo":"ALL"}'
# ② 提交一个任务（会长时间停在处理中）

# ③ 让下一个任务快速失败
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":"GENERATE","perStepDurations":{"ASSET_CHECK":300,"GENERATE":500},"applyTo":"NEXT"}'
# ④ 提交一个任务（会在 GENERATE 失败）

# ⑤ 恢复快速成功
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":null,"perStepDurations":{"ASSET_CHECK":200,"GENERATE":400,"FINALIZE":200},"applyTo":"ALL"}'
# ⑥ 提交一个任务（很快成功）
```

预期：同一页面上同时出现"处理中""失败""已完成"三种卡片，互不影响；
失败那张停住不动，处理中的继续走到各自的结果。

> 之所以能这样，是因为模拟配置在**受理时快照进 task 行**，
> 而不是一个全局开关——否则改配置会把在跑的任务也改掉。

---

## 6. 刷新恢复

在"处理中"状态直接按 F5。预期：

- 未完成的任务仍然在列表里，状态从服务端读回（不是白屏、不是"等待处理"从头来）；
- 轮询继续，几秒后自动变成已完成；
- 已完成的记录和结果图也都在。

背后：历史页/详情页由服务端渲染首屏，`TaskWatcher` 再拉一次列表并把未完成任务纳入轮询。

---

## 7. 验证未登录与生产环境

```bash
# 未登录：受保护接口 401，公开接口正常
curl -o /dev/null -w '%{http_code}\n' $API/api/tasks        # 401
curl -o /dev/null -w '%{http_code}\n' $API/api/templates    # 200
```

```bash
# 生产 profile：开发接口整体消失，演示账号也不会被创建
SPRING_PROFILES_ACTIVE=prod ... java -jar backend/target/tujang-studio-api-0.1.0.jar
curl -o /dev/null -w '%{http_code}\n' $API/api/dev/simulation   # 404
curl -o /dev/null -w '%{http_code}\n' $API/api/me               # 401
```

原因：`DevSimulationController` 与 `DemoAccountInitializer` 都标了 `@Profile("dev")`。

---

## 8. 两个账号各看各的记录

```bash
JAR_B=/tmp/tujang-cookies-b.txt

# A（用第 0 节的 jar）提交过任务后，B 注册一个新账号
curl -s -c $JAR_B -b $JAR_B -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"passw0rd123"}' $API/api/auth/register

# B 看不到 A 的记录
curl -s -c $JAR_B -b $JAR_B "$API/api/tasks?limit=50"     # []

# B 访问 A 的任务：403
curl -o /dev/null -w '%{http_code}\n' -c $JAR_B -b $JAR_B $API/api/tasks/<A 的 taskId>

# 退出登录
curl -s -c $JAR -b $JAR -X POST $API/api/auth/logout       # 204
curl -o /dev/null -w '%{http_code}\n' -c $JAR -b $JAR $API/api/me   # 401
```

浏览器里的等价操作：右上角「退出登录」→ 注册/登录另一个账号 → 「我的记录」变了。

---

## 9. 重置数据

```bash
docker compose down -v && docker compose up -d     # 清库并重新灌种子数据
```

> 表结构变过（v1.0.0 → v1.1.0 加了账号与会话表），**升级时必须重建数据卷或手工加列**，
> 见 README §8.2。

---

## 附：接口速查

| 方法 | 路径 | 登录 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | — | 注册并登录，201 + Set-Cookie |
| POST | `/api/auth/login` | — | 登录，200 + Set-Cookie |
| POST | `/api/auth/logout` | ✔ | 退出，204 |
| GET | `/api/me` | ✔ | 当前用户 |
| POST | `/api/uploads` | ✔ | 上传单张素材（multipart） |
| DELETE | `/api/uploads/{assetId}` | ✔ | 删除未使用的素材 |
| POST | `/api/tasks` | ✔ | 提交任务，202 + 完整任务对象 |
| GET | `/api/tasks/{id}` | ✔ | 查单个任务（只读，不推进） |
| GET | `/api/tasks?limit=50` | ✔ | 我的历史 |
| GET | `/api/templates?category=` | — | 模板列表 |
| GET | `/api/categories` | — | 分类与计数 |
| GET/POST | `/api/dev/simulation` | — | 模拟参数（仅 dev） |

字段与状态机详见 [api.md](api.md)。
