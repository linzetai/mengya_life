# Gate 3B-2 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**158 tests, 0 failures, 0 errors**

本轮新增/重点：

| 测试类 | 覆盖 |
|---|---|
| `AdvisorViewModelTest` | endpoint 竞态（runtime 与授权不一致、请求中换地址取消）、草案重试保持 proposalMode、双击 apply 防抖、部分/全部、撤销 |
| `AdvisorRequestBudgetTest` | system/current/context/history 共同预算、截断后来源准确 |
| `GoalProposalApplyRulesTest` | 完整三级、只选子节点、父目标删除、父层级、日期、长期父级 |
| `GoalProposalParserTest` | schema、重复 tempId、循环、父目标、非法 JSON、日期 |
| `AiConsentRulesTest` | 版本/接收方/类别 |
| 既有测试 | 上下文、规则、配置、引擎、导出、数据层 |

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`（已放入证据包）。
