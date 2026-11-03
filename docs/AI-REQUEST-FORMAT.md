# AI 模型请求结构说明（Gate 3A）

> 实现：`data/advisor/AdvisorEngine.kt`、`UrlConnectionHttpTransport.kt`、
> `domain/advisor/AdvisorRules.kt`、`ui/advisor/AdvisorViewModel.kt`

## 1. 适配器范围

Gate 3A 只实现一个 **OpenAI-compatible Chat Completions** 适配器：

```http
POST {base}/v1/chat/completions
```

用户填写的「API 服务地址」可以是：

| 用户输入 | 实际请求地址 |
|---|---|
| `https://api.openai.com/v1` | `https://api.openai.com/v1/chat/completions` |
| `https://proxy.example.com/openai/v1/` | `https://proxy.example.com/openai/v1/chat/completions` |
| `https://host/v1/chat/completions` | 原样使用 |

只允许 `https://`；拒绝 userinfo、query、fragment；`usesCleartextTraffic=false`。

## 2. 请求头

| Header | 值 |
|---|---|
| `Authorization` | `Bearer <用户 API Key>`（只在内存中；不写日志/DB/导出） |
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `User-Agent` | `MengyaLife/0.6` |

不跟随重定向（`instanceFollowRedirects=false`），避免密钥被带到其它主机；
3xx 直接返回错误。

## 3. 请求体

```json
{
  "model": "用户填写的模型名",
  "messages": [
    {"role": "system", "content": "系统提示词"},
    {"role": "system", "content": "【用户本次明确授权发送的目标摘要】…"},
    {"role": "user", "content": "更早的历史消息"},
    {"role": "assistant", "content": "更早的回复"},
    {"role": "user", "content": "本次问题"}
  ],
  "max_tokens": 800,
  "temperature": 0.6,
  "stream": false
}
```

- 不实现流式（`stream=false`）；
- 不发送 tools / function calling；
- 不发送用户数据库、日记、全部目标或 API Key 以外的配置。

### 消息顺序

1. `system`：角色是「长期成长顾问」，只有建议权，不修改数据，不给人生综合评分；
2. `system`：**可选**目标摘要（仅在用户确认后）；
3. 近期历史：最多 12 条、总字符预算 6,000（从新到旧保留，超限丢弃更早的）；
4. `user`：本次问题。

失败/取消的消息不会进入历史。

## 4. 目标上下文摘要

只有用户在选择目标并再次确认「本次将发送哪些个人数据」后才加入：

```text
【用户本次明确授权发送的目标摘要】
长期目标：<标题>
描述：<描述>
阶段目标：<标题…>
短期行动：<标题…>
最近进展：
- <日期> · <说明>（<数值> <单位>）
（未包含日记正文，也未包含其它目标。）
```

- 只取所选目标子树（阶段/行动/进展）；
- 不读日记 DAO；
- 不包含其它目标；
- 用户取消授权则不发送。

## 5. 响应解析

只接受：

```json
{"choices":[{"message":{"role":"assistant","content":"..."}}]}
```

- `content` 必须是非空字符串；否则返回「响应格式错误」，不伪造回复；
- 服务端错误体里的 `error.message` 仅用于提示，截断 160 字，不含请求头；
- 不把原始 JSON 整体展示给用户。

## 6. 错误映射

| 情况 | 代码 | 用户提示 |
|---|---|---|
| 非 HTTPS / 地址非法 | `invalid_endpoint` | 服务地址必须是有效的 HTTPS 地址 |
| 401 / 403 | `unauthorized` | API Key 无效或已过期 |
| 429 | `rate_limited` | 请求过于频繁或额度不足 |
| 5xx | `server` | 模型服务暂时不可用 |
| 连接超时（15s）/ 读超时（60s） | `timeout` | 请求超时 |
| DNS / 连接失败 / TLS | `offline` | 网络不可用或无法安全连接 |
| 非 JSON / 缺 choices / 空 content | `invalid_response` | 服务返回格式无法识别 |
| 其它 HTTP | `http` | HTTP 状态码 + 服务端简短消息 |
| 用户取消 | `cancelled` | 已取消，没有生成回复 |

所有错误都会落一条 `status=FAILED/CANCELLED` 的助手消息（内容为空），
界面显示错误与「重试」，**不会显示成 AI 回复**。
