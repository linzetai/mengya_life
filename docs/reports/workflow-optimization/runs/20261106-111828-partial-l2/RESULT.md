# Port 34225 L2 真机运行：用户暂停，结果不完整

## 已执行并通过

- QA 前置：11/11。
- JVM：Gradle `testDebugUnitTest` 成功；runner总报告因 SIGTERM 未生成。
- 迁移：v10→v12、v11→v12合计26/26。
- AI consent：g3b1 47/47。
- A→B→A恢复E2E：39/39，379.77s；快照、核心8表、Key/consent/自动发送重置、weekly AI清空、重启状态均通过。
- R2-R6：失败/崩溃断言全部显示 PASS（R2事务回滚；R3 Key删除失败后重启收敛；R4/R5崩溃收敛；R6 Journal丢失）。

## 未完成/阻断

- R7：进入场景时 `启动B+安全+故障` 断言失败，虽然后续检查显示 B 夹具 10 表非空；R7 的 Gate 缺失恢复断言尚未执行。
- fresh DB与 weekly AI L2 smoke 尚未开始。
- 用户要求暂停时，runner 在 R7 场景期间被 SIGTERM；总耗时报告/收尾清理未生成。估计已占用设备约23分钟，具体总耗时无法从被中止任务报告确认。
- 由于默认 `pm clear` 清理步骤未运行，QA包可能残留部分测试假数据。生产包/生产数据未触碰。暂停后未再对手机执行任何命令。

## 修复记录

这次真机复验发现两项 P0：
1. 快照空间预检在 `noBackup/restore` 尚未创建时直接 `StatFs`，新设备返回 ENOENT；现改为查询最近存在的父目录，并加 JVM 路径测试。
2. 恢复的 exclusive barrier 将 `ready=false` 检查放在授权 Permit 前，导致恢复内部 reset/config/settings 写也被拒绝；现改为允许继承 exclusive permit 的收敛写，并加 barrier 单测。

修复后 A→B→A E2E 39/39 与 R2-R6通过；R7及剩余项目没有最终完整报告，整轮仍为 **INCOMPLETE / NOT PASS**。原始日志在本目录 `logs/`。

## 下一轮提速措施

现在可以单独运行：

```sh
./scripts/verify.sh --module restore-validate --level integration --case R7
```

该case只执行R7，明确延期迁移/E2E/fresh DB，不会重放已通过的R2-R6。backup的WRITE、weekly的FAILED_401等也有单case入口。case PASS会报告`CASE PASS (suite deferred)`，并保持`fullLevelSatisfied=false`；目前这些case只完成dry-run和静态策略自检，没有在设备上复跑。