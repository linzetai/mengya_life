# G4A-2B 最终验收状态

## 最终状态

**NOT PASS。** 当前 QA SHA `c2fcdfc0…`上的核心恢复矩阵、Fresh、Weekly、DB不可读、migration、g3b1与JVM均取得PASS；但Coordinator内部Gate-clear故障注入与WorkManager `WorkSpec` attempt状态未直接验证，且启动维护期 `WeeklyReviewScheduler.sync`仍会更新WorkManager内部状态。没有把单case或partial runner伪装成完整suite PASS。

## 证据矩阵

| 场景 | 当前证据 | 来源/状态 |
|---|---|---|
| QA / Debug APK | QA `c2fcdfc0d50b251c286291f483d52eade2845491cee446b2c925660906feaab1`；Debug `a77858ad0a84133df9dc2ffeca7cbdc5fd9e015b1dd3fa7a3ca642dad2d829f3` | `docs/reports/G4A2B-final-acceptance/SHA256SUMS`；同SHA绑定当前补测 |
| 静态 + JVM | **303/303 PASS** | L0 summary `docs/reports/G4A2B-final-acceptance/logs/l0-jvm-303-summary.md`；不启动设备 |
| QA preflight | **11/11 PASS** | run `20261106-164126`，同QA SHA c2fc |
| v10/v11→v12迁移 | **26/26 PASS** | 当前QA SHA c2fc，`logs/migration-26of26.log` |
| g3b1授权/隐私 | **47/47 PASS** | 当前QA SHA c2fc，`logs/g3b1-47of47.log` |
| A→B→A E2E | **39/39 PASS，198.30s** | 当前QA SHA c2fc，`logs/restore-e2e-39of39.log` |
| R2 / R3 | **12/12、19/19 PASS** | 当前QA SHA c2fc；各自独立运行，不回放前一case |
| R4 / R5 / R6 / R7 | **各15/15 PASS** | 当前QA SHA c2fc；Gate/Journal强停及重启路径逐case运行 |
| Convergence settings failure | **20/20 PASS** | 当前QA SHA c2fc；Gate保持、错误不报成功，重启后恢复 |
| Fresh v12首次启动 | **6/6 PASS** | 当前QA SHA c2fc；`app_meta=(null,0,null)`，Gate缺失，待READY后可用 |
| Weekly AI L2 READY | **22/22 PASS** | 当前QA SHA c2fc；仅QA fake engine，含来源及无Key泄漏断言 |
| DB不可读启动 | **12/12 PASS** | 当前QA SHA c2fc；维护屏/无业务与AI入口/DB表计数不变/清理PASS |
| Worker/普通写/AI | **Barrier单测与静态策略PASS；业务表无副作用** | GoalRepository与WeeklyAI单测；DB不可读probe业务表前后相同。WorkManager实际attempt_count未观测。 |
| Gate清除失败 | **单测+静态策略+相邻runtime故障PASS** | `RestoreGate.clear()`会抛错；Coordinator仅在Gate/Journal clear后finishReady，异常路径进MAINTENANCE；settings convergence故障运行20/20。没有Coordinator内部`gate.clear()`专用注入。 |

## 历史partial L2与关联

34225 combined L2在R7 bulk setup时被用户暂停，`report.json`因SIGTERM未生成。其QA preflight、migration、g3b1、E2E、R2-R6的原始日志仍在 `docs/reports/workflow-optimization/runs/20261106-111828-partial-l2/`。为避免旧日志与新源码混绑，本表的核心恢复场景、Fresh/Weekly等均在当前QA SHA c2fc上重新独立执行；无需为制造combined runner报告重跑全套。

- 当前应用源码最后一次改动：`75c4d95`（启动时在创建Room ViewModel前检查restore gate）；其后main源码未变化。
- QA APK c2fc由该源码构建；当前测试映射/脚本位于`HEAD 407ef8c`及其后续本轮测试映射提交。
- 旧清单09:30记载QA `a0c0…` / Debug `6522…`；closure旧报告QA `2951…`与旧R7 runner安装/reused记录一致，但旧Debug `4395…`无对应留存APK。当前真实哈希见统一SHA256SUMS；Debug包未用于QA设备测试。

## 未覆盖/风险及判定理由

- 没有直接在Coordinator层注入 `gate.clear()` 删除/父目录fsync失败；目前由Gate异常单测、Coordinator顺序静态回归和settings-convergence失败后Gate保持/重启恢复组合验证。
- DB不可读场景中，QA activity请求worker启动且业务表无变化；但设备WorkManager数据库没有可用WorkSpec attempt记录。Worker外层Barrier及无副作用由源码映射和JVM检查补强。
- `MengyaApplication.onCreate`在startup reconcile后仍调用scheduler sync；在MAINTENANCE时可能更新WorkManager内部schedule状态。Worker本身的业务DB/AI/通知区域受Barrier阻断。若验收要求连内部WorkManager状态也必须完全不变，此项仍需修复并重测。

以上限制使最终状态保持 **NOT PASS**；不宣布G4A-2B关闭。完整日志、SHA256SUMS和源码diff见 `docs/reports/G4A2B-final-acceptance/`。
