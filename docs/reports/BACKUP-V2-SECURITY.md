# 备份 v2 安全说明（BACKUP-V2-SECURITY）

> 对应 Gate 4A-1 实现；设计依据 `G4A0-BACKUP-FORMAT-V2.md` 与 `G4A0-SECURITY-PRIVACY.md`。
> 本轮只做导出；没有恢复/导入。

## 1. 加密参数（已实现）

| 参数 | 值 |
|---|---|
| KDF | PBKDF2WithHmacSHA256 |
| 迭代 | 生产默认 **310,000**；允许 210,000–600,000 |
| salt | 16 字节随机（`SecureRandom`） |
| AEAD | AES-256-GCM，tag 128-bit |
| nonce | 12 字节随机，每次导出独立 |
| 派生 key | 256-bit |
| 压缩 | 无 |
| AAD | `CanonicalJson({format,backupFormatVersion,payloadSha256,encryption{cipher,kdf,iterations,salt,nonce,tagBits}})` UTF-8；不含 `aad`/`ciphertext` |

代码：`data/backup/BackupCrypto.kt`；参数边界 `BackupCryptoPolicy`。
口令只存在于 Compose 对话框/`CharArray`/调用栈；不写 DataStore、不写日志、不进错误消息。

## 2. 固定测试向量结果

测试 `BackupCryptoTest`（已通过）使用：

```text
passphrase = correct horse battery staple
salt       = 000102030405060708090a0b0c0d0e0f
iterations = 1000（仅测试；生产 310000）
nonce      = 101112131415161718191a1b
AAD        = {"backupFormatVersion":2,"format":"mengyalife-backup","purpose":"test"}
plaintext  = {"hello":"mengya"}
```

实测结果：

```text
key        = a69b179e3add3c1e0aaf227a0eb3aa2aa8645ab86fecf6ca00c17512697c719e
ciphertext = 67c5d9460baa9286504b5d18b4fa4a929757
tag        = 73b3c4cffa6ca1ee62c38bb3d010418a
```

Canonical 向量（`CanonicalJsonTest` 已通过）：

```text
{"b":2,"a":{"z":null,"y":[true,"x"]}} → {"a":{"y":[true,"x"],"z":null},"b":2}
SHA-256 = 8c7c049c86bae2410bbd0a21e751a14e68e57edfc270d1862be217c402e9e203

{"a":1.0,"b":1.50,"c":-0.0} → {"a":1,"b":1.5,"c":0}
SHA-256 = 20aa7fa8b4765bc5cfa908d31348ba1be7ebcbaaa3d9a5917e6aced9e16613a6

[] SHA-256 = 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945
```

## 3. 完整性/防篡改

- 外层 `payloadSha256` = SHA-256(Canonical JSON payload 的 UTF-8 字节)，payload 不含自身/加密字段。
- `perTableSha256` = 每表完整行数组的 Canonical JSON SHA-256；空表使用 `[]` 向量。
- AEAD tag 覆盖密文与 AAD；`AAD` 绑定 payload hash/KDF/salt/nonce。
- 回读时解密并重算 payload hash、counts、per-table hash；任一步失败不显示成功。
- 错误口令/篡改/截断统一拒绝；测试覆盖错误口令、字节翻转、截断、低迭代/坏 salt、空口令。

## 4. 秘密排除（已测试）

以下字段**没有进入 payload**：

- `encrypted_api_key` / API Key 明文 / Key 哈希；
- Android Keystore alias `mengya_ai_key` / `mengya_ai_key_qa`；
- consent：`granted/text_version/categories_version/granted_at/recipient/categories/epoch`；
- QA fake engine `use_fake_engine`；
- 设备标识/序列号/凭据。

`BackupExporterTest` 在**解密后的 payload 字符串**中检查上述禁止项，不是只扫描加密文件。
`AppBackupSettingsSource` 只读 endpoint/model/enabled 设置，不读 API Key。

## 5. 文件与 SAF 安全

- 明文 payload 只在内存；临时文件写入 `noBackupFilesDir/backup-tmp`，内容是**已加密** artifact。
- 临时文件 `flush` + 尽力 `fd.sync`；成功后删除。
- SAF 只使用 `CreateDocument` 新建目标；不覆盖已有文件。
- 写入后强制回读 → 解密 → 结构/哈希校验；不以流无异常为成功。
- 失败时删除临时文件；若目标是本次新建且可删除则删除；删除失败明确提示“文件可能无效”，不显示成功。
- 不自动上传/同步；不扫描公共目录。

## 6. 系统备份（已实现并验证合并 Manifest）

- `allowBackup=false` 保持。
- 新增 `dataExtractionRules` + `fullBackupContent=false`。
- `cloud-backup`/`device-transfer` 排除 root/file/database/sharedpref/external。
- 最终合并 Manifest（QA 与 debug）均确认：

```text
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="false"
```

这避免未加密 DB/Prefs 被云备份或部分 OEM 的 D2D 复制（设计风险 P0）。

## 7. 口令与认证边界

- UI 要求至少 10 个字符；不强制复杂度、不做口令提示存储。
- 无口令找回/后门；忘口令 = 备份不可解。
- 不把口令或 Key 放入日志、证据、错误消息或 Snackbar。
- `BackupViewModel` 在失败/取消/完成后清空 `CharArray`；Compose 输入在对话框关闭时清空（String 无法原地擦除，属于 UI 层残余，已限制生命周期）。

## 8. 已知残余风险

| 级别 | 风险 | 状态 |
|---|---|---|
| — | 真实 SAF 写失败 | G4A-1.1 设备端可控注入 6 场景/45 断言 PASS（未物理拔盘） |
| P1 | 设置快照非跨存储原子；变化检测后失败，极端窗口仍可能 | 已两次读取+加密后核对；G4A-2 若加恢复门禁再收紧 |
| P1 | 生产 310k 迭代未在目标机基准锁定 | 当前实现固定 310k；G4A-1 收口前可基准 |
| P1 | 无 Android instrumentation；SAF 由 UIAutomator 脚本覆盖 | QA 真机脚本 28/28 PASS；失败注入仍为 fake |
| P2 | `BackupPostRestoreProjection` 只是哈希投影，不是恢复 | 明确不用于写 DB |

## 9. 结论

加密、哈希、秘密排除、系统备份规则在 JVM/静态层已实现并通过测试；**QA 真机 SAF、回读与真实文件独立解密 28/28 PASS**。B-11 已在 G4A-1.1 由设备端可控注入覆盖（6 场景/45 断言）。
