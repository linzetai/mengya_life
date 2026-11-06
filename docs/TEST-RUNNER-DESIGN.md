# 统一测试入口设计（TEST-RUNNER-DESIGN）

## 1. 目标与边界

- 一个入口：`./scripts/verify.sh`，按**源码影响**选择 JVM 与真机测试，而不是每次跑七组回归。
- 注册表驱动：`tests/test-map.json` 是唯一权威；调度器不写死业务路径，也不由 Agent 临时决定跳过什么。
- 只做逻辑模块化：**没有**把 `app` 拆成十几个 Gradle 模块；Gradle 仍只有一个 `:app`。
- JVM 测试继续用 Gradle 自带 `--tests` 过滤，不新引入测试框架。
- 真机测试继续复用 `tools/ui-verify/*.py`（uiautomator + adb），不重写。
- 破坏性测试只允许 QA 包；正式包只读。

## 2. 命令

```sh
./scripts/verify.sh --changed                     # standard：按 Git 变更自动选择，执行要求的真机
./scripts/verify.sh --profile dev --changed       # 每轮开发快回路：静态/JVM/编译，真机延期
./scripts/verify.sh --changed --dry-run --explain # 只看计划、估算、选择和未运行原因
./scripts/verify.sh --module weekly-ai            # 显式模块（默认该模块 defaultLevel）
./scripts/verify.sh --level fast                  # 只编译 + 受影响 JVM
./scripts/verify.sh --level feature --module diary
./scripts/verify.sh --level integration --changed # 高风险变更
./scripts/verify.sh --level gate                  # 全量 JVM + QA 真机 + 正式包只读冒烟
./scripts/verify.sh --all --level release         # 发布前严格全量
./scripts/verify.sh --list-modules
```

兼容入口：`tools/ui-verify/run_all.sh` 现在转发到 `--all --level gate`，旧的单脚本运行方式仍然有效：

```sh
DEV=192.168.x.x:PORT MENGYA_PKG=com.mengyalife.app.qa \
  python3 tools/ui-verify/verify_diary.py
```

### 参数

| 参数 | 说明 |
|---|---|
| `--changed` | 读取 Git：已提交范围 + 暂存 + 未暂存 + 未跟踪 |
| `--base REF` | 指定 `--changed` 基线；默认见第 3 节 |
| `--module NAME` | 显式模块，可重复；不自动扩散到上游，显式级别决定真机范围 |
| `--level fast/feature/integration/gate/release` | 验收等级；规则可以抬高，不能被业务规则要求的级别压低 |
| `--profile standard/dev` | standard（默认）按级别完整运行；dev 延后全部真机，只跑静态/JVM或主源码编译，报告 `LOCAL PASS` 且 `fullLevelSatisfied=false`，不算Gate PASS |
| `--case ID` | 只跑注册表中标记的一个失败场景；其它选中用例列为NOT RUN，case PASS不等于完整L2 PASS |
| `--files PATH` | 仅用于选择器验收：模拟变更文件，不修改 Git 工作区 |
| `--dry-run` | 只生成计划报告，不编译、不安装、不跑测试 |
| `--explain` | 输出每个模块的选择理由与未运行原因 |
| `--all` | 强制全量：全量 JVM（不加过滤）+ 当前级别允许的全部真机 |
| `--no-device` | 跳过真机；`feature` 及以上级别将标记为不满足 |
| `--force-build` / `--no-build` | 强制重建 / 禁止构建 |
| `--keep-qa-data` | 结束后不清空 QA 包（默认清空） |
| `--online` | Gradle 允许联网（默认 `--offline`） |
| `--strict` | 把 SKIPPED 也视为级别不满足 |
| `--json` | stdout 额外输出机器可读报告 |

## 3. `--changed` 的 Git 基线

变更集是以下四部分的并集，**不是只看最近一次 commit**：

1. `git diff --name-status -M <base>...HEAD`：已提交（三点，从 merge-base 起算）；
2. `git diff --cached`：已暂存；
3. `git diff`：未暂存；
4. `git ls-files --others --exclude-standard`：新增未跟踪文件。

基线解析顺序：

1. `--base`；
2. 环境变量 `VERIFY_BASE`；
3. `git config mengya.verifyBase`；
4. `.verify/state.json` 中上一次 verify 的 `lastCommit`（必须是 HEAD 祖先）；
5. `origin/main` / `origin/master` / `main` / `master` 的 merge-base；
6. `HEAD~1`（首次运行、无远端、无状态记录时的保守回退；初始提交用空树）。

报告会写明本次使用的 base 与原因。首次运行会回退到 `HEAD~1`，因此只覆盖最近一个 commit；一旦跑过一次，后续以上次 verify 的 commit 为基线，覆盖所有已提交+未提交改动。

## 4. 选择算法（可审计）

```
变更文件
  ├─ 命中模块 sourcePaths        → 模块（直接命中）
  ├─ 命中 impactRules.patterns   → 规则模块 + 规则最低级别 + 规则真机范围
  ├─ 文档路径                    → 不选模块；不构建/不安装/不跑真机
  ├─ 测试体系路径                → test-infra（只做注册表/脚本静态校验）
  └─ 其余 app/构建路径（未知）   → 保守升级：全部模块 + L3 + 报告列出未知路径
        ↓
沿 dependsOn 反向传播一层（仅 JVM；L2+ 才跑传播模块的真机）
        ↓
级别 = max(用户级别, 规则最低级别, 无 JVM 模块的 defaultLevel)
        ↓
JVM = 所选模块 jvmTests 并集（gate/release 为全量）
真机 = 所选模块 device 中 tier 属于当前级别、且模块在规则的 deviceModules/直接命中内
```

关键约束：

- **不做关键词猜测**：只在注册表声明的路径/规则里匹配；未知源码直接 L3。
- **零测试不 PASS**：若一个被选模块既没有 JVM 也没有可执行真机（例如没有真机覆盖的 `insight` UI 改动），报告为 NOT RUN，命令非零；不会用“没跑=通过”糊弄。
- **无 JVM 的模块自动升级**：`settings`/`navigation`/`database-migration`/`app-smoke` 没有 JVM 测试，`--changed` 命中时会自动升到其 `defaultLevel`，避免 L0 空跑。
- 依赖传播只扩一层，且规则可以显式扩大；所有扩展写入报告 `selection.modules[].reasons`。

### 影响规则（摘要，完整见 `tests/test-map.json`）

| 规则 | 触发 | 选择 | 最低级别 |
|---|---|---|---|
| A-weekly | 周回顾/周 AI 源码 | weekly-review、weekly-ai、ai-consent | feature |
| B-goals-progress | 目标/进展/打卡 | goals、progress、trends、weekly-review、weekly-ai | feature |
| C-ai-consent | 授权/配置/密钥 | advisor、ai-consent、weekly-ai、insight、settings | feature（真机范围到 integration） |
| D-room-migration | Room schema/DAO/migration | 数据库模块 + 全部持久化消费方 | integration |
| E-navigation-shared-ui | 导航/共享组件/主题 | 五个一级页面 + 导航 | feature |
| F-build-manifest-di | Gradle/Manifest/Application/DI | app-smoke、navigation + 全量 JVM | integration |
| G-docs-only | 文档 | 无 | fast |
| H-test-infra | 测试脚本/注册表 | test-infra | fast |
| I-ui-screen | `ui/**` 页面 | 命中的页面模块；按模块策略判断是否另加导航冒烟 | feature |

## 5. 分层执行

| 级别 | 编译/静态 | JVM | QA 真机 | 正式包 | 迁移/隐私 |
|---|---|---|---|---|---|
| L0 fast | 注册表校验 + 依赖 Gradle 编译 | 受影响类 | 不启动设备 | — | — |
| L1 feature | 同上 | 受影响类 | 模块 feature 用例；仅当所有直接模块未声明专属UI覆盖时附加通用导航冒烟 | — | 规则 A 时含设置页 |
| L2 integration | 同上 + schema 契约 | 受影响类 | feature + integration 用例；强制含数据库/授权 | — | `verify_migration` + `verify_g3b1` + 周AI READY smoke；完整95条周AI矩阵留Gate/Release |
| L3 gate | 同上 | 全量 242+ | 全部 QA 脚本 | 只读冒烟 | 迁移 + 隐私 + 导出 |
| L4 release | 同 L3 | 全量 | 同 L3，禁止环境性跳过 | 只读冒烟 | 同 L3 |

- 静态检查包括：注册表完整性、JVM 测试类存在、真机脚本存在且具备 `require_qa/require_prod`、Room schema 编号连续且无 `fallbackToDestructiveMigration`、脚本语法。
- 周回顾真机用例在 L1/L2 以 `MENGYA_WEEKLY_SMOKE=1` 跑22条 READY 成功冒烟；Gate/Release跑完整95条状态机（超时上限由注册表的 `timeoutSec` 提到2100s）。测试模式写进选择计划和deviceSteps报告。
- `L2` 的 `alwaysModules` 固定为 `database-migration`、`ai-consent`：高风险变更不能绕开数据库与授权。

## 6. 构建与安装复用

- 需要真机时才构建 APK；L0/文档变化不构建、不安装。
- 是否需要构建：
  1. APK 不存在；
  2. `app/src`、`app/schemas`、Gradle 文件中有文件比 APK 新；
  3. 源码内容摘要与上次构建记录不一致；
  4. `--force-build`。
- 是否需要安装：拉取设备上已安装 APK 的 SHA256，与本地 APK 比对；一致则复用，不一致才 `adb install -r -t`。**从不卸载任何包。**
- 正式包安装一律 `-r`（保留数据），且永不 `pm clear`。

## 6.1 开发快回路与去重

日常改动使用显式本地 profile：

```sh
./scripts/verify.sh --profile dev --changed
# 批量改多个模块时一次选完，设备脚本按 (script, package) 去重
./scripts/verify.sh --profile dev --module advisor --module ai-consent --module settings

# R2-R7中只重跑失败场景；其他脚本明确NOT RUN，结果不是完整L2 PASS
./scripts/verify.sh --module restore-validate --level integration --case R7
./scripts/verify.sh --module backup-export --level integration --case WRITE
./scripts/verify.sh --module weekly-ai --level integration --case FAILED_401
```

- `dev` 保留相同的源码影响分析、静态校验及受影响 JVM；若模块没有 JVM 测试且变更触及 Android/Gradle 源码，则运行 `:app:compileDebugKotlin`。仅测试脚本/注册表/文档变化时跳过无关 app 编译。
- 不构建/安装 APK、不启动设备；标准 profile 下会执行的设备脚本写入 `deferredDeviceScripts`/`NOT RUN`。
- 本地通过退出码为0，但报告使用 `LOCAL PASS (device deferred)`，并保持 `fullLevelSatisfied=false`。不得用它充当 L1/L2/Gate/Release。
- `standard` 是默认且未改变的验收行为。合并前按风险运行 `--level feature`/`integration`；发版仍用 `--all --level release`。
- 一次命令传多个 `--module` 会共用构建、安装、QA前置与清理；相同 `(script, package)` 即使被多个模块引用，也只执行一次并在计划中报告折叠数量。
- 各破坏性脚本仍独立清 QA 数据，保证隔离；不为省时把故障场景共享状态串成不可复现的一条长测试。
- 注册了 `scenarios` 的失败脚本支持 `--case`：脚本为该case重新建立自己的QA基线，只跑该场景；同一selection中的其他设备脚本明确标为NOT RUN，返回 `CASE PASS (suite deferred)`而不是L2 PASS。目前覆盖恢复R2-R7与备份失败case，后续多场景脚本按同样契约逐步接入。
- 完整全字段备份 `verify_backup_v2_full.py` 已覆盖旧 count-smoke 的SAF/解密/计数，默认L1不再重复运行 `verify_backup_v2.py`；旧脚本保留手动复验。

## 7. 安全闸门

1. 调度器先跑 `verify_qa_preflight.py`：
   - applicationId 必须是 `com.mengyalife.app.qa`；
   - versionName 必须以 `-qa` 结尾；
   - 安装包必须 debuggable；
   - 设置页必须出现「QA 测试构建」「mengya-life-qa.db」「离线测试引擎（仅 QA）」。
2. `harness.require_qa()` 对每个破坏性脚本再做一次包名 + `-qa` + debuggable 校验；**不再提供正式包覆盖开关**。
3. `verify_prod_smoke.py` 使用 `require_prod()`，只读数据库；导出只创建并删除**本次新生成**的文件（用 before/after 集合判断，绝不碰用户已有导出）。
4. 正式包冒烟前额外校验版本号与前数据（2 条迁移日记、0 目标、0 进展）；不满足则 NOT RUN，不伪造 PASS。
5. 日志与报告不写入 API Key：脚本自身断言 Key 不出现在 DataStore/日志。

## 8. 报告

每次运行写入 `--report-dir`（默认 `docs/reports/test-runs/<runId>/`）：

```
report.json   # 机器读取
summary.md    # 人类摘要
logs/         # Gradle + 每个真机脚本的完整 stdout
shots/        # 真机截图（默认保留，可按需删除）
export/       # 导出样本
```

`report.json` 至少包含：git commit/branch/base/变更文件、级别/模式、设备、APK 路径/SHA256/版本/是否复用、选择模块与理由、未运行原因、规则命中、每步 PASS/FAIL/SKIPPED/NOT RUN、每模块耗时、总耗时、失败明细、日志路径、级别是否满足、退出码。

## 9. 退出码

| 码 | 含义 |
|---|---|
| 0 | 当前级别要求的所有测试都实际执行并 PASS（文档变化/无测试可跑也算满足，并在报告说明） |
| 1 | 有测试 FAIL / 构建失败 / 安装失败 / 断言失败 |
| 2 | 有要求的测试 NOT RUN（无设备、正式包前置不满足、--no-device 等）或级别不满足 |

`SKIPPED` 不会显示为 PASS；`gate/release` 不允许环境性跳过，`--strict` 会把跳过视为不满足。

## 10. 已知限制与后续项

- 没有 `androidTest`/Compose UI Test：真机层仍是 uiautomator。新增框架不在本轮范围；若要做 Room 迁移的仪器测试或 Compose 交互测试，需要额外依赖与构建时间，先单独立项。
- `growth-memory`、`insight` 没有真机脚本（JVM 覆盖）；改动这两个模块的 UI 会被标为 NOT RUN，而不是假装通过。
- 正式包冒烟依赖正式库中的验收数据（2 条 v1 迁移日记）；这是历史 Gate 的数据契约，调度器只校验、不写入。
- `verify_advisor_network.py` 需要外网；无网络时 SKIPPED（不 PASS），release 级别不允许跳过。
- 设备只支持一台；多设备时必须用 `DEV=` 指定。
