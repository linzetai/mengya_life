# Gate 3B-1 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**145 tests, 0 failures, 0 errors**

| 测试类 | 覆盖 |
|---|---|
| `AiConsentRulesTest` | endpoint 规范化、未授权、文案/类别版本、四类独立开关 |
| `AdvisorContextAssemblerTest` | 相关日记、无关日记、类别关闭、目标/进展来源、历史 epoch、预算、未授权 |
| `GoalProposalParserTest` | 合法三级树、重复 tempId、层级错误、循环、已有父目标、非法 JSON/schema、日期 |
| `AdvisorViewModelTest` | 未授权阻止、首次授权、来源保存、关闭日记/目标、epoch 历史隔离、换 endpoint、重试、取消、草案成功/失败 |
| `AdvisorRulesTest` | HTTPS、上下文预算、请求结构、输入校验 |
| `AdvisorConfigTest` | Key 加密/脱敏/删除、endpoint 校验 |
| `NetworkAdvisorEngineTest` | 401/429/超时/离线/非法响应 |
| 既有 Gate 2/3A 测试 | 数据层、导出、目标、趋势、格式 |

报告 XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`（已放入证据包）。
