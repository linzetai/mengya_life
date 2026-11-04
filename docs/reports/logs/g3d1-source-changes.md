# Gate 3D-1 源码改动清单

## 新增
- app/schemas/com.mengyalife.app.data.local.MengyaDatabase/8.json
- app/src/main/java/com/mengyalife/app/data/growth/GrowthMemoryValidity.kt
- app/src/main/java/com/mengyalife/app/data/insight/
- app/src/main/java/com/mengyalife/app/data/local/insight/
- app/src/main/java/com/mengyalife/app/domain/insight/
- app/src/main/java/com/mengyalife/app/ui/insight/
- app/src/test/java/com/mengyalife/app/advisor/GrowthMemoryValidityTest.kt
- app/src/test/java/com/mengyalife/app/advisor/InsightParserTest.kt
- app/src/test/java/com/mengyalife/app/advisor/InsightRulesTest.kt
- app/src/test/java/com/mengyalife/app/advisor/InsightServiceTest.kt
- artifacts/mengya-life-0.11.0-g3d1-debug.apk.sha256
- artifacts/mengya-life-0.11.0-g3d1-qa-test.apk.sha256
- docs/INSIGHT-SCHEMA.md
- docs/MIGRATION-G3D1.md
- docs/reports/REPORT-G3D1.md
- docs/reports/logs/g3d1-acceptance-summary.md
- docs/reports/logs/g3d1-device.log
- docs/reports/logs/g3d1-export/
- docs/reports/logs/g3d1-migration.log
- docs/reports/logs/g3d1-real-insight.log
- docs/reports/logs/g3d1-shots/
- docs/reports/logs/g3d1-source-changes.md
- docs/reports/logs/g3d1-unit-tests.md
- docs/reports/logs/g3d1-verify_diary.log
- docs/reports/logs/g3d1-verify_export.log
- docs/reports/logs/g3d1-verify_goals.log
- docs/reports/logs/g3d1-verify_navigation.log
- docs/reports/logs/g3d1-verify_prod_smoke.log
- docs/reports/logs/g3d1-verify_settings.log
- docs/reports/logs/g3d1-verify_trends.log

## 修改
- README.md
- app/build.gradle
- app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt
- app/src/main/java/com/mengyalife/app/data/advisor/AdvisorDataRepository.kt
- app/src/main/java/com/mengyalife/app/data/growth/DiaryExtractionService.kt
- app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt
- app/src/main/java/com/mengyalife/app/data/repository/JournalRepository.kt
- app/src/main/java/com/mengyalife/app/di/AppContainer.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/AdvisorContext.kt
- app/src/main/java/com/mengyalife/app/ui/growth/GrowthMemoryScreen.kt
- app/src/main/java/com/mengyalife/app/ui/screens/OverviewScreen.kt
- app/src/test/java/com/mengyalife/app/advisor/DiaryExtractionTest.kt
- app/src/test/java/com/mengyalife/app/data/ExportTest.kt
- artifacts/SHA256SUMS.txt
- docs/DATA-EXPORT-FORMAT.md
- tools/ui-verify/verify_export.py
- tools/ui-verify/verify_goals.py
- tools/ui-verify/verify_prod_smoke.py
- tools/ui-verify/verify_settings.py

## 统计
 19 files changed, 233 insertions(+), 48 deletions(-)
