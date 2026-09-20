# jobseeker 开发记录（DEV_NOTES）

> 本文件由 `preflight-check` skill 维护：动手前先读本文件，踩坑后追加记录。
> 生成时间：2026-09-20 13:59（2026-09-20 人工补齐项目名、端口与约定）
> 重新生成：`dev-notes.ps1 -Init -Force`；追加记录：`dev-notes.ps1 -Add`

## 一、项目速览

- 项目名称：jobseeker（AI 求职助手 · 平台侧：Vue3 前端 + Spring Boot 网关）
- 版本号：0.3.0（三处一致：`README.md` / `web/package.json` / `server/pom.xml`，门禁会校验）
- 技术栈：Vue3 · Vite · TypeScript · Pinia · Vue Router · Java 17 · Spring Boot · MyBatis-Plus · MySQL
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
