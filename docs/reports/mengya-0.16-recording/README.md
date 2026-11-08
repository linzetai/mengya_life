# MengYa 0.16 · Conversational Recording

基线：`0.15.3A-morrow`；本包 `0.16-morrow` / versionCode 21；数据库 v13（新增 `record_proposals`，提供 v12→v13 迁移）。本轮只完整实现 **目标进展（PROGRESS）**，未开发日记/目标/决定的 AI 写入，也未改顾问业务语义、授权机制或对话协议。

## 1. 对话式记录流程

1. 用户正常发送消息，先走既有聊天链路（请求、回复、失败/取消、历史保存都不变）。
2. 回复成功后，本地闸门 `RecordIntentGate` 判断这句话是否像“记录类表达”（记录/存/攒/完成… + 数字，含中文数量词），避免每条闲聊都多发一次请求。
3. 仅当「目标」「进展」两类都已授权时，再发一次**提取请求**：只带本次原话 + 已授权目标清单（id/层级/标题/状态）+ 最近少量进展；不带整库、不带日记正文。
4. 模型按严格 JSON 协议返回 `record` 或 `record:null`；应用用 `RecordProposalParser` 校验字段、目标 id、日期、数值/单位，非法即丢弃。
5. 结果落到 `record_proposals`（`message_id` 唯一，重复提取不会产生第二条）。
6. 处理策略：
   - **明确指令**（“帮我记录/记一下”）且字段完整、目标唯一、授权充分 → 直接写入，卡片显示“已记录”，提供**修改 / 撤销**；
   - **聊天中推断**（“现在已经攒到 14.7 万了”）→ 只生成“待确认”卡片，提供**确认记录 / 修改 / 取消**，不静默保存；
   - **目标不确定** → 卡片列出候选目标（1–3 个）并标注“还需要确认：目标”，用户点选后才可确认；
   - 缺数值/日期等 → 写入 `pendingFields`，不写入数据库。
7. 写入完成或用户取消后，目标、轨迹、首页发现卡都通过既有 Room Flow 自动刷新。

### 增量 vs 累计快照

- `DELTA`：本轮新增量（“又存了 2000”“今天学了 2 小时”）；
- `SNAPSHOT`：当前累计状态（“现在有 14.7 万”），原样记录为一条快照，**不会**被当成新增量；
- `NONE`：无数值进展；
- 不做单位换算：`2000 元` 与 `147000 元` 会按单位分开成不同序列（与既有轨迹页“不换算单位”的约定一致）。

## 2. 结构化提案与领域接口

**提案模型** `domain/advisor/RecordProposal.kt`

```
RecordProposalDraft(
  kind: PROGRESS,                 // 结构上可扩展，本轮只实现 PROGRESS
  goalId / goalTitle,             // 关联目标
  goalCandidates,                 // 澄清候选
  amountKind: DELTA|SNAPSHOT|NONE,
  metricValue / metricUnit,
  note, occurredAt,
  pendingFields,                  // 待澄清字段
  intent: EXPLICIT|SUGGESTED,     // 明确指令 / 聊天建议
  originalText,                   // 用户原话（来源）
)
```

状态：`DRAFT → APPLIED / CANCELLED`，`APPLIED → UNDONE`；表 `record_proposals` 记录 `message_id`（来源消息）、`applied_checkin_id`（正式记录 id）、时间戳，可追溯。

**写入边界** `data/advisor/ProgressRecordApplyService.kt`

- 只接受用户确认后由 ViewModel 调用；模型不能直连数据库。
- 事务内重新校验：目标存在、说明/数值/单位合法、`DELTA/SNAPSHOT` 必须有有限数值。
- 先经 `GoalRepository.addCheckin(source = advisor)` 落库，再 `markAppliedIfDraft` 原子迁移；状态被并发改过则整笔回滚，**重复点击/重试不会重复写入**。
- 撤销：删除对应 `goal_checkins` 行并置 `UNDONE`；修改已记录值：经 `GoalRepository.updateCheckin` 更新同一行（不改目标，换目标需撤销后重记）。
- 未授权「进展」→ 不提取，并在顾问页明确提示；未授权「目标」→ 无法判断归属，同样提示且不写入。

### 主要修改文件

- 新增：`domain/advisor/RecordProposal.kt`、`data/advisor/RecordProposalRepository.kt`、`data/advisor/ProgressRecordApplyService.kt`、`data/local/proposal/RecordProposalEntity.kt`、`RecordProposalDao.kt`、`ui/advisor/RecordProposalCard.kt`
- 修改：`AdvisorRules.kt`（提取协议）、`AdvisorViewModel.kt`（提取/确认/撤销/修改）、`AdvisorContract.kt`、`AdvisorScreen.kt`（卡片接入、跟随最新滚动）、`MengyaLifeApp.kt`、`MainActivity.kt`（仅 QA 注入入口）、`MengyaDatabase.kt`（v13 + 迁移）、`GoalRepository.kt` / `GoalDao.kt` / `GoalModels.kt`（来源与更新/删除）、`AdvisorEngine.kt`（QA 确定性提取）、`Di/AppContainer.kt`、`TrendsScreen.kt`（来源文案）
- 测试：`app/src/test/java/com/mengyalife/app/advisor/RecordProposalTest.kt`

## 3. 真机验证（ADB，QA 包）

设备：Android 17 / Xiaomi 2509FPN0BC，1200×2608、480dpi、字号 0.9；`com.mengyalife.app.qa`（离线 QA 引擎，回复明确标注“QA 离线测试引擎”）。截图见本目录。

| 场景 | 结果 | 证据 |
|---|---|---|
| 明确指令直接记录（“帮我记录一下，今天存了2000”） | 通过：自动写入，卡片“已记录”，可修改/撤销 | `01-explicit-delta-applied.png` |
| 自然聊天触发建议（“现在已经攒到14.7万了”） | 通过：只出“待确认”卡片，未自动写入 | `02-snapshot-suggestion.png` |
| 用户确认后保存 | 通过：状态变“已记录”，goal_checkins 新增行 | `03-suggestion-confirmed.png` |
| 用户取消不写入 | 通过：状态“已取消”，数据库中无对应进展 | `04-suggestion-cancelled.png` |
| 多相似目标主动澄清 | 通过：列出 3 个候选目标，选后确认 | `05-ambiguous-goal.png`、`06-ambiguity-resolved.png` |
| 增量与累计快照区分 | 通过：DELTA=2000 / SNAPSHOT=147000，卡片与轨迹分开显示 | `01`、`02`、`09` |
| 修改已记录错误数值（2000→1500） | 通过：卡片显示“已更新”，数据库同一 checkin 更新为 1500 | `07-edited-value.png` |
| 保存后目标/轨迹更新 | 通过：首页发现卡日期变为当天；轨迹新增两条数值点 | `09-trajectory-updated.png` |
| 重启后记录仍在 | 通过：强制停止并重启后卡片仍为“已记录”，DB 中 checkin 保留 | `08-after-restart.png` |
| 真实模型（deepseek-flash）明确指令 | 通过：自动写入，回复不否认应用能力 | `real-ai/01-explicit-delta-real.png` |
| 真实模型自然聊天建议 | 通过：只出“待确认”卡片 | `real-ai/02`、`04` |
| 真实模型建议确认 | 通过：写入并显示“已记录” | `real-ai/03-snapshot-confirmed-real.png` |
| 真实模型建议取消 | 通过：状态“已取消”，无写入 | `real-ai/05-suggestion-cancelled-real.png` |
| 真实模型信息不足 | 通过：没有编造记录，主动追问金额/目标/日期 | `real-ai/06-insufficient-info-real.png` |
| 真实模型多目标歧义 | 通过：自动列出“每月固定储蓄 3000 元 / 五年内积累 50 万”两个候选，未静默写入；选长期目标后确认成功 | `real-ai/07`、`08`、`09` |
| 未配置/网络失败/授权拒绝 | 未做设备复测；沿用既有聊天错误提示；新增“未授权进展/目标→提示不写入”分支（见代码），本轮未单独截图 | — |

录屏：`record-flow.mp4`（约 30s，展示冷启动进入顾问、发送明确记录指令、回复与“已记录”卡片生成）。

数据库核对（QA 库 v13）：`goal_checkins` 新增 `source=advisor` 记录，id 4=2000、5=147000 元、6=1500（修改后）；`record_proposals` 中对应 APPLIED，CANCELLED 项未产生 checkin。

真实模型：`deepseek-flash`（QA 包，endpoint api.deepseek.com，未开离线引擎），完成 6 轮真实调用：显式记录、快照建议、建议确认、建议取消、信息不足追问、多目标歧义澄清并确认。回复与卡片均符合预期；未出现编造数值。

单测：**313 项通过，0 失败**（新增 10 项记录候选规则测试）；`assembleDebug`、`assembleQa` 成功。

## 4. 已知限制 / 未覆盖

1. 真实模型（deepseek-flash，QA 包）已完成端到端验证；真实测试中额外发现并修正了一处提示词矛盾：旧提示写“只有建议权”，模型会回答“我不能代写”，与应用已具备的记录能力冲突。修正后模型不再否认该能力，但仍不会声称自己直接写库。
2. 真实模型下的网络失败/401/429 等错误路径未逐一复测；沿用既有聊天错误提示。
3. 授权拒绝路径仅实现、未做设备截图验证。
4. 已记录的进展只能改说明/数值/单位，不能改目标（需撤销后重记，卡片已写明）。
5. 提取会额外发一次模型请求（仅在记录类表达时）；真实模型下会增加延迟与 token 消耗（实测单轮约 5–15 秒）。
6. `record_proposals` 未纳入备份 v2 的导出表；恢复备份后这些候选不会随文件迁移（已写入的 `goal_checkins` 不受影响），孤儿候选在无对应消息时不会展示。
7. QA 包新增 `qa_advisor_prompt` intent extra（仅 `QA_BUILD`），因为 ADB 无法输入中文；正式包不读取该 extra。

以上交付等待视觉与产品验收，不进入下一步交互开发。
