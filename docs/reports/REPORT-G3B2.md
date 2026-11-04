# Gate 3B-2 报告 · 安全修复、草案审核、事务创建与撤销

| 项 | 值 |
|---|---|
| 版本 | **0.8.0-g3b2**（versionCode 8） |
| 数据库 | Room **v5**（v4→v5 非破坏迁移） |
| 包名 | `com.mengyalife.app` / QA `com.mengyalife.app.qa` |
| 结论 | **PASS（核心）**；未验证项见 §5，不把未测当通过 |
| 执行 | 2026-11-04（Asia/Shanghai） |

---

## 1. 结论

Gate 3B-2 的 P0 安全修复、P1 草案审核、事务创建、撤销与状态机均已实现并验证：

- endpoint 竞态：不再出现「旧地址通过授权、请求发往新地址」；
- 上下文来源只包含真正进入请求的记录，system/current/context/history 共用预算；
- 草案失败重试保持 proposalMode，不重复用户消息，保留失败原文；
- 全屏草案审核（非超长 AlertDialog），支持编辑/勾选/父子依赖；
- 唯一 `GoalProposalApplyService`，用户确认后才写；Room 事务；DRAFT→APPLIED 原子；
- 撤销只删本次创建且未被修改/产生进展/挂接数据的目标；重复撤销幂等；
- 真实模型完成自然对话 → 草案 → 审核 → 创建 5 个目标 → 目标页 → 撤销。

### 关键结果

| 项 | 结果 |
|---|---|
| JVM | **158/158** |
| Gate 3B-2 真机（手动+DB） | 15 项 PASS，4 项未验证（见 §5） |
| v4→v5 迁移 | PASS，旧日记/消息/草案保留 |
| 真实模型完整流程 | PASS（创建 5 个目标后撤销） |
| QA 包草案事务/撤销 | PASS（部分/全部/已有父级/双击/重启） |
| 正式包非破坏 | PASS（仅验证迁移与旧草案/删除父目标安全阻止） |

---

## 2. 逐项标记

### 任务 A：安全修复

| 项 | 标记 | 证据 |
|---|---|---|
| A1 endpoint 竞态 | **PASS** | 两个 JVM 可控制顺序测试；`AdvisorViewModelTest` |
| A2 来源只含最终记录 + 共同预算 | **PASS** | `AdvisorRequestBudgetTest` 3 条 |
| A3 草案失败重试 proposalMode | **PASS** | 单测 + 真实模型失败/重试 DB |

A1 细节：

1. 发送时 runtime endpoint 必须等于 UI 快照 endpoint、当前 config endpoint，并通过 consent 的 recipient 校验；
2. 配置/接收方/授权变化时取消进行中的请求；
3. 引擎返回后再次校验，变化则不保存回复；
4. 不向未授权的新地址发送。

A2 细节：

- assembler 输出可单独取舍的 entries；
- `buildRequest` 统一扣减 system/current/context/history；
- 只有 included entries 的 source 被保存/展示；
- 截断条目不计来源。

### 任务 B：草案审核

| 项 | 标记 | 说明 |
|---|---|---|
| 全屏详情（非超长 AlertDialog） | **PASS** | 应用内全屏 Surface + LazyColumn |
| 长期→阶段→行动 | **PASS** | 缩进树 + 依据 |
| 摘要/假设/风险 | **PASS** | 顶部区块 |
| 编辑标题/描述/日期 | **PASS** | 真机修改标题并应用 |
| 节点勾选 | **PASS** | 部分创建 2 个 |
| 父子依赖合法 | **PASS** | 勾选子自动勾祖先；取消父取消后代；规则双保险 |
| 取消审核不写业务数据 | **PASS** | 关闭/取消只保留草案 |
| 区分已有/将创建 | **PASS** | 「补充到已有目标 #id · 将新建」 |
| 单会话 | **PASS** | 未增加会话列表 |

### 任务 C：确认写入

| 项 | 标记 | 说明 |
|---|---|---|
| AI 不能直接写 | **PASS** | 无模型工具；只有 apply service |
| 用户点击确认才执行 | **PASS** | 按钮 → ViewModel → service |
| 写前重新校验 schema/层级/父子/日期/父目标/选择 | **PASS** | `GoalProposalApplyRules` + service |
| 父→子插入 + tempId→goalId | **PASS** | 真机 parent_id 链正确 |
| Room 事务整体回滚 | **PASS（实现）** / **未验证（故障注入）** | `withTransaction`；见 §5 |
| 复用 GoalRules/Repository | **PASS** | 每个节点调用 `GoalRepository.createGoal` |
| 重复点击/重启不重复 | **PASS** | 原子 DRAFT→APPLIED + Mutex + 按钮状态 |
| 记录草案/目标/时间/确认 | **PASS** | `applied_at`、`applied_goal_ids_json`、status |
| 不修改/删除已有目标、不自动打卡 | **PASS** | 只插新节点；无 checkin 写入 |

### 任务 D：撤销

| 项 | 标记 | 说明 |
|---|---|---|
| 只撤销本次创建 | **PASS** | `applied_goal_ids` |
| 不删除原有目标 | **PASS** | 已有父级补阶段/行动撤销后保留 |
| 修改/进展/挂接阻止 | **PASS（修改）** / **未验证（进展）** | 修改已真机阻止；进展尝试未形成 checkin |
| 事务性 | **PASS（实现）** / **未验证（故障）** | withTransaction |
| 重复撤销幂等 | **PASS（代码/单测）** | UNDONE 返回 Success；UI 无按钮 |

### 任务 E：状态

`DRAFT / APPLIED / UNDONE`。Room v5 新增 `proposal_mode`、`applied_at`、`applied_goal_ids_json`、`selected_temp_ids_json`、`undone_at`；非破坏迁移。

### 任务 F：测试覆盖

| 测试 | 标记 |
|---|---|
| 中途换服务地址不发送 | PASS（JVM） |
| 上下文截断来源准确 | PASS（JVM） |
| 草案失败重试 | PASS（JVM + 真实） |
| 新增长期+阶段+行动 | PASS（QA 3；真实 5） |
| 已有长期补阶段 | PASS（真机） |
| 已有阶段补行动 | PASS（真机） |
| 只确认部分节点 | PASS（QA 2 目标） |
| 父未选子被选 | PASS（规则单测；UI 禁止） |
| 审核期间父目标删除 | PASS（正式包真实旧草案） |
| 批量中途失败回滚 | **未验证** |
| 快速双击 | PASS（真机只 1 个；ViewModel 单测） |
| 重启后重复确认 | PASS（APPLIED 无按钮） |
| 撤销成功 | PASS（QA + 真实） |
| 修改/进展阻止撤销 | PASS（修改）/ **未验证（进展）** |
| Room 迁移 | PASS |
| 五页面 v5 回归 | **未验证**（v4 日记/目标/趋势通过；v5 仅加列） |
| 正式/QA 隔离 | PASS |

---

## 3. 真实模型

正式包、用户自己的 DeepSeek `deepseek-flash`，未向脚本提供 Key：

1. 模型第一次草案引用已删除的 #21，解析器报「g1：父级不存在」，未写业务数据；
2. UI 显示「重试草案」，重试保持 proposalMode，不重复用户消息；
3. 用户要求忽略旧 #21 后，生成含新长期/阶段/3 行动的有效草案；
4. 审核 → 确认创建 5 个目标（#22–#26）；
5. 目标页显示「共 5 个目标」，父子链正确；
6. 返回顾问显示「已创建 5 个目标」；
7. 撤销成功，goals=0，草案 UNDONE，旧日记保留。

证据：`g3b2-real-model.log`、截图 `g3b2-real-*.png`。  
没有用 Fake 结果冒充真实模型。

---

## 4. 数据与迁移

- v4→v5 覆盖安装，`user_version=5`；
- 旧日记 `MigrationA/B` 逐字段一致；
- 旧 12 条消息保留，`proposal_mode=0`；
- 旧草案保留 DRAFT；
- 真实测试产生的草案 UNDONE 保留；
- 业务表结构未变。

详见 `MIGRATION-G3B2.md`、`g3b2-migration.log`。

---

## 5. 未验证 / 已知风险

### 未验证（不声称通过）

1. **批量写入中途失败的真实回滚**：实现使用 `withTransaction`，但未加故障注入，真机没有制造第二次插入失败。
2. **有进展时阻止撤销**：代码检查 `goal_checkins`，真机尝试未成功写入 checkin，不能声称该路径通过。
3. **父未选子被选的真机断言**：规则单测与 UI 防呆已覆盖，但未单独制造该非法选择。
4. **v5 五页面全量回归**：v4 上已通过；v5 只新增列/表，但未重跑全部 5 页脚本。

### 风险

- 撤销依赖 `updatedAt == createdAt` 判断「未修改」；同一毫秒创建/修改理论边界；
- 撤销不删除 checkins（已先验证为 0）；若未来允许自动打卡需扩展；
- 草案编辑只做基础字段校验，最终以 apply service 重校验为准；
- 服务层 Mutex 只保证单进程；跨进程/多实例不在本轮范围；
- 真实模型对旧目标 #21 的“记忆”会造成无效草案，本轮通过用户澄清/解析错误处理，不自动改写历史；
- 调试签名不可用于发布。

---

## 6. 交付与哈希

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.8.0-g3b2-debug.apk` | `13b043f568551e4fe2997e0c211a80d365e67cc7ffde2b96357e483566ebc8a3` · 11,368,672 B |
| `artifacts/mengya-life-0.8.0-g3b2-qa-test.apk` | `2990309d1fed85f0673705a806bc96250cb1285f4351bc343565cc59424344a0` · 11,368,680 B |
| 设备 base.apk | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3B2-evidence.zip` |

调试证书 SHA-256：`6fcae914998d767b41461241c69b4213b67143a01bbc385e81636a9b69a0efaf`。

---

## 7. 源码改动清单

新增：

- `data/advisor/GoalProposalApplyService.kt`（含 `GoalProposalApply` 接口、`GoalProposalApplyRules`）
- `ui/advisor/GoalProposalReviewDialog.kt`
- 测试：`GoalProposalApplyRulesTest`、`AdvisorRequestBudgetTest`
- 文档：`GOAL-PROPOSAL-APPLY.md`、`PRIVACY-RACE-FIX.md`、`MIGRATION-G3B2.md`

修改：

- `AdvisorViewModel.kt`（endpoint 竞态、统一请求预算、草案重试、apply/undo、双击 guard）
- `AdvisorRules.kt`（`buildRequest` 统一预算；proposal prompt）
- `AdvisorContext.kt`（entries/source）
- `AdvisorContract.kt` / `AdvisorScreen.kt` / `SettingsScreen.kt`
- `ConversationRepository.kt` / `AdvisorMessageEntity.kt` / `AdvisorModels.kt`
- `GoalProposalRepository.kt` / `GoalProposalEntity.kt` / `GoalProposalDao.kt`
- `MengyaDatabase.kt`（v5 + MIGRATION_4_5）
- `AppContainer.kt`
- `AdvisorEngine.kt`（QA 草案现有父级 fake）
- `app/build.gradle`（0.8.0-g3b2）
- 既有测试适配

---

## 8. 建议（Gate 3B-3）

1. 加入 QA 专用故障注入（事务中途失败），补真机回滚证据；
2. 对撤销增加 checkin/外部子目标/修改的完整真机矩阵；
3. 草案编辑保存草稿状态与版本号，支持多轮修订；
4. 真实模型增加「禁止引用已删除目标」的系统约束或校验反馈重试；
5. 将 v5 五页面全量回归纳入固定套件。
