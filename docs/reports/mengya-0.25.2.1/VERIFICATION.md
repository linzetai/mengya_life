# MengYa Life 0.25.2.1 — 验收记录

基线：`1f8908105874c187a535b95da5ebbd32b8ec5e5c`；versionName 0.25.2.1 / versionCode 38。

## 本地验证

| 项目 | 结果 | 说明 |
|---|---|---|
| `:app:testDebugUnitTest` | **PASS** | 430 tests，0 failures / 0 errors / 0 ignored。 |
| `:app:assembleDebug` | **PASS** | Debug APK 构建成功。 |
| `:app:assembleQa` | **PASS** | QA APK 构建成功。 |
| `:app:assembleQaAndroidTest` | **PASS** | Instrumented 测试 APK 编译/打包成功。 |
| `:app:lintDebug` | **PASS** | 0 errors / 40 warnings（已有 API/依赖/资源告警）。 |
| `:app:connectedQaAndroidTest` | **NOT RUN** | 用户在解锁交接中停止手机操作；遵照指示未再操控设备。Room instrumented tests 仅编译，未执行。 |

## AdvisorEngine.chat 调用计数

以下是 JVM 测试中 `ScriptedAdvisorEngine.calls` 对实际代码 `AdvisorEngine.chat()` 接口的计数，不是外部供应商调用量：

| 场景 | Advisor chat 次数 | 断言结果 |
|---|---:|---|
| 主回复后撤权、附加提取前 | 1 | 无附加调用/草案 |
| 附加计划提取期间撤权 | 2 | 第二次结果丢弃，无候选/自动应用 |
| 主回复后切 endpoint 或 model | 1 | 不向旧配置发起附加请求 |
| Restore 排队进入附加提取 | 2 | generation 变化后不落候选 |
| 取消计划/进展附加提取 | 每例 2 | 不落候选、不调用记录 apply |
| 附加计划提取超时 | 2 | 不回退创建草案 |
| JOURNAL/PROGRESS/GOALS 分别未授权 | 每例 1 | 仅主请求；被禁止类别数据不进入请求 |
| 跨 6 轮/15 分钟的同一 Insight 对话 | 10 | 稳定来源仍进入草案 |
| Insight 依据超过上下文预算 | 0 | handoff 的 Advisor 请求在发出用户消息前拒绝；准备测试建议的独立 InsightService Scripted 调用 1 次 |

`NetworkAdvisorEngineTest` 使用 `FakeTransport.calls` 断言单个 engine chat 对应一个模拟 POST。**真实外部模型调用数为 0**；未使用 QA FakeEngine 结果宣称真实模型通过，也未验证线上模型质量、真实 HTTP 延迟或服务端取消语义。

## UI / 设备状态

- 旧版截图：[0.25.2 旧版顾问对话](../mengya-0.25.2/screenshots/03-advisor-discussion-qa.png)，可见旧消息中 fact IDs/内部指令、来源统计和逐条时间标签。
- 新版代码与 JVM 测试覆盖简短自然 Handoff、可展开依据、精简时间标签与稳定来源 metadata；历史消息不会被重写。
- **新版截图和真机完整流程未完成**：设备操作已被用户停止，本轮不能提供前后截图或声称建议→handoff→对话→草案编辑/确认→Today/Trajectory→重启通过。重新允许设备操作后需补做 `connectedQaAndroidTest` 和真机全路径。
