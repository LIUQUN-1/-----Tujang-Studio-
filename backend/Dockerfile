# ---------------------------------------------------------------------------
# 后端：Spring Boot 3.3 · JDK 21
#
# 两阶段构建：build 阶段带 Maven 与 JDK，运行阶段只留 JRE + 一个 jar。
#
#   docker build -f backend/Dockerfile -t tujang-api ./backend
#
# 运行期需要的两份目录：
#   /app/seed  只读种子素材（compose 挂载 ./seed）
#   /app/data  上传与结果图（compose 挂载具名卷 app-data，重启不丢）
# ---------------------------------------------------------------------------

# ---- 构建 ----------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先只拷 pom，让依赖下载独立成层：改源码不会重新拉一遍 Maven 仓库
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---- 运行 ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# 不用 root 跑应用；数据目录提前建好并授权，
# 空具名卷首次挂载时会继承镜像里这个目录的属主，因此容器内可写。
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/tujang-studio-api-*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R app:app /app

USER app

ENV SERVER_PORT=8080 \
    APP_STORAGE_DATA_DIR=/app/data \
    APP_STORAGE_SEED_DIR=/app/seed \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

EXPOSE 8080

# 镜像自带健康检查，单独 docker run 时也能看出来是否就绪。
# compose 里另有一份带 start_period 的定义（依赖它做 depends_on 条件）。
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/api/health" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
