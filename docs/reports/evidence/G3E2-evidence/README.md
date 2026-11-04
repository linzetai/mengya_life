# 萌芽人生 · Mengya Life

> Android 应用。**工程名 MengyaLife**，包名 `com.mengyalife.app`。
> 当前版本 **0.13.0-g3e1**（Gate 3E-1：每周成长回顾 V1）。
>
> 技术栈：Kotlin + Jetpack Compose + Material 3 + **Room** + **DataStore** + **Android Keystore**。
> 无自有后端；用户自带 API Key（BYOK），请求直达用户配置的模型服务。

---

## 1. 当前状态

| 项 | 状态 |
|---|---|
| 五个一级页面（总览 / 目标 / 趋势 / 日记 / 顾问） | ✅ |
| 日记 / 三层目标 / 进展 / 趋势 / 导出 | ✅ Gate 2A–2C |
| 设置页 AI 服务：HTTPS 地址、模型、API Key、测试连接、修改、删除 | ✅ **Gate 3A** |
| API Key 加密存储（Android Keystore AES/GCM） | ✅ 明文不落 Room/日志/导出 |
| 真实顾问对话：输入、发送、等待、失败、重试、取消、重启恢复 | ✅ **Gate 3A** |
| 单会话持久化（Room `advisor_messages`，数据库 v8） | ✅ |
| 首次一次性授权、四类数据开关、撤回、G3A 升级不自动同意 | ✅ **Gate 3B-1** |
| 自动上下文：目标/进展/日记/历史按需检索 + 来源展示 | ✅ 规则式，预算 3500 字 |
| 历史 epoch 隔离、换接收方重授权 | ✅ |
| AI 目标草案：JSON 协议、校验、持久化、分层 UI | ✅ |
| 草案审核：编辑/部分选择/父子依赖/已有目标标注 | ✅ **Gate 3B-2** |
| 日记 AI 提取 + 成长线索 + 用户纠正/删除 | ✅ **Gate 3C-1** |
| 跨日记成长记忆 + 生命周期 + 顾问 MEMORY | ✅ **Gate 3C-2** |
| 成长洞察 V1：规则事实 + AI 解释 + 来源校验 + 缓存版本 | ✅ **Gate 3D-1** |
| 洞察请求前绑定/取消/结果隔离 + factIds + 时间边界 | ✅ **Gate 3D-2** |
| 每周本地回顾 + WorkManager 唯一周期任务 + 通知去重 | ✅ **Gate 3E-1** |
| 总览「最近值得关注」+ 洞察详情 + JOURNAL 关闭隔离 | ✅ |
| 线索顾问上下文、原日记去重、JOURNAL 隔离 | ✅ 真机验证 |
| 用户确认后事务创建 + 撤销 + DRAFT/APPLIED/UNDONE | ✅ 唯一 ApplyService |
| endpoint 竞态、请求预算、草案重试安全修复 | ✅ |
| QA 隔离包 + 离线测试引擎 | ✅ 仅 QA；正式包不创建假引擎 |
| 导入恢复 | ⏳ 未实现，界面如实提示 |
| JVM 单元测试 | ✅ **230/230** |
| 真机验收 | ✅ Gate 3E-1 每周回顾/通知/回归；未验证项见报告 |
| Gate 3A 报告 | `docs/reports/REPORT-G3A-ADVISOR.md` |
| Gate 3B-1 报告 | `docs/reports/REPORT-G3B1.md` |
| Gate 3B-2 报告 | `docs/reports/REPORT-G3B2.md` |
| Gate 3C-1 报告 | `docs/reports/REPORT-G3C1.md` |
| Gate 3C-2 报告 | `docs/reports/REPORT-G3C2.md` |
| Gate 3D-1 报告 | `docs/reports/REPORT-G3D1.md` |
| Gate 3D-2 报告 | `docs/reports/REPORT-G3D2.md` |
| Gate 3E-1 报告 | `docs/reports/REPORT-G3E1.md` |
| 请求格式 / 安全隐私 | `docs/AI-REQUEST-FORMAT.md`、`docs/SECURITY-PRIVACY-G3A.md` |

**Gate 3E-1 结论：PASS（核心）**。本地每周回顾、WorkManager 唯一周期任务、通知
权限/去重/点击、目标跳转与分类隐私已真机验证；每周 AI 不后台请求。
详情自动失效、讨论预填、跨周通知等边界未验证，详见 `REPORT-G3E1.md`。

**Gate 3B-2 结论：PASS（核心）**（历史）。安全竞态、审核、事务创建与撤销通过；
批量失败回滚、进展阻止撤销、v5 五页全量回归未验证。

**Gate 3B-1 结论：PASS**（历史）。持续授权、自动上下文、历史隔离与目标草案均通过。

**Gate 3A 结论：PASS**（历史）。功能闭环与真机验收通过；
用户已在正式包输入有效 Key，DeepSeek 真实回复于 2026-11-04 08:11 成功返回并落库；
另验证了真实 401 与 QA 离线引擎，见报告 §1。

---

## 2. 架构分层

```text
Compose (screens / journal / goals / advisor)
        │ 只读 UiState + 发 Action
        ▼
ViewModel (Journal / Goals / Theme / Export / Advisor / AiSettings)
        │ 业务规则、时间戳、授权、重试、取消、错误文案
        ▼
Repository
  ├── JournalRepository / GoalRepository / DataExportRepository
  ├── ConversationRepository（唯一写 advisor_messages）
  ├── AdvisorConfigRepository（DataStore + Keystore）
  └── AdvisorContextSource（GoalRepository 的只读目标摘要）
        │
        ├── Room (MengyaDatabase v3, WAL)
        └── AdvisorEngine
              ├── NetworkAdvisorEngine → HttpTransport → HTTPS
              └── FakeAdvisorEngine（仅 QA）
```

- Compose 不直接访问数据库或网络；
- Engine 只接收构造好的消息，不接触 DAO，不能修改目标/日记；
- 依赖装配在 `MengyaApplication` → `AppContainer`。

---

## 3. BYOK 模型配置（Gate 3A）

设置 → AI 服务：

- **API 服务地址**：只允许 `https://`；支持 `https://host/v1` 或完整
  `/v1/chat/completions`；拒绝 query/fragment/userinfo；
- **模型名称**；
- **API Key**：遮挡输入，保存后不回显；可替换、可删除；
- **测试连接**：真实发送最小请求，成功/失败都有明确文案；
- **删除密钥**：二次确认；删除 DataStore 密文与 Keystore 别名，旧密文永久不可解；
- 正式包无「离线测试引擎」开关；QA 包有，用于无 Key 的自动化。

Key 安全：

- Android Keystore AES-256/GCM，随机 IV；
- 密文存独立 DataStore（正式 `mengya_ai` / QA `mengya_ai_qa`）；
- 不写 Room、不写日志、不进入导出 JSON；UI 遮挡；
- 别名按构建类型隔离，正式/QA 不共享 key。

详见 `docs/SECURITY-PRIVACY-G3A.md`。

---

## 4. 真实顾问对话（Gate 3A）

- 单一持续会话，消息保存在 Room `advisor_messages`；
- 发送流程：
  用户消息落库 → 构造请求 → `POST {endpoint}/v1/chat/completions` →
  成功保存助手回复 / 失败保存 FAILED 消息（内容为空）→ 界面显示错误与重试；
- 支持等待状态、取消（写 CANCELLED，不产生假回复）、重试（复用用户消息，不重复提问）；
- 冷启动恢复全部对话；
- 未配置模型时显示引导；
- 请求超时 15s 连接 / 60s 读取；不跟随重定向；错误不包含 Key；
- 不实现流式、工具调用、多 Agent。

请求结构详见 `docs/AI-REQUEST-FORMAT.md`。

---

## 5. 个人数据与隐私

默认只发送：

- 当前问题；
- 最近 12 条对话（预算 6,000 字符）。

默认不发送：日记正文、全部长期目标、全部进展、完整数据库。

可选目标上下文：

- 用户选择一个长期目标；
- 发送前弹出「本次将发送哪些个人数据」：目标/描述/阶段/行动/最近进展摘要；
- 明确列出「不发送：日记正文、其它目标、完整数据库」；
- 确认后才发送；取消则不发送；重试不重复附带。

AI 只有建议权：不能自动新增/修改/删除目标、日记、进展，也不生成人生综合评分。

---

## 6. 数据库

**v3**（Gate 3A）新增 `advisor_messages`，v1/v2 表不变：

```sql
CREATE TABLE advisor_messages (
  id TEXT PRIMARY KEY NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  status TEXT NOT NULL,
  error_code TEXT,
  error_message TEXT,
  context_goal_id INTEGER
);
```

- `MIGRATION_2_3` 只建新表 + 索引；
- 保留 `app/schemas/` 的 1.json / 2.json / 3.json；
- 禁用 `fallbackToDestructiveMigration()`；
- 真机覆盖安装：`user_version` 2→3，旧日记逐字段不变（见报告 §7）。

---

## 7. 测试隔离与签名

| 项 | 正式包 | QA 包 |
|---|---|---|
| applicationId | `com.mengyalife.app` | `com.mengyalife.app.qa` |
| 数据库 | `mengya-life.db` | `mengya-life-qa.db` |
| DataStore | `mengya_settings` / `mengya_ai` | `mengya_settings_qa` / `mengya_ai_qa` |
| Keystore 别名 | `mengya_ai_key` | `mengya_ai_key_qa` |
| 离线假引擎 | 不存在 | 可开关 |

- 破坏性脚本默认只跑 QA 包，`require_qa()` 硬校验包名；
- 正式包只跑只读冒烟（`verify_prod_smoke.py`）；
- 调试签名固定为 `keystore/mengya-debug.keystore`（证书 SHA-256
  `6fcae914…a0efaf`），与已安装应用一致，可覆盖安装；
  该密钥口令公开，**仅用于开发/测试，发布必须换发布签名**。

---

## 8. 构建与安装

```sh
. ./env.sh
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug :app:assembleQa --no-daemon

export DEV=<手机IP>:<无线调试端口>
adb connect "$DEV"
adb -s "$DEV" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEV" install -r -t app/build/outputs/apk/qa/app-qa.apk
```

如果签名来源变化导致 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：
**先备份数据并报告**，不要未经确认卸载正式包。

---

## 9. 权限与安全

- 唯一新增权限：`INTERNET`，用于把用户主动发送的问题/授权摘要发往用户配置的
  HTTPS 服务；无自有后端、无广告/统计 SDK；
- 不申请存储、通知、位置、通讯录等权限；
- `usesCleartextTraffic=false`；
- API Key 不进入 Room、日志、崩溃信息、导出 JSON 或测试截图；
- 导出仍只包含日记/目标/进展，不包含 AI 配置、Key、聊天记录。

---

## 10. 已验证结果

| 验证 | 结果 |
|---|---|
| JVM 单元测试 | **158/158** |
| Gate 3B-2 真机手动 + DB 核对 | 15 PASS / 3 未验证 |
| Gate 3B-1 真机脚本 | **47/47** |
| 日记 / 目标 / 趋势 / 导出回归（v4） | 25+64+73+49 |
| 正式包只读冒烟（v4） | **50/50** |
| Room v4→v5 迁移 | 旧日记/消息/草案保留 |
| 真实模型完整流程 | PASS（5 目标创建后撤销） |

真实模型**成功**：✅ 用户 Key，DeepSeek `deepseek-flash`；目标/进展、日记、草案三场景均成功。

---

## 11. 已知限制

1. 批量写入中途失败的真实回滚、进展阻止撤销、v5 五页全量回归未验证。
2. 撤销依赖 `updatedAt == createdAt` 判断未修改；同毫秒边界未覆盖。
3. 不流式输出；聊天记录不导出；卸载会丢失。
4. 撤销后草案不能直接再次应用，需重新生成。
5. 无证书固定；无多会话/向量检索/工具调用；调试签名不可发布。
6. 调试签名公开，不能用于发布。
7. 服务商可能记录问题与授权后的目标摘要，取决于其政策。

---

## 12. 下一步（Gate 3B）

- 用更多真实提示词验证稳定性；
- AI 结构化建议 → 用户确认 → 写入目标（带审批/回滚）；
- 流式输出、聊天导出/导入；
- 目标上下文扩展到阶段/行动，但保持最小化授权；
- 发布签名与正式渠道打包。
