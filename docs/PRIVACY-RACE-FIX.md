# Gate 3B-2 · 安全修复：endpoint 竞态、来源预算、草案重试

## 1. 服务地址竞态（P0）

### 旧问题

发送开始时用 UI 快照里的 endpoint 做授权校验，随后再从仓库读取 runtimeConfig 发请求。
如果两者之间用户改了服务地址，旧代码可能「用旧地址通过授权，却把请求发到新地址」。

### 修复

`AdvisorViewModel.startRequest`：

1. 捕获 `sendConfig = state.config` 与授权 epoch；
2. `runtimeConfig()` 返回后检查：
   - `runtime.endpoint == sendConfig.endpoint`
   - `configRepository.state.first().endpoint == runtime.endpoint`
   - `consent.isValidFor(runtime.endpoint)`
   - `consent.epoch == capturedEpoch`
3. 任一不满足：不调用引擎，保存 `CANCELLED`，提示「服务配置或授权已变化」；
4. 请求中监听 config flow；endpoint/model/hasKey/fakeEngine 任一变化立即取消请求；
5. 引擎返回后再次检查 config endpoint + consent epoch/recipient，变化则丢弃回复，保存 CANCELLED。

授权 `recipient` 是规范化 endpoint；换地址后 consent 失效，必须重新授权（Gate 3B-1 已实现）。

### 测试

`AdvisorViewModelTest`：

- `runtime endpoint 与授权接收方不一致时不发送`：UI 快照旧地址，runtime 被改成新地址 → `engine.calls == 0`，最后消息 CANCELLED；
- `请求中服务地址变化会取消，不保存旧回复`：引擎阻塞，保存新 endpoint → 请求取消，保存 CANCELLED，无成功回复。

两条均 PASS。

## 2. 上下文来源与预算（P0）

### 旧问题

- assembler 先收集 sources，再截断上下文字符串；被截断的记录仍计入来源；
- 仅 assembler 上下文受 3500 字限制，system/current/history 各自计算，极端情况总请求可能超 6000。

### 修复

- assembler 返回 `List<AdvisorContextEntry>`，每条记录单独带 source；不再提前拼成不可分割的大字符串；
- `AdvisorRules.buildRequest` 统一预算：
  - system + extraSystemPrompt + 当前问题 + 上下文条目 + 历史；
  - 只有真正放入请求的条目才进入 `usedSources`；
  - 历史从新到旧，超出预算即停止；
  - 返回 `contextChars/historyMessages/droppedContextEntries`；
- 助手消息保存 `builtRequest.usedSources`，不是候选 sources。

### 测试

`AdvisorRequestBudgetTest`：

- 小预算下丢弃部分条目，`dropped + used == candidates`，来源只在请求文本中存在；
- system/current/context/history 总字符 ≤ 预算；
- 无上下文时来源为空。

三条 PASS。

## 3. 草案失败重试（P0）

- `advisor_messages` 增加 `proposal_mode`；
- 草案解析失败保存 `SENT + proposalError + proposalMode=true`（保留原文作为错误证据）；
- 失败消息不删除；重试只追加新的助手消息，不重复用户消息；
- 重试读取 `failed.proposalMode`，保持 proposalMode，不会退化为普通聊天；
- 重复点击受 sending 保护。

单测：`草案解析失败后重试仍使用 proposalMode 且不重复用户消息` PASS。  
真机：真实模型草案失败后出现「重试草案」，重试仍走草案路径；DB 保留两条失败原文与重试记录。

## 4. 未覆盖

- 没有对 6000 字上限做真实模型长上下文压力测试；
- 端点评测依赖 JVM 可控制 Fake；真机仅做常规/真实模型验证。
