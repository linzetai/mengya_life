# 测试体系验收（TEST-ACCEPTANCE · Gate 3F）

> 验收对象：`scripts/verify.sh` + `tests/test-map.json` + 新增真机用例。
> 设备：2509FPN0BC / Android 17 / `192.168.10.183:33537`。
> 所有选择结果来自实际命令输出；命令均带 `--dry-run` 的样本不会启动设备。

## 0. 验收结论

| # | 样本 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| 1 | 只改 WeeklyAiParser | 周 AI / 周回顾 / 授权；不全跑七组 | 6 模块 / 19 JVM / 3 真机（周回顾冒烟、设置、导航） | 通过（`--dry-run` 计划） |
| 2 | 只改 DiaryScreen | 日记相关 | 8 模块 / 19 JVM / 2 真机（日记、导航） | 通过（`--dry-run` 计划） |
| 3 | 改 Room Migration | 数据/迁移高风险 | 14 模块 / 31 JVM（全量）/ 5 真机 | 通过（`--dry-run` 计划） |
| 4 | 只改 README | 不安装 APK、不跑真机 | 0 模块 / 0.2s / 无构建与安装 | 通过（实际执行） |
| 5 | 未知源码路径 | 保守升级并说明 | 16 模块 / 全量 JVM / 全部真机 + 正式包冒烟 | 通过（保守升级） |
| 6 | 注入一个失败测试 | 非零退出、报告不得整体 PASS | rc=1 / 21.4s / 报告 FAIL（JVM 失败探针） | 通过（非零退出，整体不显示 PASS） |

## 1. 样本 1：WeeklyAiParser

```sh
./scripts/verify.sh --changed \
  --files app/src/main/java/com/mengyalife/app/domain/weekly/WeeklyAiParser.kt \
  --dry-run --explain
```

实际选择：`weekly-review`、`weekly-ai`、`ai-consent`、`advisor`、`insight`、`navigation`；JVM 19 类；真机 `verify_weekly_ai.py`(L1 冒烟) + `verify_settings.py` + `verify_navigation.py`。

实际执行（非 dry-run）：**PASS**，总耗时 **644.2s**；周回顾冒烟 308.3s、设置 170.0s、导航 124.8s；未跑 goals/trends/export/prod。报告 `docs/reports/G3F-acceptance/weekly-l1/report.json`。

依据：规则 `A-weekly`。没有选择 goals/trends/export/prod_smoke，因为数据库与 UI 页面未变；导航冒烟仍保留（跨页面入口）。

## 2. 样本 2：DiaryScreen

```sh
./scripts/verify.sh --changed \
  --files app/src/main/java/com/mengyalife/app/ui/screens/DiaryScreen.kt \
  --dry-run --explain
```

实际选择：`diary`（直接命中）+ 依赖传播的 `weekly-review`、`weekly-ai`、`insight`、`export`、`growth-memory`、`trends` + `navigation`；真机只跑 `verify_diary.py` + `verify_navigation.py`。

实际执行：**PASS**，总耗时 **337.6s**（日记 161.3s、导航 123.2s、构建 11.8s、JVM 10.2s）；报告 `docs/reports/G3F-acceptance/diary-l1/report.json`。

依据：规则 `I-ui-screen` + 源码路径命中 `diary`；依赖传播把日记消费方（weekly-review/weekly-ai/insight/export/growth-memory/trends）的 **JVM** 纳入。真机只跑日记 + 导航。

## 3. 样本 3：Room Migration

```sh
./scripts/verify.sh --changed \
  --files app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt \
  --dry-run --explain
```

实际选择：`database-migration` + 全部持久化消费方；真机 `verify_migration.py`、`verify_export.py`、`verify_g3b1.py`、`verify_settings.py`、`verify_navigation.py`；正式包冒烟仍留到 Gate。

依据：规则 `D-room-migration` 强制 L2；选择全部持久化消费方，真机选择迁移、导出、AI 授权/设置、导航（不跑 goals/trends 的全量 UI，避免把 L2 变成 L3）。

补充实际执行：用 `--module database-migration --level integration` 跑同级别模块模式，**PASS**，总耗时 **790.5s**（迁移 29.2s、授权 426.4s、导航 124.0s、设置 170.3s、JVM 6.9s）。模块模式不会自动加入 `export`；导出已在基线单独 PASS（195s），Gate 会同时包含两者。报告 `docs/reports/G3F-acceptance/db-l2/report.json`。

## 4. 样本 4：README

```sh
./scripts/verify.sh --changed --files README.md
```

实际：`静态检查 PASS (0.0s)` + `JVM SKIPPED`；总耗时 0.2s；未调用 Gradle 构建、未安装 APK、未启动设备；结论 `PASS (docs-only)`。

依据：规则 `G-docs-only`；不构建 APK、不启动设备；只跑注册表静态校验。

## 5. 样本 5：未知源码

```sh
./scripts/verify.sh --changed \
  --files app/src/main/java/com/mengyalife/app/newthing/Foo.kt \
  --dry-run --explain
```

实际：未知路径列入 `unknownFiles`，警告“存在无法映射的路径，按保守策略升级到 L3”；报告 `notRun` 为空，全部模块+真机。

依据：`unknownChangePolicy` 强制 L3 全量；报告列出未知路径并给出警告。

## 6. 样本 6：失败必须非零

注入一个临时失败测试（随后删除，不提交）：

```kotlin
// app/src/test/java/com/mengyalife/app/advisor/GateFailureProbeTest.kt
class GateFailureProbeTest {
    @Test fun probe() = org.junit.Assert.assertEquals(1, 2)
}
```

执行：

```sh
./scripts/verify.sh --all --level fast
echo $?
```

实际：`rc=1`，总耗时 21.4s；`summary.verdict=FAIL`、`exitCode=1`；失败明细为 `GateFailureProbeTest.probe: expected:<1> but was:<2>`；`summary.md` 不出现整体 PASS。删除探针后重跑：`rc=0`、21.3s、全量 JVM PASS。报告：`docs/reports/test-runs/20261105-041250/report.json`。

期望：退出码 ≠ 0；`report.json` 的 `summary.verdict=FAIL`、`summary.fail>=1`；`summary.md` 不出现整体 PASS。探针删除后重跑恢复 PASS。

## 7. 新增真机用例实测

| 用例 | 断言 | 结果 | 耗时 |
|---|---|---|---|
| `verify_weekly_ai.py` | 95（完整）/ 22（L1 冒烟） | PASS | 1160s（完整）；L1 冒烟 308s |
| `verify_migration.py` | 17 | PASS | 28s（runner）/ 35s（直接运行） |
| `verify_qa_preflight.py` | 10 | PASS | 28.6s |

Weekly AI 用例覆盖：

- 成功 READY + factIds 构造 sources；
- 401 失败 + 超时；
- 请求中断后的 generating 恢复、额度不重复；
- READY → STALE（数据变化）；
- 修改类别、撤回授权；
- 自动开关关闭后后台 Worker 停止发送（含正/负对照）。

**未覆盖 / 不算通过**：真实第三方模型成功请求仍是 Beta 前置项；本轮只用 QA 假引擎，报告不会把 Fake 当真实模型。

## 8. 验收环境与证据

- 每次运行的 `report.json` / `summary.md` / 日志：`docs/reports/test-runs/<runId>/`；最终验收运行另存 `docs/reports/G3F-acceptance/`。
- APK SHA256、设备型号、commit、变更文件、每模块耗时见报告。
- 失败注入的日志保留在验收报告目录，探针源码不提交。
