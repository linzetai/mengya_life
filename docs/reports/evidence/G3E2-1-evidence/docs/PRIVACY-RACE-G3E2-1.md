# AI 周回顾隐私竞态

- 绑定：consent epoch/类别、recipient、endpoint/model/configHash、输入 dataVersion、自动开关。
- 请求前：重新读取并校验；失败删除 PENDING，不占用预算。
- 发送前：`markSent` 原子把 PENDING→SENT；只有成功者调用 engine。
- 请求后：再次校验；变化写 FAILED（结果隔离），绝不 READY。
- 取消：写 FAILED“服务端是否接收未知”，预算占用，不自动重试。
- 已发送字节无法撤回；本设计只保证不把过期结果写成最新。
