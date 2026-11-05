# G4A-1 测试结果（TEST RESULTS）

> 执行环境：手机上的 Ubuntu proot；Gradle 8.9 / JDK 21 / AGP 8.7.3。
> 时间：2026-11-05。所有数字为实际执行；没有执行的项目明确写 NOT RUN。
> 设备：`adb devices` 为空（历史无线地址 `192.168.10.183:33537` 离线/不可连接），因此 QA 真机与真实文件未执行。

## 1. 模块注册与命令

`tests/test-map.json` 已注册：`backup-format`、`backup-crypto`、`backup-export`、`backup-security`；影响规则按格式/加密/导出/安全分流，未把所有 `backup/**` 一律升 L2。

| 命令 | 退出码 | 结果 | 实测 |
|---|---|---|---|
| `./scripts/verify.sh --module backup-format --level fast` | 0 | PASS（2 类） | 10.0s |
| `./scripts/verify.sh --module backup-crypto --level fast` | 0 | PASS（1 类） | 8.3s |
| `./scripts/verify.sh --module backup-export --level fast` | 0 | PASS（3 类） | 10.0s |
| `./scripts/verify.sh --module backup-security --level fast` | 0 | PASS（1 类） | 9.3s |
| `./scripts/verify.sh --all --level fast` | 0 | PASS，**273/273 JVM** | 38.1s（测试 35.2s） |
| `./gradlew :app:assembleQa :app:assembleDebug --offline` | 0 | BUILD SUCCESSFUL | 25s（增量） |
| QA 真机 `verify_backup_v2.py` | **NOT RUN** | 无设备 | — |

全量 JVM 报告：`docs/reports/test-runs/20261105-091819/report.json`（273 tests / 0 failures）。
模块报告：`docs/reports/test-runs/20261105-091x*`（具体目录见测试运行时输出；最终归档见 §7）。

## 2. B-01..B-15 覆盖

| ID | 用例 | JVM/静态结果 | QA 真机 |
|---|---|---|---|
| B-01 | 完整 v2 导出（10 表） | **PASS**：`BackupExporterTest` + `BackupFormatV2Test` | **NOT RUN** |
| B-02 | 空数据库备份 | **PASS**：empty snapshot | **NOT RUN** |
| B-03 | 文件过大 | **PASS**：64 MiB 边界测试（不分配 64MB） | **NOT RUN** |
| B-04 | v1 识别、不误当 v2 | **PASS**：`detect` V1_LEGACY | **NOT RUN** |
| B-05 | 未知表/未知 feature | **PASS**：拒绝；未知可选字段 warning | **NOT RUN** |
| B-06 | 加密往返 | **PASS**：PBKDF2/AES-GCM | **NOT RUN** |
| B-07 | 错误口令 | **PASS**：统一拒绝 | **NOT RUN** |
| B-08 | 篡改/截断 | **PASS**：AEAD/hash 拒绝 | **NOT RUN** |
| B-09 | KDF 参数边界 | **PASS**：210k–600k；低迭代拒绝 | **NOT RUN** |
| B-10 | counts/per-table hash | **PASS**：篡改均拒绝 | **NOT RUN** |
| B-11 | SAF 写入失败 | **PASS（fake destination）**：不报成功、删除/提示 | **NOT RUN** |
| B-12 | API Key/consent 排除 | **PASS**：解密 payload 扫描；Manifest 静态检查 | **NOT RUN** |
| B-13 | 固定向量 | **PASS**：KDF/AES-GCM、CJ、空表 hash | **NOT RUN** |
| B-14 | 未知字段/未来版本 | **PASS**：未知表/feature/version 拒绝 | **NOT RUN** |
| B-15 | 原始 vs 恢复后哈希 | **PASS**：post-restore projection 变换后 hash 不同 | **NOT RUN** |

**结论：B-01/B-11/B-12 的真机部分未完成，因此 B-01..B-15 不能整体标 PASS；Gate 不得标 PASS。**

## 3. 加密/哈希固定向量实测

来自 `BackupCryptoTest`（通过）：

```text
passphrase = correct horse battery staple
salt       = 000102030405060708090a0b0c0d0e0f
iterations = 1000 (test only)
nonce      = 101112131415161718191a1b
AAD        = {"backupFormatVersion":2,"format":"mengyalife-backup","purpose":"test"}
plaintext  = {"hello":"mengya"}

key        = a69b179e3add3c1e0aaf227a0eb3aa2aa8645ab86fecf6ca00c17512697c719e
ciphertext = 67c5d9460baa9286504b5d18b4fa4a929757
tag        = 73b3c4cffa6ca1ee62c38bb3d010418a
```

Canonical/空表向量的实测值见 `BACKUP-V2-SECURITY.md` §2；`CanonicalJsonTest` 3 条全部通过。

## 4. 数据/敏感字段检查

- `BackupExporterTest` 在**解密后的 payload**中断言不含：`encrypted_api_key`、`use_fake_engine`、`granted`、`mengya_ai_key`。
- `BackupFormatV2Test` 覆盖 10 表、counts、perTableSha256、空表、未知表/feature、未来版本。
- `BackupManifestSecurityTest` 检查 Manifest 与 `data_extraction_rules.xml`；最终合并 Manifest（QA/debug）已人工核对，均含三项属性。
- 旧 v1 格式与 `ExportTest` 未修改；全量 JVM 包含原有导出测试。

## 5. 备份文件大小与耗时

| 项 | 结果 |
|---|---|
| QA 真机真实备份文件大小 | **NOT RUN**（设备离线） |
| QA 真机真实耗时 | **NOT RUN** |
| JVM 合成样本（12 条假记录，310k 迭代） | **6,689 字节；397ms**（`G4A1_METRIC`，`BackupArtifactMetricsTest`） |

合成样本只用于确认编码/加密链路与大小量级，**不代表真实用户数据或真机性能**。

## 6. APK

| 包 | 版本 | size | SHA256 |
|---|---|---|---|
| QA `app-qa.apk` | `0.14.1-g3e2-1-qa` | 12,517,274 B | `baa7a8b15bda89bc21b86cac86ceecc64861b2099b3b3dd304d698e432a04fba` |
| Debug `app-debug.apk` | `0.14.1-g3e2-1` | 12,517,270 B | `dc97eedc77930fa2b5943fef8245ac0a68399db21bdeb86a6f882f85ee532c76` |

## 7. 证据路径

- 全量 JVM：`docs/reports/G4A1-acceptance/jvm/report.json`（273/273；已归档）
- 模块运行：`docs/reports/G4A1-acceptance/modules/{backup-format,backup-crypto,backup-export,backup-security}/report.json`
- QA 脚本：`tools/ui-verify/verify_backup_v2.py`（**NOT RUN**）
- 独立解密探针：`tools/ui-verify/backup_v2_probe.js`
- 合并 Manifest：`docs/reports/G4A1-acceptance/manifest/merged-{qa,debug}.xml`
- 构建日志：`docs/reports/G4A1-acceptance/build/build.log`

> 本批没有执行恢复测试、没有跑七组真机回归；这是范围限制，不是把它们当作 PASS。
