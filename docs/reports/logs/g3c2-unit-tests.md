# Gate 3C-2 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**191 tests, 0 failures, 0 errors**

重点：

| 测试类 | 覆盖 |
|---|---|
| `DiaryExtractionTest` | 授权撤回 gate、旧版本结果、插入失败回滚、网络失败、编辑/删除/频率/幂等 |
| `DiaryExtractionProtocolTest` | evidence 原文、事实/推测、诊断拒绝、非法 JSON |
| `GrowthMemoryTest` | 多篇合并、更新 version、不相关不合并、来源失效、纠正优先、删除墓碑 |
| `AdvisorContextAssemblerTest` | 线索/记忆来源、原日记去重、纠正优先、JOURNAL 关闭 |
| `AdvisorViewModelTest` | endpoint 竞态、预算来源、proposal 重试、apply 防抖 |
| `GoalProposalIdsTest` / 既有测试 | JSON 映射/tempId/appliedGoalIds、审核/事务 |

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`（放入证据包）。
