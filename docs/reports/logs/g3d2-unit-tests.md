# Gate 3D-2 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**217 tests, 0 failures, 0 errors**

新增/重点：
- InsightServiceTest：请求前撤回、JOURNAL 关闭、endpoint 切换、编辑进展、请求期间改数据；断言 engine.calls 与 DB READY。
- InsightRulesTest：单目标无进展不足、趋势最新来源、目标描述/日期分桶、记忆内容/纠正优先。
- InsightParserTest：factIds 必填/白名单、来源白名单。
- 既有 207 条全部通过。

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`。
