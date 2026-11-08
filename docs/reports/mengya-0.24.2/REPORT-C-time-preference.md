# Gate C
- 新增 CareTimeWindow + 严格 codec（SUN 20:00，非法返回 null）。
- 仅 WEEKLY_REVIEW 机会应用时段；PLAN_CHECKIN/GOAL_STALL 不再被全局拦截。
- 发送前用 freshNow 复核 DND；偏好表述为“尽量”。
- 测试：JVM 周日 20/18/23、周一、非法值。
- 未完成：跨周/时区真机、软偏好 UI 文案复测。

## Round 11
- 抽出 CareTimePreferenceScope.applies：仅 WEEKLY_REVIEW；JVM 测试断言 PLAN_CHECKIN/GOAL_STALL 不受时段偏好约束。
- 358 JVM tests PASS。

## Round 15（真机）
- QA test_now=2026-12-20 20:00（周日），notifications=false。
- 决策 #59 SEND：reason 明确“周日晚20:00正合用户偏好周回顾时段…”，文案“周日晚上好…”。
- 说明应用层时间偏好与模型提示均生效；PLAN/GOAL 未被时段拦截。
- 仍缺：周一/跨时区真机、TIME 在模型运行中变化的复核。
