# 测试现状审计（TEST-ARCHITECTURE-AUDIT · Gate 3F）

> 审计对象：commit `10c14d3`（Gate 3E-2.2），Android Kotlin + Compose + Room，单 `:app` 模块。
> 数据来源：仓库实际文件、`app/build/test-results/`、`docs/reports/logs/`、真机实测（见第 5 节）。
> 所有耗时均为本机（手机上的 Ubuntu proot + 同一台真机）实测，不是估算。

## 1. 测试资产清单与入口

### 1.1 JVM 测试

- 位置：`app/src/test/java/com/mengyalife/app/**`
- 运行：`./gradlew :app:testDebugUnitTest`（可加 `--tests <FQCN>` 过滤）
- 结果：`app/build/test-results/testDebugUnitTest/TEST-*.xml`
- 规模：**31 个测试类 / 244 个用例**（Gate 3F 新增 1 类 2 用例；基线 30 类 242 用例，均 0 fail/0 error）
- 辅助文件：`AdvisorFakes.kt`、`FakeGrowthSignalDao.kt`（不是测试类）
- **没有** `app/src/androidTest`、没有 Compose UI Test、没有 Espresso、没有 Robolectric、没有 Room migration 仪器测试。
- 本轮新增 `WeeklyAiFakeEngineTest`（2 用例），锁定 QA 假引擎的周回顾协议。

| 包 | 测试类 |
|---|---|
| `advisor` | AdvisorConfigTest, AdvisorContextAssemblerTest, AdvisorRequestBudgetTest, AdvisorRulesTest, AdvisorViewModelTest, AiConsentRulesTest, DiaryExtractionProtocolTest, DiaryExtractionTest, GoalProposalApplyRulesTest, GoalProposalIdsTest, GoalProposalParserTest, GrowthMemoryTest, GrowthMemoryValidityTest, InsightParserTest, InsightRulesTest, InsightServiceTest, NetworkAdvisorEngineTest, QaFaultInjectorTest, WeeklyAiTest, WeeklyAiFakeEngineTest, WeeklyReviewRulesTest, WeeklyReviewServiceTest |
| `data` | ExportTest, GoalRepositoryTest, JournalRepositoryTest |
| `domain` | GoalRulesTest, GoalTrendsTest, JournalRulesTest |
| `ui` | JournalViewModelTest |
| `util` | MetricFormatTest, TimestampsTest |

### 1.2 真机 / UI 测试

入口是 Python + `adb` + `uiautomator dump`（不是 Android 测试框架），公共库 `tools/ui-verify/harness.py`：

- 定位：Compose `testTag` → resource-id，或文本；不使用固定坐标。
- 断言：`check()` 收集、`summary()` 聚合、`sys.exit(0/1)`。
- 截图：`SHOT_DIR`（默认 `docs/reports/logs/verify-shots`）。
- 数据：`pm clear` QA 包 + `restart_app`；直接 `run-as` 拉取 SQLite 做断言。
- 旧总入口：`tools/ui-verify/run_all.sh`（固定顺序 9 个脚本；**未包含** advisor/g3b1/network 三个已存在脚本）。

| 脚本 | 覆盖 | 断言数 | 清 QA 数据 | 改数据 | 最近一次结果 |
|---|---|---|---|---|---|
| `verify_settings.py` | 设置文案、导出/备份/恢复入口、QA 标识、版本 | 40 | 是 | 否 | 最近历史38/38（新增2个入口断言待真机确认） |
| `verify_diary.py` | 空态/空正文/新建/编辑/重启/删除 | 25 | 是 | 是（日记） | 25/25 |
| `verify_theme.py` | 浅/深/跟随系统/重启保持 | 11 | 否 | 主题偏好 | 11/11 |
| `verify_navigation.py` | 五页切换、设置进出、返回、崩溃 | 28 | 否 | 否 | 28/28 |
| `verify_goals.py` | 三层目标/禁止跳级/进展/状态/编辑/删除 | 64 | 是 | 是（目标/进展） | 64/64 |
| `verify_trends.py` | 空/单点/多点/多单位/非法数值/演示区 | 73 | 是 | 是（目标/进展） | 73/73 |
| `verify_dates.py` | 截止日期选择、重启、UTC 零点 | 17 | 是 | 是（目标） | 17/17 |
| `verify_export.py` | SAF、JSON 完整性、与库一致、清理 | 49 | 是 | 是 + 写 Downloads | 49/49 |
| `verify_advisor.py` | 假引擎对话、401/429/超时/离线/取消、密钥安全 | 59 | 是 | 是（AI 配置/对话） | 59/59 |
| `verify_g3b1.py` | 授权、类别开关、撤回、上下文来源 | 47 | 是 | 是（授权/目标/日记） | 47/47 |
| `verify_advisor_network.py` | 真实网络 401（gate层，外网无效测试 Key） | 10 | 是 | 是（AI 配置） | 9/10（历史上曾失败） |
| `verify_prod_smoke.py` | 正式包旧数据/冷启动/文案/只读导出 | 50 | 否 | 只读 + 临时导出文件 | 50/50 |

> 运行时间优化：`--profile dev` 提供无设备的本地开发回路；多模块一次执行共享构建/安装并按 `(script, package)` 去重。`verify_backup_v2_full.py` 已覆盖旧基础脚本的SAF/解密/计数断言，默认不再重复跑 `verify_backup_v2.py`。详见 `TEST-RUNNER-DESIGN.md`。
>
> 本轮新增（基线之后）：`verify_weekly_ai.py`、`verify_migration.py`、`verify_qa_preflight.py`，详见 `TEST-MODULE-MAP.md`。

## 2. 各测试覆盖的业务功能

- **日记**：JVM 规则/仓库/ViewModel + 真机 CRUD/重启/删除确认。无 AI 提取的真机用例（只有 JVM `DiaryExtraction*`）。
- **目标/进展**：JVM 规则/仓库/趋势格式化 + 真机三层结构、禁止跳级、进展、状态、编辑、删除影响。
- **趋势**：JVM `GoalTrendsTest`/`MetricFormatTest` + 真机多单位、单点、空态、演示区。
- **顾问/AI**：JVM 请求预算/解析/上下文/网络错误 + 真机假引擎全状态、密钥不回显/不入库/不进日志、真实网络 401。
- **授权**：JVM 规则 + 真机同意/拒绝/类别开关/撤回/来源。
- **成长记忆/洞察**：只有 JVM；**没有真机脚本**。
- **周回顾/AI**：JVM 规则/服务/AI 协议；真机在本轮之前 **没有** 脚本（G3E2-2 报告明确 NOT VERIFIED）。
- **设置/主题**：真机文案扫描 + 主题持久化；无 JVM。
- **导出**：JVM `ExportTest` + 真机 SAF/JSON 逐项一致。
- **数据库迁移**：**没有自动化测试**，历史上靠手工升级 + 拉库核对（`docs/reports/logs/*-migration.log`）。
- **导航/启动**：真机导航 + 正式包冷启动；无 JVM。
- **隐私安全**：密钥加密/不回显/日志不含 Key（顾问脚本）、授权分类、导出提醒；正式包只读。

## 3. 测试之间的依赖

1. **正式包冒烟依赖固定数据**：正式库必须有 2 条 v1 迁移日记（MigrationA/B）、0 目标、0 进展；脚本只校验不准备。数据被改动后，冒烟会失败。
2. **正式包冒烟依赖 debuggable 正式构建**：用 `run-as` 读库；release 包无法执行。
3. **导出依赖日记/目标/进展的先验创建**；趋势依赖目标+进展；周回顾 AI 依赖授权+配置+周开关。
4. **QA 脚本之间顺序无关**（每个破坏性脚本先 `pm clear`），但**模块内**有严格顺序（创建→断言→删除）。
5. **主题脚本改变全局主题但不复位**；后续脚本不依赖主题，但截图/亮度断言可能受影响。
6. **导航脚本只读**，但会停留在某个页面；后续脚本自己 `restart_app` 或切 tab。
7. **AI 脚本共享同一个 QA AI 配置/授权**；每个都会 `pm clear` 后重建，不能并行。
8. **设备是独占资源**：任何两个 uiautomator 脚本并行都会互相抢焦点，必须串行。

## 4. QA / 正式包的数据隔离

| 维度 | 正式包 | QA 包 |
|---|---|---|
| applicationId | `com.mengyalife.app` | `com.mengyalife.app.qa` |
| 数据库 | `mengya-life.db` | `mengya-life-qa.db` |
| 设置/主题 Prefs | `mengya_settings` | `mengya_settings_qa` |
| AI 配置 Prefs | `mengya_ai` | `mengya_ai_qa` |
| 授权 Prefs | `mengya_consent` | `mengya_consent_qa` |
| Keystore alias | `mengya_ai_key` | `mengya_ai_key_qa` |
| `BuildConfig.QA_BUILD` | false | true |
| 假引擎 | 不创建（`null`） | 仅 QA 可开 |
| 破坏性测试 | 禁止 | 允许（`require_qa`） |

- 隔离由 `app/build.gradle` 的 `qa` buildType 注入，不是测试脚本“约定”。
- 正式包测试 `require_prod()` 硬校验包名；`verify_prod_smoke` 不清库、不新建/删除日记/目标、不切主题；导出只创建并删除**本次新文件**。
- 已知弱点（已修复）：旧 `run_all.sh` 不校验 APK 版本/哈希，可能测到旧安装包；旧 `verify_prod_smoke` 用“最新导出文件”兜底，极端情况下可能删掉用户已有导出。本轮改为 before/after 集合，只认本次新文件。
- `run-as` 依赖 debuggable：这是测试构建的假设，正式 release 包不满足；发布前必须明确“只读冒烟针对 debug 正式包”。

## 5. 实测耗时（基线）

### 5.1 JVM 全量

| 项 | 实测 |
|---|---|
| 命令 | `./gradlew :app:testDebugUnitTest --offline --rerun-tasks` |
| 墙钟 | **253s（4m12s，基线 242 用例）** |
| 测试执行总时长（XML 求和） | **1.97s** |
| 用例 | 242 / 30 类，0 失败 |
| 结论 | 99% 时间在 Gradle 配置/编译/KSP/打包，测试本身可忽略 |

### 5.2 真机脚本（逐项实测，QA 串行）

| 脚本 | 耗时 |
|---|---|
| verify_settings | 171s |
| verify_diary | 167s |
| verify_theme | 178s |
| verify_navigation | 127s |
| verify_goals | 470s |
| verify_trends | 475s |
| verify_dates | 93s |
| verify_export | 195s |
| verify_advisor | 653s（34/59，脚本漂移，已修复） |
| verify_g3b1 | 492s（42/47，草案 tag 漂移，已修复） |
| verify_advisor_network | 102s（10/10） |
| verify_prod_smoke | 181s |
| **真机合计** | **3304s（55m04s，含 12 个脚本）** |

> 基线中 advisor 34/59、g3b1 42/47 是**脚本漂移**导致的失败（见 §10），不是业务回归；修复后重跑：advisor 56/56（368s），g3b1 47/47（425s）。其余脚本全 PASS。

### 5.3 构建 / 安装 / 数据准备 / 清理

| 项 | 实测 |
|---|---|
| QA+正式 APK 构建（`assembleQa assembleDebug`） | 61s（增量构建 assembleQa + assembleDebug；冷构建本轮未单独测量） |
| 安装 QA（`adb install -r -t`） | 1s |
| 安装正式（`adb install -r -t`） | <1s（同一命令流） |
| 每个破坏性脚本 `pm clear` + 冷启动 | ~5–8s（含在脚本耗时内） |
| 单脚本数据准备（advisor/g3b1 配置+授权） | ~60–90s（含在脚本耗时内） |
| 清理 QA 数据 | ~1s（`pm clear`） |

### 5.4 旧流程全量

- 旧 `run_all.sh` 实际只跑 9 个脚本（settings/diary/theme/navigation/goals/trends/dates/export/prod），不含 advisor/g3b1/network；加上 JVM 全量与构建/安装，见 `TEST-PERFORMANCE-BEFORE-AFTER.md`。
- 旧流程没有逐项计时、没有 JSON、没有 SKIPPED，只有日志。

## 6. 哪些测试需要重新安装 APK

| 变更 | 是否重装 |
|---|---|
| `app/src/**`、`app/schemas/**`、Gradle/Manifest/DI | 需要：重建 QA + debug 正式包，分别 `install -r` |
| 只改 `tools/ui-verify/**`、`tests/**`、`scripts/**` | 不需要 |
| 只改 `docs/**`、README | 不需要，且不应启动设备 |
| 只改正式包数据 | 不需要重装，但会破坏正式包冒烟前置 |
| 换设备/恢复出厂 | 需要重新安装两个包 |

APK 版本：`versionCode 15`、`versionName 0.14.1-g3e2-1`（QA 追加 `-qa`），Room v11。

## 7. 重复执行 / 重复初始化 / 重复编译

1. **每个破坏性脚本都 `pm clear` 整个 QA 包**：这是模块独立性的代价，不是浪费；但也导致 10 次清库 + 冷启动 + 重新配置 AI/授权。
2. **advisor、g3b1、advisor_network 各自重建 AI 配置与授权**：~1–2 分钟/次。
3. **Gradle `--rerun-tasks` 会全量重编译**；日常 `testDebugUnitTest` 有增量缓存。旧流程没有区分“需要重编译”和“只改文档”。
4. **每次 Gate 都重装两个 APK**，即使哈希未变；旧流程没有安装哈希比对。
5. **旧 `run_all.sh` 的脚本列表与仓库脚本不一致**：advisor/g3b1/network 存在但从不被总入口执行（漏跑），同时 theme/dates 不在“七组”里却被每次执行。
6. **测试结果 XML 可能残留**：Gradle 任务 UP-TO-DATE 时不会重写；旧流程没有“每个选中类都必须有结果”的校验。

## 8. 当前是否支持按模块单独运行

- **部分支持**：每个 Python 脚本可单独运行；Gradle 可用 `--tests` 过滤。
- **不支持**：没有模块注册表、没有源码→测试映射、没有 `--changed`、没有级别约定、没有统一报告、没有缺省安全前置校验、没有“未执行=非 PASS”的机器判定。
- `run_all.sh` 是固定清单/固定顺序，无法按影响选择，也无法声明某模块“没有真机覆盖”。

## 9. 会清理数据库或删除数据的操作

| 操作 | 位置 | 范围 |
|---|---|---|
| `pm clear com.mengyalife.app.qa` | settings/diary/goals/trends/dates/export/advisor/g3b1/network/weekly_ai | 整个 QA 包数据（库、Prefs、AI 配置、授权） |
| 删除日记 | verify_diary | QA 库测试日记 |
| 删除目标/进展 | verify_goals/trends/export | QA 库测试数据 |
| 删除 AI 测试 Key | verify_advisor / network | QA Keystore + 配置 |
| 撤回授权/关闭类别 | verify_g3b1 / weekly_ai | QA 授权 Prefs |
| 写/删 Downloads 导出文件 | verify_export / prod_smoke | 仅本次新生成文件（已加 before/after 保护） |
| 切主题 | verify_theme | QA 主题 Prefs |
| 正式库 | 任何脚本 | **不写、不删、不清**（prod smoke 只读；导出临时文件会删除） |

## 10. 失败退出码与报告可靠性

- 单脚本：`sys.exit(0 if summary() else 1)`；断言失败/异常 → 非零。
- `run_all.sh`：`set -uo pipefail`，`if ... | tee | tail` 的管道状态能反映 Python 退出码，最终 `exit $fail`。**退出码本身可靠**；但：
  - **没有超时**：adb/uiautomator 卡住会无限等待；
  - 没有机器可读结果；失败只有日志尾部 60 行；
  - 没有 SKIPPED/NOT RUN 语义；
  - 脚本清单漏掉 advisor/g3b1/network；
  - 不校验安装包版本/哈希；
  - 不校验“选中的测试类是否真的执行”。
- JVM：Gradle 退出码可靠；但 XML 可能残留，需要先清结果目录并逐类核对。
- 历史证据：`verify_advisor_network` 最近一次 9/10（真实网络/TLS 不稳定），旧 Gate 脚本仍可能把它当通过（取决于是否跑）。
- 版本号硬编码：`verify_settings`/`verify_prod_smoke` 曾写死 `0.14.1-g3e2-1`，版本升级会误报；本轮改为 `MENGYA_EXPECTED_VERSION` 注入（默认仍兼容当前版本）。

## 11. 审计结论

1. 测试资产不少（242 JVM + 321 条真机断言），但**全部真机回归是固定清单**，与代码影响无关。
2. 数据隔离设计正确（QA applicationId + 独立库/Prefs/Keystore），主要风险在**执行策略**：无版本/哈希校验、无超时、无机器报告、漏跑三个脚本。
3. 数据库迁移与周回顾 AI 是最大覆盖缺口；本轮分别新增 `verify_migration.py` 与 `verify_weekly_ai.py`（QA 假引擎，真实模型仍记为 Beta 前置项）。
4. 不需要拆 Gradle 模块：`tests/test-map.json` + 统一调度器即可实现逻辑模块化，并保留全部现有脚本。
