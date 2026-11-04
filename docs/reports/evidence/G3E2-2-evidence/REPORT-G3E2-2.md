# Gate 3E-2.2 报告 · AI 周回顾协议/缓存/临界区收尾

| 项 | 值 |
|---|---|
| 版本 | 0.14.1（versionCode 15，未提升 schema） |
| 数据库 | Room v11 |
| 结论 | **PARTIAL**：协议/临界区/全量 UI 回归 PASS；真实 AI 与缓存真机状态 NOT VERIFIED |

## A Prompt/Parser

| 项 | 状态 | 证据 |
|---|---|---|
| 模型只输出 factIds，不要求 sources | **PASS（JVM）** | prompt + parser 测试 |
| 客户端从授权 facts 确定性构造 sources | **PASS（JVM）** | 模型 sources 被忽略；未知 factId 失败 |
| insufficient 无来源合法 | **PASS（JVM）** | 专项测试 |
| 真实规则输出到 Parser 端到端 | **PASS（JVM）** | WeeklyAiTest |
| 合法 JSON 示例 | **PASS（JVM）** | parse prompt example |

## B 缓存

| 项 | 状态 |
|---|---|
| local review 与 AI 输入版本区分 | 代码/设计；**NOT VERIFIED** 真机 |
| AI 有效性（过滤输入/consent/config/period） | 代码；**NOT VERIFIED** 真机 |
| 旧 READY 标 STALE | 代码；**NOT VERIFIED** 真机 |
| 撤权/切类别/改模型/改数据状态测试 | **NOT VERIFIED**（未补齐真机） |

## C 临界区

| 项 | 状态 |
|---|---|
| markSent 后、chat 前再次校验 | **PASS（JVM）** |
| markSent 期间切 endpoint → engine.calls=0 | **PASS（JVM）** |
| 已发送后变化 → 不写 READY | **PASS（JVM）** |
| 异常不释放已发送预算 | 代码；**NOT VERIFIED** 超时真机 |

## D 开关/文案

- 自动 AI 需同时满足 weekly 开启 + AI 开关 + 配置 + 授权/类别 + 预算；代码。
- 设置页过时文案已改；周回顾页已区分本地/手动 AI/后台自动 AI。
- 基础周回顾关闭时 AI 开关禁用并提示。

## E 真机/真实模型

- 真实 QA AI：**NOT VERIFIED**；无可用 QA 模型配置，未伪造成功。
- AI 成功/失败/超时/取消/generating 状态：**NOT VERIFIED**（仅代码/JVM）。
- 0.14.1 全量 UI 回归：**PASS**：25/64/73/28/38/49/50（日志 g3e22-*）。
- 正式数据升级 v10→v11：**PASS**（user_version=11，日记保留）。

## 测试

- JVM **242/242**，XML 见证据包。
- UI 回归日志：`g3e22-verify_*.log`（以实际执行结果为准，不用预期代替）。
- 真实 AI：NOT VERIFIED。

## APK

- Debug：`9e1bb8e9abe212805195c541f2c57022f3f4ce869fcf39028c149bd55c77bd7d` · 12,392,234 B
- QA：`293a4e28209082aefb17030105e5d829a7e0cfe85212ea6f5d420ea53704c7bc` · 12,393,063 B
- 设备安装哈希一致；Room v11。

## 未验证清单

1. 真实 QA 模型成功请求。
2. AI 超时/取消/generating UI 恢复。
3. 缓存失效/STALE 真机。
4. 自动/手动并发与开关真机。
5. 0.14.1 全量 UI 回归已 PASS；无。
6. 生产测试周回顾/AI 历史行未清理。
