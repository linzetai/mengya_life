# MengYa Life 0.25.2 — 未解决问题

1. **`engine.chat()` 持有恢复写屏障**：完整网络请求仍在 `RestoreWriteBarrier.withWrite` 内。慢请求会延迟恢复；真实网络取消、超时和恢复最长等待仍需单独量化，本轮不扩展恢复框架。
2. **通知 claim/post 语义**：claim 成功而 Android `NotificationManager.post` 失败时可能丢通知；数据库 claim 与系统通知不是一个事务。
3. **SAF 恢复用户流程**：真实文件选择、预览及确认仍缺真机验收。
4. **同 endpoint 恢复授权策略**：现行代码在 endpoint 相同的情况下保留 consent；是否应因恢复数据而要求重新授权仍需产品决策。
5. **模型文本安全**：结构化 Insight 会拒绝常见诊断性标签；自由顾问回复使用明确 system policy 和输出拦截，但自然语言过滤不是完整医疗语义分类器，真实模型体验仍需审查。
6. **外部模型体验**：真机 walkthrough 使用 QA fake engine 和显式标记的 QA 样例，不代表真实用户进展或外部模型回答质量。正式数据与正式包未操作。
7. **远端 push**：本地提交已建立；GitHub SSH 443/22 均在 banner exchange 阶段超时，HTTPS 只读可达但没有可用的 push 凭据（非交互 push 报无 username）。本轮未完成远端 push；远端 master 最后确认仍为 `419fc111a0c2bb0db44791f5719049b6ba2b4811`。
