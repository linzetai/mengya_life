# Gate 2B 报告 · 遗留问题修复 + 真实目标系统

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 工程名 / 包名 | MengyaLife / `com.mengyalife.app` |
| 应用版本 | **0.4.0-g2b**（versionCode 4） |
| 轮次 | Gate 2B：P0/P1 修复 + 三层目标系统 + 数据库 v2 迁移 |
| 执行时间 | 2026-11-03 00:14 – 09:00（Asia/Shanghai） |
| 报告路径 | `docs/reports/REPORT-G2B-GOALS.md` |
| 结论 | **PASS** |

---

## 1. 结论

**PASS**。Gate 2A 的 6 项遗留问题全部修复；目标页从静态演示变成 **Room 真实数据**的
三层目标系统（长期 → 阶段 → 短期行动），带进展记录、状态流转、删除影响提示；
数据库从 v1 迁移到 v2，**真机升级实测日记逐字段未变**。

| 关键指标 | 值 |
|---|---|
| 交付 APK | 11,088,320 字节，SHA-256 `a367460605649d6ca052fc6e6476d590ce87f3f8622611b18c7809d7d91f4907` |
| 真机验收 | **80/80 断言通过**，单次连续执行（`logs/g2b-final-regression.log`） |
| JVM 单元测试 | **50/50**（6 个测试类） |
| 数据库 | v1 → v2，`user_version=2`，新增 `goals` / `goal_checkins` |
| 迁移验证 | 真机覆盖安装，`journal_entries` 两行 **逐字段完全一致** |
| 权限 | 未新增；仍无 `INTERNET` |
| 崩溃 | 本应用无崩溃记录 |

---

## 2. Gate 2A 遗留问题修复

### 2.1 过期/不实文案（P0）

逐条审查 `SettingsScreen.kt`、`DemoData.kt`、`README.md`，修正如下：

| 位置 | 原文（错） | 现在（准确） |
|---|---|---|
| 设置 → AI 服务 | 当前配置状态：**未配置（演示环境）** | 未配置（**未接入任何模型**） |
| 设置 → 数据与隐私 | 本地数据管理：**当前页面内容均为内置演示数据，未写入数据库**（badge「演示」） | 日记与目标保存在**本机数据库（Room）**，不会上传（badge「本机」） |
| 设置 → 数据与隐私 | 导入/导出：「当前没有可导入或导出的数据」 | 「目前还不能导出或导入数据」（功能仍未实现，但不再暗示无数据） |
| 设置 → 删除全部数据 | 点击不会删除任何内容 | 点击**不会删除你的日记或目标**（并明确"尚未实现"） |
| `DemoData.DATA_SCOPE_NOTE` | 当前版本不读取、不上传任何真实数据；**页面内容均为内置演示数据** | 日记与目标保存在本机数据库，不会上传；**总览中的成长趋势与近期洞察仍是演示内容** |
| README | 通篇「本轮不接 Room」「主题不持久化」 | 已重写为 Gate 2B 状态，明确哪些是真实、哪些是演示 |

同时把**演示区块逐张标注**：总览的「人生方向 / 成长趋势预览 / 近期洞察」卡片各自挂
「演示」标签，并写明"不是你的真实数据"；而「长期目标」区块读真实库、**无演示标签**。

### 2.2 `confirmDelete()` 吞掉失败（P0）

原实现无论删除成功与否都关闭弹窗，等于把失败伪装成成功。改为：

```kotlin
val outcome = runCatching { repository.deleteEntry(target.id) }
val deleted = outcome.getOrNull() == true
if (deleted) { /* 关闭弹窗 */ } else { /* 保留弹窗与记录上下文，回填 deleteError */ }
```

- 失败时**保留**待删记录与弹窗，并在弹窗内显示「删除失败：…」；
- 失败路径有单元测试：`JournalViewModelTest.删除失败时保留上下文并显示错误`
  （用一个"删除返回 0"的假 DAO 驱动）。

### 2.3 解释 `g2a-verify-all.log` 的 2 条失败（P1）

两条失败是：

```text
FAIL  tab_overview 设置页已就位
FAIL  tab_overview 设置页隐藏底栏
```

**结论：自动化时序问题，不是产品缺陷。** 具体原因：

1. 该轮脚本在回归前先到顾问页输入文字，**软键盘弹起把底部导航顶高**；
2. 随即点击底栏切到「总览」，布局正在回弹，`uiautomator dump` 拿到的是过渡态；
3. 于是"设置页是否就位"的判断落空。

后续修复：输入后先 `KEYCODE_BACK` 关闭键盘并等待布局稳定；单独复跑同一路径 **28/28 通过**，
手动冷启动后连点 3 次设置入口也全部成功。

本轮按要求补充了**一次连续执行的完整回归**（不是拼接多次通过结果）：
`docs/reports/logs/g2b-final-regression.log`，12 个阶段、**80/80 断言**一次跑完。

### 2.4 「空状态」截图与名称不一致（P1）

Gate 2A 的证据包里，名为"空状态"的截图实际包含一条日记。处理：

- 经用户同意，**删除**了那条真实私人日记（走 App 的删除流程，不是改库）；
- 重新拍摄了**真正为空**的日记页：`logs/g2b-shots/00-diary-empty.png`
  （页面显示「还没有记录」，可逐字核对）。

### 2.5 验证脚本入库（P1）

脚本原先在 `/tmp`，换会话即丢。现已提交到仓库：

```text
tools/ui-verify/
├── harness.py              # adb + UIAutomator 公共库（含前台恢复、PNG 亮度计算）
├── verify_goals.py         # 目标三层 / 层级合法性 / 进展 / 状态 / 删除影响
├── verify_diary.py         # 日记 CRUD 与持久化
├── verify_theme.py         # 主题三态 + 重启保持（亮度断言）
├── verify_navigation.py    # 五页与设置导航回归
├── run_all.sh              # 一条命令跑全部
└── README.md               # 用法与已知坑
```

- 设备地址改为 `export DEV=...`，未设置会直接报错退出；
- `verify_goals.py` 会清空目标数据，因此**必须显式** `MENGYA_TEST_DESTRUCTIVE=1` 才能跑。

### 2.6 私人日记不进证据包（P1）

- 真实日记已删除，本轮所有截图与数据库快照**只含测试记录**
  （`MigrationA` / `MigrationB` / `LongHealth` 系列）；
- Gate 2A 证据包已重建：移除 3 张含私人日记的截图，并写入 `SCRUB-NOTICE.txt` 说明；
- 数据库快照只输出结构与行数，**不输出正文**。

---

## 3. 目标系统设计

### 3.1 领域模型

| 概念 | 取值 | 说明 |
|---|---|---|
| 层级 `GoalLevel` | `LONG_TERM` / `STAGE` / `ACTION` | 严格逐级嵌套，深度 0/1/2 |
| 领域 `GoalDomain` | 健康 / 职业 / 财富 / 生活 / 学习 | 固定枚举，本轮不做自定义 |
| 状态 `GoalStatus` | 未开始 / 进行中 / 已完成 / 已暂停 / 已取消 | 手动切换 |

父子规则（`GoalRules`，纯函数、可单测）：

1. 长期目标**不能有父**；阶段目标**必须**挂长期目标；行动**必须**挂阶段目标；
2. 跳级（行动直接挂长期）被拒绝；
3. **循环引用被拒绝**：`wouldCreateCycle(goalId, newParentId, allGoals)` 沿父链回溯，
   命中自己或自己的后代即非法；
4. 父目标不存在、标题为空/超长、截止早于开始，全部拒绝。

> 层级在**创建时**确定，编辑不改变父级，因此正常路径不会产生环；
> 环检测函数仍然实现并单测，为将来"移动目标"预留。

### 3.2 进展记录

- 独立表 `goal_checkins`：目标 id、记录时间、说明（必填）、可选数值 + 单位、来源（固定 `manual`）；
- 来源字段只为将来区分"导入/自动"预留，**本轮只有用户手动录入**；
- 记录进展时若目标还是「未开始」，自动置为「进行中」（符合直觉，且已在单测里固化）。

### 3.3 不给"人生"打分

- 没有"人生综合分"这类指标；
- 总览里长期目标的进度条 = **已完成阶段数 / 阶段总数**（可解释、可核对）；
- 数值型目标的真实数值只在**进展历史**里逐条展示，不做任何推断或外推；
- 总览的 0–100 曲线仍存在，但已标注「演示」，且文案写明"这一块尚未接入真实统计"。

---

## 4. 数据库与迁移

### 4.1 表结构（v2）

```sql
CREATE TABLE `goals` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `title` TEXT NOT NULL,
  `description` TEXT,
  `level` TEXT NOT NULL,            -- LONG_TERM / STAGE / ACTION
  `parent_id` INTEGER,              -- 根目标为 NULL
  `domain` TEXT NOT NULL,
  `status` TEXT NOT NULL,
  `start_date` INTEGER,             -- Epoch 毫秒，可空
  `due_date` INTEGER,               -- Epoch 毫秒，可空
  `created_at` INTEGER NOT NULL,
  `updated_at` INTEGER NOT NULL,
  FOREIGN KEY(`parent_id`) REFERENCES `goals`(`id`) ON DELETE CASCADE
);
CREATE INDEX index_goals_parent_id ON goals(parent_id);
CREATE INDEX index_goals_level     ON goals(level);
CREATE INDEX index_goals_status    ON goals(status);

CREATE TABLE `goal_checkins` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `goal_id` INTEGER NOT NULL,
  `recorded_at` INTEGER NOT NULL,
  `note` TEXT NOT NULL,
  `metric_value` REAL,
  `metric_unit` TEXT,
  `source` TEXT NOT NULL,
  FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`) ON DELETE CASCADE
);
CREATE INDEX index_goal_checkins_goal_id ON goal_checkins(goal_id);
```

关系：`goals.parent_id → goals.id`（自引用，级联删除）；`goal_checkins.goal_id → goals.id`（级联删除）。

### 4.2 迁移代码

`MengyaDatabase.MIGRATION_1_2`：只 `CREATE TABLE` + `CREATE INDEX`，
**完全不触碰 `journal_entries`**。没有 `fallbackToDestructiveMigration()`。

### 4.3 真机迁移验证（覆盖安装，不卸载）

| 阶段 | user_version | 表 | 日记行 |
|---|---|---|---|
| 升级前（0.3.0-g2a） | 1 | `journal_entries` 等 4 张 | 2 行（id 6/7） |
| 升级后（0.4.0-g2b） | **2** | 新增 `goals`、`goal_checkins` | **2 行，逐字段一致** |

比对结果（脚本自动 diff `id/title/body/created_at/updated_at`）：

```text
迁移前 user_version: 1 -> 迁移后: 2
日记行完全一致: True
  6 MigrationA 'alpha v1 content' 1793636289310 1793636289310
  7 MigrationB 'beta v1 content' 1793636317600 1793636317600
```

schema JSON 已保留 `app/schemas/.../1.json` 与 `2.json`。

---

## 5. 目标页与总览

### 5.1 目标页交互

- 默认只展示**长期目标卡片**（摘要优先），阶段/行动按需展开，第一屏不会出现巨大卡片；
- 每个节点右侧「⋯」菜单：记录进展 / 进展历史 / 修改状态 / 编辑 /（按层级）新建阶段或行动 / 删除；
- 新建入口：空状态卡片 + 底部按钮；
- 删除：先算影响范围（`assessDeleteImpact`），弹窗明确写出
  「删除后，还会一并删除：1 个阶段目标、1 个短期行动、1 条进展记录。」，确认后级联删除。

### 5.2 总览联动

- 「长期目标」区块读真实快照：数量、进行中数量、前三条目标、最近进展时间；
- 无目标时显示真实空状态 + 「新建长期目标」按钮（直接跳到目标页并打开新建面板）；
- 其余区块保留演示内容但**逐张标注「演示」**，不与真实数据混同。

---

## 6. 验收逐条对照

| # | 要求 | 结果 | 证据 |
|---|---|---|---|
| 1 | Gate 2A 遗留修复、设置页表述准确 | ✅ | §2；`g2b-final-regression.log` 导航/设置段 |
| 2 | 真机创建 长期→阶段→行动 | ✅ | 日志 §1 段；`shots/02-two-levels`、`03-three-levels`；DB 中 3 行且父子正确 |
| 3 | 编辑、状态、进展记录且重启保留 | ✅ | 日志 §3/§5；DB `ActionAB` / `DONE` / `goal_checkins` 1 行 |
| 4 | 父子关联正确、非法层级与循环被阻止 | ✅ | UI 菜单层面只提供合法层级（日志断言菜单项）；`GoalRulesTest` 18 例覆盖跳级/环 |
| 5 | 删除有范围提示、不静默丢数据 | ✅ | 「一并删除：1 个阶段目标、1 个短期行动、1 条进展记录」；取消无副作用；删除后 DB 无孤儿进展 |
| 6 | v1→v2 迁移后日记完全保留 | ✅ | §4.3 逐字段 diff；日志"v1 迁移来的日记 2 条且内容未变" |
| 7 | 空目标状态无伪造目标 | ✅ | `shots/01-goals-empty`；`DemoData` 已删除目标演示块 |
| 8 | 总览读取真实目标 | ✅ | 日志 §6 段；`shots/09-overview`；删除后空状态 `12-overview-empty` |
| 9 | 日记/主题/导航不回归 | ✅ | 日志 §9/§10/§11 段 |
| 10 | 完整编译、安装、冷启动、无崩溃 | ✅ | `g2b-build-clean.log`（37 tasks, exit 0）；日志 §12 |
| 11 | APK 哈希、连续日志、关键截图 | ✅ | `a3674606…`；`g2b-final-regression.log`（单次 80/80）；15 张截图 |
| 12 | 自动化脚本入库可重跑 | ✅ | `tools/ui-verify/` + `run_all.sh` |
| 13 | 不增加非必要权限 | ✅ | 仍只有 AndroidX 注入的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

---

## 7. 真机构建与测试

### 7.1 构建

| 项 | 值 |
|---|---|
| 命令 | `./gradlew clean :app:assembleDebug --no-daemon --offline --no-build-cache` |
| 结果 | `BUILD SUCCESSFUL in 1m 29s`，`37 actionable tasks: 37 executed`，`EXIT_CODE=0` |
| 日志 | `docs/reports/logs/g2b-build-clean.log` |

**关于两个哈希（必须说清）**：

| 用途 | SHA-256 |
|---|---|
| **交付并以真机验收的 APK** | `a367460605649d6ca052fc6e6476d590ce87f3f8622611b18c7809d7d91f4907` |
| 上述 clean 重建产出（同源码） | `29ce67b9fdb0a6995700a72894e57ec1425f54a5f4507999129405287eb8d958` |

两者字节不同（AGP debug 包在本机增量/clean 构建间不完全可复现，Gate 1/2A 同样出现）。
为保证**交付物 = 验收物**，构建目录已恢复为验收版本（`a3674606…`），clean 日志仅用于证明可完整构建。

> 诚实说明：该 APK 安装到真机后**未再修改源码**，80/80 回归即跑在这个二进制上；
> 但设备在本轮收尾时无线调试掉线，**未能再次回读设备内 APK 做哈希比对**
> （Gate 2A 时做过这项比对）。如需复核，重新连接后执行：
> `adb pull $(adb shell pm path com.mengyalife.app | sed 's/package://') && sha256sum base.apk`

### 7.2 单元测试

```text
data.GoalRepositoryTest     13   失败 0
data.JournalRepositoryTest   8   失败 0
domain.GoalRulesTest        18   失败 0
domain.JournalRulesTest      6   失败 0
ui.JournalViewModelTest      3   失败 0
util.TimestampsTest          2   失败 0
总计 50 用例，失败 0
```

### 7.3 真机回归（单次连续）

`logs/g2b-final-regression.log` 十二个阶段：

```text
0 清理遗留目标   1 三层目标   2 进展+历史   3 状态+编辑
4 数据库校验     5 重启保留   6 总览联动    7 删除影响
8 总览空状态     9 日记回归   10 主题回归   11 导航回归   12 稳定性
==== SUMMARY: 80/80 passed ====
```

---

## 8. 源码与变更摘要

### 8.1 新增

```text
domain/goals/GoalModels.kt          层级 / 领域 / 状态枚举
domain/goals/GoalRules.kt           父子规则 + 环检测（纯函数）
data/local/goal/GoalEntity.kt       目标表
data/local/goal/GoalCheckinEntity.kt 进展表
data/local/goal/GoalDao.kt          DAO + 事务删除
data/local/Converters.kt            枚举 ↔ TEXT
data/repository/GoalRepository.kt   校验 / 树构建 / 影响评估 / 进展
ui/goals/GoalContract.kt            UiState + Action
ui/goals/GoalsViewModel.kt          目标页全部状态与动作
ui/screens/GoalSheets.kt            编辑 / 状态 / 进展 / 历史 / 删除弹层
tools/ui-verify/*                   真机验证脚本（5 个 + run_all.sh + README）
app/src/test/.../GoalRulesTest.kt   18 用例
app/src/test/.../GoalRepositoryTest.kt 13 用例
app/src/test/.../JournalViewModelTest.kt 3 用例（含删除失败路径）
app/schemas/.../2.json              v2 schema
```

### 8.2 修改

| 文件 | 变更 |
|---|---|
| `MengyaDatabase.kt` | v2 + `MIGRATION_1_2` + 两个 DAO |
| `MengyaLifeApp.kt` | 接入 `GoalsViewModel`、目标弹层、Snackbar 提示、总览新签名 |
| `ui/screens/GoalsScreen.kt` | 完全重写为真实目标树 |
| `ui/screens/OverviewScreen.kt` | 真实目标区 + 演示区块逐张标注 |
| `ui/screens/DiaryScreen.kt` | 删除失败时在弹窗内显示错误 |
| `ui/journal/*` | 契约与 ViewModel 增加 `deleteError`，删除失败不再关闭弹窗 |
| `ui/screens/SettingsScreen.kt` | 全部过期文案修正 |
| `data/DemoData.kt` | 删除目标演示块；数据范围说明改为准确表述 |
| `di/AppContainer.kt` | 注入 `GoalRepository` |
| `app/build.gradle` | versionCode 4 / versionName `0.4.0-g2b` |

### 8.3 规模

24 个主源码文件 3,406 行 → 现 **35 个 Kotlin 文件**；测试 3 个文件 230 行 → **6 个文件 47×行**。

---

## 9. 已知问题与技术债

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | **构建不字节可复现**（增量 vs clean 哈希不同） | 无法用哈希做跨环境校验 | 交付以"验收物"为准，报告记录两个哈希 |
| 2 | **没有自动化迁移测试**（`MigrationTestHelper` 未引入） | v2 迁移只在真机覆盖安装上验证过 | Gate 2C 引入 `room-testing` + instrumented 迁移测试 |
| 3 | `verify_goals.py` 会清空目标数据 | 误跑会删用户数据 | 已加 `MENGYA_TEST_DESTRUCTIVE=1` 开关；仍建议只在测试机跑 |
| 4 | **截止日期选择器未做自动化验证** | 记录/展示路径未覆盖 | 下一轮补一条 UI 用例 |
| 5 | 数值型目标没有图表，只逐条显示数值 | 体验偏弱 | Gate 2C 可加简单折线（复用 Gate 1 的 Canvas 组件） |
| 6 | 目标不能在层级间移动 | 建错只能删了重建 | 需要"移动目标"时再引入（环检测已就绪） |
| 7 | 目标与日记尚无关联 | 与产品愿景有差距 | 留待后续 Gate |
| 8 | 总览的趋势/洞察仍是演示数据 | 与真实目标并存易混淆 | 已逐张标注；接入真实统计前保持标注 |
| 9 | 删除是硬删除，无回收站 | 误删不可恢复 | 有二次确认 + 影响范围提示；恢复功能后续再议 |
| 10 | 自动化脚本依赖固定文案 | 改文案需同步改脚本 | 已尽量用 resource-id，文案断言仅作补充 |

---

## 10. Gate 2C 建议

1. **补迁移测试基建**：引入 `androidx.room:room-testing`，先为 v1→v2 写一条 instrumented
   `MigrationTestHelper` 用例，把流程跑通，再考虑 v3。
2. **目标进展可视化**：数值型目标加一条简单的历史折线（复用自绘 Canvas，不引第三方库）。
3. **目标移动 + 环检测落地**：`GoalRules.wouldCreateCycle` 已就绪且有单测，接一个"移动到其他父级"入口即可。
4. **导出/备份**：设置页的「数据导入导出」仍是占位；实现时注意 **WAL**（必须同时处理 `-wal`/`-shm` 或先 checkpoint）。
5. **CI-free 自检脚本**：把 `tools/ui-verify/run_all.sh` 扩成「构建 → 单测 → 装机 → 回归」一条链，
   每轮结束跑一次，减少手工取证。

---

## 附录：证据索引

| 路径 | 内容 |
|---|---|
| `logs/g2b-final-regression.log` | **单次连续**真机回归，80/80 |
| `logs/g2b-unit-test.log` | 50 个 JVM 单元测试 |
| `logs/g2b-build-clean.log` | clean 全量构建（37 tasks，exit 0） |
| `logs/g2b-verify-delete.log` | 删除影响范围专项 |
| `logs/g2b-verify-status-edit.log` | 状态/编辑专项 |
| `logs/g2b-shots/*.png` | 16 张真机截图 |
| `docs/reports/evidence/G2B-evidence.zip` | 证据包（截图/日志/源码/测试/schema/脚本） |
| `tools/ui-verify/` | 可重跑的真机验证脚本 |
