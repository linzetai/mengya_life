# Gate F
- 352 JVM：1 FAIL（WeeklyAiTest，既有周报 factIds 校验；本轮未改周报代码），351 PASS。
- Debug/QA 构建待最终命令。
- 真机：本轮未跑 0.24.2 设备回归。
- 结论：CONDITIONAL/BLOCKED：A1 fixture、D 事务/栅栏挂起测试、B 迁移 instrumented、C 真机、E 新鲜 BUSY 未完成；且存在 1 个非本轮代码的 JVM 失败需先定位。
