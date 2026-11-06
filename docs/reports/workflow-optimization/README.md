# Workflow optimization run evidence

- `runs/20261106-102658/`: latest full fast profile; test-map/scripts validation + 299/299 JVM, no device.
- `runs/20261106-101701/`: earlier full fast profile before adding two settings-entry assertions.
- `runs/20261106-101716/`: dev-profile restore loop; LOCAL PASS with four device tests deferred and `fullLevelSatisfied=false`.
- `runs/20261106-102012/`: latest changed-files dev loop; static/script checks only, 3.1s, no unrelated Android compile.
- `runs/20261106-094849/`: UI-only settings dev profile; compileDebugKotlin, no device.
- `runs/20261106-100440/`: test-infra/docs-only `--changed --profile dev`; skips unrelated Android compile, 2.7s.
- `plans/advisor-l2-dedupe.txt`: current advisor L2 plan; real-network test deferred to Gate.
- `plans/standard-advisor-dedupe.txt`: pre-optimization comparison plan.
- `plans/`: backup de-dup, focused restore L2, and weekly L2 smoke vs Gate full plans; plans only.
