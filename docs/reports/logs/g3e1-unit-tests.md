# Gate 3E-1 JVM 单元测试

命令：`./gradlew :app:testDebugUnitTest`

结果：**230 tests, 0 failures, 0 errors**

新增重点：
- InsightRulesTest：8 点趋势首尾来源、目标字段/日期版本；
- InsightParserTest：factIds/来源与事实关联、只引用 insufficient 时允许空来源、无关来源拒绝；
- WeeklyReviewRulesTest：本周/上周边界、跨年、无记录、单点、不同单位、到期/完成/取消、类别关闭、AI 全关仍本地、纠正记忆；
- WeeklyReviewServiceTest：同周期复用、codec 来源保留；
- 既有 G3D-2/G3C-2 全部通过。

XML：`app/build/test-results/testDebugUnitTest/TEST-*.xml`。
