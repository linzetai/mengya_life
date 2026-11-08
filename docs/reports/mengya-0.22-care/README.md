# MengYa 0.22 · Care Engine（真机实测）

基线 0.21；`0.22-morrow` / code 31；DB v19。

## 真机已验证（真实 deepseek-flash）

1. 创建 ACTIVE 小计划“整理一次简历” → 今天页生成候选（GOAL_STALL + PLAN_CHECKIN）。
2. 真实 Care Planner 输出 SEND：
   `机会#2｜ACTIVE｜0 次执行｜窗口今天 23:59｜优先于依据较弱的目标候选`
   建议文案：“这周简历整理得还顺利吗？需要帮忙随时说。”
3. 策略门通过后今天页出现「MengYa 想和你聊聊」卡（截图 `01`）。
4. 点「聊聊」：记录 ENGAGED，计划机会置 DISMISSED，进入唯一顾问对话并预填
   “（来自主动关心·计划「整理一次简历」）…”（截图 `02`），没有自动发送。
5. 重启后：同一候选不再出现（DISMISSED + ENGAGED），未重复调用生成同一卡片；
   另一次规划真实调用失败时记录 `model_ok=0` 并安全退出，不发消息。
6. 数据库：
   `care_decisions`: SEND(model_ok=1)，含 reason/suggested/trace；
   `care_feedback`: ENGAGED；`care_opportunities`: #2 DISMISSED、#1 PENDING。

## 本轮修复的关键 bug

- 决策先落库导致策略把它自己当成“刚发过”而拒绝 → 改为**先策略门、后落库**。
- 决策 JSON 被 500 token 截断 → 提高到 900；并兼容 `decision`/`record` 包裹与字符串 id。
- 模型原始响应写入失败原因（截断 240 字），便于诊断。

## 未完成

- 真实 DEFER / 主动 DROP / SILENCED：今日决策预算已用尽（5/5），未继续造场景。
- 多候选自主选择只体现在本次 reason（AI 比较 #2 与 #1 并选 #2），未做第二次对照。
- 免打扰、撤销授权、每日发送上限的真机拦截未单独验证（有单测）。
- care 表未纳入备份。
- 无录屏。
- 今天页“暂时忽略 / 不再提醒”未点验（卡片已被 ENGAGED 消耗）。

## 测试
346 项通过，0 失败（含策略、解析、未来周候选边界）。
