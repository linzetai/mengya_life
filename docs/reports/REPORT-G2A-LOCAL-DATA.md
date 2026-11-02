# Gate 2A 报告 · 本地数据基础与持久化

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 工程名 / 包名 | MengyaLife / `com.mengyalife.app` |
| 应用版本 | **0.3.0-g2a**（versionCode 3） |
| 轮次 | Gate 2A：Room 数据层 + 日记真实持久化 + 主题持久化 |
| 执行时间 | 2026-11-02 12:00 – 23:50（Asia/Shanghai） |
| 报告路径 | `docs/reports/REPORT-G2A-LOCAL-DATA.md` |
| 结论 | **PASS** |

---

## 1. 执行结论

**PASS**。验收标准 10 条逐条通过，证据见 §6。

一句话概括：日记从「内存里的演示列表」换成了 **Room 真实存储**，
主题从「重启即丢」换成了 **DataStore 持久化**，业务逻辑从 `MengyaLifeApp.kt`
移到了 **ViewModel + Repository**，并用 **16 个 JVM 单元测试 + 72 项真机断言**
覆盖了新建 / 重启 / 编辑 / 删除 / 空正文 / 主题 / 回归。

| 关键指标 | 值 |
|---|---|
| 构建 | `BUILD SUCCESSFUL`，exit 0，37 tasks，67 s（clean + offline + no-build-cache） |
| APK | 10,762,270 字节，SHA-256 `5eb18f17c7546889516c855cfeafb53044c62ff9c305ffe3754adede6d018001` |
| 设备内 APK | 回读哈希与交付 APK **完全一致** |
| 单元测试 | 16/16 |
| 真机断言 | 72/72（CRUD 28 + 主题 11 + 回归 28 + 中文 5） |
| 崩溃 | `logcat -b crash` 无记录 |
| 权限 | 仅 AndroidX 自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，**无 INTERNET** |

---

## 2. 完成事项与未完成事项

### 2.1 已完成（A–E 全部）

| 编号 | 要求 | 状态 |
|---|---|---|
| A | Room：Entity / DAO / Database / Repository 分层，JournalEntry 落地 | ✅ |
| A | 稳定 ID、正文、创建/更新时间、可选标题 | ✅ |
| A | 时间戳统一保存、按设备时区展示 | ✅（epoch 毫秒 + `java.time`） |
| A | DAO 支持增删改查，列表时间倒序 | ✅ |
| A | 数据库 v1 + 明确迁移策略 | ✅（`exportSchema`，禁用破坏性迁移） |
| A | DemoData 不写入用户数据库 | ✅（DemoData 已无日记数据） |
| B | 日记页用真实数据库展示 | ✅ |
| B | 新建（正文必填） | ✅ |
| B | 查看详情 / 编辑 / 删除（二次确认） | ✅ |
| B | 保存立即可见、切页/重开仍在 | ✅ |
| B | 空状态合理 | ✅ |
| B | 用户记录不被演示数据覆盖 | ✅ |
| B | AI 提取区保留占位但**不假装分析** | ✅（文案改为「本轮不会分析你的日记」） |
| C | DataStore 保存三态主题 | ✅ |
| C | 重启后仍使用上次选择 | ✅ |
| C | 「跟随系统」响应系统深浅色变化 | ✅（实测系统切浅/切深，App 跟随） |
| D | 业务逻辑不再堆进 `MengyaLifeApp.kt` | ✅（新增 `JournalViewModel` / `ThemeViewModel` / `Repository`） |
| D | Compose 只负责呈现与响应用户操作 | ✅ |
| D | 五个导航页与设置页行为不变 | ✅（回归 28/28） |
| E | UI 只做必要适配，未大规模重做 | ✅ |

### 2.2 未完成（明确不在本轮范围）

- ❌ 目标 / 趋势入库（本轮禁止开发目标管理数据库）
- ❌ AI 日记提取（禁止提前实现）
- ❌ 日记导出 / 导入（设置页仍是「后续接入」占位）
- ❌ 数据库迁移测试（v1 没有历史版本可迁移，尚无测试对象）

---

## 3. 关键技术决策

### 3.1 为什么手工依赖容器，不引 Hilt

Hilt 需要额外 KSP 处理器与插件，和本轮新引入的 Room KSP 叠加会放大编译风险；
`AppContainer` + `viewModelFactory` 已经足够，且依赖关系一眼可见。

### 3.2 为什么把「弹出层 / 草稿」放进 ViewModel

Gate 1 的做法是把状态提到 `MengyaLifeApp`。但本轮编辑面板、待删除确认都属于日记业务，
继续上提会让根组件继续膨胀。放进 `JournalViewModel` 后：
- 根组件重新变薄；
- ViewModel 是 Activity 级作用域，**进设置页再回来，面板和草稿不会丢**。

### 3.3 时间戳：统一 epoch 毫秒，展示才带时区

数据库只存 `Long`（UTC 语义），`Timestamps` 用 `ZoneId.systemDefault()` 渲染。
这样跨时区、跨夏令时都正确 —— 单元测试里专门用 `America/New_York`
验证了 2026-11-02 已进入 EST（UTC-5）这一边界。

### 3.4 数据库安全策略：宁可崩，不可清库

`Room.databaseBuilder(...)` **不调用** `fallbackToDestructiveMigration()`。
缺迁移时 Room 抛 `IllegalStateException`，用户会看到错误而不是「数据凭空消失」。
schema 用 `exportSchema = true` 导出到 `app/schemas/`，随源码提交，供后续写迁移时 diff。

### 3.5 演示数据与真实数据彻底分离

- `DemoData` 中的 `diaryEntries` / `DiaryEntry` 已**整体删除**；
- 日记页顶栏不再挂「演示数据」标签（新增 `PageHeader(showDemoTag = false)`）；
- 总览 / 目标 / 趋势 / 顾问仍是演示数据，**保留**「演示数据 · 非真实记录」标签。

> 这是本轮修掉的一个真实缺陷：第一版实现里日记页仍挂着「演示数据」标签，
> 而它展示的已经是用户的真实记录——标签含义反了。

### 3.6 可测试性修复

Compose `testTag` 依赖根节点的 `testTagsAsResourceId`；
`ModalBottomSheet` 是**独立窗口**，不继承该语义属性，导致面板内所有测试标签
在 UIAutomator 里都读不到（首轮自动化因此失败 13 项）。
已在两个底部面板的内容根节点上单独声明该语义，之后全部通过。

---

## 4. 新增依赖及兼容性验证

### 4.1 新增

| 依赖 | 版本 | 用途 | 兼容性依据 |
|---|---|---|---|
| `androidx.room:room-runtime` / `room-ktx` | 2.7.2 | ORM | android 变体 `minCompileSdk=34` ≤ 35 |
| `androidx.room:room-compiler` | 2.7.2 | KSP 生成 DAO | 与 room-runtime 同版本 |
| `com.google.devtools.ksp` | 2.2.0-2.0.2 | 符号处理 | 版本号前缀即对应 Kotlin **2.2.0**，与本工程严格同源 |
| `androidx.datastore:datastore-preferences` | 1.1.7 | 主题偏好 | `minCompileSdk=34` ≤ 35 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.1 | `viewModel()` | `minCompileSdk=35` ≤ 35 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.1 | 生命周期感知收集 | `minCompileSdk=35` ≤ 35 |
| `junit:junit` | 4.13.2 | 单元测试 | 仅 test 配置 |
| `kotlinx-coroutines-test` | 1.10.2 | 协程测试 | 仅 test 配置 |

**没有升级**：JDK 21 / Gradle 8.9 / AGP 8.7.3 / Kotlin 2.2.0 / Compose 编译器 2.2.0 /
compileSdk·targetSdk 35 / build-tools 35.0.1。

### 4.2 验证过程（不是"看着应该行"）

1. **KSP 版本选择**：Kotlin 2.2.0 对应的 KSP 只有 `2.2.0-2.0.2`（`2.2.0-2.0.1` 返回 404，
   2.2.20+ 属于更高 Kotlin）。
2. **AAR 元数据逐个核对**：下载 `room-runtime-android` / `datastore-preferences-android` /
   `lifecycle-viewmodel-compose-android` / `lifecycle-runtime-compose-android` 的
   `aar-metadata.properties`，确认 `minCompileSdk` 均 ≤ 35。
3. **仓库顺序问题（实测踩到）**：aliyun 镜像对
   `com.google.devtools.ksp.gradle.plugin:2.2.0-2.0.2` 持续返回 **502**，
   而 Gradle 对 5xx **不会降级**到下一个仓库，会直接终止解析。
   已把 `settings.gradle` 改为官方源优先、镜像兜底。
4. **构建实证**：`kspDebugKotlin` 任务成功，`app/schemas/.../1.json` 正常导出。

---

## 5. 数据表结构

### 5.1 表 `journal_entries`（v1）

```sql
CREATE TABLE IF NOT EXISTS `journal_entries` (
  `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `title`      TEXT,
  `body`       TEXT NOT NULL,
  `created_at` INTEGER NOT NULL,
  `updated_at` INTEGER NOT NULL
);
CREATE INDEX `index_journal_entries_updated_at` ON `journal_entries` (`updated_at`);
```

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | INTEGER | PK, AUTOINCREMENT | 稳定标识 |
| `title` | TEXT | 可空 | 空串一律归一为 NULL |
| `body` | TEXT | NOT NULL | 必填，写入前 trim；上限 20,000 字 |
| `created_at` | INTEGER | NOT NULL | Unix Epoch 毫秒 |
| `updated_at` | INTEGER | NOT NULL | 更新时刷新，列表按它倒序 |

### 5.2 库级信息（从设备导出实测）

```text
user_version = 1
tables: android_metadata, journal_entries, room_master_table, sqlite_sequence
indexes: index_journal_entries_updated_at
```

### 5.3 迁移策略

1. 改表必须 `version + 1` 并提供 `Migration(old, new)`；
2. 不使用破坏性迁移（缺迁移时抛异常，不清库）；
3. `exportSchema = true`，schema 随源码提交，用两份 JSON 做 diff；
4. 迁移需在真机/迁移测试中验证后才合入（Gate 2B 补 `MigrationTestHelper`）。

---

## 6. 验收逐条对照

> 所有断言均来自真实设备自动化（`adb` + UIAutomator），定位方式为
> **resource-id（Compose testTag）与文本**，不使用固定坐标。

### ① 编译、安装、冷启动、无崩溃

| 证据 | 内容 |
|---|---|
| `logs/g2a-build-final.log` | `BUILD SUCCESSFUL in 1m 7s`，`37 actionable tasks: 37 executed`，`EXIT_CODE=0` |
| `logs/g2a-install.log` | `Performing Streamed Install` → `Success`，`INSTALL_EXIT=0`；覆盖安装后 `databases/` 仍在 |
| `logs/g2a-launch.log` | `Status: ok`，`LaunchState: COLD`，`TotalTime: 440`，`START_EXIT=0` |
| 回归日志 | `PASS 无崩溃日志` |

### ② 新建一条中文日记，退出并重启后仍可查看

中文由**用户在真机键盘输入**（`adb input text` 不支持中文，系统亦无可用剪贴板命令）。

| 证据 | 内容 |
|---|---|
| `shots/09-chinese-entry.png` | 列表中出现「汕头 / 2026年11月2日 23:10 / 今天从汕头玩了两天回来，明天要上班了」 |
| `shots/10-chinese-after-restart.png` | force-stop + 冷启动后仍在 |
| `shots/11-chinese-detail.png` | 详情页完整中文正文 + 「创建于 2026年11月2日 23:10」 |
| `logs/g2a-verify-chinese.log` | `PASS 中文日记标题在列表中` / `PASS 重启后中文标题仍在` / `PASS 详情页正文完整` |
| 数据库直查（§5.2 同批导出） | `id=3 title='汕头' body='今天从汕头玩了两天回来，明天要上班了'`，`created_at=1793632227880` |

### ③ 编辑后重启，修改仍存在

| 证据 | 内容 |
|---|---|
| `logs/g2a-verify-core.log` | `PASS 标题已更新`、`PASS 列表显示已编辑标记`、`PASS 重启后编辑结果仍在` |
| `shots/05-edited.png` / `shots/06-after-edit-restart.png` | 编辑后的标题与「已编辑」标记，重启后保持 |

### ④ 删除需要确认；确认后删除，重启后仍不存在

| 证据 | 内容 |
|---|---|
| `shots/07-delete-confirm.png` | 确认弹窗「删除这条日记？」+ 删除 / 取消 |
| `logs/g2a-verify-core.log` | `PASS 取消后记录仍在`（先取消验证无副作用）→ `PASS 确认后记录消失` → `PASS 重启后测试记录不存在` |
| `shots/08-deleted.png` | 删除后的列表状态（用户原有记录未受影响） |

### ⑤ 空正文不可保存，并有明确界面反馈

| 证据 | 内容 |
|---|---|
| `shots/02-blank-body-error.png` | 正文框红色描边 + 下方红字「正文不能为空」 |
| `logs/g2a-verify-core.log` | `PASS 空正文出现明确错误`、`PASS 错误节点带测试标签`、`PASS 面板仍打开`、`PASS 取消后未产生新记录` |
| 单元测试 | `JournalRulesTest` 覆盖空串 / 纯空白 / 超长 |

### ⑥ 新安装且无用户日记时，不出现伪装成用户输入的演示记录

| 证据 | 内容 |
|---|---|
| `shots/00-empty-state-before-any-record.png` | 首次进入时显示「还没有记录 / 写下的第一条会出现在这里」，**没有任何示例记录** |
| `logs/g2a-verify-core.log`（空库轮次） | `PASS 日记页空状态`、`PASS 重启后为空状态` |
| 源码 | `DemoData.kt` 已删除 `diaryEntries` / `DiaryEntry`，日记区只剩一条说明注释 |
| 自动化 | `PASS 无演示日记标题`、`PASS 日记页无「演示数据」标签` |

### ⑦ 主题切换后重启仍生效

| 证据 | 内容 |
|---|---|
| `shots/12-theme-light.png` / `13-theme-dark.png` | 显式浅 / 深对比 |
| `shots/14-theme-dark-after-restart.png` | 重启后仍是深色 |
| `shots/16-system-light.png` / `17-system-dark.png` | 「跟随系统」下，系统切换深/浅色 App 实时跟随 |
| `logs/g2a-verify-theme.log` | 11 项全 PASS，含亮度断言：浅 244.6 / 深 26.8；系统切换 238.4 / 32.7 |

### ⑧ 五个导航页、设置入口、返回行为不回归

| 证据 | 内容 |
|---|---|
| `logs/g2a-verify-regression.log` | **28/28 PASS**：四页内容正确、顾问页可进入、四页均可进设置、设置页隐藏底栏、返回后底栏恢复 |
| `PASS 日记数据仍在（回归不破坏数据）` | 回归过程不触碰用户数据 |

### ⑨ AndroidManifest 不增加 INTERNET 权限

```text
$ aapt2 dump badging app-debug.apk | grep uses-permission
uses-permission: name='com.mengyalife.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'

$ adb shell dumpsys package com.mengyalife.app | grep -A2 "requested permissions"
requested permissions:
  com.mengyalife.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

该权限由 AndroidX 在 targetSdk≥34 时自动注入，与网络无关；**没有 INTERNET**。

### ⑩ 关键逻辑有可复现测试，不只是文字声称

| 层级 | 形式 | 结果 |
|---|---|---|
| 业务规则 / 摘要 / 长度上限 | `JournalRulesTest` | 6 用例 |
| 时区与时间戳格式 | `TimestampsTest`（含美东夏令时边界） | 2 用例 |
| 仓库行为（trim / 时间戳 / 更新 / 删除 / 排序） | `JournalRepositoryTest` + 内存 Fake DAO | 8 用例 |
| 端到端 CRUD 持久化 | 真机自动化脚本 | 28 断言 |
| 主题持久化 / 跟随系统 | 真机自动化 + 亮度断言 | 11 断言 |

命令：`./gradlew :app:testDebugUnitTest --no-daemon`（**不需要真机**，可在任何机器复现）。

---

## 7. 真机构建与测试结果

### 7.1 构建

| 阶段 | 命令 | 结果 |
|---|---|---|
| 首次冷构建（含新依赖） | `./gradlew :app:assembleDebug --no-daemon` | SUCCESS，**7m**，36 tasks |
| 最终全量重建 | `./gradlew clean :app:assembleDebug --no-daemon --offline --no-build-cache` | SUCCESS，**1m 7s**，**37 tasks 全部执行**，exit 0 |

`37` 比 `36` 多出的一个任务是 `kspDebugKotlin`（Room 注解处理）。

### 7.2 安装与启动

```text
install: Success（覆盖安装，用户数据保留）
start:   Status: ok / LaunchState: COLD / TotalTime: 440
```

安装后从设备回读 `base.apk` 比对：

```text
设备内 : 5eb18f17c7546889516c855cfeafb53044c62ff9c305ffe3754adede6d018001
交付包 : 5eb18f17c7546889516c855cfeafb53044c62ff9c305ffe3754adede6d018001
```

→ 验收所用的就是交付的这一份二进制。

### 7.3 测试汇总

| 套件 | 断言数 | 结果 |
|---|---|---|
| JVM 单元测试 | 16 | 16/16 |
| 真机 CRUD 持久化 | 28 | 28/28 |
| 真机主题持久化 | 11 | 11/11 |
| 真机回归 | 28 | 28/28 |
| 真机中文 | 5 | 5/5 |
| **合计** | **88** | **88/88** |

### 7.4 过程中修掉的问题（都留了痕迹）

| # | 问题 | 处理 |
|---|---|---|
| 1 | aliyun 对 KSP 构件 502，Gradle 不降级 → 解析失败 | 仓库改为官方源优先 |
| 2 | 日记页仍挂「演示数据」标签（真实数据被误标） | `PageHeader(showDemoTag=false)` |
| 3 | ModalBottomSheet 是独立窗口，面板内测试标签全部失效 | 面板内容根节点补 `testTagsAsResourceId` |
| 4 | 单元测试里美东时区期望值写错（误以为仍是 EDT） | 改为 EST 正确值并加注释 |
| 5 | 测试脚本自身缺陷：`%s` 被 Python 提前格式化、软键盘顶起底栏导致点击落空、首次组合慢于一次 dump | 逐个修正脚本（**非 App 缺陷**） |

---

## 8. 源码路径与变更摘要

### 8.1 新增（15 个主源码文件 + 3 个测试文件 + schema）

```text
app/src/main/java/com/mengyalife/app/
├── MengyaApplication.kt
├── di/AppContainer.kt
├── data/local/JournalEntryEntity.kt
├── data/local/JournalEntryDao.kt
├── data/local/MengyaDatabase.kt
├── data/repository/JournalRepository.kt
├── data/preferences/ThemePreferences.kt
├── domain/JournalRules.kt
├── util/Timestamps.kt
├── ui/journal/JournalContract.kt
├── ui/journal/JournalViewModel.kt
└── ui/settings/ThemeViewModel.kt

app/src/test/java/com/mengyalife/app/
├── data/JournalRepositoryTest.kt
├── domain/JournalRulesTest.kt
└── util/TimestampsTest.kt

app/schemas/com.mengyalife.app.data.local.MengyaDatabase/1.json
```

### 8.2 修改

| 文件 | 变更 |
|---|---|
| `app/build.gradle` | 加 KSP 插件与 8 个依赖；`ksp { room.schemaLocation }`；versionCode 3 / versionName `0.3.0-g2a` |
| `build.gradle` | 声明 KSP 插件版本 `2.2.0-2.0.2` |
| `settings.gradle` | 仓库顺序改为官方优先（KSP 502 问题） |
| `AndroidManifest.xml` | 注册 `android:name=".MengyaApplication"`；**未加任何权限** |
| `MengyaLifeApp.kt` | 移除日记业务状态，接入两个 ViewModel；净减约 40 行业务代码 |
| `ui/screens/DiaryScreen.kt` | 完全重写为真实数据 + 面板/详情/删除确认（391 行） |
| `ui/components/Common.kt` | `PageHeader` 增加 `showDemoTag` 参数 |
| `data/DemoData.kt` | 删除日记演示数据（232 行，原 268 行） |
| `README.md` | 更新为 Gate 2A 状态与架构说明 |

### 8.3 规模

| 项 | 数量 |
|---|---|
| 主源码 | 24 个 Kotlin 文件，3,406 行 |
| 测试源码 | 3 个文件，230 行 |
| 本轮 git 变更 | 28 个条目 |

---

## 9. 已知风险和技术债

| # | 风险 / 债 | 影响 | 建议 |
|---|---|---|---|
| 1 | **WAL 模式下主库文件很小**（4 KB），数据主要在 `-wal` | 若只拷贝 `.db` 会看到空库（本次取证时踩到） | 备份/导出必须同时处理 `-wal` / `-shm`，或先 `PRAGMA wal_checkpoint` |
| 2 | 无迁移测试 | 未来加表/改字段时，迁移错误只能在运行时暴露 | Gate 2B 引入 `MigrationTestHelper` |
| 3 | `AppContainer` 手工装配 | 模块变多后样板代码增加 | 超过 5～6 个依赖再考虑 Hilt |
| 4 | 日记无导出/备份 | 卸载即丢失 | Gate 2B 做本地导出 |
| 5 | 长正文仅前端上限（20,000 字） | 极端输入会拖慢列表 | 后续可加 DB 层约束或截断策略 |
| 6 | 列表用 `excerpt()` 在每次重组时计算 | 记录很多时有轻微开销 | 数量上到几千条再考虑缓存摘要 |
| 7 | 主题切换会导致整树重组 | 极短重绘 | 目前可接受；如需消除可加 `AnimatedContent` |
| 8 | 真机自动化脚本仍在 `/tmp` | 换会话就丢 | **Gate 2B 必须沉淀到 `tools/ui-verify/`** |
| 9 | 中文输入依赖用户手动 | 无法全自动回归 | 后续引入 instrumented Compose 测试可绕过输入法 |
| 10 | 仓库依赖官方源 | 国内网络下首次下载较慢（实测 7 分钟） | 可加本地 maven 缓存或按需切换镜像 |

---

## 10. Gate 2B 是否具备启动条件

**具备，但建议先做三件小事（约半天）再开新功能：**

1. **把自动化脚本入库**（`tools/ui-verify/`），否则本轮 88 项断言的价值会在下次会话丢失；
2. **补迁移测试骨架**（哪怕只有一条空迁移），把「改表流程」先跑通一次，
   再往里加第二张表；
3. **决定第二张表的设计边界**（目标表的层级、与日记的关联方式），
   避免目标表落地时又要改 `journal_entries`（那会触发第一次真实迁移）。

满足以上三点后，Gate 2B 可以按「目标入库 + 日记导出」推进；
AI 相关能力仍建议等到数据层稳定后再开。

---

## 附录：证据索引

| 路径 | 内容 |
|---|---|
| `docs/reports/logs/g2a-build-1.log` | 首次冷构建（含 KSP 与新依赖下载） |
| `docs/reports/logs/g2a-build-final.log` | 最终全量构建（37 tasks，exit 0，APK 哈希） |
| `docs/reports/logs/g2a-unit-test.log` | 16 个 JVM 单元测试 |
| `docs/reports/logs/g2a-verify-core.log` | 真机 CRUD / 空正文 / 持久化（28 断言） |
| `docs/reports/logs/g2a-verify-theme.log` | 主题三态 + 重启 + 跟随系统（11 断言） |
| `docs/reports/logs/g2a-verify-regression.log` | 五页与设置回归（28 断言） |
| `docs/reports/logs/g2a-verify-chinese.log` | 中文日记与重启（5 断言） |
| `docs/reports/logs/g2a-verify-all.log` | 四套件一次性汇总运行 |
| `docs/reports/logs/g2a-shots/*.png` | 18 张真机截图 |
| `docs/reports/evidence/G2A-evidence.zip` | 证据包（截图 + 日志 + 源码 + 测试 + schema） |
