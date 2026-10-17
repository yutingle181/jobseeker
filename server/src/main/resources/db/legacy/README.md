# db/legacy —— 已废弃的手工迁移脚本（保留仅为对账）

这三个脚本在 2026-10-16 之前是**改表结构的唯一途径**，做法是人工连库执行：

```powershell
mysql -u root -p jobseeker < server/src/main/resources/db/legacy/add_delivery_exam_done.sql
```

## 为什么废弃

不是因为"手工执行"这件事本身有问题，而是因为**执行状态无法查证**：

| 问题 | 实际后果 |
| --- | --- |
| 执行过哪些、有没有执行过，只存在于人的记忆里 | 换台机器、换个新人接手，只能逐个 `information_schema` 查列/查索引 |
| **文件名与实际列名不一致** | `add_delivery_deadline_url.sql` 实际加的是 `exam_deadline` + `apply_url`。本次盘点时一度按文件名猜列名，误判成"这个脚本没执行过" |
| 与 `schema.sql` 的先后关系靠约定 | 容器 initdb 只跑 `schema.sql`（不含这三个增量）→ **容器结构与本机长期不一致**，且从代码里看不出来 |
| 没有执行记录，出问题无法定位 | 也没有回滚概念 |

## 现在的做法

权威路径是 Flyway 迁移：`server/src/main/resources/db/migration/V*.sql`

| 版本 | 内容 | 对应旧脚本 |
| --- | --- | --- |
| `V1__init_schema.sql` | 全量建表（11 张表） | `schema.sql` |
| `V2__add_delivery_exam_done.sql` | `delivery_record.exam_done` | `add_delivery_exam_done.sql` |
| `V3__add_delivery_exam_deadline_and_apply_url.sql` | `exam_deadline` + `apply_url` | `add_delivery_deadline_url.sql` |
| `V4__add_list_indexes.sql` | 五个 `(user_id, created_at)` 联合索引 | `add_list_indexes.sql` |

执行状态记录在 `flyway_schema_history` 表里，随时可查：

```sql
SELECT installed_rank, version, description, success, installed_on
FROM jobseeker.flyway_schema_history ORDER BY installed_rank;
```

**改表结构一律加 `V<n>__<描述>.sql`，不要再改库、不要改这里的历史脚本。**
（Flyway 会校验已执行迁移的 checksum，改了历史文件会导致启动失败——这是特性不是限制 ✓）

## 附：两个语法坑（Flyway 迁移里已规避）

1. `USE jobseeker;` **必须去掉** —— Flyway 已把会话切到目标库；
   更危险的是它写死库名，切临时库做演练时会把 DDL 打到同名库上。
2. `DELIMITER //` **必须去掉** —— 那是 `mysql` 客户端的指令，Flyway 的解析器不认，遇它直接语法错误。
   连带地，用 `CREATE PROCEDURE` 写的"列存在就跳过"在 Flyway 里也不适用，
   `V2`~`V4` 改用 `information_schema` 判断 + `PREPARE/EXECUTE` 实现同样的幂等。