# G4A-1 验收结论（ACCEPTANCE）

## 1. Gate 状态

**G4A-1 Gate：NOT PASS（CONDITIONAL / 部分完成）。**

原因不是 JVM 或代码失败，而是 **QA 真机不可用**：

- `adb devices -l` 为空；历史无线地址 `192.168.10.183:33537` 离线且无法连接。
- 因此 `verify_backup_v2.py` **NOT RUN**：没有真实 SAF 保存、没有真实文件、没有真机回读。
- 按批次规则“B-01 至 B-15 有阻断项未完成不得标 PASS”，不能把 JVM 结果替代真机结果。

已完成的硬证据：**273/273 JVM PASS**、静态检查 PASS、QA/debug 构建成功、合并 Manifest 安全属性正确。

## 2. 阻断项

| 阻断项 | 状态 | 说明 |
|---|---|---|
| B-01 完整 v2 导出（QA 真机） | **NOT RUN** | JVM 合成全表 PASS，但没有真机文件 |
| B-11 SAF 写入失败/回读（QA 真机） | **NOT RUN** | JVM fake destination PASS；真实 SAF 未执行 |
| B-12 解密 payload 敏感字段扫描（QA 真机） | **NOT RUN** | JVM 解密扫描 PASS；真实文件未生成 |
| 真实文件大小/耗时 | **NOT RUN** | 仅 JVM 合成样本 6,689 B / 397ms |
| QA 导出脚本 | **NOT RUN** | 脚本与独立 Node 探针已就绪 |

## 3. 已通过的验收项

| 项 | 结果 |
|---|---|
| 10 表逐字段编码 | PASS（JVM fixture/validator） |
| counts / perTableSha256 / payloadSha256 | PASS |
| Canonical JSON / 空表向量 | PASS |
| PBKDF2 + AES-GCM 固定向量 | PASS |
| 错误口令/篡改/截断 | PASS |
| API Key/consent/Keystore/fake engine 排除 | PASS（解密 payload 扫描） |
| v1 只识别、旧导出不回归 | PASS |
| SAF 失败不报成功、清理/删除策略 | PASS（fake）；真机 NOT RUN |
| 系统备份排除 | PASS（静态+合并 Manifest） |
| 全量 JVM | **273/273 PASS**，35.2s 测试 |
| APK 构建 | PASS；QA `baa7a8b1...`，debug `dc97eedc...` |

## 4. 验收命令（设备恢复后）

```bash
cd /root/workspace/mengya-life
adb devices -l
# 期望出现唯一 QA 设备
./scripts/verify.sh --module backup-export --level feature
```

或直接：

```bash
DEV=<qa-serial> python3 tools/ui-verify/verify_backup_v2.py
```

该脚本会：清 QA 假数据 → 建目标/日记 → 设置页 → 口令 → SAF 保存 → 回读成功 →
拉取文件 → Node 独立解密/哈希/表校验 → 与 DB 计数对照 → 删除本次新文件。

## 5. 重新判定为 PASS 的条件

1. QA 设备在线，`verify_backup_v2.py` 退出码 0；
2. 真实文件能独立解密，10 表 counts 与 QA DB 一致；
3. 失败注入/取消路径不显示成功，且不删除已有文件；
4. 产物大小/耗时记录；
5. 不引入恢复代码。

满足后可将本文件状态改为 **PASS**；当前不得改。

## 6. 未完成/NOT RUN 清单

- QA 真机完整导出、SAF 回读、真实文件解密（B-01/B-11/B-12）；
- 真实备份文件大小/耗时；
- 设备上的设置页 UI 手测（仅编译通过）；
- 恢复/导入（本批明确不做）；
- 七组完整真机回归（本批明确不做，不能当作 PASS）。

## 7. 证据

- 全量 JVM：`docs/reports/G4A1-acceptance/jvm/report.json`
- 构建：`docs/reports/G4A1-acceptance/build/build.log`
- 合并 Manifest：`docs/reports/G4A1-acceptance/manifest/merged-{qa,debug}.xml`
- APK 哈希：`docs/reports/G4A1-acceptance/apk.sha256`
- 合成指标：`docs/reports/G4A1-acceptance/jvm/metric.txt`
- QA NOT RUN：`docs/reports/G4A1-acceptance/qa/NOT-RUN.txt`
- 设备脚本：`tools/ui-verify/verify_backup_v2.py`
- 独立探针：`tools/ui-verify/backup_v2_probe.js`
