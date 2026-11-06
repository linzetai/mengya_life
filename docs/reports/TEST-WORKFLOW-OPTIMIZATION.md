# 真机测试工作流提速与去重

## 结论

已增加明确的本地开发快回路；standard 默认验收行为不变。日常每次编辑可只跑受影响的静态/JVM/编译，不再因 UI/高风险 impact rule 自动花数分钟启动真机。真机用例仍列在报告的延期清单，**LOCAL PASS 不等于 L1/L2/Gate PASS**。

## 已实施

1. **`--profile dev`**
   - 命令：`./scripts/verify.sh --profile dev --changed`
   - 保留源码影响分析与受影响 JVM；无JVM类且改动触及Android/Gradle源码时执行 `:app:compileDebugKotlin`；仅测试体系/文档改动不触发无关app编译。
   - 不构建或安装 APK、不连 adb、不跑QA前置、不执行任何设备脚本。
   - 报告列出每个延期用例，结果为 `LOCAL PASS (device deferred)`、退出码0，同时 `fullLevelSatisfied=false`；失败则退出1。
   - 与 `--strict`、`--no-device` 冲突时明确拒绝，避免把本地快检误用成验收。

2. **保留标准层级，避免安全闸门被削弱**
   - `standard` 仍是默认 profile：规则选中的 feature/integration/device 路径照常执行。
   - 合并前运行标准 L1/L2；发布前仍须 `--all --level release`。

3. **共享脚本去重与模块级导航覆盖**
   - 跨模块同一 `(script, package)` 只执行一次；计划会报告“模块声明次数→唯一脚本次数”。advisor + ai-consent + settings 当前 L2 dry-run 从7份声明折叠为5个唯一脚本，`verify_g3b1.py`、`verify_settings.py`各只跑一次。
   - 如果所有直接命中的模块都声明已有针对性UI用例，则跳过通用五页导航冒烟；导航/共享UI规则命中时仍强制运行导航脚本。
   - 批量验证请在一条命令中重复 `--module`，从而复用一次构建、安装、QA前置和清理。

4. **移除默认重复的备份 smoke**
   - `verify_backup_v2_full.py` 已包含SAF导出、独立探针解密、哈希/计数，并逐字段比较120列；早期 `verify_backup_v2.py` 主要重复入口、SAF、探针、计数。
   - 默认L1改为 full + failure suites，不再重复跑旧基础脚本；旧脚本仍保留供手动兼容复验。历史基础脚本单次111.6s，因此每次备份L1至少省约112s，不降低全字段覆盖。

5. **把真实网络握手移到 Gate**
   - `verify_advisor_network.py` 的外网 401 探针从每次 L2 integration 移到 gate tier；L2仍跑 `NetworkAdvisorEngineTest` 与离线假引擎流程。Gate会尝试真实外网验收，Release不允许环境性跳过。
   - 再配合模块自身UI脚本跳过通用导航后，advisor + ai-consent + settings 的standard L2从7个唯一脚本降到5个，估时1345s→1119s（省约226s）；标准共享导航改动仍会跑导航冒烟。

6. **周AI L2 smoke / full 分层**
   - `verify_weekly_ai.py` 的 L1/L2 只跑READY成功路径22项；95项失败/超时/中断/STALE/撤权全状态矩阵保留在 Gate/Release。
   - JVM状态机测试仍在每次受影响本地循环运行；L2保留端到端成功调用，减少约852s（历史全矩阵1160s→成功冒烟约308s）；Gate仍跑完整矩阵。

7. **时间估算**
   - 计划输出按唯一设备脚本和当前 smoke/full 模式累加估时，并明确不含构建/前置/设备空闲；未知用例时长不伪造精确值。
   - `verify_settings.py` 历史38/38用时112s；本轮只增加“完整备份”和“恢复预览”入口可见两项断言到40项（UI源文件不变），脚本静态通过，未重跑真机。估时暂沿用112s。

## 本轮实际验证

| 命令/计划 | 结果 | 说明 |
|---|---:|---|
| `--profile dev --module restore-validate --level integration` | **4.5s LOCAL PASS** | 6个JVM类，0真机；fullLevelSatisfied=false |
| `--profile dev --module settings --level feature` | **3.8s LOCAL PASS** | 没有JVM类时实际执行 app Kotlin 编译；0真机 |
| `--profile dev --changed`（本轮测试体系/文档改动） | **3.1s LOCAL PASS** | 静态脚本检查；跳过无关app编译/JVM/真机 |
| `--all --level fast` | **5.9s PASS** | 全量静态 + JVM 299/299；0真机 |
| 标准L2 dry-run advisor + ai-consent + settings | **7→5 唯一脚本** | 网络401移Gate，已覆盖UI模块跳过导航；g3b1/settings共享声明折叠 |
| weekly-ai integration dry-run | **smoke 22条，估时881s总链路** | 包含迁移/授权/settings；95条全矩阵仅Gate/Release，静态策略自测确认模式 |
| 备份模块 dry-run | **4→2个设备脚本** | 移除重复基础导出+通用导航，保留full字段校验+失败矩阵；历史估计省约233s |

旧 broad integration 实测 **2043.5s（34.1分钟）**。G4A-2B focused L2 按迁移34s + E2E估算393s + R2-R7历史518s + fresh DB 14.5s，脚本约960s；加QA前置/JVM后预计约16–17分钟（约少一半），这是估算，未声称最终设备PASS。

复验报告和dry-run计划已提交到 `docs/reports/workflow-optimization/`；其中 dev restore 报告明确列出4个延后设备脚本，且 `fullLevelSatisfied=false`。本轮没有启动真机测试；设备层统计来自历史实测或dry-run估时。

## 推荐节奏

```sh
# 每轮开发：只做本地检查，约数秒到分钟
./scripts/verify.sh --profile dev --changed

# 功能完成 / 合并前：对真正受影响的模块一次性跑standard
./scripts/verify.sh --changed --level feature
./scripts/verify.sh --changed --level integration

# 手工批量模块，不要拆成多次启动（脚本会按package+script去重）
./scripts/verify.sh --profile dev --module advisor --module ai-consent --module settings

# 发布验收：不允许用dev profile
./scripts/verify.sh --all --level release
```

调试设备不可用时，standard 的 L1+ 报告应保持 INCOMPLETE/NOT RUN；不要改成 PASS。