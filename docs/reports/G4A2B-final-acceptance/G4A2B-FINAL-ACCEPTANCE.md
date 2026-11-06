# G4A2B-FINAL-ACCEPTANCE

**最终状态：NOT PASS**。核心恢复矩阵和缺失的Fresh/Weekly/DB-unreadable目标均在同一QA APK SHA上PASS；但Worker WorkManager执行状态未被观察，且Gate.clear故障只做单测+静态控制流验证、没有Coordinator级直接注入，故不宣布关闭。

## 1. Git / APK 核验

- Branch：`master`；最终HEAD、commit清单与 `git status --short` 在交付包 `git-final-state.txt` 中记录，且必须为空。
- 当前 QA APK：`app/build/outputs/apk/qa/app-qa.apk`，SHA-256 `c2fcdfc0d50b251c286291f483d52eade2845491cee446b2c925660906feaab1`。
- 当前 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `a77858ad0a84133df9dc2ffeca7cbdc5fd9e015b1dd3fa7a3ca642dad2d829f3`。
- 统一核验表：本目录 `SHA256SUMS`；需在仓库根目录执行 `sha256sum -c docs/reports/G4A2B-final-acceptance/SHA256SUMS`。
- 旧 `docs/reports/G4A2B-closure/apk/apk.sha256` 时间为09:30，记录QA `a0c0fba9…` / Debug `65223851…`，早于后续修复构建（QA APK在11:18更新）。旧closure报告曾记录QA `2951bdf6…`，该值与旧版R7报告的已安装QA包相符；其Debug `439565fa…` 不等于当前Debug artifact，属于未保留产物的中间记录，不能作为当前SHA。Debug APK未用于破坏性设备验收。详细来源与限制见 `apk-history.md`。

## 2. 版本 / 测试证据关联

- Restore业务源码最后改动为 `75c4d95`（`MengyaLifeApp` 启动Gate前置）；之后到最终补测，`app/src/main`无变化。QA APK c2fc按该源码构建；Debug APK只作构建/哈希交付，不曾安装到测试包。
- 旧partial L2（run `20261106-111828`）无最终`report.json`，因此其当时的installed SHA/commit字段不能从原runner直接证明；其逐脚本PASS仅作历史佐证，不与当前结果混作精确绑定。
- 当前QA hash `c2fcdfc0…`由run `20261106-164126` runner report记录；此后无重装APK。该same install用于本轮E2E、迁移、g3b1、R2-R7、Fresh、Weekly和DB不可读case，独立PASS按同一QA版本聚合，无combined报告。

## 3. 验收矩阵

| 场景 | 本轮状态 | 证据/限制 |
|---|---|---|
| 全量L0 | PASS 303/303 JVM + 静态检查，7.7s | 0设备；包含映射、Gate/Release case限制及fail-closed源码策略自检。 |
| v10/v11→v12迁移 | 当前QA SHA c2fc：26/26 PASS | `/tmp/migration-final.log`；old schema均升级到v12并保留数据。 |
| g3b1授权/隐私 | 当前QA SHA c2fc：47/47 PASS | `/tmp/g3b1-final-apk-34707-final.log`；仅QA fake engine。 |
| A→B→A E2E | 当前QA SHA c2fc：39/39 PASS，198.30s | `logs/restore-e2e-39of39.log`；恢复/Key/consent/AI禁用/重启PASS。 |
| R2 | 当前QA SHA c2fc：12/12 PASS | 事务回滚后B保留、Key/consent保留、Gate清理。 |
| R3 | 当前QA SHA c2fc：19/19 PASS | Key删除故障时Gate保持；重启后安全收敛。 |
| R4/R5/R6 | 当前QA SHA c2fc：各15/15 PASS | DB提交/consent完成后强停；重启收敛、Gate释放。 |
| R7 Gate缺失 | 当前QA SHA c2fc：15/15 PASS | 删除Gate后由app_meta+journal恢复，A与安全复位PASS。 |
| SETTINGS_FAIL | 当前QA SHA c2fc：20/20 PASS | 收敛异常时Gate保持、未报告成功；重启恢复。 |
| Fresh v12首次启动 | 当前QA SHA c2fc：6/6 PASS | app_meta初始`(null,0,null)`；Gate不存在；等待READY后页面可用。 |
| Weekly AI L2 READY | 当前QA SHA c2fc：22/22 PASS | QA fake engine；来源正确、Key不入库/日志。 |
| DB不可读启动 | 当前QA SHA c2fc：12/12 PASS | 维护页可见、Goal/AI入口缺失、业务表计数不变，QA清理PASS。没有读取WorkManager WorkSpec attempt。 |
| UNKNOWN/MAINTENANCE普通写/Worker/AI | L0 303/303 + DB不可读probe PASS | GoalRepository无写、WeeklyAI无DAO/引擎副作用；Worker入口受Barrier保护，测试时业务表不变。 |
| Gate释放失败 | 单测 + static policy + SETTINGS_FAIL运行PASS | `RestoreGate.clear()`错误会抛出；Coordinator在clear/journal clear后才`finishReady`，异常转MAINTENANCE。未做Coordinator内部`gate.clear()`故障的独立设备注入。 |
| QA前置 / Runner | 当前QA SHA c2fc的preflight 11/11 PASS | 另一次前置在前台切换时UI定位超时；因此目标case使用已通过的同SHA preflight和独立直跑证据。 |

## 4. Restore 写屏障审计

- `RestoreWriteBarrier` 在 UNKNOWN/RECONCILING/MAINTENANCE 拒绝普通写；独占restore permit只授权恢复内部收敛写。
- 旧SHA2951的DB不可读探针曾在Room Flow上触发`SQLiteCantOpenDatabaseException`；`75c4d95`将restore gate检查移到`MengyaLifeApp`创建任何Room ViewModel之前。当前SHA c2fc的DB不可读探针维护页/无业务入口12/12 PASS。
- `RestoreCoordinator` 的app_meta读取以 `Result` 捕获；Gate/Journal清除与 `finishReady()` 位于同一成功顺序内，异常路径转MAINTENANCE。
- Worker、Goal/Journal及AI写入口均用共享Barrier；变更映射 `L-restore-write-barrier` 把屏障改动提升至L2，并选择 restore / ai-consent安全覆盖。
- Gate/Release禁止 `--case`；未知代码路径保守扩展到完整模块集合；`dev` profile静态策略检查要求0设备步骤。
- `CASE PASS (suite deferred)`保留`fullLevelSatisfied=false`；DB不可读case的runner原始report亦未修改该字段。
- 完整源码diff见 `source/restore-write-barrier.patch`；selector/测试diff见 `source/test-selection-and-scenario.patch`。

## 5. 最终判定

只有当表中全部必需项为当前代码/适用证据上的PASS、没有安全关键NOT RUN、APK manifest校验通过且worktree clean时，状态才可改为PASS并宣布G4A-2B关闭。不得编辑 `fullLevelSatisfied` 或用case PASS替代suite PASS。
