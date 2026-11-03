# 萌芽人生 · Mengya Life

> Android 应用。**工程名 MengyaLife**，包名 `com.mengyalife.app`。
> 当前版本 **0.5.0-g2c**（Gate 2C：遗留问题修复 + 本地数据导出 + 真实进展趋势）。
>
> 技术栈：Kotlin + Jetpack Compose + Material 3 + **Room** + **DataStore**，local-first。
> 本轮仍**不接 AI、不接后端、不申请网络权限**。

---

## 1. 当前状态

| 项 | 状态 |
|---|---|
| 五个一级页面（总览 / 目标 / 趋势 / 日记 / 顾问） | ✅ 沿用 Gate 1 |
| 独立设置页 + 三态主题（DataStore 持久化） | ✅ 重启保持；「跟随系统」随系统变化 |
| 日记：Room 存储 / 新建 / 查看 / 编辑 / 删除确认 / 空状态 | ✅ Gate 2A |
| 目标：长期 → 阶段 → 短期行动 三层、进展、状态、删除影响提示 | ✅ Gate 2B |
| 总览：真实长期目标 + 明确「进行中」统计口径 | ✅ **Gate 2C 修正** |
| 设置页「导出我的数据」：JSON + SAF + 敏感信息提醒 + 回读校验 | ✅ **Gate 2C 新增** |
| 真实数值趋势：`goal_checkins` → Canvas 折线/单点/空状态 | ✅ **Gate 2C 新增** |
| 非法指标值（NaN / Infinity / 超范围）仓库层拒绝 | ✅ **Gate 2C 新增** |
| 日期选择器保存 / 重启展示（UTC 零点语义） | ✅ **Gate 2C 验证** |
| QA 测试构建（独立 applicationId + 独立数据库） | ✅ **Gate 2C 新增** |
| 导入恢复 | ⏳ 未实现；界面明确标注，留待后续独立验收 |
| 单元测试（JVM，无需真机） | ✅ 88 个用例全通过 |
| 真机自动化验收 | ✅ 分脚本 345 条通过；settings 定向复跑（见报告 §7.10） |
| Gate 报告 | `docs/reports/REPORT-G2C-DATA-TRENDS.md` |
| 导出格式 / 趋势说明 | `docs/DATA-EXPORT-FORMAT.md`、`docs/TRENDS.md` |

**Gate 2C 状态：功能与单元测试完成；真机脚本分脚本累计 345 条断言通过。
最后一次连续运行中 8 个脚本全绿，settings 首轮为冷启动自动化时序问题、
修复后定向复跑 33/33；唯一缺口见 `docs/reports/logs/MISSING-EVIDENCE.md`。**

---

## 2. 架构分层

```text
Compose (ui/screens, ui/journal, ui/goals)
        │  只读状态 + 发 Action
        ▼
ViewModel (Journal / Goals / Theme / Export)
        │  业务规则、时间戳、错误文案、SAF 写入与校验
        ▼
Repository (Journal / Goal / DataExport)      Preferences (ThemePreferences)
        │                                        │
        ▼                                        ▼
DAO (JournalEntryDao / GoalDao)              DataStore (BuildConfig.PREFS_NAME)
        │
        ▼
Room Database (MengyaDatabase, v2, WAL)
```

- 写日记只经 `JournalRepository`；写目标/进展只经 `GoalRepository`；
- 导出只经 `DataExportRepository`：在同一事务里读三张表；
- 依赖装配在 `MengyaApplication` → `AppContainer`（手工容器，不引 Hilt）；
- `MengyaLifeApp.kt` 只做装配与导航。

### 数据流（导出）

```text
设置 → 导出我的数据
  → ExportViewModel.requestExport()
  → DataExportRepository.buildBundle()
      · RoomDatabase.withTransaction { 日记 + 目标 + 进展 }
  → 敏感信息提醒（条数 + 不上传说明）
  → CreateDocument(SAF) 选择保存位置
  → 写 JSON（UTF-8）
  → 从同一 URI 回读 → ExportJson.verify(文本, 快照)
  → Snackbar「导出完成并已校验：日记 N / 目标 N / 进展 N」
```

---

## 3. 本地数据导出（Gate 2C）

- 入口：设置 → 数据与隐私 → **导出我的数据**。
- 使用 `ActivityResultContracts.CreateDocument("application/json")`，
  **不申请存储权限**，不写死 Downloads 路径。
- 导出前明确提醒：文件可能包含**日记正文等敏感个人信息**，请妥善保管；
  应用不会上传、不会自动分享。
- 导出范围：日记、三层目标（含 `parentId`）、进展记录、所有关联 id、时间戳、
  导出格式版本、数据库结构版本、应用版本。
- 一致性：**不复制 `.db`**（WAL 模式下不安全），而是在 Room 事务里查询三张表。
- 写后回读校验：结构、计数、父子关系、进展→目标关联，全部通过才报成功。
- 不含 API Key / 账号 / 设备标识 / 设置项。
- **导入恢复尚未实现**，界面写的是「本轮只支持导出；导入恢复尚未实现」，
  不暗示可以还原。

格式细节见 `docs/DATA-EXPORT-FORMAT.md`。

---

## 4. 真实进展趋势（Gate 2C）

- 数据源：`goal_checkins` 表，只取**用户手动录入**的 `metric_value`（`source=manual`）。
- 横轴：真实记录时间；纵轴：真实指标值。
- 按「目标 + 单位」分组；**不同单位不混画、不换算**。
- 0 条数值 → 空状态；1 条 → 单点 + 「无法判断趋势」说明；≥2 条 → 折线 + 数据点。
- 显示单位、数据点、对应时间；下方列出「时间 + 值 + 单位」明细。
- 不根据少量记录给「人生整体评分」。
- 目标进展历史与趋势页都展示；趋势页保留演示区，并整体标注「演示区（非真实数据）」。
- 图表复用项目内 Canvas（`ui/components/Charts.kt`），不引入图表库。

细节见 `docs/TRENDS.md`。

---

## 5. 数据库

**v2**（Gate 2B 引入，Gate 2C 未改表）：

```sql
CREATE TABLE `journal_entries` (
  id, title, body, created_at, updated_at
);
CREATE TABLE `goals` (
  id, title, description, level, parent_id, domain, status,
  start_date, due_date, created_at, updated_at
);
CREATE TABLE `goal_checkins` (
  id, goal_id, recorded_at, note, metric_value, metric_unit, source
);
```

- `PRAGMA user_version = 2`；schema 导出在 `app/schemas/`（1.json / 2.json 均保留）。
- 迁移 `MIGRATION_1_2` 只新增表，不触碰 `journal_entries`。
- **不使用** `fallbackToDestructiveMigration()`：缺迁移时抛异常而不是清库。
- 列表/趋势查询固定排序，保证同一份数据的展示顺序稳定。
- 导出读取用事务快照，不直接复制 WAL 模式下的 `.db` 文件。

---

## 6. 测试构建与数据隔离（Gate 2C）

真机破坏性测试**不再碰日常使用的正式包**。`app/build.gradle` 新增 `qa` build type：

| 项 | 正式包 | QA 包 |
|---|---|---|
| applicationId | `com.mengyalife.app` | `com.mengyalife.app.qa` |
| 应用名 | 萌芽人生 | 萌芽人生 QA |
| versionName | `0.5.0-g2c` | `0.5.0-g2c-qa` |
| Room 数据库 | `mengya-life.db` | `mengya-life-qa.db` |
| DataStore | `mengya_settings` | `mengya_settings_qa` |
| 设置页标识 | 无 | 顶部显示 QA 隔离说明 |

```sh
./gradlew :app:assembleDebug   # 正式 APK
./gradlew :app:assembleQa      # 测试 APK（可单独安装）
```

测试脚本 `tools/ui-verify/*.py` 默认只跑 QA 包：
`MENGYA_PKG` 不是 QA 包时，`require_qa()` 会直接退出；
正式包只在 `verify_prod_smoke.py` 里以**只读**方式验证（不清数据、不新建记录、不切主题）。

---

## 7. 环境事实（本轮实测）

| 项 | 值 |
|---|---|
| 设备 | `2509FPN0BC`，Android 17（API 37），HyperOS，`arm64-v8a` |
| 运行时 | DSH App 内置 proot Ubuntu 24.04 |
| JDK | OpenJDK 21 |
| SDK | `/opt/android-sdk`，platform android-35，build-tools 35.0.1 |
| aapt2 | `/opt/android-aapt2/aapt2`（AArch64，通过 `env.sh` 注入） |
| adb | Ubuntu `adb 1.0.41 / 34.0.4`，无线调试连接 |

`env.sh` 现在支持两种环境：

```sh
. ./env.sh    # PREFIX 存在（Termux）或自动复用 /opt/android-sdk/env.sh
```

---

## 8. 构建与安装

```sh
. ./env.sh
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug :app:assembleQa --no-daemon
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk   # 交付 / 真机验收
app/build/outputs/apk/qa/app-qa.apk         # 测试专用（隔离数据）
```

安装：

```sh
export DEV=<手机IP>:<无线调试端口>
adb connect "$DEV"
adb -s "$DEV" install -r -t app-debug.apk
adb -s "$DEV" install -r -t app-qa.apk
```

> **签名注意**：调试签名（`~/.android/debug.keystore`）在不同构建环境之间可能不同。
> 若系统报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，说明签名变了，
> **不要**直接卸载后不管数据；先做完整备份（数据库 + DataStore），
> 安装新包后再恢复并逐字段校验。本轮真机验收即按此流程完成，旧日记未丢失。

---

## 9. 安全与范围

- **无网络能力**：`AndroidManifest.xml` 不申请 `INTERNET`；实际权限只有 AndroidX
  自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
- 不接 AI、不接后端、不收 API Key；设置页 AI 分组仍是「后续接入」占位。
- 不引入云同步 / 账号 / 支付。
- 导出只写用户通过 SAF 选择的位置；没有自动上传 / 自动分享。
- 仓库不含密钥；不含设备绝对路径。

---

## 10. 已验证结果

| 验证 | 方式 | 结果 |
|---|---|---|
| 规则 / 时间戳 / 仓库 / ViewModel / 导出 / 趋势分组 | JVM 单元测试 | **88/88** |
| 目标三层 + 统计口径 + 删除影响 | 真机（QA） | G2C 回归 |
| 日记 CRUD / 主题三态 / 导航回归 | 真机（QA） | G2C 回归 |
| 导出完整性与逐项校验 | 真机（QA + 正式包） | G2C 回归 |
| 真实趋势（空 / 单点 / 多点 / 多单位） | 真机（QA） | G2C 回归 |
| 非法指标值 | 单元测试 + 真机（QA） | 拒绝且不写库 |
| 日期选择器保存 / 重启展示 | 真机（QA） | G2C 回归 |
| 旧日记 / 目标不丢失 | 正式包升级 + 数据库逐字段比对 | G2C 回归 |
| 冷启动无崩溃 | 正式包 + QA 包 | G2C 回归 |
| 总断言数 | 9 个真机脚本 | **345 条**（分脚本；settings 定向复跑） |

> 逐项断言数、APK 字节数与 SHA-256 见 `docs/reports/REPORT-G2C-DATA-TRENDS.md`；
> settings 首轮时序失败的原始日志与最终结论见
> `docs/reports/logs/MISSING-EVIDENCE.md`。

---

## 11. 已知限制

1. **导入恢复未实现**：导出文件目前只能被校验/查看，不能导回应用。
2. **顾问仍是演示**：发送不调用任何模型；日记的「AI 提取」区是占位。
3. **总览的「人生方向 / 成长趋势预览 / 近期洞察」仍是演示内容**，逐张标注；
   趋势页的演示区同理；真实数据只在目标、日记、进展与趋势真实区。
4. **目标不能跨层级移动**；建错只能删除重建（环检测已就绪）。
5. **没有回收站**：目标删除为硬删除，但有影响范围提示 + 二次确认。
6. **进展不能编辑/补录数值**，单位是自由文本（`kg` 与 `KG` 视为不同单位）。
7. **长正文上限 20,000 字**（软护栏）。
8. **调试签名轮换**会让覆盖安装失败，需要备份/恢复流程（见 §8）。

---

## 12. 下一步（Gate 3 候选）

- **导入恢复**（与导出共用 schema，做 ID 冲突策略与二次确认）作为独立验收。
- **AI 顾问接入**：BYOK、本地存储密钥、请求前显式确认、可关闭、不自动上传日记；
  建议先做「用户选中一段日记/目标 → 生成拆解建议」的最小闭环，不接云同步。
- 迁移测试基建（`MigrationTestHelper`）与更多导出格式版本测试。
- 目标跨层级移动、进展编辑与单位归一。
