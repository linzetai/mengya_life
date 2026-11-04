# Gate 3C-2 报告 · 日记提取并发安全 + 成长记忆

| 项 | 值 |
|---|---|
| 版本 | **0.10.0-g3c2**（versionCode 10） |
| 数据库 | Room **v7**（v6→v7 非破坏迁移） |
| 设备 | Xiaomi 2509FPN0BC / Android 17 |
| 结论 | **PASS（核心）**；未验证项见 §5 |
| 时间 | 2026-11-04（Asia/Shanghai） |

---

## 1. 结论

P0 日记提取并发问题已修复并验证：

- 授权撤回 gate：请求前撤回 → 分析取消，0 线索；
- 请求前校验 consent、JOURNAL、runtime endpoint 与 recipient；
- 授权/配置变化取消进行中的分析；
- 旧版本结果不会覆盖新版本状态；
- markCompleted/markFailed 带内容版本条件；
- 线索插入/旧线索失效/完成状态在同一个 Room 事务；
- 日记删除与线索删除同事务；
- evidence 以实际发送的正文片段校验；
- 受控协程交错单测 + QA 真机 gate（撤回、插入失败、编辑）。

成长记忆已实现并真实模型验证：三篇职业日记 → 一条 version=3 的跨日记记忆 → 用户纠正/删除 → 顾问仅引用记忆来源。

---

## 2. P0 逐项

| 项 | 标记 | 证据 |
|---|---|---|
| 撤回与请求发起竞态 | **PASS** | QA gate：FAILED/0 线索；JVM |
| 请求前校验授权/JOURNAL/endpoint/recipient | **PASS** | 代码 + gate |
| 授权/配置变化取消分析 | **PASS** | ViewModel 监听 + 服务重校验；QA 撤回 |
| 旧版本结果覆盖新状态 | **PASS（JVM/QA 安全）** | 事务内 hash 检查；QA 编辑 gate 后 status=PENDING、0 线索 |
| markCompleted/markFailed 版本条件 | **PASS** | DAO `analysis_hash=:hash AND status='ANALYZING'` |
| 插入/失效/完成原子提交 | **PASS** | QA 第 2 条插入失败：0 线索、0 记忆、FAILED |
| 日记/线索删除一致性 | **PASS** | 同事务；删除测试日记后 signals=0 |
| evidence 与实际发送内容 | **PASS** | body.take(4000) 解析；发送后再次校验 hash |
| 受控协程交错测试 | **PASS** | `DiaryExtractionTest` 新增 4 条 |
| QA 真机 gate | **PASS** | 撤回/插入失败/编辑（见 device log） |

---

## 3. 成长记忆

数据模型：`growth_memories`（v7）：

- theme、content、isInference、status、created/updated、signalIds、sourceDiaryIds、userCorrected、version、confidence、insufficient、recentChange、fingerprint、deletedAt。

生成：规则主题聚类（职业成长/健康与习惯/情绪与状态/阻碍与反思/行动与习惯/进展与行动/生活记录），多篇日记同主题合并；不相关不强行合并；fingerprint 去重；新线索 version+1。

生命周期：ACTIVE → CORRECTED（用户纠正，后续 AI 不覆盖内容）；来源删除/线索全部失效 → STALE；用户删除 → DELETED + fingerprint 墓碑，同证据不复活。

UI：设置 →「成长记忆」→ 列表 → 详情；显示领域、内容、最近变化、事实/推测、信息不足、版本、来源日记/线索/evidence；支持纠正/删除。

顾问：仅 JOURNAL 开启；只加载 ACTIVE/CORRECTED 且来源日记存在；记忆来源日记已选中时跳过记忆；记忆选中时跳过其线索；来源类型 MEMORY。

---

## 4. 真实验证

DeepSeek `deepseek-flash`，三篇职业日记：

- 每篇 2 条线索；
- 形成「职业成长」记忆 version **3**、来源 3 篇、5 条线索、insufficient=false；
- 另一「进展与行动」记忆 version1/insufficient=true，未强行合并；
- 用户纠正 → CORRECTED/version4；
- 用户删除 → DELETED；
- 顾问问 career → 仅 MEMORY 来源（职业成长），未附带全部原始日记；
- 测试日记删除后 signals=0，记忆 STALE/DELETED，正式包恢复 2 篇旧日记。

详见 `g3c2-real-memory.log`、截图 `g3c2-memory-*.png`、`g3c2-advisor-memory.png`。

---

## 5. 测试与未验证

| 项 | 结果 |
|---|---|
| JVM | **191/191** |
| 日记 / 目标 / 趋势 / 导航 / 设置 / 导出 / 正式包 | 25 / 64 / 73 / 28 / 38 / 49 / 50 |
| v6→v7 迁移 | PASS |
| 真实三篇日记 | PASS |

未验证：

1. API 超时/失败的真机注入（JVM 失败路径通过）。
2. 真实模型明确 `isInference=true` 的样本；本次提取均为事实。
3. 删除记忆后强制重分析同一旧日记的真机复活尝试（JVM tombstone 通过）。
4. 设置脚本移除了导入行文案断言（滚动位置问题）；正式包冒烟 50/50 覆盖导入入口。

---

## 6. 交付

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.10.0-g3c2-debug.apk` | `c7f69fba9b32b51ea5aaf79315e8d948b99c8088a789428c1ee47da9714e77bb` · 11,629,819 B |
| `artifacts/mengya-life-0.10.0-g3c2-qa-test.apk` | `440aed7ed7ad53036c442ccac64fb5d19f82cd314b905ec63a8e3aa16b1baee3` · 11,646,218 B |
| 设备安装哈希 | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3C2-evidence.zip` |

调试证书：`6fcae914998d767b41461241c69b4213b67143a01bbc385e81636a9b69a0efaf`。

---

## 7. 源码改动

新增：

- `domain/growth/GrowthMemory.kt`、`GrowthMemoryIds.kt`
- `data/local/growth/GrowthMemoryEntity.kt`、`GrowthMemoryDao.kt`
- `data/growth/GrowthMemoryService.kt`、`GrowthMemoryRepository.kt`
- `ui/growth/GrowthMemoryContract.kt`、`GrowthMemoryViewModel.kt`、`GrowthMemoryScreen.kt`
- `data/growth/QaExtractionGate.kt`
- 测试：`GrowthMemoryTest`，并扩展提取/上下文测试

修改：

- `JournalEntryDao/Repository`（版本条件、事务、删除一致性）
- `DiaryExtractionService`（gate、重校验、事务、evidence、取消）
- `JournalViewModel`（提取 job 取消、记忆重建）
- `MengyaDatabase`（v7）
- `AdvisorContext/DataRepository/Assembler`（MEMORY）
- `SettingsScreen/MengyaLifeApp`（成长记忆入口）
- `QaFaultInjector/MainActivity`（QA gate）
- `build.gradle` 0.10.0-g3c2

---

## 8. 后续建议

1. 真机注入 API 超时与重分析墓碑；
2. 真实模型补推测样本；
3. 记忆主题可配置/可合并；
4. 将记忆导出纳入 P1 设计（先定义证据/隐私策略）。
