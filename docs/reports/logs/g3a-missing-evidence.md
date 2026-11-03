# Gate 3A 证据边界（已补齐）

## 结论

**PASS**。原先唯一的缺口——「有效 Key 的真实成功回复」——已由用户补齐：

- 2026-11-04 08:11，用户在正式包 `com.mengyalife.app` 的「设置 → AI 服务」
  输入自己的 Key（`https://api.deepseek.com` / `deepseek-flash`）；
- 顾问页发送「hi」，收到 DeepSeek 的真实回复；
- 正式包 Room 中保存：
  - `USER / SENT / "hi"`
  - `ASSISTANT / SENT / "你好。我是你的长期成长顾问…"`
- 证据：`g3a-shots/advisor-13-real-success.png`、`g3a-real-model-success.log`；
- 证据包不包含密钥、密文或请求头。

## 仍然存在的边界

- 真实成功样本目前只有一次「hi」；更多提示词、长回复、限流等仍由后续轮次覆盖。
- QA 离线引擎的回复明确标注，不冒充真实模型输出。
- 服务商可见问题与授权后的目标摘要，取决于其日志/留存政策。

## 如何复核

用有效 Key 在正式包发送任意问题，预期：
1. 顾问显示真实模型回复；
2. `advisor_messages` 新增 USER + ASSISTANT 两条 SENT 记录；
3. 重启后对话仍在。
