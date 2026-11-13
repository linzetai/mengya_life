# 0.25.2.1 AI 出站与 Insight 来源数据流

## 基线出站点盘点

基线 `app/src/main` 有 10 处直接 `engine.chat()`：AdvisorViewModel 主聊天 1、附加提取 4、DiaryExtractionService 1、WeeklyAiService 1、InsightService 1、CareRunner 有/无 barrier 分支 2。当前收敛为 5 个直接 chat 调用位置：AdvisorViewModel 单一入口服务主聊天+四种附加提取，其余四个领域服务各 1 个；CareRunner 无 barrier 后备调用已移除。

## Advisor 请求

1. 读取当前 config/consent，拒绝未知/恢复维护状态、endpoint recipient 不匹配或 runtime endpoint/model/key 不一致。
2. 目标/进展/日记按授权类别过滤；Conversation 历史仅在已授权时进入 prompt。用户输入、授权数据快照和来源引用一起生成绑定。
3. `requestAi()` 绑定 config、runtime、consent epoch/categories、数据快照、InsightReference 与 Restore generation。每次 engine 调用前在 barrier 内复核；引擎返回后重新进入 barrier 并复核后才接受。主回复和附加提取共享这条入口。
4. `local.sending` 覆盖所有附加提取；请求失败、撤权、endpoint/model/key 变化、来源数据变化、generation 变化、取消、解析失败均不创建/自动应用草案。计划提取不再回退本地伪草案。
5. 当选中 Insight 的任一事实/原始来源条目会因 6000 字符上下文预算被丢弃，VM 在保存用户消息与调用 engine 前拒绝发送；`BuiltRequest.includedEntries` 给该判断提供精确证据。

## 其他服务出站

| 服务 | 数据类别 | 边界和最终写入 |
|---|---|---|
| DiaryExtractionService | JOURNAL | exact config/runtime、consent epoch/categories、日记 hash、Restore generation；chat 前后及线索事务内复核。 |
| InsightService | 已授权的 GOALS/PROGRESS/JOURNAL | exact config/runtime、授权和来源 fingerprint/window key；请求、READY 写入与网络/解析失败报告写入都在 barrier 内复核。 |
| WeeklyAiService | GOALS/PROGRESS/JOURNAL 子集 | exact config/runtime、授权、dataVersion、generation；真实 chat 前复核，结果写入前再次复核。 |
| CareRunner | GOALS；仅授权时含 PROGRESS | exact config/runtime、epoch/categories/generation、目标/计划/进展与 Care 候选；模型前/后复核 BUSY/PAUSED/TIME、候选、反馈、最近发送历史和静音状态；偏好写入与最终决策共享 barrier。 |

## 来源持久化和确认

- RecordProposal 在已有 `source_message_ids_json` 保存 versioned `record-source-v1` envelope，包含关联消息 ID、`sourceLinkKnown`、`sourceLinkInvalid` 与可选 `{reportId,dataVersion,insightId}`；旧 JSON 数组继续读取，无需 DB migration。
- direct `PlanRecordApplyService.apply/applyProgress` 在读候选前固定 generation，取得 barrier 后才读数据；显式或历史消息标记为 Insight 来源的草案要求 `applyValidated`。Validated 路径在同一 Room transaction 中复核 Insight/来源，并同步更新候选状态；没有 barrier 或 generation 过期不写。
- GoalProposalApplyService 也在 transaction 中执行 source validator；无 validator 的直接入口拒绝带 Insight marker 的草案。普通、明确无 Insight 来源的旧草案保持兼容。

## Insight window

`sourceFingerprint` 只标识授权范围与原始事实；`dataVersion` 将其与本地日期、zone ID 和实际观察窗口起止 epoch 分开。跨午夜、同一当地日期跨时区、或 DST 造成的窗口边界变化都能产生新窗口版本，同时来源 fingerprint 不变。数据/授权变化才改变 fingerprint。
