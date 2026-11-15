# MengYa Life 0.25.3 · 验证记录

状态：**实现与证据交付，Gate 4 为 PARTIAL，不是全部验收通过。** 用户停止了手机操作；已停止全部手机操作，不再尝试唤醒或解锁。最后的失效草案主操作修正尚未真机复验；不是用户独立 UI 验收通过，也不是 0.26 放行。

## 基线与安全边界

- 起点：`master / 9a19932d21a375679fdfad4e93e8db43f6cff019`。
- 版本：0.25.3 / versionCode 39；QA 包 `com.mengyalife.app.qa`。
- Gate 0 独立提交：`5c87b2d05a4f334313bca0a1b084038b3f851f73`。
- UI 主体提交：`0123120f173c4ba4b75ac23533a29d0b1dfd29c6`。
- 最终应用代码提交：`dbf5f839248a5160da7fe78585356875a2d2ded3`（失效草案卡内改用重评主操作；此末轮补丁已构建/单测，未真机复验）。之后交付提交归档证据、文档与未自动执行的恢复辅助脚本。
- 评审分支：`ui/0.25.3-review`，不以本轮交付自动放行 0.26。
- 测试仅使用独立 QA 数据库与 QA 离线引擎，不读写正式包数据，不提取正式模型密钥。
- 前后主矩阵使用同一份 QA 数据快照（同一已确认计划、同一两组阅读记录、同一报告与对话），不拿不同成长状态冒充 UI 差异。

## 自动验证（与视觉验证分开）

| 项目 | 结果 |
|---|---|
| `:app:testQaUnitTest` | 440 tests，0 failures/errors/skipped |
| `:app:testDebugUnitTest` | 440 tests，0 failures/errors/skipped |
| QA / Debug APK 构建 | 成功 |
| QA AndroidTest APK 构建 | 成功 |
| AndroidJUnitRunner 真机直接执行 | 中间版本 `OK (33 tests)`，含新增 Gate 0 的 9 项；末轮纯 UI 补丁后未重跑真机测试，[原始日志](<logs/instrumentation-first.log>) |
| `:app:lintDebug` | 0 errors / 40 warnings；[日志](<logs/lint-debug.txt>) |

末轮新增 2 项恢复操作展示回归测试（失效来源切换为重评；普通错误不误判），最终共 440 项单元测试。另有 8 项 UI 展示回归测试覆盖实际快照变化、增量语义、日记时间窗、未来记录、单点不推断、友好来源名、结论去重、独立事实保留及历史证据授权隔离（共 8 项测试方法，部分方法含多个断言）。新增 Gate 0 仪器测试覆盖来源直调旁路、legacy/损坏/缺失来源、三种目标变更的事务内拒绝、成功幂等、子项失败完整回滚、旧 generation 与维护、缺 barrier。

## 真机与显示矩阵

一台 Android 物理设备；通过系统逻辑显示参数形成三种视口，不是三台物理设备，也不是模拟器截图：

| 逻辑宽度 | 系统分辨率覆盖 | 密度 | 字体 | 模式 |
|---|---|---|---|---|
| 320dp | 960×2080px | 480dpi | 1.0 / 1.3 | 浅色 / 深色 |
| 360dp | 1080×2340px | 480dpi | 1.0 / 1.3 | 浅色 / 深色 |
| 412dp | 1236×2678px | 480dpi | 1.0 / 1.3 | 浅色 / 深色 |

主矩阵每组合包括今天（建议与行动同时存在）、顾问已有对话、轨迹，共 12 组合 × 3 页；基线与新版均保存原始真机截图及 UI 节点信息。完整 226 张原图含各审查轮次，见 [截图索引](<SCREENSHOT-INDEX.md>)、[逐图哈希清单](<SCREENSHOT-MANIFEST.json>)；最终 APK 哈希及测试身份见 [构建身份](<logs/build-identity.json>)。

精确时间只用于记录测试，不影响产品行为；主矩阵数据状态相同，系统时钟/宿主顶部状态不可能逐像素相同。

## 视觉复核闭环

第一轮原图保存在 [r1 截图](<screenshots/review-r1/after-320-light-13-today.png>)，没有用最终图覆盖整改证据。

发现并修复：
1. 320dp/1.3 今天与轨迹大标题产生孤字；缩短自然文案并使用 26sp 二级展示标题，不限制字体缩放。
2. 320dp/1.3 成功计划名称与“查看行动”争夺一行；改为标题和入口上下分层，完整文本不截断。
3. 输入占位两行；改为“说说近况…”。
4. 用户消息也重复显示“建议依据”；入口保留在顾问回复下，避免将用户原话误称为建议。

- [今天与顾问逐组合复核](<VISUAL-MAIN-REVIEW.md>)
- [轨迹逐组合复核](<VISUAL-TRAJECTORY-REVIEW.md>)

已采集的主矩阵 36 张原图已逐张复验（末轮恢复主操作补丁之前）；没有观察到主操作不可见、组件内硬截断、App 横向溢出或输入被导航遮住。保留非阻塞问题：部分次操作间距偏松、长文案偶有短尾行、外部宿主悬浮层可能覆盖局部文字。静态首屏不替滚动末端、键盘和点击接线背书；交互结果另列。

## 修改前后同状态对照

两列的业务数据一致；变更的是布局与操作层级。旧版洞察没有折叠状态，因此对照旧版完整详情与新版展开末端，另保留新版折叠截图。

| 同状态 | 0.25.2.1 基线 | 0.25.3 |
|---|---|---|
| 今天：同一建议 + 同一已确认计划，360dp/浅色/1.0 | ![之前今天](<screenshots/before-360-light-10-today.png>) | ![之后今天](<screenshots/after-360-light-10-today.png>) |
| 顾问：同一对话与同一成功计划，320dp/深色/1.3 | ![之前顾问](<screenshots/before-320-dark-13-advisor.png>) | ![之后顾问](<screenshots/after-320-dark-13-advisor.png>) |
| 洞察：同一报告与依据，360dp/浅色/1.0 | ![之前洞察](<screenshots/before-360-light-10-insight.png>) | ![之后展开依据](<screenshots/after-360-light-10-insight-expanded-bottom.png>) |
| 草案：同一“这周读三次书”待确认草案，360dp/浅色/1.0 | ![之前草案](<screenshots/before-360-light-10-draft.png>) | ![之后草案](<screenshots/after-360-light-10-draft.png>) |
| 编辑：同一草案、标题字段焦点与真实键盘，360dp/浅色/1.0 | ![之前编辑IME](<screenshots/before-360-light-10-edit-ime.png>) | ![之后编辑IME](<screenshots/after-360-light-10-edit-ime.png>) |
| 轨迹：同一真实持久化 QA 记录，412dp/浅色/1.0 | ![之前轨迹](<screenshots/before-412-light-10-trajectory.png>) | ![之后轨迹](<screenshots/after-412-light-10-trajectory.png>) |

## 交互与扩展状态

另有 12 组合 × 7 状态原图：洞察收起、展开、展开末端、编辑、编辑键盘、草案、顾问键盘。48 张表单/聊天图及 36 张洞察图均逐张审查；没有把所有顾问键盘图都说成出现待确认按钮（其中 6 张仅显示聊天/历史内容）。

- [表单与键盘逐图报告](<VISUAL-FORM-REVIEW.md>)
- [洞察逐图报告](<VISUAL-INSIGHT-REVIEW.md>)
- [保存/取消/长对话/失效来源交互图像复核](<VISUAL-INTERACTION-REVIEW.md>)

| 场景 | 已取得的实际证据 | 边界 |
|---|---|---|
| 编辑 | 320dp/1.3 聚焦周期后手动滚动，完整周期、关联目标与操作可达；完成编辑后仍为待确认 | [键盘滚动证据](<screenshots/after-320-light-13-period-scrolled-ime.png>)，不把初态半露字段误认为已完整验证 |
| 确认 | 修改名/周期→保存→简洁结果；实际点击查看行动并断言出现同名行动 | [保存后](<screenshots/after-320-light-13-confirmed.png>)、[查看行动](<screenshots/after-320-light-13-view-action.png>) |
| 取消 | 生成已验证输入对应的草案→点击取消→明确已取消结果 | [取消后](<screenshots/after-360-light-10-cancelled.png>)；“明天散步十分钟”在 QA 离线引擎未形成草案，不当作该输入成功证据 |
| 长对话 | 补充 12 轮 QA 请求；320/360/412 的真实键盘、上翻历史与再次聚焦截图 | [360大字IME](<screenshots/after-360-light-13-long-dialog-ime.png>)、[历史IME](<screenshots/after-360-dark-13-long-history-ime.png>)；不宣称精确像素锚点或真实模型质量通过 |
| 真实记录变化 | 通过记录进展表单实际保存 QA 新增量 3 pages、备注 QA-UI-verified-reading，轨迹显示新日期与新增数值 | [保存表单](<screenshots/after-360-light-10-real-record-before-save.png>)、[轨迹变化](<screenshots/after-360-light-10-trajectory-new-record.png>)、[数值明细](<screenshots/after-360-light-10-trajectory-new-record-detail.png>)；合成输入真实写入，不代表用户实际阅读或成长 |
| 失效与重试 | 更改来源记录后旧草案保存被拦截；旧报告仍能展开来源；手动滚到重评入口后实际点击，返回今天并执行重新生成 | [旧依据仍可看](<screenshots/after-360-light-10-stale-evidence.png>)、[拦截](<screenshots/after-360-light-10-stale-blocked.png>)、[重评后](<screenshots/after-360-light-10-retry-updated.png>)；这些是末轮主操作修正**之前**的图 |

### 最后一轮发现与未通过项

1. 实测发现失效卡仍突出保存、重评位于卡外且需滚动。已在最终代码改为卡内“重新评估建议”主操作，旧图问题保留在 [r3](<screenshots/review-r3/stale-save-still-primary.png>)。**改后的真机图、点击验证和新版本仪器测试尚未取得，不能用修改前重试成功替代。**
2. 手机自动息屏/锁屏后，采集读到了旧界面节点，导航/定位断言失败；一次黑屏结果已用先前实际成功场景重拍替换。未将这些中断或屏外命中失败记为通过。用户随后停止手机操作，本轮不再继续尝试。
3. 没有完成最终 QA 数据库导出、重启后持久性抽检；不能把计划名只出现一次当作数据库唯一性证明。Gate 0 的事务/回滚/幂等由其专门仪器测试覆盖。
4. 洞察图夹具仅含 1 条已在解释中引用的事实。额外独立事实的保留有单元断言，但**缺少多独立事实的专项真机视觉夹具**，不能以单测代替该项视觉覆盖。
5. 尚有长英文名末字母孤行、无键盘表单/取消卡留白偏松、部分提示文案可更明确等非阻塞观察。外部 DSH 宠物可能盖住文字；未将其算作 App 自绘缺陷，也不隐瞒该环境干扰。

### 设备设置恢复提示

停止手机操作时，最后已知测试设置为 1080×2340 覆盖、480dpi、字体 1.0、浅色；原始状态为物理 1200×2608、480dpi、字体 0.9、夜间模式 auto。**停止后没有再执行恢复操作**。如需恢复，请用户重新授权后运行 [恢复脚本](<../../../tools/ui-verify/restore_ui_0253_display.sh>)；脚本仅提供，不会自动执行。

## 真实模型与独立验收

本轮截图使用明确标记的 QA 离线引擎。真实外部模型体验：**未验证**；没有把离线聊天当作真实回复、网络时延、服务端取消或模型输出质量的通过证据。用户独立 UI 验收：**待进行**。0.26：**未开始、未放行**。
