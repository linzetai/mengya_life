# Gate 3E-2.1 JVM 测试

命令：`./gradlew :app:testDebugUnitTest`
结果：**241/241，0 failures**。

新增：请求前撤权/切 endpoint/改数据 0 calls；请求中改数据无 READY；并发一次；prompt 合法 JSON；insufficient 空来源；缓存数据版本变化。
真实模型与真机 UI 未由 JVM 替代。
