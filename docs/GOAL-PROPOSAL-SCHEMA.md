# Gate 3B-1 · AI 目标草案协议

版本：`schemaVersion = 1`  
解析器：`GoalProposalParser`  
持久化：Room v4 `goal_proposals`（状态 `DRAFT`）  
本轮**不写入** `goals` / `goal_checkins`。

## 1. 触发

顾问页「整理为目标草案」按钮，而不是每次聊天都要求 JSON。

请求在普通系统提示之外追加 `AdvisorRules.proposalSystemPrompt()`，要求只输出 JSON；不依赖 tool calling。输出 token 上限 3000，并在提示中限制最多 12 个节点、描述 ≤120 字，避免 JSON 被截断。

## 2. JSON

```json
{
  "schemaVersion": 1,
  "summary": "一句话总结",
  "assumptions": ["需要用户确认的假设"],
  "risks": ["可能的风险"],
  "goals": [
    {
      "tempId": "g1",
      "level": "LONG",
      "parentTempId": null,
      "parentGoalId": null,
      "title": "长期目标标题",
      "description": "描述",
      "domain": "HEALTH",
      "startDate": "2026-11-04",
      "dueDate": null,
      "basis": "建议依据"
    },
    {
      "tempId": "s1",
      "level": "STAGE",
      "parentTempId": "g1",
      "parentGoalId": null,
      "title": "阶段目标",
      "domain": "HEALTH"
    },
    {
      "tempId": "a1",
      "level": "ACTION",
      "parentTempId": "s1",
      "parentGoalId": null,
      "title": "短期行动",
      "domain": "HEALTH"
    }
  ]
}
```

层级：`LONG`（或 `LONG_TERM`）/ `STAGE` / `ACTION`。  
领域：`HEALTH`、`CAREER`、`WEALTH`、`LIFE`、`LEARNING`。

## 3. 校验

`GoalProposalParser` 严格校验：

1. 能截取 JSON；`schemaVersion == 1`；
2. `summary` 非空；`goals` 非空且 ≤30；
3. 每个节点有唯一 `tempId`、合法 `level`、非空标题（≤60 字）、描述 ≤1000 字；
4. `LONG` 不能有父级；
5. `STAGE` 父级必须是长期目标；`ACTION` 父级必须是阶段目标；
6. `parentTempId` 必须存在；`parentGoalId` 必须存在于真实目标，且层级正确；
7. 临时父链不能循环；
8. 日期必须是 `yyyy-MM-dd`，且 `dueDate >= startDate`；
9. 非法输入返回 `Failure(message)`，不崩溃、不生成草案。

## 4. 失败处理

- 助手原文保存为普通消息（最多 4000 字）；
- `proposal_error` 保存解析错误；
- 不生成 `goal_proposals`，不伪造草案；
- 用户可继续正常对话或重试。

## 5. 持久化

`goal_proposals`：

| 列 | 说明 |
|---|---|
| `id` | UUID |
| `message_id` | 对应助手消息 |
| `created_at` | 时间 |
| `schema_version` | 1 |
| `status` | `DRAFT` |
| `summary` | 摘要 |
| `assumptions_json` | 假设数组 |
| `risks_json` | 风险数组 |
| `nodes_json` | 节点数组 |

重启后从 Room 读取并展示；不写业务表。

## 6. UI

- 草案弹窗标记「目标草案（尚未保存）」；
- 长期 → 阶段 → 行动按层级缩进；
- 展示 summary、assumptions、risks、basis、日期；
- 补充到已有目标时显示 `→ 已有目标 #id`；
- 关闭后仍可从消息入口再次打开。

## 7. 真实模型验证

2026-11-04 08:50，正式包使用用户有效 Key（DeepSeek `deepseek-flash`）生成草案：

- 草案 schemaVersion=1，status=DRAFT；
- 含阶段目标 + 2 个短期行动，阶段挂在已有目标 #21；
- `goal_proposals` 1 条，`goals` 仍为 1 条，`goal_checkins` 仍为 1 条；
- 详情见 `docs/reports/logs/g3b1-real-model.log`。

第一次真实尝试因输出 1600 token 截断导致 JSON 不完整，解析器正确报错且未保存草案；将提案上限提高到 3000 并限制节点数后成功。这次失败也验证了「非法/不完整 JSON 不崩溃、不伪造」。
