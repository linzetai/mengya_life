# 0.25.2.1 — 未完成问题与限制

1. **真实外部模型体验未验收**：本轮真实供应商模型调用数为 0。QA 完整路径使用隔离 `.qa` 包、合成 `QA 样例` 数据和本地 FakeAdvisorEngine；只证明 UI/数据流，不代表真实模型质量、服务端取消语义或线上延迟。
2. **Gradle connected task 的收尾工具缺失**：AndroidJUnitRunner 通过 ADB 直接执行 24/24 tests、exit 0；Gradle `:app:connectedQaAndroidTest` 的用例也全部执行且 XML failures=0，但 UTP `onAfterAll` 无法启动 build-tools 的 `aapt`，Gradle task 最终 exit 1。此项是 SDK/runner cleanup 问题，不是测试用例失败。
3. **恢复等待延迟**：沿用 RestoreWriteBarrier，HTTP 请求期间持有写许可；长请求可能延迟 Restore。协程取消无法证明服务端撤回已收到的请求；未扩展恢复框架。
4. **话题切换识别仍为短语规则**：已覆盖“换个话题/聊个别的/不谈刚才那个”等常见表达，但隐式或未覆盖的切换仍可能延续当前 Insight 线程；未引入语义分类器/新会话模型。
5. **孤儿旧草案来源不可恢复**：新草案有稳定 Insight pointer；旧版 JSON 数组会尝试从关联消息恢复。如果旧草案与相关消息都缺失/损坏，历史来源无法重建；普通旧版无 Insight 草案在顾问确认流程中保持兼容。
6. **早期 dataVersion 不透明**：0.25.2 及更早版本只保存不可拆分的哈希，无法准确区分来源事实变化与窗口变化；旧报告只显示保守说明，不声称用户改过事实。
7. **继承限制**：Care 数据库 claim 与 Android NotificationManager.post 不是同一事务；SAF 恢复选择/预览/确认流程仍缺独立真机验收；同 endpoint 恢复时是否重新授权仍需产品决策；自由文本诊断拦截是保守规则而非完整语义分类。
