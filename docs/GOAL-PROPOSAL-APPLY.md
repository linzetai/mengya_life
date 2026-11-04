# Gate 3B-2 · 目标草案审核与事务性创建

版本：`0.8.0-g3b2`（versionCode 8）  
服务：`GoalProposalApplyService`（实现 `GoalProposalApply`）  
数据库：Room v5

## 1. 边界

- AI 只能生成草案；**没有任何工具/代码路径允许模型直接写** `goals` / `goal_checkins`。
- 只有用户在草案详情页点击「确认创建」后，ViewModel 才调用 apply service。
- 已有目标不会被 AI 修改或删除；本轮也不自动创建进展记录。

## 2. 审核 UI

`GoalProposalReviewDialog` 是应用内全屏覆盖层，不是超长 AlertDialog：

- 标题显示 `DRAFT / APPLIED / UNDONE`；
- summary、assumptions、risks；
- 长期 → 阶段 → 行动缩进树；
- 节点显示「新建」或「补充到已有目标 #id · 将新建」；
- 可编辑标题/描述/开始/截止日期；
- 可勾选节点；勾选子节点自动勾选祖先，取消父节点自动取消后代；
- 取消审核不写业务表；
- 应用后显示「已创建 N 个目标」和「撤销本次创建」。

## 3. 写前校验

`GoalProposalApplyRules.validate(...)` 重新校验：

- 草案可解码、schemaVersion=1；
- 至少选一个节点；
- 标题/描述合法；
- 层级：LONG 无父；STAGE 父为 LONG；ACTION 父为 STAGE；
- 临时父子必须同时选中；
- `parentGoalId` 必须仍存在且层级正确（审核期间被删除会失败）；
- 日期 `yyyy-MM-dd`、截止不早于开始；
- 非法输入返回错误，不进入事务。

## 4. 事务与幂等

`MengyaDatabase.withTransaction` 内：

1. 读取草案，必须是 `DRAFT`；
2. 重新校验；
3. 按 LONG → STAGE → ACTION 插入，`tempId → real goalId`；
4. `markAppliedIfDraft` 原子改为 `APPLIED`，写 `applied_goal_ids_json`、`selected_temp_ids_json`、`applied_at`；
5. 任一步抛错 → Room 回滚，包括已插入目标和状态更新。

幂等：

- `Mutex` 串行化同一服务；
- 状态必须 `DRAFT` 才能应用；
- 已 `APPLIED` 重复提交返回「已经创建过」；
- 已 `UNDONE` 不能再次创建；
- 双击确认由 ViewModel 同步 `applying` 标志 + 按钮 loading + service 状态三重保护。

## 5. 撤销

只允许 `APPLIED → UNDONE`，事务内：

- 只读取 `applied_goal_ids_json` 里的目标；
- 任一目标不存在、`updatedAt != createdAt`（被修改）、有进展记录、或挂接了外部子目标 → 阻止并返回原因；
- 否则按深层到浅层删除**本次创建**的目标；
- 更新草案 `UNDONE`、`undone_at`；
- 重复撤销：`UNDONE` 返回成功（幂等），不重复删除；
- 原本存在的父目标不在 `applied_goal_ids` 中，永不删除。

## 6. 状态

`goal_proposals.status`：`DRAFT` / `APPLIED` / `UNDONE`。

Room v5 新增：

- `advisor_messages.proposal_mode`
- `goal_proposals.selected_temp_ids_json`
- `applied_at`
- `applied_goal_ids_json`
- `undone_at`

## 7. 验证结果

| 场景 | 结果 |
|---|---|
| 新增长期+阶段+行动 | PASS（QA 3 目标；真实模型 5 目标） |
| 部分选择（仅长期+阶段） | PASS（2 目标，selected JSON） |
| 只选子节点 | PASS（规则拒绝；UI 禁用） |
| 已有长期目标补阶段 | PASS（新增 1 阶段；撤销不删长期） |
| 已有阶段补行动 | PASS（新增 1 行动；撤销不删阶段） |
| 已有父目标审核期间删除 | PASS（正式包真实草案，错误提示，无写入） |
| 快速双击 | PASS（真机只创建 1 个；ViewModel 单测） |
| 重启后重复提交 | PASS（APPLIED 无 apply 按钮；状态阻止） |
| 撤销成功 | PASS（QA + 真实模型） |
| 已修改阻止撤销 | PASS（真机错误提示） |
| 有进展阻止撤销 | 未验证（尝试未成功写入 checkin，不能声称通过） |
| 批量中途失败回滚 | 未验证（实现使用 Room 事务；未做故障注入） |
| 修改节点标题 | PASS（真机编辑并应用） |
| 父节点未选择但子选中 | PASS（规则单测；UI 禁用/自动祖先） |

## 8. 已知限制

- 草案撤销后不能直接再次应用，需重新生成；
- 撤销只针对本次创建的节点；用户后续修改/进展会触发保守阻止；
- 删除目标会删除其空 checkins（撤销前已确认无 checkins）；
- 没有错误注入开关，批量失败回滚仍缺少真机证据。
