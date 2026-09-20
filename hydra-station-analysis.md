# Hydra Station Roadmap: Analysis

> Reviewed against: `hydra-station-roadmap.md`

---

## Overall Assessment

The plan is well-structured and shows strong architectural thinking. The phasing is logical — infrastructure first, then data, then intelligence, then I/O — and several decisions reflect genuine production experience rather than tutorial-level thinking. There are a few gaps and risks worth addressing before execution begins.

---

## What's Strong

**Resource discipline is explicit.** Hard memory limits in Phase 1 are set before writing a single line of application code. That is the right order, and it will save a painful retrofit later on a 4GB / 1-core ceiling.

**Security is a first-class concern, not an afterthought.** JWT dependency injection guards and `LANGGRAPH_STRICT_MSGPACK=true` both appear at the correct phase, before any data is wired up. The `user_id` FK on every business table is a clean multi-tenancy pattern.

**Celery was intentionally rejected.** The custom `asyncio` task loop in Phase 4 is the right call for a single-core budget. Celery would require a separate worker process, a beat scheduler, and more RAM. The plan correctly calls out that the worker must use only async clients to avoid blocking the FastAPI event loop.

**The backup system is honestly scoped.** Deferring Google Drive integration and shipping a `LocalFileBackupProvider` first is mature planning. The protocol/interface abstraction means the swap later will be clean.

---

## Gaps and Risks by Phase

### Phase 1: Infrastructure and Base Scaffolding

**Risk: Low**

No mention of environment variable management. A `.env` file and something like `pydantic-settings` are needed to manage secrets (DB password, JWT secret, API keys) across Docker and the application. This should be scaffolded in Phase 1, not retrofitted when Phase 2 needs `JWT_SECRET`.

---

### Phase 2: Core Data Layer and Security

**Risk: Medium**

The schema defines `password_hash` but the plan does not name the hashing library. The wrong default (e.g., MD5 or plain SHA-256) would be a silent security failure. `bcrypt` or `argon2-cffi` should be explicitly specified here.

---

### Phase 3: The Agentic Brain (LangGraph)

**Risk: Medium**

When the async worker (Phase 4) and the FastAPI request handlers both invoke agents concurrently, SQLAlchemy's `AsyncSession` must be scoped per-request or per-task, not shared. This is easy to get wrong and produces hard-to-debug data corruption under concurrent load. The roadmap assumes correct scoping but does not make the pattern explicit.

---

### Phase 4: Asynchronous Processing (Redis Queue)

**Risk: Low**

The Redis polling interval is unspecified. A tight loop (e.g., `BLPOP` with a 0 timeout) versus polling every N seconds has a meaningful impact on CPU usage on a single-core machine. This should be decided before implementation, not tuned reactively.

---

### Phase 5: Visibility and Backup

**Risk: Low (acceptable for v1)**

`APScheduler` running inside the FastAPI process is reasonable for an early version, but fragile under restarts. If the process dies mid-backup, there is no resumption. This is an acceptable v1 trade-off, but worth documenting explicitly so it does not come as a surprise later.

---

### Phase 6: Client Integration

**Risk: Medium**

SDUI schema versioning is not addressed. If the Android app and backend ship out of sync, a JSON schema mismatch will silently break rendering on the client. Even a simple `schema_version` field in the response envelope protects against this.

Additionally, no rollback or migration strategy is defined. Alembic is listed in Phase 1, but there is no guidance on handling a bad migration in production where dropping and recreating the database is not viable once real data exists.

---

## Summary Table

| Phase | Risk Level | Primary Gap |
|---|---|---|
| 1: Infrastructure | Low | Missing `.env` / secrets scaffolding |
| 2: Data and Auth | Medium | Password hashing library unspecified |
| 3: LangGraph | Medium | Async session scoping needs an explicit pattern |
| 4: Redis Queue | Low | Polling strategy unspecified |
| 5: Visibility / Backup | Low | APScheduler restart fragility (acceptable for v1) |
| 6: Client Integration | Medium | SDUI schema versioning and migration rollback gaps |

---

## Phase Sequencing Note

Phase 5 (Backup and Agent Console) ships before Phase 6 (Android integration), which is correct from a data-safety perspective. However, the Agent Console depends on `agent_runs` telemetry, which only becomes meaningful once agents are being invoked via the Android client in Phase 6. The console will be nearly empty until then. Consider whether Phase 5's console is a Phase 6+ deliverable, or whether some Phase 6 end-to-end testing should run in parallel with Phase 5.

---

## Conclusion

The plan is solid enough to begin execution. The gaps above are best resolved phase-locally (before the relevant phase starts) rather than all at once, since they are largely contained to their own scope. No structural rework is needed.
