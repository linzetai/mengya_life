# MengYa Life 0.25.2 — 验收报告

基线 `master/419fc111a0c2bb0db44791f5719049b6ba2b4811`。直接在 `master` 开发，无 worktree。目标版本 `0.25.2` / versionCode 37。

## Gate A–D：闭环验收

| 场景 | 状态 | 验证边界 |
|---|---|---|
| A：长期未更新目标 → 有依据建议 → 唯一顾问对话 → 可编辑计划草案 → 用户确认 → Today/Trajectory 更新 | **PASS（QA 真机 + JVM）** | 真机使用显式标记的 QA fixture 和本地离线引擎；用户在现有计划草案上追加 `_QA-edited` 后确认，QA Room 里出现 SmallPlan；Today 和 Trajectory 显示该计划，退出重启后 Today 仍可见。来源 IDs/factIds/确认前无正式写入另由 JVM 测试覆盖。 |
| B：积极趋势建议 → 用户不同意 | **PASS（QA 真机 + JVM）** | QA 离线建议引用两条同单位 trend 进展；用户说不同意后会话继续，页面没有新目标/计划草案；JVM 测试断言无正式候选与 apply。 |
| C：修改建议的原始目标来源 → 旧建议失效 | **PASS（QA 真机 + JVM）** | 在目标编辑器修改 QA 目标描述，Today 不再显示旧 Insight；继续请求计划时 Advisor 提示“原始记录或授权已变化…重新评估”，不发送旧建议、不新增 action 消息/草案。JVM 测试另外覆盖删除进展、授权变化和数据不足。 |
| 事实不足 / 诊断性标签 | **PASS（JVM）** | 不足时不请求 AI；结构化 Insight 拒绝常见诊断标签；从日记来源的自由顾问输出出现诊断术语时替换为安全拒答。 |

> 真机体验均使用 QA 包、`QA 样例` 标记的合成 Room 夹具与本地 fake engine；不代表用户真实发生过这些进展，也没有访问或修改正式包数据。fake engine 无网络请求。

## Gate E：测试与构建

| 项目 | 状态 | 结果 |
|---|---|---|
| `:app:testDebugUnitTest` | **PASS** | 400/400，0 failures / 0 errors / 0 skipped。 |
| `:app:assembleDebug` | **PASS** | BUILD SUCCESSFUL。 |
| `:app:assembleQa` | **PASS** | BUILD SUCCESSFUL。 |
| `:app:assembleQaAndroidTest` | **PASS** | Instrumented APK 已编译和打包。 |
| `:app:lintDebug` | **PASS** | 0 errors / 40 warnings。 |
| `:app:connectedQaAndroidTest` 最终重跑 | **PASS** | 最终连接设备 run 完成 21/21 tests，0 failures。包括 `PlanRecordApplyServiceTest` 的 Room 草案编辑、确认与重复应用幂等性验证。 |
| GitHub master push | **PASS** | 实现提交 `e1fb2d9cb2cc97dc1b5411e57f9bd6a4aee0950f` 已推送并 fetch 验证；本报告的状态更正作为 follow-up commit。 |

## 真机证据（QA fixture）

- 建议与 QA 标签：[01-suggestion-qa](screenshots/01-suggestion-qa.png)、[02-insight-details-qa](screenshots/02-insight-details-qa.png)
- 唯一顾问对话 / 来源回链：[03-advisor-discussion-qa](screenshots/03-advisor-discussion-qa.png)
- 草案编辑与确认：[04-plan-edited-qa](screenshots/04-plan-edited-qa.png)、[05-plan-confirmed-qa](screenshots/05-plan-confirmed-qa.png)
- Today 与轨迹：[06-today-plan-qa](screenshots/06-today-plan-qa.png)、[07-trajectory-plan-qa](screenshots/07-trajectory-plan-qa.png)、[13-plan-after-restart-qa](screenshots/13-plan-after-restart-qa.png)
- 积极趋势 / 用户不同意：[08-positive-trend-qa](screenshots/08-positive-trend-qa.png)、[09-disagreement-qa](screenshots/09-disagreement-qa.png)
- 原始来源修改及阻断：[10-stale-insight-qa](screenshots/10-stale-insight-qa.png)、[11-stale-blocked-qa](screenshots/11-stale-blocked-qa.png)、[12-source-edit-block-qa](screenshots/12-source-edit-block-qa.png)
- 视频：[QA 成长闭环 walkthrough](growth-loop-qa.mp4)、[来源修改/失效 walkthrough](source-invalidation-qa.mp4)。

## 构建产物 SHA256

| APK | SHA256 |
|---|---|
| Debug `app-debug.apk` | `5a4078b35c7d66b54c050a2b04ad2be11a7492029de8071f0b35359869e6b0a8` |
| QA `app-qa.apk` | `453fc07a77186b17042ff183b8f8565e9f3eaf925178953aa67b3a475f6ccaf6` |
| QA AndroidTest `app-qa-androidTest.apk` | `8ec2684d3f6399c25f647677434ff8709c9420e068ce5c62c3e12bdf3a918879` |

## 最终 Commit

Growth Loop 实现提交为 `e1fb2d9cb2cc97dc1b5411e57f9bd6a4aee0950f`。本验收报告的最终状态更正由 follow-up commit 记录；最终 master SHA 见交付说明及 `git rev-parse HEAD`。
