# Gate 3B-1 报告 · 持续授权、自动上下文与目标草案

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 版本 | **0.7.0-g3b1**（versionCode 7） |
| 包名 | `com.mengyalife.app`（QA：`com.mengyalife.app.qa`） |
| 数据库 | Room **v4**（非破坏性迁移 v3→v4） |
| 结论 | **PASS**（核心功能与真机验收）；未覆盖项见 §5 |
| 执行时间 | 2026-11-04（Asia/Shanghai） |

---

## 1. 执行结论

**PASS**。

Gate 3B-1 的 P0/P1 均已实现并在真机验证：

- **持续授权**：首次一次性授权、持久化、文案/类别版本、授权时间、接收方、四类开关、撤回；
- **顾问交互**：未配置引导、未授权授权页、拒绝不发数据、同意后不再逐次确认；
- **自动上下文**：按授权类别检索目标/进展/日记/历史，规则式相关性与预算；
- **历史隔离**：`contextEpoch`，关闭类别或换接收方后旧上下文不进入新请求；
- **来源展示**：每条回复保存实际引用，UI 显示「参考了 X 个目标…」；
- **目标草案**：版本化 JSON、严格校验、持久化、分层 UI、重启恢复、不写业务表。

### 证据摘要

| 项 | 结果 | 日志 |
|---|---|---|
| JVM 单测 | **145/145** | `g3b1-unit-tests.md` + XML |
| Gate 3B-1 真机脚本 | **47/47** | `g3b1-device.log` |
| 日记回归 | **25/25** | `g3b1-verify_diary.log` |
| 目标回归 | **64/64** | `g3b1-verify_goals.log` |
| 趋势回归 | **73/73** | `g3b1-verify_trends.log` |
| 导出回归 | **49/49** | `g3b1-verify_export.log` |
| 正式包冒烟 | **50/50** | `g3b1-verify_prod_smoke.log` |
| 真机断言合计 | **308/308**（47+261） | — |
| v3→v4 迁移 | 通过，旧日记逐字段一致 | `g3b1-migration.log` |
| 真实模型 | 3 个场景成功（目标/进展、日记、草案） | `g3b1-real-model.log` |

> 真实模型使用用户自己配置的 DeepSeek `deepseek-flash`；没有使用 QA Fake 冒充真实结果。
> 真实模型第一次生成草案时因 1600 token 截断返回不完整 JSON，解析器正确报错、未保存；
> 提高到 3000 token 并限制节点数后成功。这次失败也验证了非法 JSON 路径。

---

## 2. 已实现

### 2.1 持续授权（任务 A）

- `AiConsentState` / `DataStoreAiConsentRepository`：
  `granted`、`textVersion`、`categoriesVersion`、`grantedAt`、`recipient`、`categories`、`epoch`；
- 默认未授权；Gate 3A 升级后没有授权文件，真机确认出现首次授权页；
- 首次同意四类全开；设置页独立开关；
- 更换 endpoint 后 `isValidFor` 失败，顾问页要求重新确认；
- 授权文案/类别版本不一致需重新授权；新增类别不会自动沿旧同意；
- 撤回保留本地数据；epoch 每次变更 +1。

### 2.2 顾问交互（任务 B）

- 未配置 → 配置引导；
- 配置完成但未授权 → 首次/重新授权页；拒绝后不读取、不发送；
- 已授权直接进入对话；**删除** Gate 3A 的逐次目标摘要确认；
- 设置页新增「AI 与隐私」：状态、接收方、授权时间、四类开关、撤回、限制说明。

### 2.3 自动上下文（任务 C）

- `AdvisorContextAssembler`：纯函数，规则式检索；
- 只读取授权类别；目标按关键词/规划意图；进展按目标/关键词；日记按关键词且无关问题不附加；
- 预算：2 个长期目标、6 阶段、8 行动、6 进展、2 篇日记×300 字、上下文 3500 字；
- 历史 12 条/6000 字符预算沿用；
- 未授权返回空上下文。

### 2.4 历史隔离（任务 D）

- `advisor_messages.context_epoch`；
- 新请求只使用 `contextEpoch == 当前 epoch` 的 SENT 历史；
- 关闭 JOURNAL/GOALS/PROGRESS/CONVERSATION、撤回、换接收方都会改变 epoch；
- 旧聊天仍可查看；请求中授权变化会取消并保存 CANCELLED；
- 不要求第三方撤回已发送数据，UI 明确说明。

### 2.5 来源展示（任务 E）

- `sources_json` 保存类型、记录 ID、短标签；
- 助手消息显示「参考了 X 个目标 / Y 篇日记 / Z 条进展」，点击查看来源清单；
- 不保存完整正文副本、请求体或 Key。

### 2.6 目标草案（任务 F/G）

- `GoalProposalParser`，schemaVersion=1；
- 临时父子引用、已有父目标校验、重复 ID、循环、日期、层级；
- `goal_proposals` 表，status=DRAFT；
- 顾问页「整理为目标草案」；
- 分层 UI、假设/风险/依据、「草案，尚未保存」；
- 重启恢复；不写 `goals`/`goal_checkins`。

---

## 3. 已测试 / 通过

### 3.1 JVM（145/145）

- 授权规则/版本/类别；
- 上下文检索、无关日记、类别关闭、预算、历史 epoch；
- 草案合法/重复/循环/父目标/非法 JSON/日期；
- ViewModel 未授权、授权、来源、关闭类别、换 endpoint、重试、取消、草案成功/失败；
- 既有数据层/导出/规则测试。

### 3.2 真机 Gate 3B-1（47/47）

覆盖：

- 未配置引导、首次授权页、拒绝不发数据、同意；
- 未配置 → 配置 → 授权 → 对话；
- 目标/进展/日记创建；
- 日记来源、关闭日记后无日记来源、epoch 递增；
- 关闭目标后无目标/进展来源；
- 撤回后授权页，消息/目标/日记不删除；
- 换 endpoint 重新授权；
- 草案生成、弹窗、持久化、不写业务表、重启恢复；
- 无崩溃。

### 3.3 回归

| 套件 | 结果 |
|---|---|
| 日记 | 25/25 |
| 目标 | 64/64（含编辑、状态、父子、进展、重启） |
| 趋势 | 73/73 |
| JSON 导出 | 49/49（schemaVersion=4） |
| 正式包只读冒烟 | 50/50 |
| 设置/导航全量 | 未在 v4 重跑；Gate 3A 为 38/38、28/28 |

### 3.4 迁移与隔离

- 正式包 v3 备份 → 安装 v4：`user_version=4`；
- 旧日记 `MigrationA`/`MigrationB` 逐字段一致；
- 旧顾问消息保留，`context_epoch=0`，不会进入新请求；
- 新增 `goal_proposals`；
- QA/正式包：包名、DB 名、AI prefs、consent prefs、Keystore alias 隔离。

### 3.5 真实模型

| 场景 | 结果 | 来源 |
|---|---|---|
| 结合目标与进展讨论规划 | 成功 | `GOAL #21`、`PROGRESS #6` |
| 结合相关日记讨论成长问题 | 成功 | `JOURNAL #10 "SleepNote"` |
| 整理为三级目标草案 | 成功 | schema=1、STAGE+ACTION、DRAFT |
| 草案首次截断 | 失败但正确处理 | `proposal_error`，无草案、无崩溃 |

模型：DeepSeek `deepseek-flash`。证据不包含 Key/密文/请求头。

---

## 4. 未验证 / 边界

1. **设置页/导航全量回归未在 v4 重跑**；设置页新增「AI 与隐私」由 G3B1 脚本与截图覆盖。
2. 真实模型仅一个服务商、一个模型、3 个场景；未覆盖限流、长对话、长回复、多语言压力。
3. 未实现流式输出、多会话、向量检索、云同步、正式发布签名（按任务范围）。
4. 聊天记录导出/恢复未实现；导出仍只含日记/目标/进展。
5. 草案只生成/展示/持久化；确认写入正式目标留给 Gate 3B-2。
6. 来源引用在删除目标/日记后仍保留历史 ID；UI 可显示，但记录本身已不存在。
7. 第三方服务商可能记录请求；应用无法撤回已发送数据。

---

## 5. 已知风险

- 规则式检索对中文长句/同义词有限；可能漏选或选到弱相关记录；
- 严格 epoch 策略会在任何授权变化后丢掉历史连续性（隐私优先的取舍）；
- 草案 JSON 依赖模型输出完整；已限制 token/节点并在失败时保留错误；
- 设置页开关变化与请求并发由 ViewModel 取消处理，仍建议后续加更细的请求状态机；
- 调试签名公开，不能用于发布；
- 数据库迁移只覆盖 v3→v4；更老版本仍走 1→2→3→4 链。

---

## 6. 交付

| 文件 | SHA-256 / 说明 |
|---|---|
| `artifacts/mengya-life-0.7.0-g3b1-debug.apk` | `d5eae3fada72ca532b58c218b2cddb991b9547ebb3cd6e745121edea057e638b` · 11,416,412 B |
| `artifacts/mengya-life-0.7.0-g3b1-qa-test.apk` | `2400e83b86870758e9176f625e6d41e23232dae5e8763808e2c9347169172e80` · 11,416,433 B |
| 设备安装哈希 | 与上面一致（`base.apk` 拉回） |
| 证据包 | `docs/reports/evidence/G3B1-evidence.zip` |
| 报告 | `REPORT-G3B1.md` |
| 隐私 | `PRIVACY-CONSENT-G3B1.md` |
| 上下文 | `AI-CONTEXT-G3B1.md` |
| 草案协议 | `GOAL-PROPOSAL-SCHEMA.md` |
| 迁移 | `MIGRATION-G3B1.md` |

设备：Xiaomi 2509FPN0BC / Android 17 / arm64-v8a。  
调试证书 SHA-256：`6fcae914998d767b41461241c69b4213b67143a01bbc385e81636a9b69a0efaf`。

---

## 7. 源码改动清单

新增：

- `domain/advisor/AiConsent.kt`
- `domain/advisor/AdvisorContext.kt`
- `domain/advisor/GoalProposal.kt`
- `data/consent/AiConsentRepository.kt`
- `data/advisor/AdvisorDataRepository.kt`
- `data/advisor/GoalProposalRepository.kt`
- `data/local/proposal/GoalProposalEntity.kt` / `GoalProposalDao.kt`
- `ui/settings/AiPrivacySection.kt`
- 测试：`AiConsentRulesTest`、`AdvisorContextAssemblerTest`、`GoalProposalParserTest`
- 工具：`tools/ui-verify/verify_g3b1.py`

修改：

- `MengyaDatabase.kt`（v4 + MIGRATION_3_4）
- `AdvisorMessageEntity.kt` / `ConversationRepository.kt`
- `AdvisorViewModel.kt` / `AdvisorContract.kt` / `AdvisorScreen.kt`
- `AdvisorRules.kt`（自动上下文参数、草案提示）
- `AiSettingsViewModel.kt` / `SettingsScreen.kt` / `MengyaLifeApp.kt`
- `AppContainer.kt`
- `app/build.gradle`（0.7.0-g3b1、consent prefs）
- `AdvisorFakes.kt` / 既有测试适配
- 导出 schema 4 断言与文档

完整 diff 见证据包 `source/` 快照与 git 提交。

---

## 8. Gate 3B-2 建议

1. 草案确认写入：用户逐项确认 → 事务写 goals/checkins → 可撤销；
2. 将草案节点与真实目标建立映射，避免重复创建；
3. 更细的授权审计（每次发送记录类别/接收方/时间，不含正文）；
4. 上下文检索的可解释性：展示为何选中某条记录；
5. 设置/导航全量回归纳入 v4 固定套件；
6. 聊天记录导出/恢复（P1 遗留）。

---

## 9. 缺失证据清单

- 设置页/导航 v4 全量脚本日志（本轮用 G3B1 脚本与截图替代，旧版 38/38、28/28 未在本版重跑）；
- 真实模型的限流/长对话/长回复压力测试；
- 草案写入正式目标（按范围未实现）；
- 聊天记录导出/恢复（按范围未实现）。
