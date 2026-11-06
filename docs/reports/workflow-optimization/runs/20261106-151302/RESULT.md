# R7 单场景复跑（port 34707）

- 命令：`DEV=127.0.0.1:34707 ./scripts/verify.sh --module restore-validate --level integration --case R7`
- 结论：**CASE PASS (suite deferred)**；R7 **16/16 PASS，90.9s**；整条命令125.6s。
- QA preflight 11/11、JVM 6类/24 tests均PASS；QA数据清理PASS。
- report 明确 `fullLevelSatisfied=false`：迁移、E2E、fresh DB等其他L2脚本被标为NOT RUN；这次只是R7单场景，不是完整L2 PASS。
- 直接覆盖：DB_COMMITTED后删除Gate，重启时以app_meta+journal重建/收敛；A核心数据、weeklyAI清空、Key/consent/自动发送复位、Gate释放均PASS。
- 真机日志与主机序列号不随本交付包展开；无生产包/生产数据操作。
