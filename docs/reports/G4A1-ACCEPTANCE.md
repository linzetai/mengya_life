# G4A-1 验收结论（ACCEPTANCE）

## 1. Gate 状态

**G4A-1 Gate：PASS。**

QA 真机已恢复（`10.13.224.241:46295`，Android SDK 37），安装本次 QA APK 后执行
`verify_backup_v2.py`：**28/28 PASS，退出码 0**。真实 SAF 导出、回读、解密与表计数校验均通过。

## 2. QA 真机结果

| 项 | 结果 |
|---|---|
| 设备 | `2509FPN0BC`，SDK 37，`com.mengyalife.app.qa` 0.14.1-g3e2-1-qa |
| 数据 | **假数据**：1 日记、1 目标、1 周回顾；无真实用户数据 |
| 完整导出/SAF/回读 | **PASS** |
| 独立 Node 解密/哈希/表校验 | **PASS** |
| 10 表 counts 对照 | **PASS**（1/1/0/0/0/0/0/0/1/0） |
| 解密 payload 敏感字段扫描 | **PASS**（无 API Key/consent/Keystore/fake engine） |
| 真实文件大小 | **5,445 字节** |
| 端到端耗时（确认口令 → 成功） | **18.06s**（含 310k PBKDF2、SAF、回读） |
| 导出后 QA DB 计数不变 | **PASS** |
| 本次新建文件清理 | **PASS**（未触碰旧文件） |

证据：

- `docs/reports/G4A1-acceptance/qa/run-output.txt`（28/28）
- `docs/reports/G4A1-acceptance/qa/sample-v2.mengyalife`（假数据加密样本）
- `docs/reports/G4A1-acceptance/qa/probe.json`、`probe-recheck.json`
- `docs/reports/G4A1-acceptance/qa/device.txt`

## 3. B-01..B-15 验收

| ID | 结果 |
|---|---|
| B-01 完整 v2 导出（10 表） | **PASS**（JVM + QA 真机） |
| B-02 空数据库备份 | **PASS**（JVM） |
| B-03 文件过大 | **PASS**（JVM 64 MiB 边界） |
| B-04 v1 识别、不误当 v2 | **PASS**（JVM） |
| B-05 未知表/feature | **PASS**（JVM） |
| B-06 加密往返 | **PASS**（JVM） |
| B-07 错误口令 | **PASS**（JVM） |
| B-08 篡改/截断 | **PASS**（JVM） |
| B-09 KDF 参数边界 | **PASS**（JVM） |
| B-10 counts/per-table hash | **PASS**（JVM） |
| B-11 SAF 写入失败 | **PASS（fake destination）**；真实 SAF 故障注入未执行（见 §5） |
| B-12 API Key/consent 排除 | **PASS**（JVM 解密扫描 + QA 真机解密扫描） |
| B-13 固定向量 | **PASS**（JVM） |
| B-14 未知字段/未来版本 | **PASS**（JVM） |
| B-15 原始 vs 恢复后哈希 | **PASS**（JVM projection） |

## 4. 其他验收项

| 项 | 结果 |
|---|---|
| 全量 JVM | **273/273 PASS** |
| 静态检查 | PASS（schema-contract / script-syntax / validate-map） |
| 系统备份 | PASS（allowBackup=false + dataExtractionRules + fullBackupContent=false；QA/debug 合并 Manifest） |
| v1 保留 | PASS（旧导出未修改） |
| APK | QA `baa7a8b1...`；debug `dc97eedc...` |
| 恢复/导入 | 未实现（本批范围外） |

## 5. 残余与边界

1. B-11 的**真实 SAF 写失败**未被物理注入；JVM fake destination 已覆盖“写失败不报成功、尝试删除/提示”逻辑。
2. 未执行七组完整真机回归；本批只跑备份相关真机。
3. 未执行恢复测试；没有 RestoreGate/app_meta/schema 变更。
4. 真实文件耗时是一次 QA 假数据样本的端到端值，不代表大数据量。
5. 测试样本为假数据；已归档加密样本，不含真实用户信息。

## 6. 复验命令

```bash
cd /root/workspace/mengya-life
DEV=10.13.224.241:46295 python3 tools/ui-verify/verify_backup_v2.py
```

该脚本会清 QA 假数据 → 建目标/日记 → 设置页 → 口令 → SAF → 回读成功 →
拉取 → Node 独立解密/哈希/表校验 → 与 DB 计数对照 → 清理本次新文件。

## 7. 结论

G4A-1 的备份导出范围已完成并验收通过；停止在本批，不进入 G4A-2。
