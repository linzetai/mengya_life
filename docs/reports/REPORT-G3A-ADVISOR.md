# Gate 3A 报告 · 真实 AI 顾问基础闭环

| 项 | 值 |
|---|---|
| 项目 | 萌芽人生 / Mengya Life |
| 包名 | `com.mengyalife.app`（QA：`com.mengyalife.app.qa`） |
| 版本 | **0.6.0-g3a**（versionCode 6） |
| 轮次 | Gate 3A：BYOK 模型配置 + 真实顾问对话 + 本地持久化 |
| 执行时间 | 2026-11-03 17:29 – 2026-11-04（Asia/Shanghai） |
| 结论 | **PASS**（真实模型成功回复已由用户输入有效 Key 验证，见 §1） |

---

## 1. 执行结论

**PASS**。

Gate 3A 的功能闭环已实现并验证：

- 设置页可配置 HTTPS 地址、模型、API Key，可保存、修改、测试连接、删除密钥；
- Key 由 Android Keystore AES/GCM 加密，明文不在 Room / DataStore / 日志 / 导出中出现；
- 顾问页从演示聊天改为真实请求链路：用户消息落库 → 构造上下文 → 调用引擎 →
  成功保存回复 / 失败保存 FAILED 消息（不伪造回复）→ 重试 → 取消 → 重启恢复；
- Room 从 v2 迁移到 v3，新增 `advisor_messages`，旧日记/目标/进展完整保留；
- 目标上下文必须用户选择 + 发送前确认；默认不发送日记或全部目标；
- 模型输出只作为文本建议，代码中没有自动修改业务数据的路径。

**真实模型成功回复已补齐**：用户在正式包设置页输入自己的 Key
（`https://api.deepseek.com` / `deepseek-flash`），于 2026-11-04 08:11
发送「hi」，收到 DeepSeek 的真实回复，并保存到正式包 Room：

```text
USER      SENT  "hi"                          createdAt=1793751106813
ASSISTANT SENT  "你好。我是你的长期成长顾问…"  createdAt=1793751108613
```

- 证据：`g3a-shots/advisor-13-real-success.png`、`g3a-real-model-success.log`；
- 配置证据：DataStore 中 endpoint/model 正确、`encrypted_api_key` 存在、
  `use_fake_engine=false`；未读取或导出任何密钥；
- 真机仍对无效测试 Key 验证了 **HTTP 401**，网络错误映射成立。

因此验收第 4、5 条改为 PASS；QA 离线引擎的回复仍然明确标注，不冒充真实输出。

---

## 2. 完成功能

### 2.1 BYOK 模型配置（设置 → AI 服务）

- API 服务地址：只允许 HTTPS；支持 `https://host/v1` 或完整 `/chat/completions`；
- 模型名称；
- API Key：遮挡输入；保存后不回显，只能替换或删除；
- 保存配置；修改配置；
- 测试连接：真实发送最小请求，成功/失败都有明确文案；
- 删除密钥：二次确认；同时删除 DataStore 密文与 Keystore 别名；
- 隐私说明：请求直达用户填写的服务，无自有后端；
- QA 构建额外提供「离线测试引擎」开关（正式包不存在）。

### 2.2 真实顾问对话

- 输入、发送、等待状态；
- 真实 `POST {endpoint}/v1/chat/completions`；
- 成功显示模型回复；
- 失败显示错误与「重试」，失败消息内容为空，不伪装成回复；
- 用户取消：写入 `CANCELLED` 消息，不产生假回复；
- 单会话聊天记录持久化，冷启动恢复；
- 未配置模型时显示明确引导；
- 发送期间禁止重复发送；重试不新增重复用户消息。

### 2.3 个人数据上下文授权

- 用户可在顾问页选择一个长期目标；
- 发送前弹出「本次将发送哪些个人数据」：
  目标名称/描述、阶段/行动数量与标题、最近进展摘要；
- 明确列出「不发送：日记正文、其它目标、完整数据库」；
- 确认后才发送；取消则不发送；
- 消息记录 `context_goal_id`；重试不会重复附带目标摘要。

### 2.4 数据库

- v3：`advisor_messages`（id/role/content/created_at/status/error_code/error_message/context_goal_id）；
- `MIGRATION_2_3` 只新增表与索引；
- 保留 1.json / 2.json / 3.json；禁用破坏性迁移；
- 真机覆盖安装后 `user_version=3`，旧日记逐字段不变。

---

## 3. 技术决策

| 决策 | 理由 |
|---|---|
| 单一 OpenAI-compatible 适配器 | 先保证链路可靠，不引入多厂商路由 |
| `HttpURLConnection` + 自定义取消 | 不引入 OkHttp 等额外依赖；取消时断开连接 |
| 不跟随重定向 | 防止 `Authorization` 被带到其它主机 |
| 只允许 HTTPS + `usesCleartextTraffic=false` | 避免明文传输密钥 |
| Key 用 Android Keystore AES/GCM | 密钥不可导出；删除别名后旧密文不可解 |
| 配置存 DataStore、消息存 Room | 配置非敏感、消息需要查询/迁移 |
| 引擎与仓库解耦 | `AdvisorEngine` 不接触 DAO；可替换 Fake/Network |
| 重试复用用户消息 | 避免重复扣费；不出现两条相同提问 |
| 上下文按「目标+单位无关」摘要 | 不发送日记；只发送用户确认的最小摘要 |
| 不流式 | 第一版优先稳定与错误处理 |
| QA 离线引擎 | 无 Key 时可自动化验证 UI/持久化；正式包不创建 |

---

## 4. BYOK 与密钥安全设计

1. **加密**：Android Keystore 中生成 AES-256/GCM 密钥（别名按构建类型隔离）；
   每次加密随机 IV，存储 `Base64(iv):Base64(ciphertext)`。
2. **存储**：密文写入独立 DataStore（正式 `mengya_ai`，QA `mengya_ai_qa`）；
   不写 Room，不写日志，不进入导出。
3. **展示**：只显示 `hasKey` 布尔值；输入框 `PasswordVisualTransformation`；
   保存后清空输入，不回显。
4. **删除**：清除 DataStore 密文 + 删除 Keystore 别名；旧密文恢复后也无法解密。
5. **验证**：
   - JVM：假加密器断言存储不含明文、删除后 runtime null；
   - 真机：QA 测试 Key 写入后，DataStore 文件、Room、应用进程日志、导出 JSON
     均不含明文；删除后 `hasKey=false`、测试连接要求重新填写。
6. **权限**：只新增 `INTERNET`；无存储/通知/位置等权限；
   Android manifest 禁止明文 HTTP。

> 调试签名 `keystore/mengya-debug.keystore` 口令公开，仅用于开发/覆盖安装；
> 发布必须使用独立发布签名。

---

## 5. AdvisorEngine 与对话架构

```text
AdvisorScreen (Compose)
   │ 发 Action，只读 UiState
   ▼
AdvisorViewModel
   │ 构造请求 / 授权确认 / 重试 / 取消 / 历史预算
   ├── ConversationRepository（Room，唯一写 advisor_messages）
   ├── AdvisorConfigRepository（DataStore + Keystore）
   ├── AdvisorContextSource（GoalRepository 只读摘要）
   └── AdvisorEngine
         ├── NetworkAdvisorEngine → HttpTransport → HTTPS
         └── FakeAdvisorEngine（仅 QA）
```

- `AdvisorEngine` 接口：`chat()` / `testConnection()`；
- `HttpTransport` 接口：生产 `UrlConnectionHttpTransport`，测试 Fake；
- Engine 只接收 `AdvisorChatMessage`，没有 DAO、没有目标写入口；
- 所有持久化在 ViewModel/Repository；Compose 不直接访问数据层。

### 上下文预算

- 最多 12 条历史，总字符预算 6,000；
- 最近消息优先；超限丢弃更早的；失败/取消消息不进入历史；
- 当前问题单独放最后；
- 不做长期记忆压缩（Gate 3A 不做）。

### 请求 / 错误

详见 `docs/AI-REQUEST-FORMAT.md`：非 HTTPS、401、429、5xx、超时、离线、
格式错误分别映射为可读错误；错误不显示假回复。

---

## 6. 个人数据授权机制

默认发送：当前问题 + 最近对话。

默认不发送：日记正文、全部目标、全部进展、完整数据库。

可选目标：

1. 顾问页点「选择」→ 选择一个长期目标；
2. 发送时弹出授权摘要（目标/阶段/行动/最近进展数量 + 不发送声明）；
3. 用户确认 → 本次请求附带该目标摘要，消息记录 `context_goal_id`；
4. 取消 → 不发送；重试不自动重复附带。

模型权限边界：只有建议权；不自动新增/修改/删除目标、日记、进展；
不生成「人生综合评分」。代码中 Engine 返回文本后只写 `advisor_messages`，
没有任何业务表写操作。

---

## 7. 数据库变更与迁移

从 v2 → v3，只新增：

```sql
CREATE TABLE `advisor_messages` (
  `id` TEXT PRIMARY KEY NOT NULL,
  `role` TEXT NOT NULL,
  `content` TEXT NOT NULL,
  `created_at` INTEGER NOT NULL,
  `status` TEXT NOT NULL,
  `error_code` TEXT,
  `error_message` TEXT,
  `context_goal_id` INTEGER
);
CREATE INDEX `index_advisor_messages_created_at` ON `advisor_messages` (`created_at`);
```

- 不触碰 `journal_entries` / `goals` / `goal_checkins`；
- 保留 `1.json` / `2.json` / `3.json`；
- `fallbackToDestructiveMigration()` 仍未使用；
- 真机结果：正式包安装后 `user_version=3`，
  `journal_entries` 两行逐字段不变；QA 包同样迁移成功。

---

## 8. 验收逐条对照

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 设置页完成模型配置 | PASS | `g3a-verify-advisor.log`、设置截图 |
| 2 | Key 加密存储，不在日志/导出 | PASS | DataStore 密文检查、日志检查、导出检查 |
| 3 | 测试连接成功/失败 | PASS | QA 引擎成功；真实 HTTPS 401 |
| 4 | 有效配置完成真实模型请求 | PASS | 用户 Key + DeepSeek 真实回复；另验证 401 |
| 5 | 顾问显示真实模型回复 | PASS | 真机截图 + Room 记录 |
| 6 | 对话重启后保留 | PASS | 真机重启 + DB |
| 7 | 401/429/超时/离线反馈 | PASS | 真机 401 + QA 模拟 429/timeout/offline |
| 8 | 重试不产生重复用户消息 | PASS | 真机用户数不变 + JVM 测试 |
| 9 | 选择目标上下文 | PASS | 真机目标选择 + DB context_goal_id |
| 10 | 发送前确认范围 | PASS | 授权弹窗截图与取消路径 |
| 11 | 默认不上传日记正文 | PASS | 请求构造无日记 DAO；导出/上下文检查 |
| 12 | AI 不直接修改 Room 业务数据 | PASS | 代码路径 + 目标/进展数量不变 |
| 13 | Room 升级旧数据保留 | PASS | 正式包 v2→v3，日记逐字段一致 |
| 14 | 正式包/QA 隔离 | PASS | 包名、库名、DataStore、Keystore 别名 |
| 15 | 真机编译/安装/冷启动 | PASS | 安装日志、冷启动截图 |
| 16 | 无崩溃 | PASS | crash buffer 为空 |
| 17 | APK/哈希/源码/证据 | PASS | §9 与证据包 |

---

## 9. 真机测试与 APK 哈希

| 项 | 值 |
|---|---|
| 设备 | Xiaomi 2509FPN0BC / Android 17 / arm64-v8a |
| 正式 APK | `artifacts/mengya-life-0.6.0-g3a-debug.apk` · 11,256,370 B · `cc4ac2af2f0efe4aa37a14557e1f6fa566d5fb138f42721dd27b49fdf63470a4` |
| QA APK | `artifacts/mengya-life-0.6.0-g3a-qa-test.apk` · 11,256,402 B · `4c8bed2a9f2fffd9c70813ee84f67aa5a8ce908f0d8cdc078342aa3f08c4fc73` |
| 安装 | `adb install -r -t`，覆盖旧版成功（签名固定） |
| 顾问脚本 | `g3a-verify-advisor.log`：**59/59** |
| 真实网络脚本 | `g3a-verify-network.log`：**10/10**（真实 401） |
| 真实成功回复 | 用户输入有效 Key，DeepSeek 回复并落库（08:11） | `g3a-real-model-success.log` + `advisor-13-real-success.png` |
| 回归 | settings 38、diary 25、goals 64、trends 73、navigation 28、prod 50、export 49 |
| 真机断言合计 | **396/396**（59+10+38+25+64+73+28+50+49） |
| JVM | **126/126** |
| 安装/冷启动 | `g3a-install.log` / `g3a-launch.log`：`Status: ok`、`COLD` |
| 设备 APK 哈希 | `g3a-apk-hash.log`，与交付文件一致 |
| 证据包 | `G3A-evidence.zip`（日志、56 张截图、源码快照、导出样本与清单） |
| 截图 | 56 张（顾问关键路径 + 真实回复 + 回归） |

> 真实模型成功回复已由用户输入有效 Key 验证；QA 引擎回复不冒充真实输出。
> 真机断言合计 396 条（顾问 59、真实网络 10、回归 327），另加 1 条真实成功回复证据。

---

## 10. 风险、未完成事项与 Gate 3B 建议

### 风险

1. 真实成功样本目前只有一次「hi」（DeepSeek）；更多提示词/长回复仍未覆盖。
2. 服务商可能记录用户问题与授权后的目标摘要；其留存政策不在应用控制内。
3. 无证书固定；企业代理/恶意 CA 仍可能截获。
4. 调试签名公开，不能用于发布。
5. 无流式输出；长回复等待期间只能取消/重试。
6. 聊天记录不随导出备份；卸载会丢失。
7. 单位/目标摘要为纯文本拼接，未做结构化工具调用。

### Gate 3B 建议

1. 用更多真实提示词与长回复验证稳定性、超时与取消。
2. 结构化建议：AI 输出「建议行动」JSON → 用户确认 → 写入目标系统（带审批与回滚）。
3. 流式输出与更细的超时/重试策略。
4. 聊天记录导出（可单独加密）与导入。
5. 上下文选择扩展（阶段/行动），但继续默认最小化。
6. 多会话/会话标题（非本轮）。
7. 发布签名与正式渠道打包。
