# Gate 2C 报告 · 遗留问题修复 + 本地数据导出 + 真实进展趋势

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 工程名 / 包名 | MengyaLife / `com.mengyalife.app` |
| 应用版本 | **0.5.0-g2c**（versionCode 5） |
| 轮次 | Gate 2C：P0/P1 修复 + 数据导出 + 真实数值趋势 |
| 执行时间 | 2026-11-03（Asia/Shanghai） |
| 报告路径 | `docs/reports/REPORT-G2C-DATA-TRENDS.md` |
| 结论 | **PASS（功能）**；分脚本 345/345，通过；settings 首轮为自动化时序失败，已定向复跑（见 §7.10） |

---

## 1. 结论

> ℹ️ 验收说明：功能与单元测试完成；真机脚本分脚本累计 **345 条断言通过**。
> 最后一次 `run_all.sh` 中 8 个脚本连续通过（312 条）；`verify_settings`
> 首轮因冷启动 UI 未就绪失败（8/32），修复等待逻辑后**定向复跑 33/33**。
> 本报告不把“定向复跑”伪装成单次连续全绿，详见 §7.10 与
> `docs/reports/logs/MISSING-EVIDENCE.md`。

Gate 2C 的六项遗留问题全部修复；新增**用户主动触发的本地数据导出**与
**只基于 `goal_checkins` 的真实数值趋势**；真机破坏性测试迁移到独立 QA 包，
正式包只做只读冒烟。数据库结构未变（仍为 v2），正式包升级后**旧日记逐字段未变**。

| 关键指标 | 值 |
|---|---|
| 交付 APK（真机验收） | `artifacts/mengya-life-0.5.0-g2c-debug.apk`，`11,318,375` 字节 |
| SHA-256 | `53de2e8fd0977475110c9eee9e056940c55d643c3cc08d88dcb027885b6297fa` |
| QA 测试 APK | `artifacts/mengya-life-0.5.0-g2c-qa-test.apk`，`11,319,353` 字节 |
| QA SHA-256 | `dd2560c3c4c64b705e604e177b79c63f22b033f2f2c341937cada47fec822482` |
| JVM 单元测试 | **88/88**（9 个测试类） |
| 真机断言 | 分脚本累计 **345 条通过**（settings 定向复跑，其余 8 个脚本在最后一次连续运行中全绿） |
| 数据库 | v2，`user_version = 2`，表结构未变 |
| 权限 | 未新增；仍无 `INTERNET`、无存储权限 |
| 崩溃 | 本应用无崩溃记录（冷启动 + 全量回归后检查 crash buffer） |

> **「真机验收 APK」= 上表 debug APK 的字节**，已直接安装到设备并通过全部验收；
> QA APK 仅用于隔离数据的破坏性用例。若今后重新构建，字节与哈希可能不同，
> 必须重新计算哈希并以「安装前实际文件」为准，详见 §8。

---

## 2. 第一部分：遗留问题修复

### 2.1 设置页 DataStore 过期文案（P0）

`SettingsScreen.kt` 重写为准确描述：

| 位置 | 原文（错误） | 现在（准确） |
|---|---|---|
| 设置页顶部 | 「本轮仅完成页面结构与说明」 | 「已实现的设置会立即生效并保存在本机；标注「后续接入」的能力尚未实现。」 |
| 外观分组说明 | 「本轮不引入 DataStore，设置仅在本次运行有效，**重启后恢复「跟随系统」**」 | 「主题切换立即生效，并**保存在本机（DataStore）：重启后保持上次选择**。选择「跟随系统」时会随系统深浅色变化。」 |
| 文件头注释 | 「本轮不引入 DataStore，所以不持久化」 | 删除，改为「主题已持久化；导出是真实实现；导入尚未实现」 |
| `Theme.kt` 枚举注释 | 「仅保存在内存中，进程结束后回到跟随系统」 | 「写入 DataStore，重启后保持；跟随系统实时决定配色」 |
| `DemoData.DATA_SCOPE_NOTE` | 只提「日记与目标」 | 增加「进展记录可导出」；明确演示区范围 |
| 关于页胶囊 | 「自带密钥」 | 「无网络权限」（本版本确实不接收密钥） |

逐字扫描通过：`verify_settings.py` 在整页滚动后断言
**不存在**以下过期文案：

```text
重启后恢复「跟随系统」
本轮不引入 DataStore
设置仅在本次运行有效
当前不能导出或导入数据
目前还不能导出或导入数据
本轮仅完成页面结构与说明
```

同时断言存在：`保存在本机（DataStore）`、`重启后保持上次选择`、
`随系统深浅色变化`、`无网络权限`、`导入恢复尚未实现` 等。

README 已整体重写为 Gate 2C 状态（`0.5.0-g2c`、88 个测试、导出/趋势/QA 包/签名说明），
不再包含「主题不持久化」「只有 50 个测试」「80 项断言」等旧数据。

### 2.2 实际 APK 与 SHA-256

- 交付文件：`artifacts/mengya-life-0.5.0-g2c-debug.apk`
- SHA-256 文件：`artifacts/mengya-life-0.5.0-g2c-debug.apk.sha256`
- 同目录另存 QA 测试 APK 与其哈希，**文件名与标签明确区分**；
- 真机验收使用 `artifacts/` 里的**同一份文件**安装（不是从 `app/build` 随手取的最新产物）；
- 安装后从设备拉回 `base.apk` 复核哈希，与交付文件一致（见 §8）。

### 2.3 真机测试不再误删用户数据（P0）

**问题**：Gate 2B 的 `verify_goals.py` 会删除设备上正式包里的全部目标；
Gate 2C 引入更危险的测试（导出、趋势、日期），必须从机制上隔离。

**方案**：新增独立 QA build type，而不是靠脚本自觉。

| 项 | 正式包 | QA 包 |
|---|---|---|
| applicationId | `com.mengyalife.app` | `com.mengyalife.app.qa` |
| 应用名 | 萌芽人生 | 萌芽人生 QA |
| versionName | `0.5.0-g2c` | `0.5.0-g2c-qa` |
| Room 数据库 | `mengya-life.db` | `mengya-life-qa.db` |
| DataStore | `mengya_settings` | `mengya_settings_qa` |
| 设置页 | 无标识 | 顶部显示 QA 隔离说明 |

实现要点：

- 库名/偏好名通过 `buildConfigField` 注入；
  `MengyaDatabase` 与 `ThemePreferences` 都读 `BuildConfig`，不在代码里写死测试库；
- `qa` build type 使用 debug 签名，可与正式包**同时安装**；
- `harness.py` 的 `require_qa()`：被清数据的脚本在包名不是 QA 时**直接退出**；
- `clear_qa_data()` 只会 `pm clear com.mengyalife.app.qa`；
- 正式包只有 `verify_prod_smoke.py`：不清数据、不新建日记/目标、不切主题；
- 该脚本末尾比对正式包数据库的日记行，必须逐字段不变。

**签名轮换说明（实际发生）**：本轮构建环境的调试签名与 Gate 2B 不同，
`adb install -r` 被系统拒绝（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
处理流程：

1. 强制停止正式包，`run-as` 打包 `databases/` + `files/`（含 WAL/SHM、DataStore）；
2. 卸载旧签名包；
3. 安装新 APK；
4. 用 `tar` 把备份恢复到新包数据目录；
5. 冷启动后比对 `journal_entries`：`id/title/body/created_at/updated_at` 全部一致。

结果：两条旧日记（`MigrationA` / `MigrationB`）逐字段一致；目标表本来为空，仍为空。
详细证据见 §7.9 与 `g2c-shots/prod-*`。

> 这条流程已写入 README §8，提醒后续构建者：签名变化时**不要**直接卸载了事。

### 2.4 NaN / Infinity 等非法指标（P0）

**根因**：`String.toDoubleOrNull()` 会接受 `NaN`、`Infinity`、`-Infinity`、`1e999`
（它们都是合法的 Java `Double` 字面量），旧实现只判断「能否转成 Double」，
并且 `GoalRepository.addCheckin` 完全没有校验数值——只靠界面拦截。

**修复**（三层）：

1. `GoalRules.validateMetricValue(value: Double?)`：
   - `null` 合法（数值是可选项）；
   - `!value.isFinite()` → 拒绝；
   - `abs(value) > 1_000_000_000` → 拒绝（防溢出/坐标失真）。
2. `GoalRules.validateMetric(raw)` 先解析再调用 `validateMetricValue`；
   `GoalRules.parseMetric(raw)` 只返回有限数。
3. `GoalRepository.addCheckin` **写库前**调用
   `validateMetricValue` 与 `validateUnit`，失败返回 `GoalResult.Error`，
   不执行 `dao.insertCheckin`。
4. `GoalTrends.seriesFrom` 对历史脏数据再次过滤非有限数（纵深防御）。
5. 导出时非有限数写成 `null`，保证 JSON 合法。

新增/更新的测试：

- `NaN 与 Infinity 文本一律被拒绝`（含 `1e999` / `-1e999`）
- `NaN 与 Infinity 的 Double 值被拒绝`
- `超出范围的数值被拒绝`
- `parseMetric 不会把 NaN 变成有效值`
- `仓库层拒绝 NaN 指标且不写库`
- `仓库层拒绝 Infinity 与负无穷`
- `仓库层拒绝超出范围的指标`
- 真机：`NaN` / `Infinity` / `1e999` 三次输入均提示「指标数值不合法」，
  面板不关闭，数据库进展条数保持 5 → 5。

### 2.5 总览「进行中」统计口径（P1）

**问题**：旧实现 `inProgressCount = goals.count { status == IN_PROGRESS }` 统计**全部层级**，
但展示在「长期目标」卡片上，读起来像「长期目标进行中 N」。

**修复**：`GoalSnapshot` 新增/明确字段：

| 字段 | 口径 |
|---|---|
| `longTermCount` | `level == LONG_TERM` 的数量 |
| `stageCount` / `actionCount` | 阶段 / 行动数量 |
| `longTermInProgressCount` | 长期目标中 `status == IN_PROGRESS` 的数量 |
| `inProgressCount` | **全部层级**中 `status == IN_PROGRESS` 的数量 |
| `doneCount` | **全部层级**中 `status == DONE` 的数量 |

「未开始 / 已暂停 / 已取消」不计入进行中。

界面改动：

- 总览胶囊：`长期目标进行中 N`（只统计长期层级）；
- 总览说明：「『进行中』= 状态为『进行中』的目标。全部层级共 N 个
  （长期 X · 阶段 Y · 行动 Z），其中进行中 K 个。」
- 总览进度条说明：「已完成阶段数 ÷ 阶段总数，只描述这一个目标的拆解，不是人生评分」；
- 目标页概览拆成「长期目标 / 阶段目标 / 短期行动 / 进行中（全部层级）/ 已完成（全部层级）」，
  并写明长期目标里进行中的数量。

### 2.6 日期选择器真机用例（P1）

新增 `verify_dates.py`，在 QA 包上完成：

1. 新建目标 → 编辑 → 打开 DatePicker；
2. 选中 2026-11-28 → 确定 → 面板显示「2026年11月28日」；
3. 保存 → 目标卡片显示「截止 2026年11月28日」；
4. `am force-stop` + 冷启动 → 日期仍在；
5. 数据库 `due_date = 1795824000000`（= `2026-11-28T00:00:00Z`），
   按 UTC 渲染仍是 28 日（不会因时区偏移一天）。

代码侧同时修正：日期类字段（`dueDate`）统一用
`Timestamps.formatDateUtc()` 渲染，与 Material3 DatePicker 的 UTC 零点语义一致；
编辑面板与目标卡片都改用它。JVM 测试覆盖了纽约时区下仍使用 UTC 日期的行为。

---

## 3. 第二部分：本地数据导出

### 3.1 入口与流程

设置 → 数据与隐私 → **导出我的数据**（badge `JSON`）→
敏感信息提醒（含三类条数）→ `选择保存位置` →
系统 SAF（`CreateDocument("application/json")`）→ 写入 → 回读校验 → Snackbar。

- **不申请存储权限**；不硬编码 `/sdcard/Download`；
- 用户取消 SAF → 不写任何文件，提示「已取消导出」；
- 写入失败 / 校验失败 → 明确错误，不显示成功。

### 3.2 一致性快照

`DataExportRepository.buildBundle()`：

```kotlin
transaction {
    ExportJson.Bundle(
        exportedAt = now(),
        ...
        journalEntries = journalDao.loadAll().sortedBy { it.id },
        goals = goalDao.loadAllGoals().sortedBy { it.id },
        goalCheckins = goalDao.loadAllCheckins().sortedBy { it.id },
    )
}
```

- 生产注入 `RoomDatabase.withTransaction`，三条查询处于同一事务快照；
- **不复制 `.db`**，不处理 WAL 文件，避免漏数据与半写状态；
- 单元测试用直通事务执行器验证「三表在同一事务里读取」。

### 3.3 导出范围与格式

`schemaVersion = 1`，包含：

- `format` = `mengyalife-backup`；
- 导出时刻（Epoch 毫秒 + ISO 字符串）；
- 应用包名 / versionName / versionCode；
- 数据库结构版本（2）；
- `counts`；
- `data.journalEntries[]`：id、title、body、createdAt、updatedAt；
- `data.goals[]`：id、title、description、level、parentId、domain、status、
  startDate、dueDate、createdAt、updatedAt；
- `data.goalCheckins[]`：id、goalId、recordedAt、note、metricValue、metricUnit、source。

完整字段说明见 `docs/DATA-EXPORT-FORMAT.md`。

### 3.4 安全与能力边界

- 导出前明确提示文件可能包含**敏感个人信息**（日记正文），应妥善保管；
- 应用无网络权限、无分享 Intent，不会自动上传/分享；
- 不含 API Key、账号、设备标识、设置项；
- **导入恢复未实现**，提醒框与设置项都写明「这个文件目前不能导回应用」。

### 3.5 写后回读校验

从同一个 `content://` URI 读回文本，`ExportJson.verify(text, expected)`：

- 结构与字段类型；
- `counts` 与实际数组长度；
- 目标 id 唯一、父目标存在、层级逐级嵌套；
- 进展 `goalId` 必须存在、`source` 存在、数值为 null 或有限数；
- 与快照逐一比对：三类 id 集合、父子关系、进展→目标关联。

只有全部通过才显示「导出完成并已校验」。真机实测导出的 JSON：

```text
docs/reports/logs/g2c-export/mengya-life-export-*.json
```

其中包含 1 条日记、1 个长期目标、2 条进展（`kg`，3.5 / 4.0），
与数据库逐字段一致；正式包导出包含 2 条旧日记、0 个目标、0 条进展，
导出前后数据库逻辑内容完全一致。

### 3.6 测试

- JVM `ExportTest`：完整导出、计数篡改、孤儿进展、层级不一致、历史 NaN、
  空库导出、文件名、非 JSON 文本、无密钥字段、事务快照；
- 真机 `verify_export.py`：入口、敏感提醒、SAF、文件解析、逐字段比对、
  导出前后数据库不变、测试文件清理。

---

## 4. 第三部分：真实进展趋势

### 4.1 数据源与分组

- 唯一来源：`goal_checkins` 中 `metric_value` 非空且有限的记录；
- 按「目标 + 单位」分组；不同单位**不混画、不换算**；
- 序列按真实 `recorded_at` 升序（同刻按 id），横轴是时间而不是「第 N 次」；
- 丢弃 NaN/Infinity（纵深防御）。

### 4.2 展示

- 目标 → 进展历史：每个单位一张 `MetricTrendChart`；
  - 0 条：空状态 + 「数值是选填项」；
  - 1 条：单点 + 「无法判断趋势，不伪造曲线」；
  - ≥2 条：折线 + 每个点 + 浅色面积 + 时间轴；
  - 多单位：分开绘制 + 不换算说明；
  - 每个点下方列出「完整时间 + 值 + 单位」；
  - 数据来源固定显示「你手动记录的进展（source = manual）」。
- 趋势页：顶部「我的数值进展」真实区（最近 3 组），下方
  「演示区（非真实数据）」保留原演示图/分数/摘要，并整体标注。
- 不计算「人生整体评分」「综合分」；只展示原始值、最近值、条数。

### 4.3 实现

- 复用 Compose `Canvas`（`ui/components/Charts.kt`），没有引入图表库；
- 单点只画点；纵轴按真实 min/max 自适应；横轴日期按真实时间比例；
- 进度历史面板使用 92% 高度 + 内部滚动，保证多张图表可访问。

详细说明见 `docs/TRENDS.md`。

### 4.4 测试

- JVM `GoalTrendsTest`：空、单点、多点排序、单位拆分、空单位、非法值过滤、范围；
- 真机 `verify_trends.py`：
  - 空趋势页；
  - A 目标 `70 kg` / `71.5 kg` 两点 + `30 min` 单点；
  - B 目标 `8 score` 单点；
  - C 目标只有文字 → 无数值空状态；
  - 历史里图表、单点说明、多单位说明、来源标注；
  - 趋势页真实区与演示区分离；
  - `NaN` / `Infinity` / `1e999` 被拒绝，数据库 5 → 5；
  - 图表数据与 `goal_checkins` 逐项一致（70/71.5/30/8）。

---

## 5. 第四部分：明确不做

| 项 | 状态 |
|---|---|
| 接入 AI 模型 | ❌ 未做 |
| 云同步 / 账号 / 后端 | ❌ 未做 |
| 财务预测引擎 | ❌ 未做 |
| 自动采集（健康/账单等） | ❌ 未做 |
| 复杂目标移动 | ❌ 未做（仅删除重建） |
| 重做全部 UI | ❌ 未做（局部改动） |
| 把静态演示数据伪装成真实统计 | ❌ 未做（演示区逐张标注） |
| 导入恢复 | ❌ 未做（明确标注） |

---

## 6. 数据与迁移

- 数据库仍为 **v2**，Gate 2C 未改表，因此没有新增迁移；
- `MIGRATION_1_2` 保留；`fallbackToDestructiveMigration()` 仍禁用；
- 导出只读事务快照，不修改数据库；
- 正式包升级前后 `journal_entries` 两行逐字段一致（§7.9）；
- QA 包独立数据库，任何清库/删除只影响 QA。

---

## 7. 真机验收

### 7.1 环境

| 项 | 值 |
|---|---|
| 设备 | `2509FPN0BC`，Android 17（API 37），HyperOS，arm64-v8a |
| 构建 | JDK 21 / Gradle 8.9 / AGP 8.7.3 / Kotlin 2.2.0 |
| 安装 | `adb install -r -t`，正式包与 QA 包并存 |
| 执行 | `DEV=... ./tools/ui-verify/run_all.sh` |

### 7.2 汇总

| 脚本 | 覆盖 | 断言 |
|---|---|---|
| `verify_settings` | 过期文案、导出入口、QA 标识、版本 | **33/33**（首轮时序失败后定向复跑） |
| `verify_diary` | 空状态 / 空正文 / 新建 / 编辑 / 重启 / 删除 | **25/25**（最后一次连续运行） |
| `verify_theme` | 三态主题 + 重启 + 跟随系统 | **11/11**（最后一次连续运行） |
| `verify_navigation` | 五页 + 设置进出 | **28/28**（最后一次连续运行） |
| `verify_goals` | 三层 / 跳级 / 进展 / 状态 / 编辑 / 删除 / 统计口径 | **64/64**（最后一次连续运行） |
| `verify_trends` | 空 / 单点 / 多点 / 多单位 / 非法值 / 演示区 | **73/73**（最后一次连续运行） |
| `verify_dates` | 日期选择 / 保存 / 重启 / UTC | **17/17**（最后一次连续运行） |
| `verify_export` | 导出提醒 / SAF / JSON 完整性 / 数据库不变 | **48/48**（最后一次连续运行） |
| `verify_prod_smoke` | 正式包旧数据 / 冷启动 / 只读导出 / 崩溃 | **46/46**（最后一次连续运行） |
| **合计** | | **345 条断言分脚本通过** |

日志：`docs/reports/logs/g2c-verify-*.log`、
`docs/reports/logs/g2c-final-regression.log`。

截图：`docs/reports/logs/g2c-shots/`。

### 7.3 旧数据不丢失

- 安装前：备份正式包 `databases/` + `files/`；
- 新 APK 安装后恢复；
- 冷启动后 `journal_entries`：
  `(6, MigrationA, alpha v1 content, 1793636289310, 1793636289310)`、
  `(7, MigrationB, beta v1 content, 1793636317600, 1793636317600)`；
- 与安装前逐字段一致；目标/进展保持 0 条；
- 正式包只读冒烟后再次比对，仍一致。

### 7.4 日期用例

`2026-11-28` 保存 → 卡片显示 → 冷启动仍显示 →
`due_date = 1795824000000`（UTC 零点）。

### 7.5 非法数值

`NaN` / `Infinity` / `1e999`：界面提示「指标数值不合法」，
面板保持打开，数据库进展条数 5 → 5。

### 7.6 导出用例

- QA：1 日记 / 1 目标 / 2 进展 → 文件 **1,420 字节** → 逐字段一致；
- 正式包：2 日记 / 0 目标 / 0 进展 → 文件可解析 → 导出前后数据库一致。

### 7.7 趋势用例

空状态、单点、两点、多单位、演示区分离、与数据库逐项一致。

### 7.8 回归

日记 CRUD、主题三态、导航、目标三层、删除影响、重启保持全部通过。

### 7.9 稳定性

- 正式包 + QA 包冷启动 `Status: ok`、`COLD`；
- 进程存活；
- crash buffer 无本应用记录。

### 7.10 最后一次连续回归结果与一次自动化时序失败

最后一次 `run_all.sh`（2026-11-03 15:47–16:12）结果：

1. `verify_diary` 25/25、`verify_theme` 11/11、`verify_navigation` 28/28、
   `verify_goals` 64/64、`verify_trends` 73/73、`verify_dates` 17/17、
   `verify_export` 48/48、`verify_prod_smoke` 46/46，
   共 **312 条断言在同一个连续运行中通过**；
2. `verify_settings` 首轮 8/32：冷启动后脚本在首页渲染完成前点击了设置按钮，
   界面仍在 DSH/系统界面，后续断言全部落空；
3. 这不是产品断言失败。脚本已加入 `wait_app_ui()`（等应用真正渲染）与
   `wait_tag("btn_settings")`，随后**定向复跑 33/33**；
4. 因此最终结论是“分脚本 345/345 通过”，并保留：
   - `g2c-verify_settings-first-attempt.log`（首轮失败，8/32）；
   - `g2c-verify_settings.log`（修复后 33/33）；
   - 其余 8 个脚本的连续运行日志。

没有把 33/33 的定向复跑混入连续运行的日志；`g2c-final-regression.log` 中可以看到
首轮失败的原始记录。`MISSING-EVIDENCE.md` 记录了“缺少一次 settings 也包含在
内的单次连续全绿运行”这一唯一缺口。

---

## 8. 产物与哈希

### 8.1 交付文件

| 文件 | 字节 | SHA-256 |
|---|---|---|
| `artifacts/mengya-life-0.5.0-g2c-debug.apk` | 11,318,375 | `53de2e8fd0977475110c9eee9e056940c55d643c3cc08d88dcb027885b6297fa` |
| `artifacts/mengya-life-0.5.0-g2c-qa-test.apk` | 11,319,353 | `dd2560c3c4c64b705e604e177b79c63f22b033f2f2c341937cada47fec822482` |

- debug APK = **真机验收所用文件**；
- QA APK = 独立 applicationId 的测试构建，仅用于破坏性用例；
- 设备副本哈希见 `docs/reports/logs/g2c-apk-hash.log`（从 `pm path` 拉回 `base.apk` 计算）。

### 8.2 为何不是「所有构建产物都一样」

- 这轮在验收前构建一次，安装同一份 `artifacts` 文件；
- 若重新执行 `assembleDebug`，APK 内的时间戳/签名块可能变化，字节与哈希会不同；
- 因此报告只把**实际装机并通过验收的那一份**标为「真机验收 APK」，
  其它构建产物必须重新计算哈希、重新安装与验收，不能混用。

---

## 9. 已知风险

1. **签名轮换**：不同环境的 debug keystore 不同，覆盖安装会失败；
   需要备份→卸载→安装→恢复→校验（本轮已执行并验证）。若用户手动卸载，数据仍会丢；
   建议后续提供「导入恢复」或把签名固定到受控 keystore。
2. **导入未实现**：导出文件目前只能人工查看/外部解析，不能在应用内恢复。
3. **导出无加密**：JSON 是明文；已提示敏感信息，但用户若保存到公共目录仍有泄露风险。
4. **单位自由文本**：`kg` / `KG` 会被当成两个单位；不做归一。
5. **趋势页只显示最近 3 组**：更多数据需进入各目标历史查看。
6. **进展不可编辑**：录错数值只能新增一条记录（本轮未做编辑）。
7. **SAF 依赖系统文件选择器**：部分定制系统可能改按钮文案；
   自动化脚本按「保存」文本查找，失败时需人工确认。
8. **QA 清理依赖 `pm clear`**：若未来 QA 包被误改为与正式包同名，会绕过隔离；
   `require_qa()` 已硬校验包名，但仍需在 CI 中保留该检查。
9. **自动化脚本的时序敏感性**：Snackbar、底部弹层动画、软键盘都可能带来偶发失败；
   本轮已通过轮询与「先关闭弹层再断言」降低概率，仍可能在慢设备上重跑。
10. **未做数据库迁移测试基建**：v2 未变，本轮未新增迁移；后续改表仍需补
    `MigrationTestHelper`。
11. **缺少一次“9 个脚本全在同一轮”的连续全绿日志**：最后一次 `run_all.sh` 中
    settings 首轮因冷启动 UI 未就绪失败，定向复跑才通过。产品断言全部通过，
    但若评审要求单次连续全绿，需要再跑一次 `run_all.sh`。

---

## 10. Gate 3 AI 顾问接入建议（不提前实现）

1. **先做导入恢复，再做 AI**：AI 建议需要稳定的数据底座；
   导入恢复应与本轮 schema 兼容，并做 ID 冲突/覆盖确认。
2. **BYOK + 本地加密存储**：密钥只存本机（EncryptedSharedPreferences/Keystore），
   不随导出、不写日志、不进崩溃上报；设置页需能一键删除密钥。
3. **显式授权**：每次发送前展示「将发送哪些内容」（日记片段/目标/进展），
   用户确认后才发；默认不带日记全文。
4. **可关闭、可离线**：无网络权限的现状应保持为默认；接入 AI 时新增网络权限
   必须单独说明，并允许用户拒绝。
5. **不做云同步**：顾问输出只落本地（可作为「建议」记录），不自动改写目标/进展。
6. **最小闭环**：选择目标 → 生成 2–3 个下一阶段行动 → 用户手动采纳为短期行动；
   不自动打「人生评分」，不把模型输出伪装成统计数据。
7. **可测试性**：为 AI 层定义接口（`AdvisorEngine`），先接一个本地假实现跑通 UI 与
   错误路径（超时、401、限流），再接真实服务；记录请求/响应摘要时脱敏。

---

## 11. 证据索引

| 内容 | 路径 |
|---|---|
| 报告 | `docs/reports/REPORT-G2C-DATA-TRENDS.md` |
| 证据包 | `docs/reports/evidence/G2C-evidence.zip` |
| APK + 哈希 | `artifacts/`、`artifacts/*.sha256` |
| 单元测试日志 | `docs/reports/logs/g2c-unit-test.log` |
| 真机日志 | `docs/reports/logs/g2c-verify-*.log`、`g2c-final-regression.log` |
| 截图 | `docs/reports/logs/g2c-shots/` |
| 导出样本 | `docs/reports/logs/g2c-export/` |
| 验收汇总 / 缺口说明 | `docs/reports/logs/g2c-acceptance-summary.md`、`MISSING-EVIDENCE.md` |
| 导出格式 | `docs/DATA-EXPORT-FORMAT.md` |
| 趋势说明 | `docs/TRENDS.md` |
| 设备 APK 哈希 | `docs/reports/logs/g2c-apk-hash.log` |
| 构建日志 | `docs/reports/logs/g2c-build.log` |

---

## 12. 复现命令

```sh
# 1) 单元测试 + 构建
. ./env.sh
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug :app:assembleQa --no-daemon

# 2) 安装（正式包与 QA 包可共存）
export DEV=<手机IP>:<无线调试端口>
adb connect "$DEV"
adb -s "$DEV" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEV" install -r -t app/build/outputs/apk/qa/app-qa.apk

# 3) 真机全量验收
./tools/ui-verify/run_all.sh
```
