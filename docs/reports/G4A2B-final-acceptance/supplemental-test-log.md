# G4A-2B 最小补测日志

**Build binding:** QA APK SHA `c2fcdfc0d50b251c286291f483d52eade2845491cee446b2c925660906feaab1`; Debug SHA `a77858ad0a84133df9dc2ffeca7cbdc5fd9e015b1dd3fa7a3ca642dad2d829f3`. QA运行要求 `com.mengyalife.app.qa`，所有数据均为QA合成数据。

## 当前APK上的独立PASS

| 测试 | 结果 | 耗时/备注 |
|---|---|---|
| QA前置 | 11/11 PASS | runner report `20261106-164126`；package/version/debuggable与QA横幅均通过。 |
| Fresh v12 | 6/6 PASS | `fresh-v12-6of6.log`；首次启动后Room version=12，`app_meta=(null,0,null)`，无Gate，页面进入READY。 |
| Weekly AI READY L2 | 22/22 PASS | `weekly-ai-ready-22of22.log`；QA fake engine，仅成功路径，非95项Gate全状态矩阵。 |
| DB不可读启动 | 12/12 PASS | `db-unreadable.log`及case report；文件权限拒读时维护页显示，目标/AI入口不显示，Room业务表行数前后不变；QA清理PASS。case report正确保留`fullLevelSatisfied=false`。旧SHA2951尝试曾因Room Flow触发SQLiteCantOpen crash，已由startup gate修复。 |
| A→B→A E2E | 39/39 PASS | `restore-e2e-39of39.log`；198.30s。 |
| Migration v10/v11→v12 | 26/26 PASS | `migration-26of26.log`；Room迁移数据保留、v12幂等。 |
| g3b1 AI consent | 47/47 PASS | `g3b1-47of47.log`；使用QA离线引擎。 |
| Restore failure cases | 全部PASS且逐case独立 | R2 12/12、R3 19/19、R4/R5/R6各15/15、R7 15/15、SETTINGS_FAIL 20/20；日志`restore-*.log`。总case耗时约655s。 |
| L0 full JVM/static | 303/303 PASS | `l0-jvm-303-summary.md`；7.7s，未启动设备。 |

这些direct case从同一安装的QA APK运行；`verify.sh` run `20261106-164126`记录installed/reused QA SHA=c2fc。其后没有重新安装不同APK；App源码SHA也未变化。各脚本独立清QA数据，matrix中某case失败可单独重跑。

## 证据复用与偏差

- Port 34225旧partial L2（run `20261106-111828`）的preflight/migration/g3b1/E2E/R2-R6原始日志保留，但因SIGTERM没有`report.json`，不能单靠它证明该run的精确commit/APK。
- 因后续改了启动维护Gate，E2E、migration、g3b1、R2-R7以及本次缺项均在QA SHA c2fc上重新验证；未为生成combined runner报告而全量重跑。
- 一次标准`--case FRESH_V12` runner因QA应用离开前台导致UI前置定位超时，实际测试未开始；另一run同QA SHA的前置11/11通过，Fresh脚本随后以直跑方式6/6通过。该前置超时记录保留，不计PASS。
- DB不可读probe还向QA Activity请求一次性Weekly Worker；业务表计数未变，Worker外层Barrier由L0源码策略及单测覆盖。不过WorkManager的`WorkSpec.run_attempt_count`没有获得可读快照，故没有宣称Worker重试计数PASS。

## 维护态写入与Gate释放

- 当前L0的`RestoreWriteBarrierTest`、`GoalRepositoryTest`、`WeeklyAiTest`验证UNKNOWN/Maintenance拒绝普通目标写及AI DAO/引擎调用；静态契约核查Worker调用被Barrier包裹。
- `RestoreGate.clear()`删除失败单测PASS；Coordinator静态策略测试确认Gate/Journal清除成功后才调用`finishReady`，clear异常转MAINTENANCE；`SETTINGS_FAIL`真机20/20验证Gate保持、失败不报告成功、重启后恢复。
- **尚未**在Coordinator内部通过专门fault injector精确让`gate.clear()`失败；当前结论依赖Gate单测+Coordinator顺序检查+相邻收敛故障runtime用例。
- `MengyaApplication`启动后仍调用WorkManager `sync`；维护期可能修改WorkManager内部调度状态，但Worker业务DB/AI/通知路径由Barrier阻断。若要求WorkManager队列本身在维护期零变化，需要后续加状态守卫和专测。
