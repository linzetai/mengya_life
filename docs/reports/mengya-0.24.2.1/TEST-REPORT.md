# 0.24.2.1 测试报告

- JVM：`./gradlew :app:testDebugUnitTest` → 361 tests PASS / 0 FAIL（含 RestoreCredentialPolicyTest 4）。
- 构建：`:app:assembleDebug :app:assembleQa` → BUILD SUCCESSFUL。
- lint：`:app:lintDebug` → BUILD SUCCESSFUL。
- Android instrumented（设备 2509FPN0BC / Android 17 / 127.0.0.1:37523）：
  `adb shell am instrument -w com.mengyalife.app.test/androidx.test.runner.AndroidJUnitRunner`
  → OK (16 tests)，含迁移/事务回滚/Runner 恢复交错/旧代次 0 行断言。
- 设备最小回归：本轮未重新跑通知/备份 UI；沿用 0.24.2 设备证据。
- BLOCKED：无。
