# Gate 3C-1 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**180 tests, 0 failures, 0 errors**

新增/重点：

| 测试类 | 覆盖 |
|---|---|
| `DiaryExtractionProtocolTest` | 合法事实/推测、evidence 必须来自原文、医学诊断拒绝、schema/category/confidence 非法 |
| `DiaryExtractionTest` | 未配置/未授权跳过、首次提取、同版本幂等、编辑重提取、失败重试、删除、撤回、频率限制 |
| `GoalProposalIdsTest` | tempId 格式、映射往返、selected、旧数组格式、appliedGoalIds |
| `AdvisorContextAssemblerTest` | 线索来源、原日记去重、纠正优先、JOURNAL 关闭 |
| `QaFaultInjectorTest` | 创建/撤销注入索引 |
| 既有测试 | 安全竞态、预算来源、审核/apply、Room/导出 |

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`（放入证据包）。
