# G4A-0 备份格式 v2 设计（BACKUP FORMAT V2）

> 本文是设计，不是已实现代码。当前产品只有 v1 三表导出（见 `G4A0-DATA-INVENTORY.md`）。
> 目标：在不破坏 v1 既有语义的前提下，定义可版本化、可校验、可加密、可跨设备恢复的完整备份格式。

## 1. 现有 v1 审计

### 1.1 真实行为

| 项 | v1 现状 | 证据 |
|---|---|---|
| 格式标识 | `format="mengyalife-backup"` | `ExportJson.kt:43` |
| 格式版本 | `schemaVersion=1` | `ExportJson.kt:43,98` |
| 数据范围 | journal 5 字段、goals 11 字段、checkins 7 字段 | `ExportJson.kt:152-184` |
| 一致性 | Room `withTransaction` 内读三表 | `DataExportRepository.kt:47-49` |
| 校验 | JSON 结构、计数、id、父子、checkin→goal | `ExportJson.kt:206+` |
| 加密 | 无 | — |
| 导入 | **没有**；Settings 明确“导入恢复尚未实现” | `SettingsScreen.kt:264-268,406` |
| 文件写入 | SAF `openOutputStream(uri,"wt")` 直接写，回读校验 | `ExportViewModel.kt:157-168` |
| 文件名 | `mengya-life-export-YYYYMMDD-HHmmss.json` | `ExportJson.kt` |
| 系统备份 | `allowBackup=false` | `AndroidManifest.xml:15` |

### 1.2 v1 的语义（必须保留）

- v1 是“**三表导出**”，不是完整备份；它的字段、文件名、JSON 结构不能改变。
- v1 文件可以被新版本**识别**，但不能被静默当作完整备份恢复。
- v1 不含分析状态、对话、草案、成长线索/记忆、洞察、周回顾、设置、加密。
- v1 的 `database.schemaVersion` 是导出时的 Room 版本（当前 11），不是备份格式版本。

## 2. v2 设计原则

1. **格式版本与数据库版本分离**：`backupFormatVersion` 独立于 `dbSchemaVersion`。
2. **完整覆盖用户数据**：所有 10 张 Room 表 + 非秘密设置；秘密/授权按策略排除。
3. **恢复安全优先**：宁可拒绝，也不做部分恢复、不静默丢字段。
4. **可审计**：每个表有计数与摘要，恢复前可预览，恢复后可核验。
5. **可移植**：默认不绑定设备；API Key/Keystore 永不进入备份。
6. **向后兼容**：v1 只识别；v2 读取器支持 `dbSchemaVersion <= current` 的有界映射，未来版本拒绝。
7. **不把 SQLite 文件当备份**：WAL/连接/设备差异不可控；使用逻辑行数据。

## 3. 文件类型与命名

| 项 | 建议 |
|---|---|
| 扩展名 | `.mengyalife`（加密 v2）；v1 继续 `.json` |
| MIME | `application/octet-stream`（SAF 不依赖扩展名） |
| 文件名 | `mengya-life-backup-v2-YYYYMMDD-HHmmss.mengyalife` |
| 明文 v2 | **不默认提供**；仅调试构建可导出，且文件名带 `-plaintext-` 警告 |
| 识别 | 先读前 4 KiB，按 `format` + `backupFormatVersion` 判定；不信任扩展名 |

## 4. v2 逻辑载荷（解密后的明文 JSON）

### 4.1 两层结构与顶层字段

v2 文件分两层，**避免任何自引用哈希**：

```text
外层（明文头，加密/未加密都保留）
  format, backupFormatVersion, payloadSha256,
  encryption {cipher,kdf,iterations,salt,nonce,tagBits},
  aad (可选 debug), ciphertext (或 payloadBase64)

内层 payload（被哈希/加密的对象；不含 payloadSha256/encryption/ciphertext）
  format, backupFormatVersion, backupId, createdAt, generator,
  compatibility, counts, data, settings, integrity
```

内层 payload 示例（注意：**没有** `payloadSha256`）：

```json
{
  "format": "mengyalife-backup",
  "backupFormatVersion": 2,
  "backupId": "0f5b4f0e-9f6a-4a6e-9c2d-2f7a0f6f1a11",
  "createdAt": 1793827200000,
  "generator": {
    "appPackage": "com.mengyalife.app",
    "qaBuild": false,
    "versionName": "0.14.1-g3e2-1",
    "versionCode": 15,
    "androidSdk": 35
  },
  "compatibility": {
    "dbSchemaVersion": 11,
    "requiredFeatures": ["core", "derived-insight", "derived-weekly"],
    "minReaderBackupFormatVersion": 2
  },
  "counts": {
    "journalEntries": 0, "goals": 0, "goalCheckins": 0,
    "advisorMessages": 0, "goalProposals": 0,
    "growthSignals": 0, "growthMemories": 0,
    "insightReports": 0, "weeklyReviews": 0, "weeklyAiReviews": 0
  },
  "data": {
    "journalEntries": [], "goals": [], "goalCheckins": [],
    "advisorMessages": [], "goalProposals": [],
    "growthSignals": [], "growthMemories": [],
    "insightReports": [], "weeklyReviews": [], "weeklyAiReviews": []
  },
  "settings": {
    "themeMode": "SYSTEM",
    "weeklyReview": { "enabled": true, "aiEnabled": false },
    "ai": { "endpoint": "", "model": "" }
  },
  "integrity": {
    "perTableSha256": {
      "journalEntries": "hex", "goals": "hex"
    }
  }
}
```

外层示例：

```json
{
  "format": "mengyalife-backup",
  "backupFormatVersion": 2,
  "payloadSha256": "e02b9645...",
  "encryption": {
    "cipher": "AES-256-GCM",
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 310000,
    "salt": "base64-16-bytes",
    "nonce": "base64-12-bytes",
    "tagBits": 128
  },
  "ciphertext": "base64(ciphertext||tag)"
}
```

- `payloadSha256` 只在外层；内层 payload 绝不包含它，因此不存在“哈希包含自身”。
- 外层 `aad` 只是可选 debug 字段，**不参与** AAD 计算（见 §5.2.1）。
- `counts` 与数组长度必须一致；`counts` 也参与校验。
- `data` 必须包含全部声明的核心表；`derived` 表可以省略，但 `requiredFeatures` 要如实声明。
- `weeklyAiReviews` 只归档、不恢复（见 §7/恢复文档）。

### 4.2 行对象（与列一一对应）

行对象使用**完整字段**，不允许“只导出 UI 需要的字段”。示例：

```json
{
  "id": 1,
  "title": "标题",
  "body": "正文",
  "createdAt": 1793827200000,
  "updatedAt": 1793827200000,
  "analysisStatus": "PENDING",
  "analysisHash": null,
  "analysisError": null,
  "analysisAttempts": 0,
  "analyzedAt": null,
  "analysisLastAttemptAt": null
}
```

表字段映射：

| v2 数组 | Room 表 | 备注 |
|---|---|---|
| `journalEntries` | `journal_entries` | 11 列 |
| `goals` | `goals` | 11 列；按 level/id 拓扑 |
| `goalCheckins` | `goal_checkins` | 7 列；FK |
| `advisorMessages` | `advisor_messages` | 12 列 |
| `goalProposals` | `goal_proposals` | 13 列；JSON 字段保持字符串或结构化？见 §4.3 |
| `growthSignals` | `growth_signals` | 15 列 |
| `growthMemories` | `growth_memories` | 16 列 |
| `insightReports` | `insight_reports` | 11 列；派生 |
| `weeklyReviews` | `weekly_reviews` | 11 列；派生 |
| `weeklyAiReviews` | `weekly_ai_reviews` | 13 列；只归档，不恢复 |

### 4.3 JSON 列：保持字符串还是结构化？

Room 中的 `sources_json`、`nodes_json`、`facts_json`、`sections_json`、`highlights_json`、`signal_ids_json` 等目前是 TEXT，内容是应用自己生成的 JSON。

**建议：在 v2 中同时保留“语义化字段”和“原始字符串”两选一，而不是无条件展开：**

- 对已知结构（proposal nodes、weekly facts/sections、insight facts/insights）使用结构化数组，便于校验与未来迁移；
- 对未知/历史脏数据提供 `raw` 字段（或 `jsonText`），恢复时原样写回并标记警告；
- 对 `weekly_ai_reviews.fact_ids_json` 特别处理：它实际是 U+001F 分隔串（`WeeklyAiService.kt:184`），v2 应输出 `factIds: ["..."]`，同时保留 `legacyFactIdsRaw` 以审计；历史行不强行改写。

> 这是格式设计，不是本批改数据。G4A-1 修 writer/reader；历史行修正需单独评审。

### 4.4 设置载荷

```json
"settings": {
  "themeMode": "SYSTEM|LIGHT|DARK",
  "weeklyReview": {
    "enabled": true,
    "aiEnabled": false,
    "notificationAsked": false
  },
  "ai": {
    "endpoint": "https://api.example.com/v1",
    "model": "model-name"
  }
}
```

规则：

- **不包含** `encrypted_api_key`、Keystore alias、`use_fake_engine`。
- **不包含** consent 的任何键（granted/textVersion/categoriesVersion/grantedAt/recipient/categories/epoch）。
- `aiEnabled` 在导出时记录，但恢复时**强制 false**（见恢复设计）。
- `notificationAsked` 恢复为 false；OS 通知权限不能被备份恢复。
- `ai` 是否纳入是 A/B 决策（§11）；默认建议纳入 endpoint/model 但不自动启用。

### 4.5 Canonical JSON、payloadSha256、perTableSha256

#### 4.5.1 Canonical JSON（CJ）精确定义

CJ 用于 `payloadSha256`、`perTableSha256`、AAD 字节序列；**不是**用于展示的 pretty JSON。

1. 编码为 UTF-8，无 BOM；末尾无换行。
2. 对象成员按**成员名的 UTF-8 字节序**升序排列；不允许重复键。
3. 无任何多余空白：`{`/`}`/`[`/`]`/`,`/`:` 之外不出现空格、换行、制表符。
4. 字符串用双引号；转义 `"`→`\"`、`\`→`\\`、控制字符使用 `\b \f \n \r \t`（其他 <0x20 用 `\u00XX`）；非 ASCII 字符直接输出 UTF-8，不转成 `\u`；`/` 不转义。
5. 数字：
   - `-0` → `0`；不允许前导 `+`/前导零；
   - 整数输出十进制整数字面量；
   - 有限小数用 `BigDecimal` 解析，去掉尾随零，使用**无指数**的普通十进制（如 `1.0`→`1`、`1.50`→`1.5`、`1e3`→`1000`）；
   - `NaN`/`Infinity` 非法（导出前拒绝）。
6. `true`/`false`/`null` 小写。
7. 数组保持语义顺序；表数组按主键升序（journal id、goals id、checkins id、messages id、proposals id、signals id、memories id、insights id、weekly id、weekly AI period_key）。

向量：

```text
输入（键乱序）：{"b":2,"a":{"z":null,"y":[true,"x"]}}
CJ 输出：{"a":{"y":[true,"x"],"z":null},"b":2}
SHA-256：8c7c049c86bae2410bbd0a21e751a14e68e57edfc270d1862be217c402e9e203

数字规范化：{"a":1.0,"b":1.50,"c":-0.0} → {"a":1,"b":1.5,"c":0}
SHA-256：20aa7fa8b4765bc5cfa908d31348ba1be7ebcbaaa3d9a5917e6aced9e16613a6

空表：[] → SHA-256 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945
```

#### 4.5.2 payloadSha256 范围（明确排除）

```text
payloadSha256 = hex(SHA-256(UTF-8(CJ(payload))))
```

- 覆盖：内层 payload 的**全部字段**，包括 `format/backupFormatVersion/backupId/createdAt/generator/compatibility/counts/data/settings/integrity`。
- 排除（它们只在外层）：`payloadSha256` 本身、`encryption`、`ciphertext`、`payloadBase64`、`aad`、文件级哈希。
- 内层 `integrity.perTableSha256` 在 payload 内，因此被 payloadSha256 覆盖；它只覆盖表数组，不覆盖 payloadSha256，**无循环**。
- 读取时对**解密后的原始字节**做 CJ 后再哈希；不要先 pretty-print/重排再哈希。若原始字节不是 CJ，则以 CJ 重新序列化后哈希，并在报告中标记“非规范编码”。

#### 4.5.3 perTableSha256 范围

```text
perTableSha256[table] = hex(SHA-256(UTF-8(CJ(tableRows[table]))))
```

- 覆盖：该表在 `data[table]` 中的完整行数组；每行包含该表全部 v2 字段；数组按主键升序。
- 不覆盖：表名、counts、其他表、`integrity`、payloadSha256。
- 行对象中不允许出现 `perTableSha256`/`payloadSha256` 字段。
- 空表按 `[]` 计算（见 §4.5.1）。
- `counts[table]` 必须等于数组长度；两者任一不符即失败。

#### 4.5.4 原始备份哈希 vs 恢复后预期数据哈希

| 哈希 | 计算对象 | 用途 |
|---|---|---|
| `fileSha256` | 备份文件的**精确字节**（外层 JSON 文件） | 证据/传输校验；不在文件内，避免自引用 |
| `payloadSha256` | 内层 payload 的 CJ UTF-8 字节 | 解密后完整性 |
| `perTableSha256` | 单表行数组的 CJ UTF-8 字节 | 定位损坏表 |
| `expectedRestoredSha256` | 按恢复规则转换后的“预期恢复后 payload”（不写回文件） | 恢复后与 DB 重读结果比对 |

`expectedRestoredSha256` 与 `payloadSha256` 不同，因为恢复有明确转换：

- `journal.analysis_status='ANALYZING'` → `FAILED`（或 `PENDING`，按规则）；
- `weekly_ai_reviews` 不恢复（从期望结果排除）；
- `weekly_reviews.notified_at` → `null`；
- 旧 schema 缺字段 → 显式默认值；
- 时间戳/枚举等按规则规范化。

恢复前用同一转换规则计算 `expectedRestoredSha256`，恢复后从 DB 重新读取并计算 `restoredSha256`；两者必须一致。**不能**把备份的 `payloadSha256` 直接当作恢复后 DB 的哈希。

## 5. 加密封装（推荐默认）

### 5.1 外层结构与 AAD 范围

```json
{
  "format": "mengyalife-backup",
  "backupFormatVersion": 2,
  "payloadSha256": "hex",
  "encryption": {
    "cipher": "AES-256-GCM",
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 310000,
    "salt": "base64-16-bytes",
    "nonce": "base64-12-bytes",
    "tagBits": 128
  },
  "aad": "optional-base64-of-aad-bytes",
  "ciphertext": "base64(ciphertext||tag)"
}
```

- `payloadSha256` 属于外层，计算范围见 §4.5.2；不进入 payload，因此无自引用。
- **AAD 不包含 `aad` 字段自身，也不包含 `ciphertext`/`payloadBase64`**；`aad` 只是可选 debug 快照。若存在且与重算值不一致，按损坏拒绝。
- **nonce** 每次导出随机 12 字节；同一密钥下绝不重用。
- **salt** 16 字节随机；每次导出重新生成。
- **不做压缩**（避免 zip bomb 与实现复杂度）；大小限制在解密前后都执行。
- 明文只存在于内存与短生命周期临时文件；写 SAF 前清理临时文件。

### 5.2 KDF/AEAD 选型比较

| 方案 | 跨设备 | 密钥遗失 | 安全强度 | 实现成本 | 结论 |
|---|---|---|---|---|---|
| 不加密 | 是 | 可读 | 依赖用户存储 | 最低 | 仅 v1 历史；v2 不默认 |
| Android Keystore AES-GCM（设备绑定） | **否** | 备份不可解 | 高（不出设备） | 低 | 适合**本地自动快照**，不适合换机备份 |
| 口令 + PBKDF2-HMAC-SHA256 + AES-256-GCM | **是** | 不可恢复 | 高（足够迭代） | 中（JDK/Android 内置） | **推荐默认** |
| 口令 + Argon2id + AES/ChaCha20 | 是 | 不可恢复 | 更高（抗 GPU） | 高（额外依赖/原生） | 备选，G4A-3 评估 |
| SQLCipher 整库加密 | 是 | 不可恢复 | 高 | 高（迁移/恢复复杂） | 不用于文件备份 |

参数建议（需 G4A-1 基准后锁定）：

- PBKDF2-HMAC-SHA256，salt 16B，输出 256-bit；
- 迭代次数初始候选 **310,000–600,000**，目标在目标机解锁 ≤1.5s；不得低于 210,000；
- AES-256-GCM，nonce 12B，tag 128-bit；
- 口令不落盘、不进日志、不进崩溃报告；不提供“找回密钥”。

### 5.2.1 AAD 的精确字节序列

AAD 的对象固定为：

```json
{
  "backupFormatVersion": 2,
  "encryption": {
    "cipher": "AES-256-GCM",
    "iterations": 310000,
    "kdf": "PBKDF2-HMAC-SHA256",
    "nonce": "base64-12-bytes",
    "salt": "base64-16-bytes",
    "tagBits": 128
  },
  "format": "mengyalife-backup",
  "payloadSha256": "hex"
}
```

```text
AAD bytes = UTF-8( CanonicalJSON(AAD object) )   // §4.5.1 的 CJ
```

排除字段（**不得**参与 AAD）：

- `aad`（debug 字段本身，避免循环）；
- `ciphertext` / `payloadBase64`（密文/载荷本身）；
- 文件级 `fileSha256`（在文件外）；
- `encryption` 之外的任何外层未来字段，除非同时提升 `backupFormatVersion` 并在新版本定义。

包含 `payloadSha256` 是有意的：它绑定“解密后的载荷摘要”，同时不形成循环，因为 `payloadSha256` 只覆盖内层 payload，不覆盖 AAD。

### 5.2.2 固定测试向量（仅测试，不是生产参数）

**KDF/AEAD 原语向量**（迭代 1000 只为测试速度；生产不得低于 210,000）：

```text
passphrase = "correct horse battery staple"
salt       = 000102030405060708090a0b0c0d0e0f (hex)
iterations = 1000
nonce      = 101112131415161718191a1b (hex)
AAD        = {"backupFormatVersion":2,"format":"mengyalife-backup","purpose":"test"}
plaintext  = {"hello":"mengya"}
key        = a69b179e3add3c1e0aaf227a0eb3aa2aa8645ab86fecf6ca00c17512697c719e
ciphertext = 67c5d9460baa9286504b5d18b4fa4a929757 (hex, 不含 tag)
tag        = 73b3c4cffa6ca1ee62c38bb3d010418a (hex, 128-bit)
ct+tag     = Z8XZRguqkoZQS10YtPpKkpdXc7PEz/psoe5iw4uz0BBBig== (base64)
```

**Canonical/表哈希向量**见 §4.5.1（空表 `[]` 的 SHA-256 可直接用于 perTable 单元测试）。

G4A-1 必须把上述向量写成 JVM 测试；任何 KDF/AEAD 参数变更都必须更新向量并提升 `backupFormatVersion`。

### 5.3 密钥遗失与跨设备

- 忘口令 = 备份永久不可解；UI 必须在设置口令时明确告知，不设后门。
- 跨设备：只要同一 `backupFormatVersion` 与口令即可解密；不绑定设备/Google 账号。
- API Key 不在备份中；恢复后必须在设置页重新输入，Keystore 重新生成。
- 不建议把备份口令存进 App 或 Keystore；可提示用户使用密码管理器。

### 5.4 不加密 v2 的选项

- 仅作为“高级/调试”导出，必须二次确认“文件包含日记/对话等敏感信息”；
- 默认关闭；正式版不提供或深藏；
- 文件名/内容带 `plaintext` 标记，导入时同样高亮警告；
- 推荐 A：v2 默认加密，明文仅开发构建。

## 6. 限额与校验

### 6.1 硬限制（建议初值）

| 项 | 上限 | 处理 |
|---|---|---|
| 加密文件 | 64 MiB | 读取前 stat 检查，超限拒绝 |
| 解密后载荷 | 48 MiB | 解密前按密文长度预估；解密后再次检查 |
| 总行数 | 200,000 | 解析时累计 |
| 单表行数 | 100,000 | 解析时累计 |
| 单 TEXT 字段 | 1 MiB（journal body 仍受 20,000 字业务限制） | 超限拒绝 |
| 单 JSON 字段 | 2 MiB | 超限拒绝 |
| 数组元素（如 highlights） | 5,000 | 超限拒绝 |
| `id`/时间戳 | 正整数/有限数 | 非法拒绝 |
| 字符串编码 | UTF-8 | 解码失败拒绝 |

> 这些是**防御性上限**，不是产品目标；G4A-1 用真实 QA 数据校准，G4A-3 复核。

### 6.2 完整性校验层次

1. 文件级：size、前 4 KiB 头部、`format`、`backupFormatVersion`；
2. 加密级：KDF 参数范围、salt/nonce 长度、AEAD tag；
3. 载荷级：`payloadSha256`、`counts` 与数组长度、`perTableSha256`；
4. 结构级：每行必填字段/类型/枚举/长度/有限数；
5. 引用级：goals 父子、checkins→goals、proposals→messages/goals、signals→journals、memories→signals/journals、insight/weekly 版本；
6. 业务级：`ANALYZING` 重置、`weekly_ai_reviews` 不恢复、consent 不恢复；
7. 恢复后：行数/摘要/采样逐项比对，失败回滚。

任一层失败都**不得**进入数据库写入。

### 6.4 未知字段与未来版本

| 位置 | 未知内容 | 行为 |
|---|---|---|
| 外层 | 未知字段 | 忽略 + 警告；不参与 AAD/hash（除非未来版本定义） |
| 外层 | `backupFormatVersion > 2` | **拒绝**；提示升级 App |
| 外层 | 未知 `cipher`/`kdf` | 拒绝；不猜测 |
| payload | 未知顶层字段 | 忽略 + 警告；不恢复 |
| payload | 未知表名/未知 `data` 数组 | **拒绝**；防止把未知数据当可恢复 |
| 行对象 | 未知字段 | 忽略 + 警告；已知字段缺失则按 §8 处理 |
| 枚举/状态 | 未知值 | **拒绝**；不允许 Converters 默认值静默替换 |
| `requiredFeatures` | 含未知 feature | **拒绝**；提示升级 App |
| `minReaderBackupFormatVersion` | > 当前 reader | **拒绝** |

原则：**未知表/未知语义 = 拒绝；未知可选元数据 = 忽略并警告**。不能把“忽略”当成“完整恢复”。

### 6.5 SAF 写入中断与校验失败的文件处理

导出：

1. 先在 `noBackupFilesDir/backup-tmp` 生成完整文件并校验；
2. 用 SAF CreateDocument 写入用户选择的位置；写入后 flush + 尽力 fsync；
3. **回读**目标文件，重新计算 `fileSha256`/解密校验；
4. 失败时：
   - 删除临时文件；
   - 若目标文件是本次新建且可删除，尝试 `DocumentsContract.deleteDocument`；
   - 若无法删除，明确提示“目标文件可能不完整，请删除后重试”，**不显示导出成功**；
5. 不覆盖已有同名文件：文件名含时间戳；冲突时追加 `-1`/`-2`。

恢复：

1. 通过 SAF 打开只读流，先读头部 + 限制大小；
2. 写入 app-private 临时文件（或分块解析），对原始字节计算 `fileSha256`；
3. 解密/校验全部通过后才进入 DB；任何截断/失败都删除临时文件，源文件不动；
4. 不尝试“修复”用户文件；不把失败文件部分导入。

## 7. v1 文件处理策略

**推荐：识别但不恢复。**

| 行为 | 说明 |
|---|---|
| 识别 | `format=mengyalife-backup` 且 `schemaVersion=1` |
| 展示 | “旧版三表导出（日记/目标/进展），不是完整备份” |
| 恢复 | **禁止**；不提供“尽力导入” |
| 原因 | 缺 7 张表与设置，静默部分恢复会造成“看起来成功但丢数据” |
| 可选 | 只读“查看摘要/校验”；未来如要做有界兼容，必须单独设计并显示“只恢复三表” |

替代方案（不推荐）：把 v1 转成 v2 的三表子集再恢复。它仍然缺对话/草案/线索/设置，且用户会误以为完整；若未来要做，必须命名 `legacy-partial-import` 并在预览中列出缺失范围。

## 8. 兼容性矩阵

| 备份 | 当前 reader | 策略 |
|---|---|---|
| v1 `schemaVersion=1` | 识别 | 只读提示，不恢复 |
| v2 `backupFormatVersion=2`，`dbSchemaVersion=11` | 支持 | 完整校验/恢复 |
| v2，`dbSchemaVersion<11` | 支持（有界） | 字段映射 + 默认值；缺字段显式警告；不静默 |
| v2，`dbSchemaVersion>11` | 拒绝 | 提示升级 App |
| v2，`requiredFeatures` 含未知 | 拒绝 | 不猜测 |
| v2，`backupFormatVersion>2` | 拒绝 | 提示升级 App |
| 跨包（QA↔正式） | 拒绝 | `generator.appPackage`/`qaBuild` 必须一致 |
| 篡改/截断/超大 | 拒绝 | 见 §6 |

## 9. 导出流程（设计）

```text
用户点击“导出完整备份”
  → 读取设置快照（theme/weekly/ai endpoint/model）
  → Room 单事务读取 10 张表（一致快照，按 id 排序）
  → 组装 v2 载荷 + counts + perTableSha256 + payloadSha256
  → 口令加密（AES-GCM）
  → 写 app-private 临时文件（noBackupFilesDir/backup-tmp）
  → 用户选择 SAF 目标
  → 流式写入 + flush + fsync（若可用）
  → 回读 → 解密 → 校验 payloadSha256/counts
  → 成功提示；失败删除临时文件，不谎报成功
```

关键点：

- Room 读取在单事务中；设置读取不在同一事务，需前后比对或维护锁；若设置变化，重试/提示。
- 不把待导出明文长期落盘；临时文件在 `noBackupFilesDir`，完成/失败即删。
- SAF 目标写入可能被中断；回读校验失败时提示“文件可能不完整”，不显示成功。
- 不自动上传、不自动分享。

## 10. 历史 AI 周报（`weekly_ai_reviews`）恢复策略（P1）

现状：表中每个 `period_key` 一行，字段包含授权/配置/预算状态；当前 UI 只展示最新周，没有“AI 周报历史列表”。恢复若直接写回，`WeeklyAiService.generateIfAllowed` 会看到非 PENDING 行并**阻止本周重新生成**，且旧结果绑定旧 consent/config，不能当作当前授权下的结果。

### 方案 B1：保存内容，恢复为只读历史

| 维度 | 影响 |
|---|---|
| schema 变更 | 需要独立 `weekly_ai_history` 表，或给 `weekly_ai_reviews` 加 `read_only/restored` 标记并修改所有查询；建议独立表，避免预算语义污染 |
| 代码复杂度 | 中高：恢复映射、历史列表 UI、查询过滤（`observeLatest/findByPeriod` 不得把历史当当前）、stale 标记 |
| 数据完整性 | 保留 AI 文案；但 facts/config 可能过期，只能只读标注 |
| 未来迁移 | 新表/标记 + 备份格式 history section；迁移成本中 |
| G4A-2 工期 | 中高：额外表/迁移/查询过滤/UI/测试 |

### 方案 B2：仅归档，不恢复到应用

| 维度 | 影响 |
|---|---|
| schema 变更 | **无**；恢复时继续删除/不写入 `weekly_ai_reviews` |
| 代码复杂度 | 低：备份包含归档区，恢复器明确忽略；预览提示 |
| 数据完整性 | AI 周报内容不在 App 内恢复；本地 `weekly_reviews`（规则回顾）仍恢复 |
| 未来迁移 | 低；以后要做 B1 再加独立历史表 |
| G4A-2 工期 | 低：只做忽略与预览提示 |

### 推荐

**G4A-2 采用 B2。** 理由：

1. 当前没有 AI 周报历史 UI，B1 的收益低；
2. 直接恢复现有表会锁死本周预算/旧授权，风险高于收益；
3. `fact_ids_json` 仍是 U+001F 技术债，B1 需要先修正；
4. B2 保持“恢复=可继续使用”的确定性；归档内容仍可在备份中审计。

若产品明确要求“换机后仍能查看历史 AI 周报”，再以 B1 立项：独立只读表 + 过滤 + 明确“历史、非当前授权结果”，并补迁移测试。

## 11. 需要用户决策的 A/B

### A. v2 是否默认加密

- **A1（推荐）**：默认必须口令加密；明文仅调试构建。
  - 优点：敏感数据不裸奔；跨设备可用；与“本机优先”一致。
  - 代价：用户忘口令无法恢复；导出多一步。
- **A2**：默认明文，可选加密。
  - 优点：简单；代价：SAF/云盘泄露风险高，不推荐。

### B. 派生缓存是否纳入

- **B1（推荐）**：核心 7 表 + 派生 2 表（insight/weekly review）纳入；`weekly_ai_reviews` 只归档。
  - 优点：完整；恢复后派生数据可按版本失效。
  - 代价：文件更大；需 stale 规则。
- **B2**：只备份核心 7 表，派生数据重建。
  - 优点：更小、更确定；代价：历史 AI 文案/周回顾丢失。

### C. v1 处理

- **C1（推荐）**：只识别、不恢复。
- **C2**：有界兼容（只恢复三表，明确警告）。
  - 代价：用户可能误以为完整；不推荐。

### D. AI endpoint/model

- **D1（推荐）**：备份 endpoint/model（不含 Key），恢复后仍要求重新输入 Key；不自动启用 AI。
- **D2**：完全不备份 AI 配置；恢复后从零配置。
  - 优点：最少元数据；代价：换机多一步。

### E. 历史 AI 周报

- **E1**：B1 只读历史（独立表）；成本高，本阶段不做。
- **E2（推荐）**：B2 归档不恢复；G4A-2 采用。

### F. API Key 恢复

- **F1（推荐）**：恢复后确认删除 Key，重新输入。
- **F2**：保留本机 Key，但仍不自动授权/发送。

## 12. 与恢复设计的衔接

- v2 的 `backupFormatVersion`/`dbSchemaVersion`/`requiredFeatures` 是恢复器的输入契约。
- `weekly_ai_reviews` 归档但不恢复；恢复器必须在预览中说明。
- 任何“缺失字段/未知版本/跨包/超限”都在写 DB 前拒绝。
- 参见 `G4A0-RESTORE-TRANSACTION.md` 与 `G4A0-SECURITY-PRIVACY.md`。
