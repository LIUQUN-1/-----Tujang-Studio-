# 演示脚本

> 每一步都是可复制的命令，预期结果写在下面。命令用 `bash` / PowerShell 都能跑
> （Windows 上 `curl` 即 `curl.exe`）。
>
> 下面的 `API` 默认是 `http://localhost:8080`（compose 默认端口）。
> 本地开发时按你的 `API_PORT` 改，例如 `API=http://localhost:8090`。

```bash
API=http://localhost:8080
SAMPLE=seed/samples/sample-1.jpg      # 演示用示例照片，仓库里自带
```

---

## 0. 看看/清空当前模拟配置

```bash
curl -s $API/api/dev/simulation
# {"failStep":null,"perStepDurations":null,"applyTo":"NEXT"}

# 清空一切注入
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":null}'
```

`applyTo` 有两种：

| 值 | 含义 |
|---|---|
| `NEXT`（默认） | 只影响**下一个**被受理的任务，用完即清 |
| `ALL` | 持续生效，直到显式清空 |

> 默认一次性，是为了避免"设了一次失败，后面所有任务都失败"的意外。

---

## 1. 成功路径

1. 打开 <http://localhost:3000>，选「旅行明信片」（需要 2 张素材）。
2. 上传两张图片（可点「示例照片」里的文件），点「提交制作」。
3. 右侧立刻出现卡片：`等待处理` → `处理中`，三阶段依次高亮。
4. 约 4 秒后变成 `已完成`，出现结果缩略图，点开可看原图。

命令行等价（不需要前端）：

```bash
# 逐个上传两张素材，记下返回的 assetId
curl -s -F "file=@$SAMPLE" -F "templateId=demo-postcard" $API/api/uploads
# {"assetId":"01...","url":"/files/assets/01....jpg", ...}

# 提交任务：立刻返回 202，不等待制作完成
curl -s -X POST $API/api/tasks -H 'Content-Type: application/json' \
  -d '{"templateId":"demo-postcard","assetIds":["<assetId1>","<assetId2>"]}'

# 反复查询，能看到 currentStep 逐步推进
curl -s $API/api/tasks/<taskId>
```

---

## 2. 指定阶段失败

三步都可以单独注入。每条命令之后提交一个新任务，就能看到对应失败。

### 2.1 失败在「素材检查」

```bash
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":"ASSET_CHECK","applyTo":"NEXT"}'
```

预期：任务很快 `FAILED`，`failedStep=ASSET_CHECK`、`errorCode=SIMULATED_ASSET_FAILURE`，
卡片显示"在「素材检查」失败：素材检查未通过（模拟注入）"。

### 2.2 失败在「模拟生成」

```bash
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":"GENERATE","applyTo":"NEXT"}'
```

预期：先经过「素材检查」，再在「模拟生成」失败，`errorCode=GENERATE_TIMEOUT`，
**没有结果图**，后续「结果整理」不会执行。

### 2.3 失败在「结果整理」

```bash
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"failStep":"FINALIZE","applyTo":"NEXT"}'
```

预期：前两步都成功，最后一步 `failedStep=FINALIZE`、`errorCode=SIMULATED_FINALIZE_FAILURE`。

> 三种失败都满足：`result` 为 `null`、后续步骤不执行、状态停在 `FAILED` 不回退。

---

## 3. 调整处理耗时

### 3.1 调慢，便于观察"处理中"

```bash
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":3000,"GENERATE":15000,"FINALIZE":3000},"applyTo":"ALL"}'
```

提交任务后，会有约 21 秒停留在处理中，足够截图、切换页面、刷新验证。

### 3.2 调快，便于连跑多次

```bash
curl -s -X POST $API/api/dev/simulation \
  -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":100,"GENERATE":300,"FINALIZE":100},"applyTo":"ALL"}'
```

### 3.3 恢复默认

```bash
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' -d '{"failStep":null}'
```

默认值在 `backend/src/main/resources/application.yml` 的
`app.simulation.default-durations-ms`（800 / 2500 / 600 毫秒），也可以直接用环境变量改。

---

## 4. 多任务同屏：两个处理中 + 一个失败

这是 UI-03 参考截图里的画面，按下面顺序执行即可稳定复现：

```bash
# ① 先让所有任务都跑得慢（20 秒生成）
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"perStepDurations":{"ASSET_CHECK":1500,"GENERATE":20000,"FINALIZE":1500},"applyTo":"ALL"}'

# ② 连提两个任务（脚本里各上传两张素材后提交）

# ③ 再让下一个任务快速失败在「模拟生成」
curl -s -X POST $API/api/dev/simulation -H 'Content-Type: application/json' \
  -d '{"failStep":"GENERATE","perStepDurations":{"ASSET_CHECK":300,"GENERATE":400},"applyTo":"NEXT"}'

# ④ 提第三个任务，然后立刻看模板详情页右侧
```

预期：一张卡片显示失败（红），两张显示处理中（绿），互不影响；
失败那张停住不动，另外两张继续走到各自的结果。

> 之所以能这样，是因为模拟配置在**受理时快照进 task 行**，
> 而不是一个全局开关——否则改配置会把在跑的任务也改掉。

---

## 5. 刷新恢复

在上面的处理中状态直接按 F5。预期：

- 未完成的任务仍然在列表里，状态从服务端读回（不是白屏、不是"等待处理"从头来）；
- 轮询继续，几秒后自动变成已完成；
- 已完成的记录和结果图也都在。

背后：历史页/详情页由服务端渲染首屏，`TaskWatcher` 再拉一次列表并把未完成任务纳入轮询。

---

## 6. 验证生产环境没有模拟接口

```bash
SPRING_PROFILES_ACTIVE=prod ... java -jar backend/target/tujang-studio-api-0.1.0.jar
```

预期：

```bash
curl -o /dev/null -w '%{http_code}\n' $API/api/dev/simulation          # 404
curl -o /dev/null -w '%{http_code}\n' $API/api/templates               # 200
```

原因：`DevSimulationController` 标了 `@Profile("dev")`，该 profile 下 bean 根本不存在。

---

## 7. 重置数据

```bash
docker compose down -v && docker compose up -d mysql     # 清库并重新灌种子数据
rm -rf backend/data                                       # 清运行时文件（上传/结果）
```

或直接用 VS Code 任务「数据库: 清空重建」。

---

## 附：接口速查

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 提交任务，202 + 完整任务对象 |
| GET | `/api/tasks/{id}` | 查单个任务（只读，不推进） |
| GET | `/api/tasks?limit=50` | 我的历史 |
| POST | `/api/uploads` | 上传单张素材（multipart） |
| DELETE | `/api/uploads/{assetId}` | 删除未使用的素材 |
| GET | `/api/templates?category=` | 模板列表 |
| GET | `/api/categories` | 分类与计数 |
| GET/POST | `/api/dev/simulation` | 模拟参数（仅 dev） |

字段与状态机详见 [api.md](api.md)。
