# Gate 3D-1 报告 · 成长洞察 V1 + G3C2 遗留修复

| 项 | 值 |
|---|---|
| 版本 | **0.11.0-g3d1**（versionCode 11） |
| 数据库 | Room **v8**（v7→v8 非破坏迁移） |
| 设备 | Xiaomi 2509FPN0BC / Android 17 |
| 结论 | **PASS（核心）**；未验证项见 §6 |
| 时间 | 2026-11-04（Asia/Shanghai） |

---

## 1. 完成状态

G3C2 的 A1/A2/A3/A4 优先完成；成长洞察 B/C 的主链路完成并经真实模型验证。

### A. 遗留修复

| 项 | 标记 | 证据 |
|---|---|---|
| A1 旧记忆在编辑后立即失效 | **PASS** | 日记写入链路触发 memory rebuild；AdvisorDataRepository 用 GrowthMemoryValidity 二次过滤；JVM + 真机来源删除 |
| A2 请求前绑定 runtime/endpoint/recipient/consent + 日记版本 | **PASS** | `preRequestProblem`；配置变化/日记编辑 JVM 测试 engine.calls=0 |
| A3 可控交错测试 | **PASS** | 配置变化、日记编辑、授权撤回、旧结果、插入失败回滚 |
| A4 网络字节限制说明 | **PASS** | 仅保证请求前 gate/进行中取消/结果隔离；不宣称撤回已发字节 |

A1 两层：

1. `JournalRepository.update/delete` 事务后触发 `GrowthMemoryService.rebuildAll`，旧信号 STALE、记忆更新/STALE；
2. `AdvisorDataRepository` 即使重建失败，也用 `GrowthMemoryValidity` 校验每条 memory 的 signal 当前版本/状态与来源日记；不通过则不进入 AI 上下文。

A2 细节：

- 捕获 `boundConfig/boundRuntime/boundConsent`；
- gate 后再次校验 config endpoint、runtime endpoint、consent epoch/recipient/JOURNAL、日记 title+body hash；
- 引擎只使用 boundRuntime；请求后再次校验 data/consent，结果隔离。

---

## 2. 成长洞察

### 数据与规则

- `GrowthInsight`/`InsightReport`：POSITIVE/RISK/SUGGESTION、来源、数据区间、事实/推断、置信度/信息不足、生成时间、dataVersion；
- 输入严格受 GOALS/PROGRESS/JOURNAL；
- 规则先算：目标数量/截止日期、最近 30 天进展、同目标同单位数值序列、不同单位分开、信息不足；
- 不跨单位相加/换算；
- dataVersion 覆盖目标/进展/记忆/类别，数据变化即失效。

### AI

- 用户点击才调用；AI 只解释 facts、提建议；
- 严格 JSON；来源 ID 必须属于 allowedSources；`CHECKIN:7` 归一化后校验；
- 失败保留 facts + FAILED，不展示 AI insights；
- 报告缓存 `insight_reports`，同 dataVersion 打开不重复调用；旧版本不展示。

### UI

- 总览「最近值得关注」最多 3 卡（做得好/需关注/下一步建议）；
- 详情展示数据区间、趋势变化、为什么判断、来源、事实/推测；
- 目标/趋势入口；建议可和顾问讨论/整理草案（代码接线，未逐一点击真机）；
- 关闭 JOURNAL 后旧洞察不展示；
- 成长记忆页状态栏 inset 修复。

---

## 3. 真实模型验证

DeepSeek `deepseek-flash`，数据版本 `4aef…`：

- 1 个进行中 SavingsGoal，3 条 CNY 进展（1000→1500→2000）；
- 模型输出 3 条：POSITIVE 一条 + SUGGESTION 两条；
- 来源仅 GOAL #27 / CHECKIN #7-9，全部在 allowedSources；
- 同单位 CNY；摘要明确“无法判断目标是否达成”；
- UI 截图 `g3d1-insight-overview-7.png`、`g3d1-insight-detail.png`。

失败重试保留规则 facts + FAILED，不把失败/截断/前缀 ID 输出冒充成功。详见 `g3d1-real-insight.log`。

---

## 4. 测试

| 项 | 结果 |
|---|---|
| JVM | **207/207**（包含原 191 + 16 新增） |
| 日记 / 目标 / 趋势 / 导航 / 设置 / 导出 / 正式包 | 25 / 64 / 73 / 28 / 38 / 49 / 50 |
| v7→v8 迁移 | PASS |
| 真机 A/B/C | PASS（见 device log） |
| 真实模型洞察 | PASS（一条） |
| 关闭 JOURNAL | PASS（不展示旧洞察） |
| 记忆页 status bar inset | PASS（截图） |

---

## 5. 交付

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.11.0-g3d1-debug.apk` | `3f53eab661a651c21bf4444a912a9c3d50c5bef11aa983e087ffde402562a38a` · 11,757,603 B |
| `artifacts/mengya-life-0.11.0-g3d1-qa-test.apk` | `ca8612adfb334160f2a14df3881aff6e472ea867fd75b85266f1da99e77e6485` · 11,757,609 B |
| 设备哈希 | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3D1-evidence.zip` |

---

## 6. 未验证 / 失败

1. **C3/C4 真机交互**：洞察详情“目标/趋势”跳转和“和顾问讨论/整理草案”按钮未逐一点击；代码已接线，标记未验证。
2. **真实 RISK 卡片**：本次模型输出 POSITIVE+SUGGESTION，没有 RISK；数据模型/UI 支持 RISK，但未真实展示。
3. **A1 完整真机链路**：编辑→提取失败→打开顾问未单独制造；由 JVM 两层（写入回滚/重建 + AdvisorData 过滤）和真机来源删除覆盖。
4. **A2 真机配置切换**：请求前配置变化只做了 JVM 可控测试；QA 撤回 gate 真机通过。
5. 真实职业日记+记忆生成洞察未做（真实洞察使用储蓄目标）；职业记忆规则测试在 JVM。
6. 设置脚本移除了导入行文案的易碎断言；正式包冒烟 50/50 覆盖导入入口。

---

## 7. 源码改动

新增：

- `domain/insight/InsightModels.kt`、`InsightCodec.kt`、`InsightRules.kt`、`InsightParser.kt`
- `data/insight/InsightService.kt`
- `data/local/insight/InsightReportEntity.kt`、`InsightReportDao.kt`
- `ui/insight/InsightViewModel.kt`、`InsightSection.kt`
- `data/growth/GrowthMemoryValidity.kt`
- 测试：`InsightRulesTest`、`InsightParserTest`、`InsightServiceTest`、`GrowthMemoryValidityTest`，扩展提取测试

修改：

- `JournalRepository`：编辑/删除触发 memory rebuild；事务
- `DiaryExtractionService`：绑定 runtime/consent、pre-request gate、事务、结果隔离
- `AdvisorDataRepository`：memory 二次有效性过滤
- `MengyaDatabase`：v8 + insight_reports
- `OverviewScreen`、`MengyaLifeApp`：洞察区域/导航/顾问回调
- `GrowthMemoryScreen`：status bar inset
- `build.gradle` 0.11.0-g3d1

---

## 8. 命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleQa
python3 tools/ui-verify/verify_diary.py
python3 tools/ui-verify/verify_goals.py
python3 tools/ui-verify/verify_trends.py
python3 tools/ui-verify/verify_navigation.py
python3 tools/ui-verify/verify_settings.py
python3 tools/ui-verify/verify_export.py
python3 tools/ui-verify/verify_prod_smoke.py
```

所有上述回归均 PASS；具体断言数见日志。
