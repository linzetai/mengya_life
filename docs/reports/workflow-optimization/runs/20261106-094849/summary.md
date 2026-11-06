# 测试执行报告 · 20261106-094849

- 开始：2026-11-06T09:48:49+0800
- 结束：2026-11-06T09:48:53+0800
- 总耗时：**3.8s**
- 级别：**feature**（L1 Feature）
- 执行 profile：**dev**
- 模式：module
- 结论：**LOCAL PASS (device deferred)**
- 退出码：0

## Git 与变更

- commit：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`
- branch：`master`
- 基线：`efa8607a59f38b90ac5ba615e7090fc90f7ca52a`（上一次 verify 运行记录）
- 工作区 dirty：True
- 变更文件：6

| 状态 | 文件 | 来源 |
|---|---|---|
| M | `tests/test-map.json` | unstaged |
| M | `tests/verify/cli.py` | unstaged |
| M | `tests/verify/gradle.py` | unstaged |
| M | `tests/verify/report.py` | unstaged |
| M | `tests/verify/selection.py` | unstaged |
| M | `tests/verify/validate.py` | unstaged |

## 设备与 APK

- 设备：未使用（本级别不启动设备）

## 选择与原因

- 规则 `explicit-module`：用户显式指定模块

| 模块 | 风险 | JVM 类 | 真机 | 原因 |
|---|---|---|---|---|
| navigation（导航与共享 UI） | medium | 0 | — | L1+ 必要导航冒烟（跨页面回归） |
| settings（设置与主题） | medium | 0 | verify_settings.py, verify_theme.py | 显式指定 --module settings |

## 执行结果

| 步骤 | 状态 | 耗时 | 计数 | 日志 |
|---|---|---|---|---|
| 静态检查（validate-map） | PASS | 0.0s | issues=0 |  |
| Gradle app 编译（dev profile 无 JVM 类） | PASS | 3.1s | tasks=1 | `gradle-compileDebugKotlin.log` |

## 未运行与原因

- **qa:verify_navigation.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收
- **qa:verify_settings.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收
- **qa:verify_theme.py**：--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收

## 失败与日志

- 无失败

- 报告目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-094849`
- JSON：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-094849/report.json`
- 日志目录：`/root/workspace/mengya-life/docs/reports/test-runs/20261106-094849/logs`

## 级别验收

- 满足当前级别：**否**
- 本 profile 检查满足：**是**
- 完整级别验收满足：**否**
- 存在被选择但没有任何测试执行的模块（NOT RUN）。
- dev profile 只验证本地静态/JVM/编译；所有选中真机用例延期，不能作为L1/L2/Gate PASS。
