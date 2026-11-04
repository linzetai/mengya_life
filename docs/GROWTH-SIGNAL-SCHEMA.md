# Gate 3C-1 · 成长线索协议

版本：`0.9.0-g3c1`  
数据库：Room v6  
表：`growth_signals`

## 1. GrowthSignal 字段

| 字段 | 说明 |
|---|---|
| `id` | UUID，唯一 |
| `sourceType` | 目前固定 `DIARY` |
| `sourceId` | 日记 id |
| `sourceVersion` | 日记 title+body 的 SHA-256；编辑后变化 |
| `category` | EVENT / PROGRESS / BLOCKER / HABIT / EMOTION / ACTION_CANDIDATE |
| `content` | 提取出的线索文本 |
| `evidence` | 日记原文依据（必须逐字出现） |
| `isInference` | true=推测，false=事实 |
| `confidence` | 可选，0..1 |
| `extractedAt` | 提取时间 |
| `status` | ACTIVE / CORRECTED / DELETED / STALE |
| `origin` | AI / USER |
| `sourceTitle` | 来源日记标题快照 |
| `contentHash` | category+content 的 SHA-256；同版本幂等/去重 |
| `updatedAt` | 最近修改 |

唯一索引：`(source_type, source_id, source_version, content_hash)`。

## 2. 提取 JSON（V1）

```json
{
  "schemaVersion": 1,
  "signals": [
    {
      "category": "EVENT",
      "content": "完成了一次重要汇报",
      "evidence": "今天完成了一次重要汇报",
      "isInference": false,
      "confidence": 0.9
    }
  ]
}
```

## 3. 严格校验

- 只截取第一个 `{` 到最后一个 `}`；非法 JSON → Failure；
- `schemaVersion == 1`；
- 最多 6 条；category 必须枚举；
- content/evidence 非空；content ≤500，evidence ≤500；
- evidence 归一化后必须是日记原文的子串（防止编造事件）；
- `isInference` 必填；
- confidence 可选，但必须有限且 0..1；
- content 含「诊断为 / 确诊 / 建议停药」→ 拒绝（不做医学/心理诊断）；
- 重复 category+content 去重；
- 任何失败不写线索，只更新日记分析状态与错误。

事实/推测由 `isInference` 显式标记；UI 必须展示。

## 4. 用户纠正/删除

- 纠正：更新 content，`origin=USER`、`isInference=false`、`status=CORRECTED`，保留 evidence；
- 删除：`status=DELETED`，UI 隐藏，不再进入顾问上下文；
- 同一日记存在 CORRECTED 线索时，顾问只使用纠正结果，不再发送该日记的旧 AI 推测；
- 日记编辑：旧版本线索 `STALE`，不进入上下文；日记删除：删除相关线索。

## 5. 顾问上下文

- 仅 JOURNAL 授权时使用；
- 仅 ACTIVE/CORRECTED 且 `sourceVersion` 等于当前日记哈希；
- 来源必须带日记 id；来源类型 `GROWTH`；
- 若原日记已被选中，则跳过该日记的线索，避免原文+提取重复；
- 失效/删除线索永不进入请求。
