# G4A-1 测试结果（TEST RESULTS）

> 执行环境：手机上的 Ubuntu proot；Gradle 8.9 / JDK 21 / AGP 8.7.3。
> 时间：2026-11-05。所有数字为实际执行；没有执行的项目明确写 NOT RUN。
> QA 真机：`10.13.224.241:46295` / `2509FPN0BC` / Android SDK 37 / `com.mengyalife.app.qa` 0.14.1-g3e2-1-qa。

## 1. 模块注册与命令

`tests/test-map.json` 已注册：`backup-format`、`backup-crypto`、`backup-export`、`backup-security`；影响规则按格式/加密/导出/安全分流，未把所有 `backup/**` 一律升 L2。

| 命令 | 退出码 | 结果 | 实测 |
|---|---|---|---|
| `./scripts/verify.sh --module backup-format --level fast` | 0 | PASS（2 类） | 10.0s |
| `./scripts/verify.sh --module backup-crypto --level fast` | 0 | PASS（1 类） | 8.3s |
| `./scripts/verify.sh --module backup-export --level fast` | 0 | PASS（3 类） | 10.0s |
| `./scripts/verify.sh --module backup-security --level fast` | 0 | PASS（1 类） | 9.3s |
| `./scripts/verify.sh --all --level fast` | 0 | PASS，**274/274 JVM** | 38.1s（测试 35.2s） |
| `./gradlew :app:assembleQa :app:assembleDebug --offline` | 0 | BUILD SUCCESSFUL | 25s（增量） |
| `DEV=10.13.224.241:46295 python3 tools/ui-verify/verify_backup_v2.py` | **0** | **28/28 PASS** | 端到端备份 18.06s |

全量 JVM 报告：`docs/reports/G4A1-acceptance/jvm/report.json`（274 tests / 0 failures）。
QA 真机日志：`docs/reports/G4A1-acceptance/qa/run-output.txt`。

## 2. B-01..B-15 覆盖

| ID | 用例 | 结果 | 证据 |
|---|---|---|---|
| B-01 | 完整 v2 导出（10 表） | **PASS** | JVM full + QA 真机 |
| B-02 | 空数据库备份 | **PASS** | JVM |
| B-03 | 文件过大 | **PASS** | JVM 64 MiB 边界（不分配 64MB） |
| B-04 | v1 识别、不误当 v2 | **PASS** | JVM `detect` V1_LEGACY |
| B-05 | 未知表/未知 feature | **PASS** | JVM；未知可选字段 warning |
| B-06 | 加密往返 | **PASS** | JVM PBKDF2/AES-GCM |
| B-07 | 错误口令 | **PASS** | JVM 统一拒绝 |
| B-08 | 篡改/截断 | **PASS** | JVM AEAD/hash 拒绝 |
| B-09 | KDF 参数边界 | **PASS** | JVM 210k–600k |
| B-10 | counts/per-table hash | **PASS** | JVM 篡改均拒绝 |
| B-11 | SAF 写入失败 | **PASS** | G4A-1.1 QA 真机 6 场景/45 断言 |
| B-12 | API Key/consent 排除 | **PASS** | JVM 解密扫描 + QA 真机解密扫描 |
| B-13 | 固定向量 | **PASS** | JVM KDF/AES-GCM、CJ、空表 hash |
| B-14 | 未知字段/未来版本 | **PASS** | JVM 未知表/feature/version 拒绝 |
| B-15 | 原始 vs 恢复后哈希 | **PASS** | JVM post-restore projection |

## 3. QA 真机导出（假数据）

| 项 | 值 |
|---|---|
| 数据 | 1 日记、1 目标、1 周回顾；无真实用户数据 |
| 文件大小 | **5,445 字节** |
| 端到端耗时 | **18.06s**（确认口令 → 成功；含 310k PBKDF2、SAF、回读） |
| 10 表 counts | journal 1、goals 1、checkins 0、messages 0、proposals 0、signals 0、memories 0、insights 0、weekly 1、weeklyAI 0 |
| DB counts 对照 | **PASS** |
| 独立 Node 解密/哈希/表校验 | **PASS** |
| 解密 payload 敏感字段扫描 | **PASS** |
| 导出后 DB 不变 | **PASS** |
| 本次新文件清理 | **PASS**（不触碰旧文件） |
| 汇总 | **28/28 PASS** |

样本与探针：

- `docs/reports/G4A1-acceptance/qa/sample-v2.mengyalife`
- `docs/reports/G4A1-acceptance/qa/probe.json`
- `docs/reports/G4A1-acceptance/qa/probe-recheck.json`

## 4. 加密/哈希固定向量实测

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

Canonical/空表向量见 `BACKUP-V2-SECURITY.md` §2；`CanonicalJsonTest` 3 条全部通过。

## 5. APK

| 包 | 版本 | size | SHA256 |
|---|---|---|---|
| QA `app-qa.apk` | `0.14.1-g3e2-1-qa` | 12,517,274 B | `baa7a8b15bda89bc21b86cac86ceecc64861b2099b3b3dd304d698e432a04fba` |
| Debug `app-debug.apk` | `0.14.1-g3e2-1` | 12,517,270 B | `dc97eedc77930fa2b5943fef8245ac0a68399db21bdeb86a6f882f85ee532c76` |

## 6. 证据路径

- 全量 JVM：`docs/reports/G4A1-acceptance/jvm/report.json`
- 模块运行：`docs/reports/G4A1-acceptance/modules/{backup-format,backup-crypto,backup-export,backup-security}/report.json`
- QA 真机：`docs/reports/G4A1-acceptance/qa/run-output.txt`、`device.txt`、`sample-v2.mengyalife`
- 独立探针复验：`docs/reports/G4A1-acceptance/qa/probe-recheck.json`
- 合并 Manifest：`docs/reports/G4A1-acceptance/manifest/merged-{qa,debug}.xml`
- 构建日志：`docs/reports/G4A1-acceptance/build/build.log`

## 7. 未执行

- B-11 已由 G4A-1.1 设备端可控注入覆盖（取消/写失败/截断/回读/清理/删除失败）；未做物理拔盘。
- 七组完整真机回归：本批不做。
- 恢复/导入：本批不做。

> 未执行项没有被报成 PASS；B-11 已由 G4A-1.1 真机故障注入覆盖。
