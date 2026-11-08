# Gate A
- RestoreTables.OPTIONAL；RestorePlanner 缺可选 counts 记 0，缺必需仍拒绝。
- RestorePlan 对旧 careDecisions 缺 notifiedAt/messageId/deliveredAt 显式补 null（原始 payload/完整性不变），摘要基于归一化计划。
- A1 真实 0.24 fixture：BLOCKED（未生成版本固定 fixture）。
- A5/A6 场景：部分（现有备份测试通过；旧格式恢复未设备往返）。

## Round 2
- 抽出 RestoreCountResolver / RestoreRowNormalizer，JVM 测试覆盖：旧备份缺可选 counts→0；缺必需 counts→拒绝；旧 careDecisions 缺 notifiedAt/messageId/deliveredAt→null；已存在值不覆盖。
- 356 JVM tests PASS。
- 仍缺 A1 真实 0.24 fixture、A5 设备恢复。

## Round 6
- BackupFormatV2.encodePayload 增加 includeRecordProposals=false（测试/旧格式 fixture 用，生产默认 true）。
- 新增测试：旧格式 payload 无 recordProposals data/count/hash；RestoreCountResolver 解析为 0。
- 仍缺：真实 0.24 加密文件 fixture 与 A6 原库/Key/授权核验。

## Round 9（设备 A6）
- QA 故障注入恢复（qa_restore_fail_transaction）：
  `FAILED_BEFORE_COMMIT / 恢复事务失败，旧数据已保留：QA injected transaction failure`
- 失败前后计数一致：journal=4, goals=13, care_opportunities=4, care_preferences=5；API Key 仍存在。
- 未验证：授权状态显式读取（未变）、旧格式备份设备预览（A5）。

## Round 10（设备 A5）
- BackupExporter.build 增加 includeRecordProposals=false；QA 钩子 qa_legacy_roundtrip 用真实编码器生成无 recordProposals 备份并执行恢复。
- 结果：`SUCCESS，已保留本机 Key 与授权`；恢复后 record_proposals=0、care_preferences=5、care_decisions=5、care_opportunities=4，Key 保留。
- BackupValidator 允许缺失可选表 perTableSha256（旧格式）。
