# 多阶段构建：Maven 打包 → JRE 运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
# 先解析依赖，利用缓存
RUN mvn -B dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/jobseeker-server.jar app.jar

# 非 root 运行
RUN useradd -u 10001 appuser && chown -R appuser /app
USER appuser

ENV AGENT_OUTPUT_DIR=/app/agent-output
ENV STORAGE_BASE_DIR=/app/data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
