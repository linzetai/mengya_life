# Gate 3D-1 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**207 tests, 0 failures, 0 errors**

新增/重点：

| 测试类 | 覆盖 |
|---|---|
| `DiaryExtractionTest` | 请求前配置变化、日记编辑、授权撤回 gate、旧结果隔离、插入失败回滚 |
| `GrowthMemoryValidityTest` | 编辑/删除来源后旧记忆不可用 |
| `GrowthMemoryTest` | 多篇合并/版本/纠正/墓碑/来源失效 |
| `InsightRulesTest` | 同单位趋势、不同单位不比较、数据不足、职业记忆来源、数据版本 |
| `InsightParserTest` | 来源白名单、越权来源、非法 JSON、来源为空 |
| `InsightServiceTest` | 缓存/不重复调用、解析失败保留 facts、数据变化旧缓存不匹配 |
| 既有 | 顾问竞态/预算/审核、导出、数据层 |

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`。
