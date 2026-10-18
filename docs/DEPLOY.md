# 部署与本地运行

> **真机验证状态（2026-10-18 更新）**
>
> 已在 **GitHub Codespaces**（2 核 / 4GB / Ubuntu）+ **Docker MySQL 8.0.43** 的
> **全新空库**上从零验证通过 ✓✓：
>
> - `mvn spring-boot:run` 启动成功，`Tomcat started on port 8080`
> - `GET /agent/backends` 返回 `{"redisEnabled":false,"cache":"memory","lock":"local","rateLimit":"local"}`
>   （Redis 未启用 → 三件套自动降级进程内 ✓✓）
> - Flyway 从空库自动建到 V4，`flyway_schema_history` 4 条 `success=1` ✓✓
>   —— 与本机（MySQL 8.0.28 + 原手工增量）**结构一致** ✓✓
>
> 过程中发现并修复：CodeSpaces 默认 JDK 25 ≠ toolchain 要求的 17、
> `toolchains.xml` 隐藏文件、重启后 Docker 容器丢失、
> **`mysql:8.0` 浮动 tag 指向的 8.0.46 镜像 entrypoint 传废弃参数导致容器启动即崩**
> （详见 DEV_NOTES 2026-10-18 小节 ✓✓，镜像 tag 已钉死 ✓✓）。
>
> **仍未验证**：多副本（真 Redis + 两个实例）下的取消/背压共享 —— 需真机部署 ✓✓。

## 前置条件

- JDK 17（本机 `C:\Program Files\Java\jdk-17`）
- Maven 3.6.3+（本机 3.5.4 偏旧，见下方"已知坑"）
- Node 20+
- MySQL 8
- Python Agent 服务（在 `../Agent实习项目`）

## 一、准备数据库

```powershell
# 建一个空库即可 —— 表结构由应用启动时的 Flyway 自动完成 ✓✓
# （V1 建 11 张表 + V2~V4 增量，见 README「数据库迁移」一节）
mysql -u root -p -e "CREATE DATABASE jobseeker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

> 2026-10-17 起**不再需要手工执行 SQL** ✓✗ —— 此前这里是
> `mysql -u root -p < server/src/main/resources/db/schema.sql`，
> 而那份脚本**只含 V1**（不含 `exam_done` / `exam_deadline` / `apply_url` 三列与五个联合索引 ✗），
> 所以按老文档部署出来的环境一直是缺列的 ✗✗。这也是引入 Flyway 的直接原因 ✓。

脚本会建 **11 张表**（`user` / `job_position` / `resume` / `resume_version` / `interview_session` / `interview_message` / `interview_review` / `knowledge_doc` / `job_search_record` / `job_site` / `delivery_record`），并预置 4 个招聘站点（BOSS 直聘 / 猎聘 / 智联招聘 / 前程无忧）。

## 二、启动 Python Agent（必须先起）

```powershell
cd ..\Agent实习项目
.\run_api.ps1          # 或 python -m uvicorn src.api.main:app --port 8000
```

验证：`http://localhost:8000/health`

## 三、启动后端

```powershell
cd jobseeker\server

# 首次运行：把仓库根模板复制为 server 下的本地配置（含 localRepository 与镜像，不进版本库）
copy ..\.mvn\local-settings.xml.example .mvn\local-settings.xml

# 注意：本机默认 Maven 本地仓库 D:\repository\mavenrepository 无写入权限，
# 用自带的 settings 覆盖（已指向 C:\Users\Administrator\.m2\repository）
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -s .mvn/local-settings.xml spring-boot:run
```

或先打包再运行 jar：

```powershell
mvn -s .mvn/local-settings.xml -DskipTests package
java -jar target/jobseeker-server.jar
```

关键配置（`application.yml`，均可用环境变量覆盖）：

| 配置 | 默认 | 说明 |
|---|---|---|
| `agent.base-url` | `http://127.0.0.1:8000` | Agent 地址 |
| `agent.api-key` | 空 | 对应 Agent 的 `API_KEY` |
| `agent.output-dir` | `../Agent实习项目/Agent_output` | AI 产物目录（**只读**） |
| `storage.base-dir` | `./data` | 本服务文件存储（可写） |
| `spring.datasource.*` | `jdbc:mysql://127.0.0.1:3306/jobseeker` | 数据源 |
| `jwt.secret` | 内置默认值 | **生产必须替换** |
| `agent.chat-cache-enabled` / `context-compress-enabled` / `metrics-enabled` | `true` | 网关响应缓存 / `user_context` 裁剪 / 指标埋点开关 |

## 四、启动前端

```powershell
cd jobseeker\web
npm install --registry=https://registry.npmmirror.com
npm run dev        # http://localhost:5173
```

开发态已配置代理：`/api` 与 `/agent` → `http://127.0.0.1:8080`（SSE 关闭缓冲）。

生产构建：

```powershell
npm run build      # 产物在 dist/；postbuild 会自动同步到 server/src/main/resources/static/（供 :8080 直接服务 UI）
```

## 五、Docker 一键起（不含 Agent）

```powershell
cd jobseeker
.\start-all.ps1    # 先起 Agent，再 docker compose up
# 或只起本软件：
docker compose up --build -d
```

- 前端 http://localhost
- 后端 http://localhost:8080
- Agent（独立）http://localhost:8000

Compose 会把 `../Agent实习项目/Agent_output` **只读**挂给 server，供简历导入使用。

## 已知坑

| 问题 | 现象 | 解决 |
|---|---|---|
| Maven 本地仓库无写权限 | `Failed to create parent directories ... .pom.part.lock` | 用 `mvn -s .mvn/local-settings.xml`（已指向用户目录） |
| Maven 3.5.4 偏旧 | 新版插件可能不兼容 | 建议升级 Maven 到 3.8+；当前用 3.5.4 可正常编译 |
| Lombok 不生效 | `找不到符号`（getter/setter） | pom 已显式配置 `annotationProcessorPaths`，勿删 |
| SSE 不流式 | 前端一次性收到全文 | 网关已设 `X-Accel-Buffering: no`；nginx 也要关 `proxy_buffering off` |
| Agent 未启动 | 网关/归档报 502 | 先启动 Agent 再启动本软件 |

## 安全注意

- 招聘站点 `url_template` 只从数据库读取（数据库即白名单），拼接时校验协议并做 URL 编码。
- AI 职位清单的免责标注为硬性要求，前端不可省略。
- 生产请修改 `jwt.secret` 与数据库密码。
