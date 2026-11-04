# Gate 3D-2 报告 · 可信洞察与交互闭环

| 项 | 值 |
|---|---|
| 版本 | **0.12.0-g3d2**（versionCode 12） |
| 数据库 | **Room v8，无迁移**（复用 insight_reports；factIds 在 JSON 内） |
| 设备 | Xiaomi 2509FPN0BC / Android 17 |
| 结论 | **PASS（核心）**；未验证项见 §7 |
| 时间 | 2026-11-04（Asia/Shanghai） |

---

## 1. A · 洞察隐私安全（P0）

| 项 | 标记 | 证据 |
|---|---|---|
| A1 绑定 consent epoch/类别、endpoint/recipient、runtime、dataVersion | **PASS** | `InsightService` 捕获 boundConsent/boundCategories/boundRuntime/boundInput |
| A2 engine.chat 前再次校验 | **PASS** | `preflightProblem` 在 chat 前与结果写入前各校验一次 |
| A3 配置/授权变化阻止未发起请求 | **PASS** | 撤回/JOURNAL 关闭/切换 endpoint/编辑进展 → engine.calls=0，DB 无新 READY |
| A3 进行中取消 | **代码已接线，真机未验证** | InsightViewModel 监听 consent/config，变化时 cancel generationJob |
| A3 旧结果隔离 | **PASS** | 请求期间改数据：engine.calls=1，但无 READY 写入 |
| A4 边界说明 | **PASS** | 区分请求前阻止、进行中取消、结果隔离；已发送字节无法撤回 |

新增测试：

- 请求前撤回授权 → 0 calls，DB 空；
- 请求前关闭 JOURNAL → 0 calls；
- 请求前切换 endpoint → 0 calls；
- 请求前编辑进展 → 0 calls；
- 请求期间改数据 → 1 call，无 AI 结果写库。

---

## 2. B · 时间与数据版本

| 项 | 标记 | 说明 |
|---|---|---|
| B1 30 天 / 全部历史 / 期限 / 生成时间 | **PASS** | progress-count 同时写全部历史与最近 30 天；目标期限单独 fact；报告 generatedAt/period 展示 |
| B2 时间边界进入 dataVersion | **PASS** | dataVersion 含 day bucket；跨日/窗口变化缓存失效 |
| B3 每条趋势记录实际区间 | **PASS** | trend fact 写 first/last 日期与“区间 … – …” |
| B4 趋势来源截断 | **PASS** | 趋势 sourceRefs 使用 takeLast(6)，最新点必含；测试 8 点验证 #8 |
| B5 只有目标无进展不算充分 | **PASS** | hasEnough 只认 ≥2 进展或记忆；单目标无进展 → insufficient |
| B6 目标字段版本 | **PASS** | 标题/描述/层级/状态/起止/父目标/创建时间均入 dataVersion |

---

## 3. C · 可信性

| 项 | 标记 | 说明 |
|---|---|---|
| C1 规则事实 vs AI 解释分层 | **PASS** | facts 本地计算；insights 单独存储/展示 |
| C2 AI 引用 InsightFact ID | **PASS** | 解析器要求非空 factIds，且必须属于本轮 facts |
| C3 不把合法 source 当已核实事实 | **PASS** | source 白名单 + factIds 双重校验；UI 标事实/推测 |
| C4 记忆实际内容参与 | **PASS** | `memory-content-*` fact 带主题/内容/来源数 |
| C5 用户纠正优先/过期不参与 | **PASS** | 同主题只展开纠正版本；AdvisorData 已过滤 STALE/DELETED/来源失效 |
| C6 数据不足不编造 | **PASS** | 规则 insufficient；prompt 要求不确定；真实模型摘要写“信息不足” |

---

## 4. D · 交互验收（真机）

| 项 | 标记 | 证据 |
|---|---|---|
| D1 详情 → 目标 | **PASS** | `g3d2-insight-to-goal.png`，定位 SavingsGoal2 |
| D1 详情 → 趋势 | **PASS（目视）** | `g3d2-insight-to-trends.png`，显示 CNY 趋势；脚本 tag 断言未命中，未记为自动 PASS |
| D1 建议 → 和顾问讨论 | **PASS** | `g3d2-insight-discuss.png`，顾问输入已预填 |
| D1 建议 → 整理为目标草案 | **PASS** | `g3d2-insight-draft.png`，显示“草案尚未保存” |
| D1 用户确认后才创建正式目标 | **PASS** | 审核前“尚未保存”；点“确认创建 6 个目标”后 DB goals=7（含原 1），再清理 |
| D2 定位到具体目标 | **PASS** | 目标页滚动到 SavingsGoal2，而非仅进入列表 |
| D3 详情优先展示相关事实 | **NOT VERIFIED** | 代码按 factIds 过滤；未取得本轮视觉截图 |
| D4 旧详情在数据/权限变化时关闭 | **NOT VERIFIED** | 代码在 hasReport=false 时关闭；未做“详情打开中改权限”真机 |
| D5 长期储蓄多期 | **PASS** | 真实 1000/1500/2000 CNY 洞察 |
| D5 风险场景 | **PASS（存在 RISK 卡）** | 真实“趋势区间需核对”RISK；不是截止日期/偏离场景 |
| D5 职业日记/成长记忆洞察 | **NOT VERIFIED** | 仅 JVM 规则测试；无真实 AI 洞察截图 |
| D5 数据不足真实输出 | **NOT VERIFIED** | JVM insufficiency 覆盖；无真实 AI 截图 |

---

## 5. 真实模型

DeepSeek `deepseek-flash`，dataVersion `312c…`：

- 1 个储蓄目标，3 条 CNY 进展；
- 模型输出 POSITIVE（目标与记录集中）、RISK（同日区间需核对）、SUGGESTION（补充记录口径）；
- 每条 insight 带 factIds：goal-count / progress-count / trend-28-CNY；
- 来源全部 GOAL#28 / CHECKIN#10-12；
- 明确写“区间起止同一天，信息不足以判断长期趋势”，未把三点说成长期增长。

失败重试：早期缺少 factIds 被解析器拒绝，保留规则 facts/FAILED；强化 prompt 后成功。详见 `g3d2-real-insight.log`。

---

## 6. 测试与回归

| 项 | 结果 |
|---|---|
| JVM | **217/217** |
| 日记 / 目标 / 趋势 / 导航 / 设置 / 导出 / 正式包 | 25 / 64 / 73 / 28 / 38 / 49 / 50 |
| Room v8 | 无迁移，正式数据保留 |
| 真实模型洞察 | PASS（1 条端到端） |
| 真机目标/趋势/顾问/草案 | 目标/顾问/草案 PASS；趋势目视 PASS |

---

## 7. 未验证 / 失败

1. **A3 进行中取消**：ViewModel 已接线，真机未注入网络中途配置变化；JVM 验证请求前阻止和结果隔离。
2. **D3**：factIds 过滤代码已实现，未取得本轮详情视觉截图。
3. **D4**：详情打开中权限/数据变化自动关闭未真机验证。
4. **D5 职业成长**：只有 JVM 规则/记忆测试，没有真实模型洞察。
5. **D5 数据不足**：只有 JVM，规则事实已明确，但没有真实 AI 输出截图。
6. **D5 风险**：真实 RISK 为“同日区间”，不是“截止日期/进度偏离”场景。
7. 趋势脚本的容器 tag 断言未命中，但页面目视 PASS，不将自动断言标记为 PASS。

---

## 8. 交付

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.12.0-g3d2-debug.apk` | `4d8c2b42372dd6b9c2165a64833cade5915e39d0dca182efaa7957807e0487f8` · 11,767,540 B |
| `artifacts/mengya-life-0.12.0-g3d2-qa-test.apk` | `9e873facacd4e62aaa73ac09404b8d88e5c14c4e6085d7e1c5598b6c48388a6b` · 11,768,163 B |
| 设备哈希 | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3D2-evidence.zip` |

---

## 9. 修改文件清单

见 `docs/reports/logs/g3d2-source-changes.md`。核心：

- `data/insight/InsightService.kt`：绑定/preflight/结果隔离
- `ui/insight/InsightViewModel.kt`：取消 generation job
- `domain/insight/InsightRules.kt`：时间/趋势区间/最新来源/充分性/目标版本/记忆内容
- `domain/insight/InsightParser.kt` + `InsightModels/Codec`：factIds
- `ui/insight/InsightSection.kt`：fact 过滤、详情导航关闭、目标定位
- `ui/goals/*`、`GoalsScreen`：FocusGoal
- 测试：InsightServiceTest 6 条新增、InsightRulesTest 4 条新增、Parser factIds、Memory 优先
- `build.gradle`：0.12.0-g3d2

---

## 10. 命令

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

全部命令实际执行；回归合计 327 条断言。
