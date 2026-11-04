# Gate 3C-2 · 成长记忆设计

## 1. 模型

`growth_memories`（Room v7）：

- `id`、`theme`（成长领域/主题）
- `content`、`is_inference`（事实/推测）
- `status`：ACTIVE / CORRECTED / STALE / DELETED
- `created_at` / `updated_at`
- `signal_ids_json`：组成记忆的 GrowthSignal 集合
- `source_diary_ids_json`：来源日记集合
- `user_corrected`、`version`
- `confidence`、`insufficient`
- `recent_change`
- `fingerprint`：主题 + 来源版本 + 当前线索内容哈希，用于去重/墓碑
- `deleted_at`

记忆不是无来源的 AI 总结：`signal_ids_json` 与 `source_diary_ids_json` 必填，UI 可展开每条线索及原文 evidence。

## 2. 生成（V1 规则聚类）

`GrowthMemoryService.rebuildAll()`：

1. 读取当前日记与 ACTIVE/CORRECTED 线索；
2. 丢弃来源版本与当前日记哈希不一致的线索；
3. 按规则主题分组：
   - 职业关键词 → 职业成长
   - 健康/睡眠/运动 → 健康与习惯
   - BLOCKER → 阻碍与反思
   - EMOTION → 情绪与状态
   - HABIT/ACTION → 行动与习惯
   - PROGRESS → 进展与行动
   - 其他 → 生活记录
4. 同一主题的线索合并为一条记忆；不同主题不强行合并；
5. 生成内容、事实/推测、置信度、最近变化、信息不足标记；
6. 版本化更新：同主题新线索 → 原记忆 version+1；
7. 没有信号的主题 → 已有记忆 STALE。

## 3. 更新与去重

- fingerprint = 主题 + 线索来源版本 + 当前内容哈希；
- 相同 fingerprint 不重复写入/不升版本；
- 新线索/纠正/删除导致 fingerprint 变化，更新既有记忆或创建新记忆；
- 多篇日记合并时保留全部 signal IDs / diary IDs（最多参与展示 6 条）。

## 4. 信息不足

`insufficient = 来源日记 < 2 或线索 < 2`。  
UI 标记「信息不足」；顾问上下文要求模型表达不确定性，不把单篇线索当确定结论。

## 5. 用户纠正

- 纠正 memory.content，status=CORRECTED，userCorrected=true，isInference=false，version+1；
- 之后新增线索只更新来源/版本，不覆盖用户纠正内容；
- 后台重建不会把 CORRECTED 降级。

## 6. 顾问集成

- 仅 JOURNAL 允许时加载 ACTIVE/CORRECTED 记忆；
- 只加载来源日记仍存在的记忆；
- 按问题相关性选择最多 2 条；
- 若记忆的任一来源日记已被选中，跳过该记忆，避免「记忆+原始日记」重复；
- 若记忆被选中，跳过其来源日记的 growth signals；
- 上下文标注「含推测」「信息不足」；来源类型 MEMORY；
- 不把记忆当目标/进展写库。

## 7. 未使用

无向量库、无复杂知识图谱、无全领域评分；V1 规则聚类可解释、可测试。
