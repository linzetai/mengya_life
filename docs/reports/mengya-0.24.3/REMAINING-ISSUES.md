# MengYa Life 0.24.3 · 未完成与风险

## 1. 恢复中断重试（0.24.2.1 遗留，技术债）

- 已知风险：恢复过程中断后的重试语义（重复提交、收敛状态、Key 删除失败）**本轮未重写、未重新验证**；
- 本轮只改了成功文案（按 endpoint 策略生成），没有改 `RestoreCoordinator` / `RestoreGate` 状态机；
- UI 只描述已实现的行为，不对「断点重试一定安全」作承诺；
- 建议下一轮先补 QA 故障注入（`QaRestoreFaultInjector`）覆盖「DB 已提交、安全收敛前中断」的重试路径。

## 2. Lint 既有 error（本轮未触碰的文件）

`lintDebug` 报告 5 个 error，均在本轮未修改的代码：

| 文件 | 问题 |
|---|---|
| `data/weekly/WeeklyReviewNotification.kt` | `notify()` 未显式检查 `POST_NOTIFICATIONS`（MissingPermission） |
| `domain/care/CarePolicy.kt` | `LocalTime.ofInstant` 需要 API 31+，minSdk 26 |
| `domain/care/CarePreferenceParser.kt` | `LocalDate.ofInstant` 需要 API 34+ |
| `data/care/CareRunner.kt`（2 处） | 同上 |

其中 `java.time` 的 `ofInstant` 在 API 26–33 上不存在，属于**运行时崩溃风险**，建议优先修复或启用 core library desugaring。
本轮没有改 care / 通知代码，以免超出 UI 改造范围。

本轮新增的 2 条风格 warning（不影响构建）：

- `TodayScreen.kt:107` ModifierParameter：`modifier` 不是第一个可选参数，建议后续统一签名；
- `TodayScreen.kt:71` ConstantLocale：静态 `DateTimeFormatter` 持有 `Locale.getDefault()`，切换系统语言时不会刷新。

## 3. 真机验收缺口

| 项 | 状态 | 原因 |
|---|---|---|
| 备份恢复「预览弹窗」截图 | BLOCKED | 需要先导出真实 v2 备份（系统 SAF 存盘），再用 SAF 选择器导入并输入口令；自动化在本机环境下无法稳定驱动系统文件选择器 |
| 主动关心（Care）卡截图 | BLOCKED | 需要满足真实规则（时间窗、记录间隔等），验收窗口内未自然触发 |
| 深色模式全页面 | 部分 | 只截了今天 / 轨迹 / 设置三页，其余页面沿用同一套语义颜色令牌 |
| Instrumented 测试 | 未执行 | 本机未配置 instrumentation 运行器；JVM 375 项已全绿 |

## 4. 自动化方式限制

- `uiautomator` 会占用无障碍通道，和 DSH Go 的手机操控桥接冲突，导致宿主运行中断；因此本轮全部改用 `screencap` + `input tap` + `dumpsys`；
- 没有控件树定位，坐标来自截图核对，页面改版后需要重新取坐标；
- 长任务需 `setsid` 脱离会话执行，单次工具调用只做轮询。

## 5. 产品层遗留

- 今天页原来的「探索人生方向 / 写一篇日记 / 讨论一个决定」三个并列磁贴，本轮收成底部三个文字入口；
  如果后续希望强化某个入口，需要产品确认优先级（当前主按钮只保留「下一步」一个）。
- 「下一步」目前是纯本地规则（关心 > 计划 > 无方向 > 停滞方向 > 日记 > 对话），
  没有接入 AI 洞察；接入前需要确定「建议」与「记录」的视觉区分，避免用户误以为 AI 已执行。
- 人生全景的领域折叠状态按领域名保存在页面内，重启后恢复默认展开前两个；
  如果用户领域很多，可能需要按「最近使用」排序。
- `README.md` 仍描述 0.14.0 的 Gate 状态，本轮没有改写历史文档，以 `docs/reports/mengya-0.24.3/` 为准。

## 6. 建议的下一步

1. 修复 care 的 `java.time.ofInstant` API 兼容问题（或开启 desugaring），并给通知补权限检查；
2. 用 QA 故障注入重验恢复中断重试，再补一张恢复预览弹窗截图；
3. 把「下一步」接入真实洞察（已有 factIds 校验），但保持「建议 ≠ 已保存」；
4. 补一张 Care 卡的真机截图，并在 `UI-VERIFICATION.md` 更新索引。
