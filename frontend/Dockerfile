# ---------------------------------------------------------------------------
# 前端：Next.js 15（App Router）
#
#   docker build -f frontend/Dockerfile \
#     --build-arg BACKEND_URL=http://api:8080 -t tujang-web ./frontend
#
# 为什么 BACKEND_URL 是构建参数：`next.config.ts` 的 rewrites 会被编进
# routes-manifest（见该文件注释），必须在 `next build` 时就知道代理目标。
# 运行期同名环境变量仍然有用——服务端组件取数（lib/server-api.ts）读它。
#
# 刻意不用 output:"standalone"（理由见 next.config.ts）：本地与容器走同一条
# 产物路径，代价是镜像里要带 node_modules。
# ---------------------------------------------------------------------------

# ---- 依赖 ----------------------------------------------------------------
FROM node:22-alpine AS deps

WORKDIR /app

# 固定 pnpm 大版本与本地一致，避免 lockfile 解释差异
RUN npm install -g pnpm@12.15.0

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# ---- 构建 ----------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

RUN npm install -g pnpm@12.15.0

ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG BACKEND_URL=http://api:8080
ENV BACKEND_URL=${BACKEND_URL}

RUN pnpm build

# ---- 运行 ----------------------------------------------------------------
FROM node:22-alpine AS runner

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# next start 需要：构建产物 + 依赖 + 配置（next.config.ts 在启动时会被读取，
# 它决定 distDir 与 rewrites；tsconfig.json 供其转译时解析路径别名）
#
# 依赖直接复用 deps 阶段的 node_modules：与构建时是同一份安装，不会出现
# "构建能跑、启动缺包"。代价是镜像里带上了 devDependencies，实测约 1 GB；
# 换来的是这条链路上没有第二套依赖解析（试过只装 production 依赖，镜像没小多少，
# 因为 pnpm 的 store 与硬链接在镜像层里会被展开重复计算）。
COPY --from=deps /app/node_modules ./node_modules

COPY package.json next.config.ts tsconfig.json ./
COPY --from=build /app/.next ./.next

USER app

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/api/health" >/dev/null || exit 1

CMD ["node_modules/next/dist/bin/next", "start", "-p", "3000", "-H", "0.0.0.0"]
