# Gate 3 调度与反馈（代码完成，设备待验）

- 时间偏好：Runner 在 SEND 前检查 TIME window_note（SUN 20:00，允许 ±2h）；偏离则 DEFER，不投递。
- nextWindowStart/End：候选进入前读取最近决策；未到 start 跳过，超过 end 标 EXPIRED；到期不补发。
- “稍后”：Receiver 写 feedback DEFERRED，机会回 PENDING；24h 内 Runner 跳过；之后仍需全量授权/静音/频控。
- “不再提醒”：Receiver 用 goAsync，写 feedback SILENCED + status DISMISSED + care_preferences SILENCED（topic 级）。
- 授权撤回：BoundCareContext 在请求前/后拦截；旧预约不会发送。
- 验收：T01/T02/T03/T04/T05/T07 代码路径完成；T06 goAsync 完成；均未真机验证。
