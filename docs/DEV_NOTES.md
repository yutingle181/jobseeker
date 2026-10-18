# jobseeker 开发记录（DEV_NOTES）

> 本文件由 `preflight-check` skill 维护：动手前先读本文件，踩坑后追加记录。
> 生成时间：2026-09-20 13:59（2026-09-20 人工补齐项目名、端口与约定）
> 重新生成：`dev-notes.ps1 -Init -Force`；追加记录：`dev-notes.ps1 -Add`

## 一、项目速览

- 项目名称：jobseeker（AI 求职助手 · 平台侧：Vue3 前端 + Spring Boot 网关）
- 版本号：0.3.0（三处一致：`README.md` / `web/package.json` / `server/pom.xml`，门禁会校验）
- 技术栈：Vue3 · Vite · TypeScript · Pinia · Vue Router · Java 17 · Spring Boot · MyBatis-Plus · MySQL · Redis（可选，默认关闭）
- 仓库根目录：D:\project\jobseeker
- 姊妹仓库：`gen-ai-career-assistant-agent`（AI 服务，FastAPI + LangGraph）——平台侧通过 `/agent/**` 薄网关转发过去

## 二、常用命令

| 用途 | 命令 |
|---|---|
| 编译 / 构建 | `cd web; npm run build`<br>`cd server; mvn -s .mvn/local-settings.xml clean package -DskipTests` |
| 测试 | `cd server; mvn -s .mvn/local-settings.xml test`（Mockito 单测，不依赖数据库；CI 里已接入） |
| 本地启动 | `cd web; npm run dev`<br>`cd server; mvn -s .mvn/local-settings.xml spring-boot:run` |
| 提交前自检 | `powershell -ExecutionPolicy Bypass -File <preflight-check skill>/scripts/preflight.ps1 -Path "D:\project\jobseeker"` |

## 三、端口与运行环境

- 前端（Vite dev server）：`5173` — `cd web; npm run dev`
- 后端（Spring Boot）：`8080` — `cd server; mvn -s .mvn/local-settings.xml spring-boot:run`
- AI 服务（**另一个仓库**）：`8000` — 不起它则 AI 相关功能不可用，但平台侧页面与接口仍可访问
- JDK 17 + Maven（本机实测 Maven 3.6.1 可用；官方建议 ≥3.6.3）

## 四、目录与配置约定

- `web/`：Vue3 前端（`npm run build` 产物为 `web/dist`）
- `server/`：Spring Boot 后端（构建产物 `server/target`，本机被 `windows-safe-delete` profile 重定向）
- `docs/`：架构 / 接口 / 部署文档；`docs/portfolio/` 是**本地作品集**（不入库，见下）
- `scripts/`：辅助脚本；`_iconbuild/`：站点图标构建的中间目录
- `.github/`：CI（`workflows/ci.yml`）与依赖更新（`dependabot.yml`）

## 五、本项目已发现的"偏离默认"约定

> 记录与框架/工具默认行为不同、容易踩坑的地方。

- **前端构建后存在 postbuild 步骤（`copy-dist.js`）**：会把 `web/dist` 同步到后端静态资源目录，勿删除。
- **Maven 构建需额外指定 `-s .mvn/local-settings.xml`**（本地仓库 / 镜像配置），否则可能拉不到依赖。
- **Maven profile `windows-safe-delete`**：D 盘直删会被拦截，构建产物被重定向到 C 盘临时目录，勿删除该 profile。
- **`docs/portfolio/`（作品集 md / PDF / 海报 / 截图）已被 `.gitignore` 忽略**：只存本机、不入库，别用 `git add -f` 强行加入。
- **版本号 0.3.0 有三处声明**（README / `web/package.json` / `server/pom.xml`）：改一处要同步其余，门禁会比对。
- **提交前别用 `git add -A`**：`web/node_modules/`、`server/target/`、`web/dist/`、后端静态产物（`static/assets/`、`static/index.html`、`favicon.*`）等都被忽略，但仍建议显式 `git add <path>`。
- **CI 会跑单元测试**（`.github/workflows/ci.yml` 的 `Run tests` 步骤）：本地改完后端代码，提交前先跑一次 `mvn -s .mvn/local-settings.xml test`。
- **依赖更新走 Dependabot**（每周一）：minor/patch 归组一条 PR、major 单独开 PR；CI 的 action 版本过期也会被它盯住。
- **`master` 已开分支保护（禁强推 / 禁删除）**：2026-09-20 起生效——改代码一律走「分支 → PR → CI 绿 → 合并」，别直接往 master 提交；建议把 CI 的构建 / 测试检查设为必需状态检查（**别把"只告警不阻断"那类检查设成必需**，否则自相矛盾）。**代价是 master 不能再 `git push --force`**：若将来需要再次改写历史，**必须先到 `Settings → Rules` 临时关闭规则**，改完再开回。自查是否生效：仓库首页若还出现「Your master branch isn't protected」提示，就是没开。

## 六、踩坑记录

> 追加命令：`dev-notes.ps1 -Add -Title "标题" -Symptom "现象" -Cause "原因" -Fix "解决"`
> 每条记录包含：日期 / 现象 / 原因 / 解决 / 标签。

<!-- 新记录由脚本自动追加到本行下方 -->

### 2026-10-15 Lettuce 连接池/超时的三个 API 坑（编译报错才逼出来）

- 现象：`RedisConfig` 编译连错三次，都是"找不到符号"。
- 原因与解决：
  1. `LettuceConnectionFactory.setPoolConfig(...)` **不存在**于当前版本 —— 连接池要走
     `LettucePoolingClientConfiguration.builder().poolConfig(...)`；
  2. `ClientOptions.Builder.commandTimeout(Duration)` 与 `LettucePoolingClientConfiguration.Builder`
     也都没有这个方法 —— 命令超时正确写法是
     `ClientOptions.builder().timeoutOptions(TimeoutOptions.builder().fixedTimeout(...).build())`；
  3. `RedisCallback` 在 `org.springframework.data.redis.**core**` 包，不是 `.connection`
     （后者是 `RedisConnection`）—— 两者名字极像，IDE 自动导入极易选错。
- 判据：`mvn -s .mvn/local-settings.xml test` 编译通过。
- 标签：spring-data-redis,lettuce,连接池,编译错误

### 2026-10-15 Mockito 打桩 void 方法：`when(...)` 直接编译失败

- 现象：测试编译报「此处不允许使用 '空' 类型」，共 4 处。
- 原因：`ValueOperations.set(...)` 等是 **void 方法**，`when(voidCall())` 非法；
  `lenient().when(voidCall())` 同样非法。
- 解决：void 方法一律 `doNothing().when(mock).method(...)` / `doThrow(...).when(mock).method(...)`。
- 连带收获：这条坑顺带暴露了一个**桩与实现不匹配** —— 实现用 `set(k, v, millis, TimeUnit)`
  重载、桩打 `set(k, v, Duration)`，导致"Redis 写失败应降级"的用例假绿；
  已把实现统一到 `Duration` 重载（少一次单位换算，也不给传错单位留口子）。
- 标签：mockito,void方法,单测

### 2026-10-15 降级必须可观察，否则等于没有

- 现象/决策：缓存、锁、限流都会在 Redis 不可用时自动降级，而降级是**静默**的（请求照常成功）。
  若只做到"能降级"，就会出现「以为在用分布式锁，其实早就是进程内锁」的情况 ——
  多实例部署下这两者语义差别很大。
- 解决：① 三件套共用一份 `RedisHealthProbe`（避免"缓存说 Redis 活着、锁说它死了"）；
  ② 启动/降级/恢复三条路径都打日志；③ 新增只读端点 `GET /agent/backends` 暴露当前后端。
- 标签：降级,可观测性,redis

### 2026-10-17 Flyway 落地：5 个坑，其中 2 个是「单测全绿但真机起不来」

- 背景：把「改表结构靠手工执行 SQL」换成 Flyway（V1 全量建表 + V2/V3/V4 三个增量），
  并顺手把本机 MySQL 从 5.7 升级到 8.0.28（in-place upgrade）。

#### 坑 1：两个装配 bug，mvn test 全绿但**应用根本起不来**

- 现象 A：WebMvcConfig 构造器需要 RateLimitInterceptor，容器里找不到，启动失败。
- 现象 B（修完 A 才暴露）：AgentGatewayController 需要单个 ChatCache bean，但容器里有两个 ——
  chatCacheRouter（新路由）和 chatResponseCache（旧实现）。
- 共同根因：**漏/多了 @Component**。而所有用例都是自己 new 或用 mock，
  **没有一个真的让 Spring 解析这两个类型**，所以单测一条也测不出来。
- 修复：RateLimitInterceptor 补 @Component；ChatResponseCache **去掉** @Component ——
  它是 ChatCacheRouter 的内部实现细节，不该暴露成 bean，改由 Router 自己按 props 创建。
- 判据：真启动一次，GET /agent/backends 应返回三件套各自的当前后端
  （实测 {"redisEnabled":false,"cache":"memory","lock":"local","rateLimit":"local"}）。

#### 坑 2：BeanWiringTest 的覆盖盲区 —— 它只查了 @Configuration

- 原先扫的是 @Configuration 的构造器注入点，**漏了 Controller/Service**，
  也漏了「一个接口多个实现」这个 Spring 最典型的坑（坑 1-B 正是它）。
- 补了一条：扫 cache/lock/ratelimit 三个包，同一接口超过一个实现且多个带 @Component 就失败。
- 教训：这类「装配体检」必须覆盖「接口多实现」，不能只查「注入点有没有 bean」——
  后者只覆盖「没有实现」，不覆盖「有多个实现」。

#### 坑 3：Flyway 迁移文件里，**注释中的英文分号会把 SQL 截断**

- 现象：Migration V1__init_schema.sql failed，报错指向 DEFAULT CHARACTER SET utf8mb4。
- 根因：Flyway **先按分号切分语句，再剥离注释** —— 所以注释行里的 USE jobseeker;
  会被当成语句分隔符，把后面的内容切成一个不完整语句。
- 修复：注释里不要写英文分号（中文分号不受影响）。
- 这条极隐蔽：报错位置指向的是**下一行**，而不是注释本身。

#### 坑 4：脚本改写多行 SQL 时只删了第一行，留下孤儿（我自己的 bug）

- 根因：schema.sql 里 CREATE DATABASE IF NOT EXISTS jobseeker 是**跨三行**的，
  我的正则只删了第一行，剩下 DEFAULT CHARACTER SET / COLLATE 两行孤儿，
  于是 V1 里出现一个没有主语的语句，直接语法错误。
- 修复：删掉孤儿行，并全文复查 CREATE DATABASE 与 USE 在 SQL 区已不存在（只在注释里）。
- 教训：**「删除以某关键字开头的行」这种正则，对跨行语句一定不完整** ——
  要么按块处理，要么删完必须全文复查关键字。

#### 坑 5：MySQL 5.7 到 8.0 原地升级（官方做法，不依赖 Installer）

- Installer 走不通：web 版拉不到包列表（No packages found），且 bundle 只带当前版本。
- 走 zip 版 + mysqld 直接启动旧 datadir：
  1. 阿里云镜像下 mysql-8.0.28-winx64.zip（211.74MB，curl -L -C - 断点续传）；
  2. **必须写 my.ini** —— Windows 上 8.0 的 zip 版会忽略命令行的 --datadir，
     回落成默认路径（不存在）后直接 Aborting；
  3. upgrade=FORCE 让它自动升级数据字典；
  4. 升级后 root 认证变了，连接要带密码。
- 备份与验证（升级前必做，且必须真恢复一次）：
  mysqldump --result-file 直写文件；
  **不要用 PowerShell 管道把 dump 落到文件**（会加 BOM 并按行重编码，备份直接报废，
  这个坑 DEV_NOTES 第 3 条记过，今天又踩了一次）；
  恢复后逐表核对行数一致，datadir 另存一份副本。

#### 最终验收（真机，全部达成）

- 全新库从零跑 V1 到 V4，flyway_schema_history 四条全部 success=YES；
- **列结构逐列一致**：临时库 92 列 vs 本机库 92 列（唯一索引差异是 Flyway 自己的表）；
- 本机库 baseline 到 V4：一条记录，数据零改动；
- mvn test 49 条全绿；应用真启动成功，/agent/backends 三件套后端符合预期；
- Flyway 版本回 9.22.3（MySQL 8.0 后无需再为迁就 5.7 钉老版本）。

- 标签：flyway,数据库迁移,spring装配,单测盲区,mysql升级,备份
### 2026-10-18 在 GitHub Codespaces 里真机验证：4 个环境坑，每个都能让新人卡半小时

- 背景：为了验证「部署产物本身是好的」✨✓（本机没 Docker ✗✗ 只能上 Codespaces ✓✓），
  在 Codespaces 里从零跑 jobseeker（+ Docker MySQL）。连踩 4 个坑，全部记在这里。

#### 坑 1：CodeSpaces 默认 JDK 是 25，而 pom 的 toolchain 要 17

- 现象：`mvn spring-boot:run` 直接
  `Cannot find matching toolchain definitions for the following toolchain types: jdk [ version='17' ]`。
- 原因：CodeSpaces 镜像用 SDKMAN 装了 **JDK 25**（`/usr/local/sdkman/candidates/java/25.0.4+1-ms` ✓✓），
  而 `pom.xml` 的 maven-toolchains-plugin 要求 JDK 17 ✨✓，
  且 `~/.m2/toolchains.xml` 在全新环境里**不存在** ✗✗。
- ⚠️ **不能简单地"放宽到 17+"** ✗✗ —— Spring Boot 3.2.5 官方只支持 JDK 17–21 ✨✓
  JDK 25 不在支持范围 ✨✓ 硬跑会出诡异问题 ✓✗。
- 解决：`apt-get install openjdk-17-jdk` + 写 `~/.m2/toolchains.xml` 指向它 ✓✓。
- 教训：**toolchain 是"环境要求"不是"代码偏好"** ✓✓ —— 要么把环境要求写进 devcontainer ✓✓
  要么在 README 里写清"需要 JDK 17"✓✓，别让人猜 ✗✓。

#### 坑 2：`toolchains.xml` 是隐藏文件，搜不到 → 有人会去全局搜 → 把 CodeSpaces 搜崩

- 现象：想确认 toolchains.xml 写没写成功，在 VS Code 里全局搜文件名 ✨✓
  → CodeSpaces **直接崩了**（重启才好 ✗✓）。
- 原因：VS Code 全局搜索会索引**整个文件系统** ✓✗ —— `/usr/lib/jvm`、`~/.sdkman/candidates`
  （好几个 JDK ✨✓）、`/opt`、`/usr/local` …… CodeSpaces 免费档只有 **4GB 内存** ✨✓ 一索引就 OOM ✨✓。
- 解决：① 隐藏文件用 `cat ~/.m2/toolchains.xml` 看 ✓✓；
  ② 要搜代码用 `grep -rn "xxx" server/src/` ✓✓ **最快最安全** ✓✓；
  ③ 或 `Ctrl+Shift+F` 时把范围限定到 `server/src/**` ✓✓。
- 教训：**在资源受限的云端环境里，别用会遍历全盘的操作** ✗✗
  （全局搜索 / `find /` / 全盘索引 ✨✓）。 CodeSpaces 的 4GB 是本机 16GB 的 1/4 ✨✓ 习惯要改 ✓✓。

#### 坑 3：CodeSpaces 重启后 Docker 容器全没

- 现象：昨天跑得好好的 MySQL，今天 `docker exec` 报 `is not running` ✨✓。
- 原因：CodeSpaces 的**文件系统持久** ✓✓ 但 **Docker 容器不持久** ✨✗（重启即清 ✨✓）。
- 解决：重启后重跑 `docker run`（或 `docker start <name>` ✓✓ 容器定义还在时 ✓✓），
  **并且要等它就绪**再起应用 ✨✓（见坑 4 ✨✓）。
- 教训：**"昨天能跑"不等于"今天能跑"** ✓✓ —— 云端开发环境的生命周期比本机短得多 ✓✓。
  这也是 devcontainer 的价值：把环境固化下来 ✓✓。

#### 坑 4（最有价值）：`mysql:8.0` 浮动 tag 指向的 8.0.46 镜像，容器启动即崩

- 现象：`docker run mysql:8.0` **返回成功** ✓✓ `docker ps -a` **看得到容器** ✓✓
  但状态是 `Exited (255)` ✨✓；应用连不上，报的是 `Communications link failure` ✨✓。
- 一路查上去才在 `docker logs` 里看到真凶 ✓✓：
  ```
  [ERROR] [MY-0113002] The syntax '--skip-host-cache' is deprecated and will be removed
  [ERROR] [MY-010091] /usr/bin/mysqld: Shutdown complete
  ```
  **MySQL 8.0.46 官方镜像的 entrypoint 传了个废弃参数** ✨✓ 自己崩了 ✨✓。
- 根因：`mysql:8.0` 是**浮动 tag** ✓✗ 永远指向最新 patch ✨✓
  → 上游一发新版，你的环境毫无预兆就挂 ✨✓。
- **为什么这个坑特别值钱** ✓✓：
  1. **误导性极强** —— `docker run` 成功、容器看得见、只是 Exited ✨✓
     而应用报的是连接失败 ✨✓ 三处症状全指向"别处"✓✗；
  2. **生产上就是事故** —— 服务器上你不在场 ✨✓ 服务起不来却不知道要看容器日志 ✓✗；
  3. **本机没 Docker 的人永远发现不了** ✨✓ —— 这次是上了 CodeSpaces 才撞见 ✓✓。
- 解决：compose 钉到 `mysql:8.0.43` ✓✓（已本地 + CodeSpaces 双重验证可启动 ✓✓）。
  同一批把 Agent 侧 `redis:7-alpine` 钉成 `redis:7.4-alpine` ✓✓。
- 教训：
  1. **compose 里的第三方镜像一律钉小版本** ✨✓ —— 浮动 tag = 把环境稳定性交给上游 ✨✓；
  2. **升级路径要写明** ✓✓ —— 先在 CI / CodeSpaces 里跑一次再改版本号 ✓✓；
  3. **排障顺序**：容器类问题先 `docker ps -a` + `docker logs` ✓✓
     别一上来就翻应用日志 ✨✓（这次就是应用日志指向连接失败、真凶在容器日志里 ✓✓）。

#### 最终验收（全部达成 ✓✓）

- CodeSpaces 里 `mvn spring-boot:run` 起成功，`Tomcat started on port 8080` ✓✓；
- `GET /agent/backends` → `{"redisEnabled":false,"cache":"memory","lock":"local","rateLimit":"local"}` ✓✓
  （三件套 Bean 装配通了 ✓✓ 降级链生效 ✓✓ 状态可观察 ✓✓）；
- Docker MySQL 8.0.43 + **全新空库** → Flyway 自动建到 V4 ✓✓（4 条 success=1 ✓✓）
  —— 与"本机 MySQL 8.0.28 + 手工增量"结构一致 ✓✓ **环境漂移消除** ✓✓✓。

- 标签：codespaces,环境坑,jdk版本,toolchain,浮动tag,mysql,devcontainer