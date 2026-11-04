# AI 隐私竞态收尾

- Prompt 不再要求模型输出 sources；模型只选 factIds。
- 客户端从本轮已授权 facts 构造 sources；未发送/未授权事实不可引用；模型 sources 被忽略。
- 绑定 consent epoch/categories/recipient、config/endpoint/model/configHash、AI 输入 dataVersion、自动开关；chat 前和 markSent 后各校验一次。
- 未发送 PENDING 可释放；SENT/FAILED/READY 占用预算；已发送不承诺撤回。
- 过期结果写 FAILED/STALE，不写 READY。
