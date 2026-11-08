# MengyaLife 0.15.3 · UI finishing

基线：`0.15.2-morrow`；版本：`0.15.3-morrow` / versionCode 19；工作树：`master`。

## P0 · 顾问页 IME

**根因（由代码结构和真机复测确认）**：Activity 开启 edge-to-edge，但 Manifest 没有指定 `windowSoftInputMode`；同时主 `Scaffold` 的系统导航栏 inset 与顾问页内的 `imePadding()` 在键盘态叠加，底栏仍占位。系统窗口平移和 Compose Insets 的可用高度不一致，造成顶栏贴入状态栏、输入框上移及大段空白。

修复：Manifest 显式 `adjustResize`；IME 显示时隐藏底部导航，并从 Scaffold 内容 Insets 中排除 navigation bars；顾问页只由 IME inset 负责避让。消息列表仍独立滚动，键盘动画完成后滚到真实末项；键盘态收紧模型/授权行与草案操作区，不隐藏消息、也不改变对话逻辑。正常真机截图实测输入框至键盘约 **13dp**，顶部不压状态栏，导航栏不与键盘重叠。

- 顾问消息态：[01-advisor-messages.png](./01-advisor-messages.png)
- 键盘态：[02-advisor-ime-open.png](./02-advisor-ime-open.png)
- 多轮消息键盘态、最新回复可见：[19-advisor-multiturn-ime.png](./19-advisor-multiturn-ime.png)
- 英文键盘及输入：[20-keyboard-english-switch.png](./20-keyboard-english-switch.png)、[21-keyboard-english-entry.png](./21-keyboard-english-entry.png)
- 最终键盘开/收短录屏：`/sdcard/Download/DSH-Go/MengyaLife-0.15.3/advisor-ime-cycle-final.mp4`

## P1/P2/P3 · 局部布局

- 目标编辑弹层移除固定 90% 高度；字段区按可用高度限高并独立滚动，操作栏保持在滚动区外。真机验证自然高度、键盘态滚到底、状态/截止日期调整、保存及取消；测试后恢复 QA 目标原状态。
  - [03-goal-editor-natural.png](./03-goal-editor-natural.png)
  - [04-goal-editor-ime-bottom.png](./04-goal-editor-ime-bottom.png)
  - [05-goal-editor-date-keyboard-bottom.png](./05-goal-editor-date-keyboard-bottom.png)
- 探索/决定结果页根据固定操作栏的实测高度动态预留滚动末尾空间；决定结果滚到底后第四项完整可读。探索顶部点状进度保留：它对应真实三步流程，非纯装饰。
  - [07-explore-result.png](./07-explore-result.png)
  - [08-decision-result-bottom.png](./08-decision-result-bottom.png)
- 人生全景的「记录进展 / 编辑 / 更多」同组并紧邻所属父目标，位于子目标之前；父子缩进与层级底色增强，不改目标数据及展开逻辑：[06-panorama-actions.png](./06-panorama-actions.png)
- 设置页保留完整安全与隐私说明、降低辅助文案权重、统一操作间距；关于页标签可换行：[09-settings-ai-config.png](./09-settings-ai-config.png)、[10-settings-ai-privacy.png](./10-settings-ai-privacy.png)
- 全局局部回归：轨迹、日记、一周回顾截图：[11-trajectory.png](./11-trajectory.png)、[12-diary.png](./12-diary.png)、[13-weekly-review.png](./13-weekly-review.png)

## 验证

- 真机：Android 17 / Xiaomi 2509FPN0BC，原始显示 1200×2608、480dpi、字号 0.9；通过 `adb -s 127.0.0.1:41891` 安装 QA 包、输入、滚动、切换中英文键盘、保存与录屏。
- 360dp / 1.3× 字体探索性回归截图：[14-360dp-font13-panorama.png](./14-360dp-font13-panorama.png)、[15-360dp-font13-goal-editor-ime.png](./15-360dp-font13-goal-editor-ime.png)、[16-360dp-font13-settings-about.png](./16-360dp-font13-settings-about.png)、[17-360dp-font13-today.png](./17-360dp-font13-today.png)。这些截图拍摄后已将 `wm size` / 字号恢复到上列原值；未再为复测改动设备显示设置。
- 单元测试：`testDebugUnitTest`，**303 项通过，0 失败**。构建：`assembleDebug`、`assembleQa` 均成功。
- 真机 QA 包使用独立 `com.mengyalife.app.qa` 数据空间；为多轮布局验证新增了一条离线 `layout-check` 测试对话，未触碰正式包的数据。目标编辑器测试更改已恢复。

## 交付与限制

安装包位于 `/sdcard/Download/DSH-Go/MengyaLife-0.15.3/`：Debug 为普通可安装包，QA 包单独隔离，仅供测试。ZIP 的 `screenshots/before/` 是 0.15.2 基线截图，`screenshots/` 是本轮真机截图；原顾问键盘问题截图只在用户消息中提供，未另存到工作区。截图中的 DSH 浮层/宠物不属于应用界面。

**仍待最终外部评审**：临时 360dp/1.3× 验收后已恢复设备设置；最终顾问键盘紧凑态补丁没有再以该尺寸重跑顾问/结果页全矩阵。QA 配置已有历史消息，未清空数据以制造空对话，因此空对话键盘场景未实测；最终候选包深色主题也未复测。正常尺寸的最终 ADB 键盘、多轮、英文键盘测试已通过。此交付不代表用户已认可 UI，也不启动后续交互开发。
