# 测试性能：旧流程 vs 新流程（TEST-PERFORMANCE-BEFORE-AFTER）

> 环境：同一台真机（2509FPN0BC / Android 17 / adb 无线调试），仓库内 Ubuntu 24.04 proot；Gradle 8.9 / JDK 21 / AGP 8.7.3。
> 所有数字来自本轮实测日志，不估算。基线 commit：`10c14d3`（Gate 3E-2.2）。

## 1. 旧流程全量（实测）

旧流程 = 构建两个 APK + 安装 + 全量 JVM + `run_all.sh` 固定 9 个真机脚本（不含 advisor/g3b1/network）。

| 阶段 | 实测 |
|---|---|
| JVM 全量（`--rerun-tasks`） | 253s |
| APK 构建（QA + debug） | 61s（增量） |
| 安装 QA + 正式 | 2s（QA 1s + 正式 <1s） |
| 真机 `run_all.sh` 9 脚本 | 2057s（34m17s，9 个脚本） |
| **合计** | **2373s（39m33s；JVM 253 + 构建 61 + 安装 2 + run_all 2057）** |
| 全 12 脚本基线（含 advisor/g3b1/network） | 3620s（60m20s；其中真机 3304s） |

七组主要回归（diary/goals/trends/navigation/settings/export/prod_smoke）单独合计：**1786s（29m46s）**。

逐脚本耗时：

| 脚本 | 耗时 |
|---|---|
| settings | 171s |
| diary | 167s |
| theme | 178s |
| navigation | 127s |
| goals | 470s |
| trends | 475s |
| dates | 93s |
| export | 195s |
| advisor | 653s（基线时脚本漂移失败；修复后重测见验收） |
| g3b1 | 492s（基线时草案 tag 漂移；修复后重测见验收） |
| advisor_network | 102s |
| prod_smoke | 181s |

## 2. 新流程典型小改动（实测）

| 场景 | 命令 | 选择 | 实测 |
|---|---|---|---|
| 仅文档 | `--changed --files README.md` | 0 模块；只做注册表校验；不构建/不安装 | 0.2s（实测） |
| 单模块 JVM | `--changed --files .../MetricFormat.kt` | trends JVM 3 类；不启动设备 | 14.8s（实测） |
| 页面 UI | `--changed --files .../DiaryScreen.kt` | diary JVM + `verify_diary` + 导航冒烟 | 337.6s（实测） |
| 周 AI | `--changed --files .../WeeklyAiParser.kt` | weekly-review/weekly-ai/ai-consent + 授权 + 周回顾 UI | 644.2s（实测，L1 冒烟） |
| 数据库（模块模式） | `--module database-migration --level integration` | 迁移 + 授权 + 设置 + 导航（不含 export） | 790.5s（实测） |

> L0 的 JVM 时间取决于 Gradle 增量缓存；首次/改源码后需要编译，热点路径远快于 `--rerun-tasks` 全量。

## 3. 节省比例

| 对比 | 旧 | 新 | 节省 |
|---|---|---|---|
| 典型 JVM 小改动 vs 全量 | 2373s（39m33s；JVM 253 + 构建 61 + 安装 2 + run_all 2057） | 14.8s（实测） | 99.4% |
| 日记页面小改动 vs 七组回归 | 1786s（29m46s） | 337.6s（实测） | 81.1% |
| 周 AI 小改动 vs 七组回归 | 1786s（29m46s） | 644.2s（实测） | 63.9% |
| 数据库变更（模块模式，不含 export） vs 旧全量 | 2373s（39m33s） | 790.5s（实测） | 66.7% |
| 文档改动 vs 全量 | 2373s（39m33s；JVM 253 + 构建 61 + 安装 2 + run_all 2057） | 0.2s（实测） | 99.99% |

## 4. 被省略的测试及依据

| 变更 | 旧流程会跑 | 新流程不跑 | 依据 |
|---|---|---|---|
| 仅 README/docs | 构建 + 安装 + 9 个真机脚本 | 全部 Android 测试 | 规则 G：文档不改变 APK |
| `WeeklyAiParser` | 7 组全量 | goals/trends/export/prod 等 | 规则 A：只选周回顾/AI/授权 + 必要导航冒烟；数据库未改，迁移不需要 |
| `DiaryScreen` | 7 组全量 | goals/trends/export/prod | 规则 I：页面 UI 只需该页面 + 导航；JVM 依赖传播覆盖日记消费方 |
| `MetricFormat` | 7 组全量 + JVM | 真机 | 纯格式化函数，模块 JVM 足够；不涉及持久化/授权 |
| 未知源码 | 可能漏跑 | 全部 + Gate | 保守策略：无法判定则全量 |

## 5. 真实耗时注意事项

- 真机脚本时间是**串行**的；设备是独占资源，并行会互相抢焦点。
- 每次真机运行都有固定开销：`pm clear`、冷启动、uiautomator dump（约 0.5–2s/次）。UI 断言越多，dump 次数越多。
- `verify_goals` 470s 是当前最慢脚本（64 断言、多次重启/展开/菜单）。
- JVM 242 个用例本身只有 1.97s，构建占 99%；因此“优化测试”应优先减少不必要的编译/安装/真机，而不是微调用例。
- 新流程的 `--changed` 在首次运行（无状态记录）时会回退到 `HEAD~1`；有过一次 verify 记录后，基线是上次 commit，覆盖更完整。
