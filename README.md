# barber-saas-schedule-api

> schedule bounded context: service API

Part of the **LMS Library** distributed system — team `lms-library`, Grupo 2.
Governance and documentation live in [`library-docs`](https://github.com/code-corhuila/library-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.

---

## BarberSaaS — what this repository is

The schedule service: each barber's weekly schedule, the exceptions per date (a day off or
special hours) and the **availability** a booking needs (`07-api/contracts/openapi/schedule-service.yaml`).
Hexagonal, three Maven modules (ADR-012, annex C): `schedule-core` (domain and use cases, no
Spring), `schedule-adapters` (HTTP in and out, JDBC, RS256 validation) and `schedule-app`
(composition root). It never migrates its schema: that is `barber-saas-schedule-db`.

| Operation | Who |
|---|---|
| `GET` / `PUT /api/v1/barber-schedules/{barberId}` | `ADMIN_BARBERSHOP` sets the whole week; a `BARBER` reads only their own |
| `GET` / `POST /api/v1/schedule-exceptions`, `GET` / `DELETE …/{id}` | `ADMIN_BARBERSHOP` manages; a `BARBER` reads only their own |
| `GET /api/v1/availability?barberId&serviceId&date` | `CLIENT`, `ADMIN_BARBERSHOP`, `BARBER` |
| `GET /health` | liveness, no token |

Rules: a week may have several blocks per day (split shift) but none overlap (422,
AGGR-INV-BARBER-001); an exception **replaces** the day, one per barber and date (422,
AGGR-INV-BARBER-002); availability is every block of the day cut by the service's duration minus
the barber's `PENDING`/`CONFIRMED`/`IN_PROGRESS` appointments, and a past date (in the
barbershop's time zone) has none. The tenant comes **only** from the token; another barbershop's
barber or exception answers `404`.

**Other domains, through their APIs (DEC-SCHED-03, golden rule 8):** the barber, the service's
duration and the time zone come from `barbershop-api`, with the caller's token; the busy slots from
`appointment-api`'s `GET /internal/v1/busy-slots`, with **this service's** `SERVICE_TOKEN` and the
barbershop of the caller's token, so a client and the barber see the same free slots (ADR-015).
Every call carries `X-Correlation-Id`, with 2 s to connect and 3 s per request; a failure answers
`503` instead of a guessed availability.

### How to start it

As part of the platform: `./scripts/up.sh dev` in `barber-saas-infra` (it needs `barbershop-api`).
Alone, without a database (in-memory repositories), pointing at a running `barbershop-api`:

```bash
mvn -B -DskipTests package
JWT_PUBLIC_KEY="$(cat ../barber-saas-infra/keys/jwt-public.pem)" BARBERSHOP_API_URL=http://localhost:8081 \
  java -jar schedule-app/target/schedule-app-0.1.0.jar
```

### Where the data is

Schema `schedule` of the shared PostgreSQL instance, as `schedule_app` (`DATABASE_URL`,
`DATABASE_USER`, `DATABASE_PASSWORD`; see `.env.example`), never as the administrator. A `PUT` of
the week deactivates the previous blocks (`is_active = false`) and inserts the new ones in one
transaction. Availability is computed, never stored.

### How it is tested

`mvn -B verify` (no Docker needed): the domain, the use cases with fake ports, the clients of the
other APIs against a stub HTTP server, and the HTTP contract over the whole service with a
stand-in `barbershop-api`, including cross-tenant tests (HU-TENANT-001). The JDBC repositories
are also tested against a database migrated by `barber-saas-schedule-db` when `TEST_DATABASE_URL`,
`TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` are set.

### What is missing

- **Clients and OQ-07.** A `CLIENT` token carries no barbershop, so availability answers `403` to
  clients until OQ-07 decides how a client is bound to a barbershop.
