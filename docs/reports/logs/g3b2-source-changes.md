# Gate 3B-2 源码改动清单

## 新增
- app/schemas/com.mengyalife.app.data.local.MengyaDatabase/5.json
- app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalApplyService.kt
- app/src/main/java/com/mengyalife/app/ui/advisor/GoalProposalReviewDialog.kt
- app/src/test/java/com/mengyalife/app/advisor/AdvisorRequestBudgetTest.kt
- app/src/test/java/com/mengyalife/app/advisor/GoalProposalApplyRulesTest.kt
- artifacts/mengya-life-0.8.0-g3b2-debug.apk.sha256
- artifacts/mengya-life-0.8.0-g3b2-qa-test.apk.sha256
- docs/GOAL-PROPOSAL-APPLY.md
- docs/MIGRATION-G3B2.md
- docs/PRIVACY-RACE-FIX.md
- docs/reports/REPORT-G3B2.md
- docs/reports/logs/g3b2-device.log
- docs/reports/logs/g3b2-migration.log
- docs/reports/logs/g3b2-real-model.log
- docs/reports/logs/g3b2-shots/
- docs/reports/logs/g3b2-source-changes.md
- docs/reports/logs/g3b2-unit-tests.md

## 修改
- README.md
- app/build.gradle
- app/src/main/java/com/mengyalife/app/data/advisor/AdvisorEngine.kt
- app/src/main/java/com/mengyalife/app/data/advisor/ConversationRepository.kt
- app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalRepository.kt
- app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt
- app/src/main/java/com/mengyalife/app/data/local/advisor/AdvisorMessageEntity.kt
- app/src/main/java/com/mengyalife/app/data/local/proposal/GoalProposalDao.kt
- app/src/main/java/com/mengyalife/app/data/local/proposal/GoalProposalEntity.kt
- app/src/main/java/com/mengyalife/app/di/AppContainer.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/AdvisorContext.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/AdvisorModels.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/AdvisorRules.kt
- app/src/main/java/com/mengyalife/app/ui/advisor/AdvisorContract.kt
- app/src/main/java/com/mengyalife/app/ui/advisor/AdvisorViewModel.kt
- app/src/main/java/com/mengyalife/app/ui/screens/AdvisorScreen.kt
- app/src/test/java/com/mengyalife/app/advisor/AdvisorFakes.kt
- app/src/test/java/com/mengyalife/app/advisor/AdvisorViewModelTest.kt
- app/src/test/java/com/mengyalife/app/data/ExportTest.kt
- artifacts/SHA256SUMS.txt
- docs/DATA-EXPORT-FORMAT.md
- tools/ui-verify/verify_export.py

## 统计
 22 files changed, 718 insertions(+), 266 deletions(-)
