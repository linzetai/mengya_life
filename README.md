# 萌芽人生 · Mengya Life

> Android 应用。**工程名 MengyaLife**，包名 `com.mengyalife.app`。
> 当前版本 **0.4.0-g2b**（Gate 2B：三层目标系统 + 数据库 v2 迁移）。
>
> 技术栈：Kotlin + Jetpack Compose + Material 3 + **Room** + **DataStore**，local-first。
> 本轮仍**不接 AI、不接后端、不申请网络权限**。

---

## 1. 当前状态

| 项 | 状态 |
|---|---|
| 五个一级页面（总览 / 目标 / 趋势 / 日记 / 顾问） | ✅ 沿用 Gate 1 |
| 独立设置页 + 三态主题 | ✅ 主题已改为 **DataStore 持久化** |
| 日记：Room 真实存储 / 新建 / 查看 / 编辑 / 删除确认 / 空状态 | ✅ Gate 2A |
| 目标：长期→阶段→短期行动 三层、进展记录、状态、删除影响提示 | ✅ **Gate 2B 本轮完成** |
| 总览：读取真实长期目标与最近进展 | ✅ Gate 2B |
| 数据库 v2（goals / goal_checkins）+ 真机 v1→v2 迁移验证 | ✅ Gate 2B |
| 空正文拦截（不静默失败） | ✅ 已实现并验证 |
| 主题重启后保持 | ✅ 已实现并验证（含「跟随系统」响应系统切换） |
| 演示数据与真实数据分离 | ✅ 日记页已**移除全部演示日记** |
| 单元测试（JVM，无需真机） | ✅ 50 个用例全通过 |
| 真机自动化验收 | ✅ 80 项断言，单次连续通过（见 §12） |
| Gate 0 / Gate 1 报告 | `docs/reports/REPORT-G0-Android-Build.md`、`REPORT-G1-UI-SHELL.md` |
| Gate 2A / 2B 报告 | `docs/reports/REPORT-G2A-LOCAL-DATA.md`、`REPORT-G2B-GOALS.md` |

**Gate 2B 结论：PASS**。

---

## 2. 架构分层

```text
Compose (ui/screens, ui/journal)
        │  只读状态 + 发 DiaryAction
        ▼
ViewModel (JournalViewModel / ThemeViewModel)
        │  业务规则、时间戳、错误文案
        ▼
Repository (JournalRepository)          Preferences (ThemePreferences)
        │                                  │
        ▼                                  ▼
DAO (JournalEntryDao)                  DataStore (mengya_settings)
        │
        ▼
Room Database (MengyaDatabase, v1)
```

- **唯一写日记表的地方**是 `JournalRepository`：时间戳、trim、空标题归一都在这里，
  UI 与 ViewModel 不自己造时间。
- **依赖装配**在 `MengyaApplication` → `AppContainer`（手工容器，不引 Hilt）。
- `MengyaLifeApp.kt` 只做装配与导航，本轮新增业务逻辑**没有**堆进它。

### 数据流（新建一条日记）

```text
点「保存」
  → DiaryAction.Save
  → JournalViewModel.save()   （校验正文；空则回填错误文案并中止）
  → JournalRepository.createEntry(body, title)
      · body.trim()
      · createdAt = updatedAt = now()
  → JournalEntryDao.insert()
  → Room 发出新列表 Flow
  → JournalViewModel.uiState（combine 数据库流 + 界面本地态）
  → Compose 重组，列表出现新记录
```

---

## 3. 页面与设置

五个一级页面与 Gate 1 一致（总览 / 目标 / 趋势 / 日记 / 顾问），设置页仍是二级页面、
不占底部导航位、系统返回键先关设置。

**本轮变化只集中在两处**：

1. **日记页**：从演示数据改为 Room 真实数据。列表 → 详情（半屏）→ 编辑 / 删除（二次确认）；
   空库时显示「还没有记录」，**不会预置任何看起来像用户输入的示例**。
2. **设置页外观分组**：主题选择写入 DataStore，重启后保持；
   「跟随系统」会随系统深浅色变化实时响应。

---

## 4. 目录结构（Gate 2A 新增部分）

```text
app/src/main/java/com/mengyalife/app/
├── MengyaApplication.kt          # 持有 AppContainer
├── di/AppContainer.kt            # 手工依赖容器（DB / Repository / DataStore）
├── data/
│   ├── local/
│   │   ├── JournalEntryEntity.kt # 表结构（epoch 毫秒时间戳）
│   │   ├── JournalEntryDao.kt    # 增删改查 + Flow 观察
│   │   └── MengyaDatabase.kt     # v1，exportSchema，禁用破坏性迁移
│   ├── repository/JournalRepository.kt
│   └── preferences/ThemePreferences.kt
├── domain/JournalRules.kt        # 纯函数校验（可单测）
├── util/Timestamps.kt            # epoch → 设备时区展示
├── ui/journal/
│   ├── JournalContract.kt        # DiaryUiState / DiaryAction / JournalSheet
│   └── JournalViewModel.kt       # 日记页全部状态与动作
├── ui/settings/ThemeViewModel.kt
└── ui/screens/DiaryScreen.kt     # 纯渲染：列表 + 编辑面板 + 详情 + 删除确认
app/schemas/com.mengyalife.app.data.local.MengyaDatabase/1.json   # Room 导出 schema
app/src/test/java/com/mengyalife/app/...                          # 16 个 JVM 单元测试
```

---

## 5. 版本矩阵与新增依赖

构建工具链**与 Gate 1 完全一致，未升级**：JDK 21 / Gradle 8.9 / AGP 8.7.3 / Kotlin 2.2.0 /
Compose 编译器 2.2.0 / compileSdk·targetSdk 35 / minSdk 26 / build-tools 35.0.1 / aapt2（Termux AArch64）。

本轮新增依赖（均已逐个核对 `minCompileSdk` ≤ 35 与 Kotlin 2.2.0 兼容性）：

| 依赖 | 版本 | 用途 |
|---|---|---|
| `androidx.room:room-runtime` / `room-ktx` | 2.7.2 | 本地数据库（`minCompileSdk=34`） |
| `androidx.room:room-compiler`（KSP） | 2.7.2 | 编译期生成 DAO 实现 |
| `com.google.devtools.ksp` | **2.2.0-2.0.2** | 与 Kotlin 2.2.0 严格同源 |
| `androidx.datastore:datastore-preferences` | 1.1.7 | 主题偏好（`minCompileSdk=34`） |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.1 | `viewModel()`（`minCompileSdk=35`） |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.1 | `collectAsStateWithLifecycle`（`minCompileSdk=35`） |
| `junit:junit` | 4.13.2 | JVM 单元测试 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.10.2 | 协程测试 |

> **仓库顺序变更**：Gate 2A 实测 aliyun 镜像对 `com.google.devtools.ksp` 构件路径返回 **502**，
> 而 Gradle 遇到 5xx 会**直接终止解析**（不会降级到下一个仓库），
> 因此 `settings.gradle` 改为**官方源优先、镜像兜底**。

---

## 6. 数据库

**v2**（Gate 2B）在 v1 基础上新增两张表，`journal_entries` 未改动：

```sql
-- 目标（自引用，parent_id → goals.id，级联删除）
CREATE TABLE `goals` (
  id, title, description, level, parent_id, domain, status,
  start_date, due_date, created_at, updated_at
);
-- 进展记录（goal_id → goals.id，级联删除）
CREATE TABLE `goal_checkins` (
  id, goal_id, recorded_at, note, metric_value, metric_unit, source
);
```

v1 的表结构（保持不变）：

```sql
CREATE TABLE `journal_entries` (
  `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `title`      TEXT,                    -- 可选；空串一律归一为 NULL
  `body`       TEXT NOT NULL,           -- 必填；写入前 trim
  `created_at` INTEGER NOT NULL,        -- Unix Epoch 毫秒（UTC 语义）
  `updated_at` INTEGER NOT NULL
);
CREATE INDEX `index_journal_entries_updated_at` ON `journal_entries` (`updated_at`);
```

- 版本 **v2**，`PRAGMA user_version = 2`，schema 导出在 `app/schemas/`（1.json / 2.json 均保留）。
- 迁移 `MIGRATION_1_2` 只建新表，**真机覆盖安装实测日记逐字段未变**。
- 列表固定 `ORDER BY updated_at DESC, id DESC`。
- **不使用** `fallbackToDestructiveMigration()`：缺迁移时 Room 抛异常而不是清库。
- 迁移策略：改表必须 version+1 并提供 `Migration`，对照 `app/schemas/` 两份 JSON 写，
  且在真机验证过再合入。

---

## 7. 环境事实（本机实测）

| 项 | 值 |
|---|---|
| 设备 | `2509FPN0BC`，Android 17（API 37），HyperOS `V816`，`arm64-v8a` |
| 运行时 | ZeroTermux + proot Ubuntu 24.04.5 LTS |
| JDK | OpenJDK 21.0.13（Termux aarch64） |
| SDK | `$PREFIX/opt/android-sdk`，platform android-35，build-tools 35.0.1 |
| aapt2 | `$PREFIX/bin/aapt2`（AArch64；SDK 内置为 x86-64，必须覆盖） |

---

## 8. 环境配置

```sh
export PREFIX=/data/data/com.termux/files/usr   # proot 容器里通常为空
. ./env.sh
```

`env.sh` 由 `$PREFIX` 推导 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME`，
并通过 `GRADLE_OPTS` 注入 aapt2 覆盖项；**仓库里没有任何设备绝对路径**。

---

## 9. 构建

```sh
./gradlew :app:assembleDebug --no-daemon           # 构建
./gradlew :app:testDebugUnitTest --no-daemon       # 单元测试（不需要真机）
```

本机实测：首次冷构建约 7 分钟（含 KSP 与新依赖下载），
`clean` + `--offline` + `--no-build-cache` 全量重建约 **67 秒**（37 tasks）。

---

## 10. 安装与运行

```sh
export PATH=/data/data/com.termux/files/usr/bin:$PATH
export HOME=/data/data/com.termux/files/home
adb connect <手机IP>:<无线调试端口>

adb shell input keyevent KEYCODE_WAKEUP      # HyperOS 锁屏时会拒绝安装
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.mengyalife.app/.MainActivity
```

---

## 11. 安全与范围

- **无网络能力**：`AndroidManifest.xml` 不申请 `INTERNET`；实际权限只有 AndroidX
  自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
- 不接 AI、不接后端、不收 API Key；设置页的 AI 分组仍是「后续接入」占位。
- 不引入云同步 / 账号 / 支付。
- 仓库不含密钥；不含设备绝对路径。

---

## 12. 已验证结果（Gate 2A）

| 验证 | 方式 | 结果 |
|---|---|---|
| 规则 / 时间戳 / 仓库 / ViewModel | JVM 单元测试 | **50/50** |
| 目标三层 + 层级合法性 + 进展 + 状态 + 编辑 + 删除影响 | 真机自动化 | 见下 |
| 日记 CRUD / 主题三态 / 导航回归 | 真机自动化 | 见下 |
| **单次连续完整回归** | 真机自动化 | **80/80** |
| 数据库内容 | 导出 `.db` + `sqlite3` 查询 | 见报告 §6 |

APK：`11,088,320` 字节，
SHA-256 `a367460605649d6ca052fc6e6476d590ce87f3f8622611b18c7809d7d91f4907`
（= 真机验收所用二进制；clean 重建产物字节不同，见 Gate 2B 报告 §7.1）。

---

## 13. 已知限制

1. **日记与目标只在本机**：没有导出/备份，卸载应用即丢失。
2. **顾问仍是演示**：发送不调用任何模型；日记的「AI 提取」区是占位，不会分析内容。
3. **趋势页仍是静态演示数据**；总览的曲线与洞察也是，已逐张标注「演示」。
4. **目标不能跨层级移动**；建错只能删除重建（环检测已就绪，留待后续）。
5. **没有回收站**：目标删除为硬删除，但有影响范围提示 + 二次确认。
4. **长正文上限 20,000 字**（软护栏），超出会给出明确错误。
5. **未做迁移测试**：v1 是首个版本，尚无历史迁移可测；规则已写入代码注释与报告。
6. **主题切换后界面会瞬时重建**，极短时间内可能看到一次重绘。

---

## 14. 下一步（Gate 2B 候选）

- Room 迁移测试基建（`MigrationTestHelper`），把 v1→v2 变成可自动重跑的用例。
- 目标进展可视化（数值目标的历史折线）与「目标移动」。
- 日记 / 目标导出（注意 WAL：必须同时处理 `-wal`/`-shm` 或先 checkpoint）。
- `tools/ui-verify/run_all.sh` 已入库，扩成「构建 → 单测 → 装机 → 回归」一条链。
