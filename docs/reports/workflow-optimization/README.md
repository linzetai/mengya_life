# Workflow optimization run evidence

- `runs/20261106-111732/`: after P0 fixes, restore-validate L0 6 classes / 24 tests PASS (19.4s); no device.
- `runs/20261106-102658/`: historical full fast profile 299/299 before the two final P0 fixes; not final-source full-JVM evidence.
- `runs/20261106-101701/`: earlier full fast profile before adding two settings-entry assertions.
- `runs/20261106-101716/`: dev-profile restore loop; LOCAL PASS with four device tests deferred and `fullLevelSatisfied=false`.
- `runs/20261106-111828-partial-l2/`: port 34225 L2 partial logs; E2E/R2-R6 pass, R7 setup failed, user paused before fresh/weekly and report cleanup.
- `runs/20261106-150118/`: latest offline L0 after restore/backup/weekly case selectors; static/schema/script-map and 299 JVM PASS; no device.
- `runs/20261106-145819/`: prior scenario-filter L0, retained for comparison.
- `runs/20261106-145527/`: earlier scenario-filter L0; retained for comparison.
- `plans/restore-r7-case.txt`, `plans/backup-write-case.txt`, `plans/weekly-failed401-case.txt`: plans select one scenario and defer siblings.
- `runs/20261106-102012/`: latest changed-files dev loop; static/script checks only, 3.1s, no unrelated Android compile.
- `runs/20261106-094849/`: UI-only settings dev profile; compileDebugKotlin, no device.
- `runs/20261106-100440/`: test-infra/docs-only `--changed --profile dev`; skips unrelated Android compile, 2.7s.
- `plans/advisor-l2-dedupe.txt`: current advisor L2 plan; real-network test deferred to Gate.
- `plans/standard-advisor-dedupe.txt`: pre-optimization comparison plan.
- `plans/`: backup de-dup, focused restore L2, and weekly L2 smoke vs Gate full plans; plans only.
