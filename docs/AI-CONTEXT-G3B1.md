# Gate 3B-1 · 自动上下文构建

组件：`AdvisorContextAssembler`（纯 Kotlin，可脱离 Android UI 单测）  
数据入口：`AdvisorDataRepository`（Room 读取，按授权类别兜底）  
调用点：`AdvisorViewModel`，每轮发送前组装一次

## 1. 输入

- 当前用户问题；
- `AiConsentState`（是否授权、类别、epoch、接收方）；
- `AdvisorContextSnapshot`：目标、进展、日记、历史消息。

`AdvisorDataRepository` 只加载已授权类别；`Assembler` 再次检查类别。未授权时返回空上下文。

## 2. 输出

`AssembledAdvisorContext`：

| 字段 | 含义 |
|---|---|
| `systemContext` | 本轮附加给模型的个人数据摘要（可为 null） |
| `history` | 按 epoch 过滤后的历史消息 |
| `sources` | 实际引用：类型 + 记录 ID + 短标签 |
| `stats` | 目标/进展/日记/历史数量、字符数、预算截断标记 |

`sources` 随助手消息保存到 `advisor_messages.sources_json`，用于 UI 来源展示；不保存正文副本。

## 3. 规则式检索（V1）

### 目标

- 仅 `GOALS` 授权；
- 优先长期目标；按问题 token 命中标题/描述打分；
- 规划类问题（目标/规划/计划/方向/阶段/行动/长期/人生/拆）无命中时，取进行中的长期目标（最多 2 个）；
- 附带阶段（≤6）与行动（≤8）；不返回全部目标。

### 进展

- 仅 `PROGRESS` 授权；
- 优先已选目标下的进展；否则按问题 token 命中或规划意图；
- 按时间倒序，最多 6 条，每条摘要 120 字。

### 日记

- 仅 `JOURNAL` 授权；
- 按标题/正文 token 打分；
- 命中 ≥2 个 token，或问题含日记/情绪/睡眠等意图且命中 ≥1；
- 最多 2 篇，每篇 300 字；
- **无关问题不附加日记**（如 "hello" 不选任何日记）。

### 历史对话

- 仅 `CONVERSATION` 授权；
- 只取 `status=SENT` 且 `contextEpoch == 当前 epoch` 的消息；
- 由 `AdvisorRules.buildRequestMessages` 再做 12 条 / 6000 字符预算。

## 4. 预算

| 项 | 上限 |
|---|---|
| 目标（长期） | 2 |
| 阶段 | 6 |
| 行动 | 8 |
| 进展 | 6 |
| 日记 | 2 篇 / 每篇 300 字 |
| 上下文摘要 | 3500 字符 |
| 历史消息 | 12 条 |
| 总请求上下文 | 6000 字符（含 system/history/current） |

超限按行截断，并记录 `droppedByBudget`；不发送完整数据库。

## 5. 模型提示

系统提示要求：

- 只使用提供的摘要，不编造未提供的个人事实；
- 信息不足时先问用户；
- 不输出人生评分/完成概率；
- AI 只给建议，不直接改业务数据。

## 6. 示例

问题：`sleep anxiety journal advice`  
授权：四类全开  
结果：`sources = [JOURNAL #10 "SleepNote"]`，上下文摘要约 300 字；真实 DeepSeek 回复引用了该日记摘要。

问题：`health goal progress next step`  
结果：`sources = [GOAL #21, PROGRESS #6]`；真实回复引用了目标与进展。

## 7. 单测

`AdvisorContextAssemblerTest` 覆盖：

- 相关日记入选、无关日记不入选；
- 日记类别关闭后完全不读取；
- 目标/进展来源与关闭后隔离；
- 历史 epoch 与 CONVERSATION 开关；
- 字符预算截断；
- 未授权返回空上下文。
