# Workflow optimization run evidence

- `runs/20261106-111732/`: after P0 fixes, restore-validate L0 6 classes / 24 tests PASS (19.4s); no device.
- `runs/20261106-102658/`: historical full fast profile 299/299 before the two final P0 fixes; not final-source full-JVM evidence.
- `runs/20261106-101701/`: earlier full fast profile before adding two settings-entry assertions.
- `runs/20261106-101716/`: dev-profile restore loop; LOCAL PASS with four device tests deferred and `fullLevelSatisfied=false`.
- `runs/20261106-111828-partial-l2/`: port 34225 combined L2 partial logs; E2E/R2-R6 pass, R7 setup failed, user paused before fresh/weekly.
- `runs/20261106-151302/`: port 34707 isolated R7 **16/16 PASS** in 90.9s; fullLevelSatisfied=false, sibling suites explicitly deferred.
- `runs/20261106-164126/`: current QA SHA c2fc DB-unreadable case report; 12/12, case PASS only, fullLevelSatisfied=false.
- `runs/20261106-174143/`: final offline L0; static/schema/map policy + 303 JVM PASS, no device.
- `../G4A2B-final-acceptance/`: current c2fc evidence, APK SHA256SUMS, focused source diff, minimal test logs; overall NOT PASS for remaining exact fault-injection gaps.
- `runs/20261106-145819/`: prior scenario-filter L0, retained for comparison.
- `runs/20261106-145527/`: earlier scenario-filter L0; retained for comparison.
- `plans/restore-r7-case.txt`, `plans/backup-write-case.txt`, `plans/weekly-failed401-case.txt`: plans select one scenario and defer siblings.
- `runs/20261106-102012/`: latest changed-files dev loop; static/script checks only, 3.1s, no unrelated Android compile.
- `runs/20261106-094849/`: UI-only settings dev profile; compileDebugKotlin, no device.
- `runs/20261106-100440/`: test-infra/docs-only `--changed --profile dev`; skips unrelated Android compile, 2.7s.
- `plans/advisor-l2-dedupe.txt`: current advisor L2 plan; real-network test deferred to Gate.
- `plans/standard-advisor-dedupe.txt`: pre-optimization comparison plan.
- `plans/`: backup de-dup, focused restore L2, and weekly L2 smoke vs Gate full plans; plans only.
