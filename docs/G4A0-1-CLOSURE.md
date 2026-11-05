# G4A-0.1 设计问题收口（CLOSURE）

> 批次性质：只修正文档与测试计划；**无代码、数据库、迁移、生产配置改动**。
> 审计/修复基线：`master` / `f580a69`（G4A-0 文档提交；工作区干净）。
> 本批不执行 JVM 全量或真机全量回归；只做文档结构/链接/一致性静态检查。

## 1. 每个问题的修正位置

| # | 评审问题 | 修正位置 | 修正要点 |
|---|---|---|---|
| 1 | 恢复崩溃窗口（P0） | `G4A0-RESTORE-TRANSACTION.md` §2、§6.4、§7、§9 | 不再以 journal.phase 判定提交；以 `app_meta.restore_attempt_id` 为唯一 truth；Gate 先于 DB；给出 12 行状态转移表与六窗口 |
| 2 | 跨存储恢复一致性（P0） | 同上 §3.5、§4.2、§7、§8；`G4A0-SECURITY-PRIVACY.md` §3.4、§4.1、§7 | Gate 持久化先于 DB；提交前不碰 Key/consent；DB 事务含 attempt/generation/settings；提交后才 reset；失败保持 Gate；resetAfterRestore 清 recipient/epoch 单调/幂等；deleteKeyConfirmed 确认 alias 不存在 |
| 3 | 备份格式规范（P0） | `G4A0-BACKUP-FORMAT-V2.md` §4.1、§4.5、§5.1、§5.2.1、§5.2.2、§6.4、§6.5 | payload 与 hash 分层、排除字段明确；canonical JSON 精确定义；AAD 字节序列/排除字段；固定 KDF/AEAD 与 canonical 向量；原始/恢复后哈希区分；未知字段/SAF 中断规则 |
| 4 | 历史 AI 周报策略（P1） | `G4A0-BACKUP-FORMAT-V2.md` §10 | B1/B2 对比 schema/代码/完整性/迁移/工期；推荐 B2（归档不恢复），B1 后续单独立项 |
| 5 | 引用校验（P1） | `G4A0-RESTORE-TRANSACTION.md` §4.3、§5、§9 | 强约束拒绝 / 历史合法允许 / 可确定修复三层；合法历史悬挂不拒绝；强引用仍拒绝 |
| 6 | G3F 测试门禁 | `G4A0-TEST-MATRIX.md` §1、§4、§5、§8 | 删除“所有 backup/** 一律 L2”；按行为面分级；L0 编辑循环；补充崩溃/幂等/密钥/历史悬挂/哈希差异用例 |

## 2. 修正前后的关键差异

### 2.1 崩溃窗口

**修正前：**

- 启动按 `journal.phase` 判断：`PREPARED` 被视为未提交。
- 若 DB 已提交但 journal 尚未更新，会被误判/清理，造成“新数据 + 旧状态”或丢失收敛路径。

**修正后：**

- `journal.phase` **只作辅助**；DB 提交唯一依据是 `app_meta.restore_attempt_id == attemptId`。
- 新增 `attemptId`（不是 backupId），防止旧 journal/旧恢复误匹配。
- 新增 `app_meta.restore_settings_json`，journal 丢失也能完成设置收敛。
- Gate 文件先于 DB 写入并持续阻断 UI/Worker/AI；只有收敛后删除。
- 六窗口、12 行状态转移表覆盖：PREPARED 未提交、PREPARED 已提交、DB_COMMITTED、设置半应用、journal 丢失/损坏 + marker、Worker/AI 早于 reconciliation、Gate 缺失/损坏。

### 2.2 跨存储一致性

**修正前：**

- 先在恢复前撤权/清 Key；DB 失败会破坏旧配置。
- 依赖 `withdraw()`；它保留 recipient 且每次 epoch++。
- `deleteKey()` 调用即视为删除。

**修正后：**

- 先 Gate → 预快照 → DB 事务（数据+app_meta）→ **提交后** reset/settings；失败保持 Gate。
- `resetAfterRestore(generation)`：清空 granted/version/recipient/categories；`epoch=max(prev+1,generation)`；幂等（已重置返回 ALREADY_RESET）；不 grant。
- `deleteKeyConfirmed()`：删除后重新加载 KeyStore 确认 alias 不存在；否则 FAILED；再清 DataStore 并回读。
- 收敛前 UI/Worker/AI 全部禁止；Worker 入口检查 Gate。

### 2.3 格式

**修正前：**

- `payloadSha256` 放在 payload 内，存在自引用风险；AAD 描述含糊（“外层头/aad 字段本身”）。
- canonical JSON 未精确定义；原始备份哈希与恢复后数据哈希未区分。

**修正后：**

- 外层持有 `payloadSha256`；payload 不含它；hash 范围/排除字段逐项列出。
- `perTableSha256` 覆盖单表 CJ 数组；空表向量。
- CJ：UTF-8、键按字节序、无空白、字符串/数字规范化精确规则；提供向量。
- AAD：固定对象 + CJ UTF-8；包含 payloadSha256/加密参数/format/version；排除 aad/ciphertext/payloadBase64；无循环。
- 固定 KDF/AEAD 向量（测试迭代 1000；生产 ≥210k）。
- `fileSha256` / `payloadSha256` / `perTableSha256` / `expectedRestoredSha256` 区分；恢复转换后再比对。
- SAF 中断/校验失败：临时文件、回读、可删则删、不可删明确提示、不报成功。

### 2.4 周 AI 历史

**修正前：** 写入备份但不恢复，未解释 schema/工期/替代方案。

**修正后：** B1 vs B2 对比；推荐 **B2**（仅归档、不恢复），避免旧预算/旧授权锁死；B1 若要保留内容，需独立只读历史表，另立项。

### 2.5 引用校验

**修正前：** 所有逻辑引用必须存在，会拒绝合法历史（删目标后的 proposal、删消息后的 context、删日记后的 signal、软删/纠正 memory）。

**修正后：**

- 强约束（goals 父子、checkins→goals、PK/唯一键）拒绝；
- 历史悬挂（message context/proposal message/applied ids/signal/memory）允许 + warning/stale；
- 可确定修复（context 置 null、signal/memory 置 STALE、ANALYZING→FAILED/PENDING）在事务内显式执行；
- 正常 App 生成的备份可通过；强引用悬挂仍拒绝。

### 2.6 测试门禁

**修正前：** 所有 `backup/**`/`restore/**`/`crypto/**` 至少 L2，编辑格式/校验器也跑数百秒。

**修正后：**

- L0：格式/模型/校验器/纯算法/向量/文档；
- L1：导出 UI/SAF/校验器；
- L2：恢复事务、app_meta、Gate、reset、并发、DataStore；
- L3：恢复总控、schema/migration、崩溃窗口、正式只读；
- Release：L3 + 严格不跳过 + APK 哈希。
- 新增 R-23..R-31、B-13..B-15；`SKIPPED`/`NOT RUN` 不得报 PASS。

## 3. 未解决的产品决策

| 决策 | 选项 | 当前建议 | 是否阻塞 G4A-1 |
|---|---|---|---|
| A. v2 加密默认 | A1 默认口令加密 / A2 默认明文 | A1 | 是（决定导出 UI/格式） |
| B. 派生缓存范围 | B1 核心 7 + 派生 2 / B2 仅核心 7 | B1（但周 AI 采用 B2 归档不恢复） | 是（决定备份 section） |
| C. v1 处理 | C1 识别不恢复 / C2 有界三表兼容 | C1 | 是（决定导入入口） |
| D. AI endpoint/model | D1 备份不含 Key、不自动启用 / D2 不备份 | D1 | 是（决定 settings 载荷） |
| E. 周 AI 历史 | E1 只读历史 / E2 归档不恢复 | **E2**（本批推荐） | 不阻塞 G4A-1；影响 G4A-2 范围 |
| F. API Key 恢复策略 | 删除 / 保留本机 Key（仍不自动用） | 删除（fail-closed） | 否（G4A-2 前确认） |

## 4. G4A-1 是否可以开始

**设计问题收口：可以进入 G4A-1 的编码准备；但编码开始前需确认 A/B/C/D（E/F 可随后确认）。**

- G3F 前置 PASS；G4A-0.1 已关闭 6 个评审问题。
- G4A-1 范围不变：v2 导出/格式/加密/安全基线；**不做恢复**。
- 若 A/B/C/D 未确认，G4A-1 只能做不影响产品语义的骨架（不建议）；建议先确认。

## 5. Git

| 项 | 值 |
|---|---|
| 分支 | `master` |
| 批次起点 HEAD | `f580a69`（G4A-0 文档提交） |
| 批次提交 | `dcd7dc1`（`Gate 4A-0.1: close design review findings`） |
| 交付时 HEAD | 本记录提交（父 `dcd7dc1`；`git log -1`） |
| 交付时 status | clean（`git status --short` 无输出） |
| 本批变更 | 修改 5 个 G4A0 文档 + 新增本收口文档；无代码/数据库/迁移/Manifest 改动 |
| 用户既有修改 | 未删除/覆盖 |

## 6. G3F 原始证据可复核路径

| 证据 | 路径 |
|---|---|
| G3F 验收索引 | `docs/reports/G3F-acceptance/README.md` |
| 数据库集成/迁移/授权 | `docs/reports/G3F-acceptance/db-l2/report.json` + `logs/` |
| 日记 L1 | `docs/reports/G3F-acceptance/diary-l1/report.json` |
| 周回顾 L1 | `docs/reports/G3F-acceptance/weekly-l1/report.json` |
| 完整周回顾 95 条 | `docs/reports/G3F-acceptance/weekly-full.log` |
| 失败注入 | `docs/reports/G3F-acceptance/failure-probe/report.json`（FAIL/rc=1） |
| G4A-0 前置复跑 | `docs/reports/G3F-acceptance/g4a0-recheck/report.json`（244/244 PASS） |
| G3F 入口/注册表 | `scripts/verify.sh`、`tests/test-map.json` |

## 7. 本批未做的事

- 未修改 `AiConsentRepository`/`SecretCipher`/`MengyaDatabase`/Manifest/任何 `app/src/**`。
- 未新增 `app_meta` 表、未改 schema、未写恢复代码。
- 未执行 JVM 全量、未执行真机回归。
- 未删除用户既有修改；未使用 `git reset --hard`。
