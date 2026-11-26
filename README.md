Enterprise Knowledge & Agent Platform

An enterprise knowledge management and AI assistant platform built with React, Spring Boot, FastAPI, PostgreSQL, and pgvector.

1. Project Overview

This project aims to build an enterprise knowledge platform that supports document management, knowledge retrieval, and AI-powered question answering.

The system separates business logic from AI capabilities:

- Frontend: React + TypeScript
- Business Backend: Java + Spring Boot
- AI Backend: Python + FastAPI
- Database: PostgreSQL + pgvector
- Infrastructure: Docker Compose

2. Project Structure

enterprise-knowledge-agent/
├── docs/
├── frontend/
├── backend/
│   ├── spring-boot/
│   └── ai-service/
├── infrastructure/
│   └── docker-compose.yml
├── scripts/
├── .gitignore
└── README.md

3. Development Environment

- JDK 25
- Maven
- Node.js and npm
- Python
- Docker Desktop
- PostgreSQL 16 with pgvector

4. Local Development

Start PostgreSQL

Configure your local database password in "infrastructure/.env", using "infrastructure/.env.example" as a reference.

```bash
cd infrastructure
docker compose up -d postgres
docker compose port postgres 5432
```

The current project database is enterprise_knowledge, the local database account is MicroJason, and the published address is 127.0.0.1:5333. PostgreSQL still listens on port 5432 inside the container. Port 5332 belongs to a different local container and must not be used for this project. The project container is enterprise-postgres and retains its named data volume.

Start Spring Boot

Spring now requires a running PostgreSQL database and DB_PASSWORD in its process environment. Docker Compose reads infrastructure/.env, but Spring does not automatically import that file. The following Git Bash commands, starting from the repository root, load the resolved Compose password without displaying it, then start Spring:

```bash
cd backend/spring-boot
export DB_URL="jdbc:postgresql://127.0.0.1:5333/enterprise_knowledge"
export DB_USERNAME="MicroJason"
unset DB_PASSWORD

if DB_PASSWORD="$(docker compose \
    --env-file ../../infrastructure/.env \
    -f ../../infrastructure/docker-compose.yml \
    config --format json |
    python -c 'import json, sys; value = json.load(sys.stdin)["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"]; assert isinstance(value, str) and value, "Database password is empty"; sys.stdout.write(value)')"; then
    export DB_PASSWORD
    ./mvnw spring-boot:run
else
    printf 'Could not load the local database configuration.\n'
fi
```

The loader uses the existing local Python installation. Unsetting DB_PASSWORD first prevents an older terminal value from overriding infrastructure/.env during Compose interpolation. Do not print the password or commit it. Changing .env does not change a password already stored in an initialized PostgreSQL data volume.

To start from IntelliJ IDEA instead, configure DB_URL, DB_USERNAME, and DB_PASSWORD locally in the Application run configuration. An already running IDEA process does not automatically inherit environment variables exported in Git Bash.

Run the complete Spring Boot test suite from the same directory and terminal after loading the environment variables. If the server is running in that terminal, stop it first with Ctrl+C:

```bash
./mvnw test
```

Flyway is the only schema migration tool. It tracks V1__create_users.sql and V2__create_knowledge_bases.sql in core.flyway_schema_history. Hibernate uses ddl-auto=validate, and Spring SQL initialization is disabled. Successful application startup applies pending migrations; subsequent startups validate existing migrations without repeating them. Preserve applied files and add a new version for later schema changes.

Run the Day 12 database constraint acceptance script from infrastructure:

```bash
docker compose exec -T postgres psql \
  -U MicroJason -d enterprise_knowledge -v ON_ERROR_STOP=1 \
  < ../backend/spring-boot/day12-constraints.sql
```

It checks the generated name key, case-insensitive name uniqueness, and the creator foreign key. Expected output includes two PASS notices and zero remaining test rows after ROLLBACK.

Initialize the current API demo creator from the repository root before the first knowledge base POST:

```bash
docker compose \
  --env-file infrastructure/.env \
  -f infrastructure/docker-compose.yml \
  exec -T postgres psql \
  -U MicroJason -d enterprise_knowledge -v ON_ERROR_STOP=1 \
  < backend/spring-boot/day13-demo-user.sql
```

This explicitly executed data script creates the fixed demo identity required by the creator foreign key. It can be repeated for the same expected identity and does not modify the schema. Its password marker disables login; it is not a real password hash or an authentication implementation. Flyway remains the only schema migration mechanism.

Health check:

"http://localhost:8080/api/v1/health"

Start FastAPI

cd backend/ai-service
source .venv/Scripts/activate
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000

Health check:

"http://localhost:8000/health"

API documentation:

"http://localhost:8000/docs"

Start Frontend

cd frontend
npm install
npm run dev

Frontend:

"http://localhost:5173"

5. Project Status

- [x] Product requirements and architecture design
- [x] Database design
- [x] API design
- [x] Spring Boot initialization and health check
- [x] FastAPI initialization and health check
- [x] React + TypeScript initialization
- [x] PostgreSQL + pgvector setup
- [x] Day 8: knowledge base creation vertical slice with an in-memory repository
- [x] Day 9: unified JSON responses, request IDs, and 409/422/500 exception mapping
- [x] Day 10: request Bean Validation, name normalization, and Unicode code point length boundaries
- [x] Day 11: knowledge base GET lists, pagination, stable ordering, and query/body boundary checks
- [x] Day 12: PostgreSQL connection, Flyway V1/V2 migrations, and database constraint acceptance
- [x] Day 13: PostgreSQL repository adapter, Domain/Entity mapping, transactions, conflict translation, and persistence after Spring restart
- [x] Day 14: backend stage acceptance, JSON contract checks, response headers, and 100 passing tests
- [ ] Day 15: frontend structure and React basics in progress

Day 9 was accepted on 2026-11-23: all 14 Spring Boot tests passed, and the learner confirmed the expected 201/409/422 responses, Location header, and request ID consistency in Postman. The exception-handler tests use a test controller; the real creation path was checked manually.

Day 10 was accepted on 2026-11-24: all 34 Spring Boot tests passed. The learner ran the four requests and response assertions in [day10-validation.http](backend/spring-boot/day10-validation.http) using IntelliJ IDEA HTTP Client and confirmed 422/422/201/409 for overlong names, overlong descriptions, valid limits, and duplicate names. Name normalization, code point limits, Location, and request ID consistency were verified. Controller tests use a mock service; the HTTP file exercises the running application.

Day 11 was accepted on 2026-11-24: all 71 Spring Boot tests passed, with no failures, errors, or skipped tests. The learner ran all 12 requests and response assertions in [day11-pagination.http](backend/spring-boot/day11-pagination.http) using IntelliJ IDEA HTTP Client and confirmed empty lists, default parameters, three created records, stable ordering, multiple pages, pages beyond the last page, invalid pagination, unknown query parameters, and rejection of non-empty GET bodies. Request IDs were checked against response headers. Controller tests use a mock service; the HTTP file exercises the running application and in-memory repository.

The list endpoint returns items/page/pageSize/total/hasNext, defaults to page=1 and pageSize=20, accepts pageSize from 1 to 100, and orders by createdAt DESC then id ASC. It excludes DELETED knowledge bases and retains ACTIVE, DELETING, and DELETE_FAILED states in the explicitly labeled admin demo scope. Authentication and employee permission filtering are not implemented.

Day 12 was accepted on 2026-11-24: the learner completed the full test run, and the mentor checked the seven current reports totaling 71 tests with zero failures, errors, or skipped tests. PostgreSQL 16.16 and pgvector 0.8.7 were verified. V1 and V2 created core.users and core.knowledge_bases; two successful SQL migration records retain their checksums and installation times after repeated context startup. The mentor ran [day12-constraints.sql](backend/spring-boot/day12-constraints.sql), and the learner also confirmed its two PASS notices. The final database check found zero rows in both business tables. This verifies selected database constraints, not API persistence or the full database design.

Day 13 was accepted on 2026-11-26 and committed as 931cbec. All 85 Spring Boot tests passed, with zero failures, errors, or skipped tests. The learner completed the four requests and assertions in [day13-persistence.http](backend/spring-boot/day13-persistence.http): POST creation, GET before restart, GET after restarting Spring, and case-insensitive duplicate rejection. A direct SQL check confirmed API-created records in PostgreSQL. The mentor also checked that database integration test fixtures had rolled back. The 409 HTTP check was sequential; direct adapter integration tests separately verify translation of the named database unique constraint and preservation of other integrity errors.

Knowledge base POST/GET now use PostgresKnowledgeBaseRepository. InMemoryKnowledgeBaseRepository is retained for manually constructed unit tests and is not a Spring repository bean. Queries load database rows and then filter, sort, and paginate in Java; database pagination has not been implemented. Creation mapping supports new ACTIVE records; lifecycle updates and deletion are outside this adapter's current save contract. The current creator remains a demo identity, and authentication and authorization remain unimplemented. Further API contract gaps are tracked in the learning log and [Day 14 acceptance record](docs/day14-backend-acceptance.md).

Day 14 was accepted on 2026-11-26: the mentor ran the complete Spring Boot suite and checked all eleven reports, totaling 100 tests with zero failures, errors, or skipped tests. The learner completed 17 HTTP requests and assertions across the five Day 14 files. Malformed JSON returns 400 / INVALID_JSON; covered field type errors and unknown JSON fields return 422 / VALIDATION_ERROR. Each current synchronous request receives a new server-generated UUID, used in both X-Request-Id and the JSON envelope, and responses set Cache-Control: no-store. The creation/list/restart demo remains available in day13-persistence.http. Evidence, module responsibilities, and remaining scope are documented in the Day 14 acceptance record.

Next: Day 15, frontend structure and React basics. The existing React + TypeScript + Vite project already has a basic static homepage. Its build (including TypeScript checks) and ESLint passed on 2026-11-26. Continue from that code with pages/components/api/types/hooks responsibilities, followed by small component and interaction exercises. Router and API integration follow the existing roadmap; browser behavior and the first directory exercise still require learner verification.

6. Documentation

See the "docs/" directory for product requirements, architecture, database design, and API specifications.

- [Current progress and acceptance records](docs/daily-log.md)
- [Day 9 learning notes and Git Bash commands](Study_note/Day9.txt)
- [Day 10 learning notes](Study_note/Day10.txt)
- [Day 11 learning notes](Study_note/Day11.txt)
- [Day 12 learning notes and database troubleshooting](Study_note/Day12.txt)
- [Day 13 persistence implementation and acceptance notes](Study_note/Day13.txt)
- [Day 14 backend stage acceptance](docs/day14-backend-acceptance.md)
- [Day 14 closeout notes](Study_note/Day14.txt)

The original handoff manual is a Day 8 snapshot. Read the current progress log and actual code when continuing. Terminal examples use Git Bash on Windows; manual API checks use IntelliJ IDEA HTTP Client files. Understanding reviews are deferred until the whole project is complete, following the learner's 2026-11-26 preference; continue with small implementation and verification steps.

For current persistence acceptance, use day13-persistence.http: run requests 1–2, check the database ID, restart only Spring, then run requests 3–4 without repeating request 1 or closing the HTTP Client session. Keep PostgreSQL running and compare the ID against SQL, for example from infrastructure:

```bash
docker compose exec -T postgres psql \
  -U MicroJason -d enterprise_knowledge -v ON_ERROR_STOP=1 \
  -c "SELECT id, name, created_by, lifecycle FROM core.knowledge_bases WHERE name LIKE 'Day13 Persistence %' ORDER BY created_at DESC, id ASC LIMIT 5;"
```

The following Day 14 files were all accepted by the learner. Start the latest Spring application, then run each file in order using IDEA HTTP Client. They work with existing demo data; the response-header file uses the first request's ID in its second assertion.

| File | Requests | Expected statuses |
| --- | --- | --- |
| [day14-boundaries.http](backend/spring-boot/day14-boundaries.http) | 4 | 422 / 422 / 422 / 400 |
| [day14-json.http](backend/spring-boot/day14-json.http) | 2 | 400 / 422 |
| [day14-unknown-fields.http](backend/spring-boot/day14-unknown-fields.http) | 3 | All 422 |
| [day14-json-types.http](backend/spring-boot/day14-json-types.http) | 6 | All 422 |
| [day14-response-headers.http](backend/spring-boot/day14-response-headers.http) | 2 | 200 / 422 |

The Day 10/11 files are historical scripts with fixed names; Day 11 additionally assumes an empty knowledge base table. Restarting Spring retains database records and cannot restore those old prerequisites. Query empty/repeated values, authentication/authorization, database pagination, lifecycle updates, and further test isolation remain explicit follow-up work in the acceptance record.

7. IntelliJ IDEA module setup

This repository contains three independent applications. Only "backend/spring-boot" is a Java/Maven module; "frontend" is the React application, and "backend/ai-service" is the Python application. The repository root is a workspace, not a second Java module.

When opening the repository root in IntelliJ IDEA, add "backend/spring-boot/pom.xml" as a Maven project and reload Maven projects. In Project Structure > Modules, the Java module should be "spring-boot". Do not create a plain Java module named "enterprise-knowledge-agent" or mark the repository root as a Java source root. Let Maven configure src/main/java, src/test/java, resources, and dependencies.

The simplest alternative is to open "backend/spring-boot/pom.xml" directly as a project. Use the Project Files view when working from the repository root to browse the other applications. IDEA metadata is local and ignored by Git; pom.xml remains the source of truth for Java dependencies.
