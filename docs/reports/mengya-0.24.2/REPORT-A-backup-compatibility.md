# Gate A
- RestoreTables.OPTIONAL；RestorePlanner 缺可选 counts 记 0，缺必需仍拒绝。
- RestorePlan 对旧 careDecisions 缺 notifiedAt/messageId/deliveredAt 显式补 null（原始 payload/完整性不变），摘要基于归一化计划。
- A1 真实 0.24 fixture：BLOCKED（未生成版本固定 fixture）。
- A5/A6 场景：部分（现有备份测试通过；旧格式恢复未设备往返）。
