# MengYa Life 0.25.2.1 — 实现、根因与修复

基线：`master / 1f8908105874c187a535b95da5ebbd32b8ec5e5c`。直接在主分支开发；无 worktree、无新增业务模块、无 RestoreCoordinator 框架扩展。版本：0.25.2.1 / versionCode 38。

## Gate A — AI 出站安全

### 根因

- 基线中顾问主聊天有 barrier，但 `maybeExtractRecord/Diary/Plan/Goal` 四个附加提取各自直连 `engine.chat()`。配置、endpoint、授权类别或 Restore generation 可在主回复后变化；部分后置检查不完整。
- 主回复写入后过早把 `local.sending=false`，附加提取时取消/配置状态观察器不再有效；`requestJob` 也可能在续跑期间被覆盖。
- 小计划提取失败会回退到本地 `PlanDraftFactory`，仍落草案；失败语义不一致。

### 修复

- [AdvisorViewModel](../../../app/src/main/java/com/mengyalife/app/ui/advisor/AdvisorViewModel.kt) 使用唯一 `requestAi()` / `withAiRequestBoundary()`，绑定 config、实际 runtime endpoint/model/key、consent epoch/categories、授权数据快照、Insight 引用与 Restore generation。每次 engine 调用前检查；返回后重新取得 barrier 并复核后才接受。主流程持续 `sending=true` 到附加提取完全结束，request Job lazy 登记且仅由拥有者清理。
- 四种附加提取复用同一入口；失败、超时、取消、撤权、配置或来源变化均不保存候选、不自动应用。移除计划提取的失败回退。若完整 Insight 依据会被 `AdvisorRules` 上下文预算裁掉，则在写用户消息/调用模型前 fail-closed。
- 其余四个出站路径也复核既有边界：[DiaryExtractionService](../../../app/src/main/java/com/mengyalife/app/data/growth/DiaryExtractionService.kt)、[WeeklyAiService](../../../app/src/main/java/com/mengyalife/app/data/weekly/WeeklyAiService.kt)、[InsightService](../../../app/src/main/java/com/mengyalife/app/data/insight/InsightService.kt)、[CareRunner](../../../app/src/main/java/com/mengyalife/app/data/care/CareRunner.kt)。顾问入口和这些服务的合计是 5 个直接 chat 调用点；CareRunner 删除无 barrier 的备用分支。
- Care 偏好写入现在复用 RestoreWriteBarrier；CareRunner 在发送前、模型返回后、最终决策/写入边界复核配置、授权、来源快照、候选、BUSY/PAUSED/TIME、历史和静音输入。

## Gate B — Insight 来源身份和确认原子性

- 基线活动 Insight 查找只扫描最近 6 条用户消息，候选来源 ID 又限 15 分钟；最新消息没有 Insight marker 时还可能遮住更早有效来源。
- [RecordProposalDraft](../../../app/src/main/java/com/mengyalife/app/domain/advisor/RecordProposal.kt) 保存稳定 `{reportId,dataVersion,insightId}` 与 source-link 状态，写入既有 `source_message_ids_json` 的 `record-source-v1` envelope，不加数据库列/迁移。旧 JSON 数组仍解码；损坏的 v1 标志/指针 fail-closed。新建普通草案显式标记“已检查、无 Insight 来源”。
- 持久对话按全量消息查来源；明确话题切换形成边界，切换后的普通计划不继承旧 Insight。短 transcript 窗口不再是唯一持久来源关联。
- 顾问确认在期望 Restore generation 的共享 barrier 内重读候选/来源。[PlanRecordApplyService](../../../app/src/main/java/com/mengyalife/app/data/advisor/PlanRecordApplyService.kt) 对 source-linked 或来源状态不明的普通 `apply/applyProgress` fail-closed；确认入口必须传 generation 与 validator，validator 在 Room transaction 内、正式写前执行。GoalProposalApplyService 同样在 transaction 内验证；无 barrier 时不写。
- 普通旧版无 Insight 草案仍兼容；legacy 数组若关联消息存在且没有 Insight marker 可沿用，带 marker 的必须走 validated 入口。

## Gate C — 自然语言 handoff

- 讨论建议的用户气泡改为简短自然句；factIds、reportId、dataVersion、验证指令只进入系统提示/来源 metadata，不进入气泡。
- [AdvisorScreen](../../../app/src/main/java/com/mengyalife/app/ui/screens/AdvisorScreen.kt) 收起普通消息的来源统计，依据仍可展开；时间只显示在最新消息或日期分界。渲染层改动，不重写旧消息或迁移数据库。

## Gate D — 跨天与时区

- 原 dataVersion 把 UTC epoch-day 混进事实 hash，导致跨日窗口失效被误报为用户修改事实。
- [InsightRules](../../../app/src/main/java/com/mengyalife/app/domain/insight/InsightRules.kt) 把 `sourceFingerprint` 与 observation-window key 拆开；window key 包含 zone ID、30 日起点和 15 日边界的实际 epoch 边界。相同来源 fingerprint、不同日/时区窗口只报告自然窗口失效；来源或授权变化才报告事实/授权变更。
- `InsightService` 对 READY 与 FAILED 报告在最终写入边界重新校验来源、授权、配置和 generation，旧绑定事实不落库。旧版不透明哈希仍只能给出保守说明，不声称用户改过记录。
