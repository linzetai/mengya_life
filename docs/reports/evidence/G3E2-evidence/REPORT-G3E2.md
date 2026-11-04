# Gate 3E-2 报告 · 可信周回顾修复 + 可选 AI 增强

| 项 | 值 |
|---|---|
| 版本 | **0.14.0-g3e2** |
| 数据库 | Room **v10** |
| 设备 | Xiaomi 2509FPN0BC / Android 17 |
| 结论 | **PARTIAL：JVM 233/233；以下代码级项未真机/集成验证，AI 真实模型 NOT VERIFIED** |

## 1. 本轮完成

- 周回顾持久化规则版本、`data_version`、`ai_consent_version`；同周缓存按三版本失效。
- 页面订阅 `weekly_reviews` / `weekly_ai_reviews` 数据变化，Worker 更新可反映。
- 周回顾记忆读取复用 `GrowthMemoryValidity` 过滤失效来源。
- 周趋势来源首点 + 最近 6 点；Insight prompt 已含 factIds，解析器要求 sources 与 factIds 事实来源关联，仅 insufficient 允许空来源。
- 通知 `tryClaimNotification` 原子 claim + 失败 release。
- 新增可选 AI 周回顾：设置开关、独立 `weekly_ai_reviews`、每 period_key 唯一预留、输出解析/来源校验、本地/AI 分层、隐私类别过滤。
- 本地回顾始终使用本地数据；AI 输入按类别重新构建。

## 2. 逐项状态

| 项 | 状态 | 说明 |
|---|---|---|
| 8+ 趋势首尾来源 | **PASS（JVM）** | InsightRulesTest 8 点 |
| prompt factIds / 来源关联 | **PASS（JVM）** | Parser 测试 |
| 周缓存 dataVersion 失效 | **NOT VERIFIED** | 代码已写，未做页面真机改数/集成测试 |
| 页面订阅数据变化 | **NOT VERIFIED** | 代码已写，未做真机 Worker 更新截图 |
| 周回顾记忆失效 | **NOT VERIFIED** | 复用 Validity 代码，未做周回顾真机/集成 |
| 通知并发去重 | **NOT VERIFIED** | 原子 SQL 代码，未做并发 Worker 真机 |
| 本地回顾不要求 AI 授权 | **PASS（JVM）** | 规则测试 |
| AI 类别过滤/输入重建 | **PASS（JVM）** | WeeklyAiTest |
| AI 每自然周一次/解析 | **PASS（JVM）** | FakeAiDao + engine 1 call |
| 真实模型 AI 周回顾 | **NOT VERIFIED** | 真机请求超时/未取得成功样本 |
| AI 手动/自动预算真机 | **NOT VERIFIED** | 仅 JVM |
| 通知/调度回归 | **NOT VERIFIED（0.14）** | 仅有 0.13 真机记录 |
| v9→v10 迁移 | **PASS** | 设备 user_version=10，旧日记保留 |

## 3. 测试

- JVM **233/233**（含 WeeklyAiTest 3 条；XML 见证据）。
- 0.13 回归 25/64/73/28/38/49/50；**0.14 未重跑完整回归**，因此不标 PASS。
- 真实 AI 周回顾：NOT VERIFIED；未把 Fake/超时请求写成真实成功。

## 4. 隐私/预算

- 本地回顾不读 AI 类别作为显示限制；AI 输入由当前 consent 类别重新构建；
- AI 请求前检查 consent epoch/categories/endpoint/config；响应后再次校验；
- 每个 period_key 唯一预留，最多一次请求；输出 token 上限 900；
- 失败保留本地回顾与 FAILED 状态，不无限重试；
- 不把完整本地回顾缓存直接作为 prompt；只发送必要 facts 与来源。

## 5. 未验证/风险

1. 真实模型 AI 周回顾成功样本；本次真机请求超时/未完成。
2. 0.14 完整 UI 回归未重跑。
3. 页面 Worker 更新、缓存失效、通知并发未真机注入。
4. AI 手动/自动预算、授权撤销/切换 endpoint 真机未验证。
5. 生产测试目标已删除，但测试 weekly_reviews/weekly_ai_reviews 行仍保留（未提供页面删除入口），未用于真实用户结论。

## 6. APK

- 正式：`6ca311c197d797d4936f51be2d37e3b2a312105a7b9a51ab87f9cd951ecfcade` · 12,075,966 B
- QA：`06684a3af2a258a9952795356b1e03ad741fe30270880d028bf27e67d42eedc7` · 12,092,358 B
- 设备 base.apk 哈希一致。

## 7. 下一 Gate 建议

先补真实 AI 周回顾成功证据、0.14 全量回归与并发/缓存真机注入；不要扩展到云同步/复杂 Agent。
