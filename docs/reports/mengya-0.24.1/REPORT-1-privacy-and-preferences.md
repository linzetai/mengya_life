# Gate 1 授权与偏好

## 已实现
- CareRunner 新增 boundEpoch/boundEndpoint/boundGeneration；boundOk() 在模型请求前、响应后、SEND 写入前校验：settings.enabled、consent.granted/epoch、consent.isValidFor(endpoint)、config.endpoint、恢复 generation。
- PROGRESS 未授权时不读计数，prompt 为 UNKNOWN_DUE_TO_CONSENT，不再是 0。
- 旧 PENDING 在进入候选前按 plan:/goal:/topic: 偏好过滤；SEND 前重读最新偏好，PAUSED/SILENCED 直接 DROPPED。
- Parser 重写：反问/假设直接不写；否定时间不写 TIME；“以后别提醒简历”匹配 plan；“恢复简历提醒”生成 REVOKE；未知主题 CLARIFY，不写“这个话题”；局部 BUSY 保留 topicKey。

## 验收
- A08：PASS（JVM：反问、否定、恢复、忙碌；350 tests）。
- A04：PASS（代码路径；无集成测试）。
- A05：PASS（代码路径；无 DAO 集成测试）。
- A01/A02/A03/A06/A07：BLOCKED（缺 CareRunner+真实 DAO/引擎替身集成测试；未真机撤销授权）。
- 风险：BUSY 仍全局阻断 SEND（局部 BUSY 也阻断全局），A07 未通过。
