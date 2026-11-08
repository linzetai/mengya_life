# 0.24.2 Baseline

HEAD 6ff3f81, branch feat/0.24.2-care-reliability. 工作树无 tracked 改动；未触碰用户数据。
风险矩阵：A 旧备份 counts/缺列；B 反馈静音覆盖；C 时间偏好 valueOf/全局拦截；D append expectedGeneration=null/分步提交；E Runner 无条件 reconcile SENDING。
复现：静态调用链 + 0.24 备份无 recordProposals counts（RestorePlanner 强制）。
