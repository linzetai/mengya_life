# Gate 3C-1 源码改动清单

## 新增
- app/schemas/com.mengyalife.app.data.local.MengyaDatabase/6.json
- app/src/main/java/com/mengyalife/app/data/advisor/QaFaultInjector.kt
- app/src/main/java/com/mengyalife/app/data/growth/
- app/src/main/java/com/mengyalife/app/data/local/growth/
- app/src/main/java/com/mengyalife/app/domain/advisor/GoalProposalIds.kt
- app/src/main/java/com/mengyalife/app/domain/growth/
- app/src/test/java/com/mengyalife/app/advisor/DiaryExtractionProtocolTest.kt
- app/src/test/java/com/mengyalife/app/advisor/DiaryExtractionTest.kt
- app/src/test/java/com/mengyalife/app/advisor/GoalProposalIdsTest.kt
- app/src/test/java/com/mengyalife/app/advisor/QaFaultInjectorTest.kt
- app/src/test/java/com/mengyalife/app/data/FakeGrowthSignalDao.kt
- docs/DIARY-EXTRACTION-DESIGN.md
- docs/GROWTH-SIGNAL-SCHEMA.md
- docs/MIGRATION-G3C1.md
- docs/reports/logs/g3c1-source-changes.md
- docs/reports/logs/g3c1-unit-tests.md

## 修改
- app/build.gradle
- app/src/main/java/com/mengyalife/app/MainActivity.kt
- app/src/main/java/com/mengyalife/app/data/advisor/AdvisorDataRepository.kt
- app/src/main/java/com/mengyalife/app/data/advisor/AdvisorEngine.kt
- app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalApplyService.kt
- app/src/main/java/com/mengyalife/app/data/advisor/GoalProposalRepository.kt
- app/src/main/java/com/mengyalife/app/data/local/JournalEntryDao.kt
- app/src/main/java/com/mengyalife/app/data/local/JournalEntryEntity.kt
- app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt
- app/src/main/java/com/mengyalife/app/data/repository/JournalRepository.kt
- app/src/main/java/com/mengyalife/app/di/AppContainer.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/AdvisorContext.kt
- app/src/main/java/com/mengyalife/app/domain/advisor/GoalProposal.kt
- app/src/main/java/com/mengyalife/app/ui/journal/JournalContract.kt
- app/src/main/java/com/mengyalife/app/ui/journal/JournalViewModel.kt
- app/src/main/java/com/mengyalife/app/ui/screens/AdvisorScreen.kt
- app/src/main/java/com/mengyalife/app/ui/screens/DiaryScreen.kt
- app/src/test/java/com/mengyalife/app/advisor/AdvisorContextAssemblerTest.kt
- app/src/test/java/com/mengyalife/app/data/ExportTest.kt
- app/src/test/java/com/mengyalife/app/data/JournalRepositoryTest.kt
- app/src/test/java/com/mengyalife/app/ui/JournalViewModelTest.kt
- docs/DATA-EXPORT-FORMAT.md
- tools/ui-verify/verify_export.py

## 统计
 23 files changed, 709 insertions(+), 75 deletions(-)
