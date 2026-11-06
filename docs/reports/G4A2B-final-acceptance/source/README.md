# 审计diff说明

- `restore-write-barrier.patch`：从G4A-2B基线提交 `3fb6781b955163994d891b98ff5f1d12c30692fc` 到最终源码，覆盖 `RestoreWriteBarrier`、`RestoreCoordinator`、Worker、AppContainer与所有写入口。
- `test-selection-and-scenario.patch`：测试映射、L2影响规则、单case选择器、Gate/Release限制、未知路径扩选与 targeted JVM/QA脚本。
- 为使仓库 `git diff --check` 严格通过，仅对**审计副本**移除了行尾空白；不用于机械应用patch。仓库源文件未因此改动。
