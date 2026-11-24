---
name: testing-qa
description: >
  Estrategia de pruebas para SAC+: unitarios JUnit/Mockito en store-be,
  typecheck/lint en store-fe, integración, aislamiento multi-tenant, gates
  de CI y criterios de "done". Usar al escribir o revisar tests, definir
  coverage o evaluar si una tarea está completa — también al decir "test",
  "prueba", "cobertura", "QA", "CI".
---

# Testing & QA — SAC+

## Estado actual

**store-be**: ~55 tests JUnit5 + Mockito (`*Test.java` junto a services).
Cubren: AuthService (rate limit, login), OrderService (creación, estados,
stock, refunds, idempotencia), ReportService (agregados), StoreService
(scoping), RateLimiter, GlobalExceptionHandler, y
`security/CrossTenantIsolationTest` (HP-QA-05: lectura+escritura rechazada
contra tenant/store ajeno en cada módulo — services reales sobre repos
mockeados) y `repository/PostgresRepositoryIT` (HP-QA-04: `@DataJpaTest` +
Testcontainers `postgres:16-alpine`, 7 tests — folio semanal, agregados del
dashboard, top productos, idempotencia de órdenes/fichas, email
case-insensitive y soft-delete). Requiere Docker; para Docker Desktop
Engine ≥28 el bloque `static` fija `api.version=1.44` (docker-java 3.7.0
fijado en `pom.xml` — la versión del BOM llama `/info` sin versionar y el
daemon responde 400).

**store-fe**: 33 tests Vitest (`pnpm test`) en `lib/` + POS utils, y
Playwright E2E `e2e/` (local, con `e2e/seed.sql`).

**CI**: `.github/workflows/ci.yml` corre `mvnw test` en push a `main` y en
PRs (los IT de Testcontainers corren donde haya Docker — en GH Actions el
runner de Ubuntu lo trae).

## Pirámide

```
        E2E (Playwright en store-fe — `pnpm test:e2e`)
      Integración (@DataJpaTest + Testcontainers — PostgresRepositoryIT)
    Unitarios (JUnit/Mockito back · Vitest front `lib/`)
  Typecheck+Lint (CI en cada PR)
```

## Reglas

1. **"Done" = código + tests + verificación**, no solo compila.
   Backend: `./mvnw -q -DskipTests compile` + `./mvnw -q test`.
   Frontend: `npx tsc --noEmit` + `npx eslint <tocados>`.
2. **Todo cambio de firma/lógica de service actualiza `src/test/` en el
   mismo commit** (regla de AGENTS.md — Railway compila tests).
3. **Tests de negocio críticos** (donde está el dinero):
   - Órdenes: stock, totales con impuesto, TOKEN canje, cancelación+refund,
     idempotencia, folio único.
   - Auth: rate limit, roles, sessionHours, token_version.
   - Scoping: usuario de tienda A NO ve órdenes/usuarios de tienda B;
     MANAGER no accede a tienda no asignada.
4. **Tests de autorización negativa**: por cada endpoint nuevo, probar
   rol sin permiso → 403 y tenant ajeno → 403/404.
5. **Frontend**: cuando se justifique, agregar Vitest + React Testing
   Library para lógica de `lib/` (cálculos de dinero en centavos,
   `apiErrorMessage`, paginación). Componentes visuales: prueba manual.
6. **Regresión**: antes de release, smoke manual del happy path:
   login → catálogo → orden POS → KDS → corte (futuro) → reporte.

## Cobertura mínima por tipo de cambio

| Cambio | Test requerido |
|---|---|
| Nuevo endpoint/service | unitario de lógica + autorización negativa |
| Cambio de cálculo (impuestos, totales, stock) | unitario con casos borde |
| Nuevo código de error | `err.<CODE>` en i18n + test de mapeo si aplica |
| Query agregada nueva | test con mocks verificando shape de `Object[]` |
| Query de repositorio delicada | caso en `PostgresRepositoryIT` (Postgres real) |
| Frontend lógica pura | Vitest (`pnpm test` en store-fe) |

## Pendientes de calidad (del audit)

- [x] ~~CI pipeline con `mvnw test` + `tsc` + `eslint` como gate~~ (HP-QA-02).
- [x] ~~Tests de aislamiento cross-tenant~~ (`CrossTenantIsolationTest`, HP-QA-05).
- [x] ~~Testcontainers/PostgreSQL real~~ (`PostgresRepositoryIT`, HP-QA-04).
- [x] ~~E2E happy path~~ (Playwright en store-fe, HP-QA-03; CI pendiente).
- [ ] Mutation/coverage report (Jacoco) para visibilidad.
