# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Quarkus 3 service (Java 17, port 4001) with two APIs:
- `/api/bulk-payments` is a **pass-through proxy to the BBVA sandbox** (`https://sbx.pe.bbvaapimarket.com/pe/bulk-payments/v1`). It does no validation and keeps no state. BBVA's status, body and `Location` are returned unchanged. A connection failure returns 502 `upstreamUnavailable`; failing to get the OAuth token returns 502 `upstreamAuthFailed`.
- `/api/bulk-payments-deferred-signature` ports `../routes/bulkPaymentsDeferredSignature.js` from the parent Node mock and must behave like it: same status codes, error codes and message text. Storage is H2 in memory with Hibernate ORM Panache (`drop-and-create`), so data is lost on restart.

Code comments and some messages are in Spanish. Match that.

## Commands

Run these from this folder. The shell's default JDK is 27, so prefix commands with `JAVA_HOME=$(/usr/libexec/java_home -v 17)`.

- BBVA credentials come only from env vars `BBVA_CLIENT_ID`, `BBVA_CLIENT_SECRET` (OAuth client_credentials) and `BBVA_PRIVATE_KEY_BASE64` (base64 of the PEM private key, PKCS#8 content) used to sign every request. Never commit real values.
- `mvn quarkus:dev`: dev mode with live reload at http://localhost:4001. Dev UI is at `/q/dev`, Swagger at `/q/swagger-ui`, OpenAPI at `/openapi`, health at `/q/health`.
- `mvn test`: runs all `@QuarkusTest` + RestAssured tests.
- `mvn test -Dtest=BulkPaymentsDeferredSignatureResourceTest#closingSettlementSucceedsOnceThenIsFull`: runs a single test. Inherited tests from `RemittanceApiTestBase` run through the concrete subclass name.
- `mvn package`: builds `target/quarkus-app/quarkus-run.jar`.

There is no linter.

## Architecture

Both APIs expose the same endpoints under `/api/bulk-payments` and `/api/bulk-payments-deferred-signature`:
- `POST /remittance` creates a remittance.
- `PATCH /remittances/{id}` appends payments.
- `POST /remittances/{id}/closing-settlement` closes the remittance.

- **Config** lives in `src/main/resources/application.yml` (extension `quarkus-config-yaml`; there is no `application.properties`). The test profile is the top-level `"%test":` key (quoted, since `%` can't start a bare YAML key). Quote values containing `;` or `"`.
- **TLS**: both REST clients set `tls-configuration-name: bbva`. That `quarkus.tls.bbva` config skips certificate and hostname verification by default (`trust-all`, `hostname-verification-algorithm: NONE`). Re-enable with `BBVA_TLS_TRUST_ALL=false` and `BBVA_TLS_HOSTNAME_VERIFICATION=HTTPS`.
- **Proxy layers**: `resource/BulkPaymentsResource` → `service/BbvaBulkPaymentsService` → `client/BbvaBulkPaymentsClient`. The resource never touches the REST client. It only turns the service's `dto/UpstreamResponse` (status, Location, body, media type) into a JAX-RS `Response`. The service owns the call, closes the upstream `Response` and logs operation and status. **Never log headers, bodies or tokens**, because they carry credentials and customer data. A timeout becomes 504 `upstreamTimeout`, and any other `ProcessingException` becomes 502 `upstreamUnavailable` (shared helper `service/UpstreamErrors`).
- **OAuth token**: `service/BbvaTokenProvider` calls `client/BbvaOAuthClient` (config key `bbva-oauth`, `POST /auth/oauth/v2/token`, form-urlencoded, client_credentials) and caches `"<token_type> <access_token>"` until 60 s before `expires_in`. `BbvaBulkPaymentsService` gets it *before* each call's `try` and passes it as an `@HeaderParam("Authorization")`, so token errors stay `ApiException` (502 `upstreamAuthFailed` for missing credentials or a non-2xx token response). A 401 from BBVA calls `invalidate()` and is relayed as-is, with no retry.
- The body is received as `JsonNode` and forwarded as its minified `String` (no DTO mapping), so BBVA-only fields (`debtorName`, `numberType`, `endToEndId`) survive.
- **OpenAPI contract**: the bulk-payments contract is hand-written in `src/main/resources/META-INF/openapi.yaml`, which is the source of truth. It is excluded from annotation scanning with `mp.openapi.scan.exclude.classes`, and in `%test` `FakeBbvaResource` and `FakeBbvaOAuthResource` are excluded too. Update the YAML when those endpoints change. Its schemas are prefixed `Bbva*` so they don't collide with the DTO schemas that deferred-signature still generates from code. `OpenApiContractTest` checks this setup. The client config key is `bbva-bulk-payments`, with `disable-default-mapper=true` (on `bbva-oauth` too) so 4xx/5xx come back as a `Response`, connect-timeout 5 s and read-timeout 30 s. There are **no retries**, because POST is not idempotent and a retry could duplicate remittances.
- **Request signing**: `client/BbvaRequestSigner` ports the Postman pre-request script. For each call, `BbvaBulkPaymentsService` serializes the body to minified JSON, signs it and passes `Digest`, `Signature`, `Date` and `Host` as `@HeaderParam`. The client sends that exact `String`, not the `JsonNode`, so the Digest matches byte for byte. `Digest` is `SHA-512=` plus the base64 SHA-512 of the body. `Date` uses `EEE, dd MMM yyyy HH:mm:ss 'GMT'`, like JS `toUTCString()`; `RFC_1123_DATE_TIME` is wrong here because it does not zero-pad the day. Host and the path prefix come from the `bbva-bulk-payments` URL. `Signature` is RSA-SHA512 over `(request-target):<method> <path>\nhost:..\ndate:..\ndigest:..`. A missing or invalid key returns 502 `upstreamAuthFailed`. Config is the typed `client/BbvaAuthConfig` (`@ConfigMapping`, prefix `bbva.auth`: `client-id`, `client-secret`, `private-key`, all `Optional`). It logs a startup WARN if any is missing but does not fail, so deferred-signature still works. Never log the key or the signing string. BBVA's header carries the remittance's *final* totals (e.g. 4 payments/12000 on a POST with 2 payments), which is one reason the local validator is not used for the proxy.
- **`domain/ApiVariant`** (`BULK`, `DEFERRED`) holds every difference between the two mock variants. Only `DEFERRED` is in use now. `BULK` is kept in case bulk-payments goes back to being mocked. The differences are: the `Location` prefix, the `functionalError` message, the `OB-` name prefix (DEFERRED only, never duplicated), optional `debtor.identity` (DEFERRED), and whether `name` is required on PATCH (BULK only). Put new per-API differences here, not in the resources or the service.
- **`resource/BulkPaymentsDeferredSignatureResource`** sets `VARIANT = DEFERRED` and delegates to `RemittanceService`.
- **`service/RemittanceService`** runs the lifecycle in `@Transactional` methods. Each remittance is stored with its `api`. Looking up an id that belongs to the other API returns `senderNotFound`, like the separate Node `Map`s did. Closing runs its checks in this order: already finalized → `fullPaymentRemittance`; no `remittances[].debtorAccount.number` → mandatory-parameters error; an account not used at creation → `notMatchingAccount`; an account ending in `0000` → `functionalError`. On success it sets `finalized=true, status=CLOSED`, and after that PATCH and close both return `fullPaymentRemittance`.
- **`service/RemittanceValidator`** ports `validateShape` + `checkBusinessRules` by hand, without Bean Validation, to reproduce the exact Node text `Mandatory parameters not reported: a, b`. The paths are listed in order, and `0`/null counts as missing, like JS truthiness. The business rules run in a fixed order and the first failure wins:
  1. Only one payment type per remittance.
  2. Creditor account length: 18 for internal payments, 20 for interbank.
  3. Transaction count must match the header.
  4. Control sum per currency, with a 0.001 tolerance.
- **Errors**: throw `ApiException` (usually `ApiException.badRequest(code, msg)`). `exception/ApiExceptionMapper` turns it into `{"messages":[{"code","message","type":"ERROR"}]}`. It also turns malformed JSON and framework errors into the same envelope.
- **`dto/`** has Jackson records for requests and responses. **`domain/`** has the Panache entities (`RemittanceEntity` → `DebtorEntity`, `PaymentEntity`).

## Tests

- Mock: `RemittanceApiTestBase` holds the remittance-lifecycle cases. Its only subclass is `BulkPaymentsDeferredSignatureResourceTest`, which supplies the base path, the Location prefix, the functional error message and the expected name.
- Proxy: `BulkPaymentsResourceTest` calls `/api/bulk-payments`. In the `%test` profile the REST clients point at `FakeBbvaResource` and `FakeBbvaOAuthResource` (`/fake-bbva/...` on the test port, read-timeout 1 s). `BbvaTestKeyResource` generates a throwaway RSA key pair and injects `bbva.auth.private-key`. `FakeBbvaResource` records the last path, headers and raw body and replies 201/204. `BulkPaymentsResourceTest` checks the Digest and verifies the signature with the public key. Proxy tests use `BASE = "/bulk-payments"`, because RestAssured already prepends `quarkus.http.root-path` (`/api`). It replies 400 when `name == "FAIL"`, 401 when `name == "UNAUTHORIZED"`, and when `name == "SLOW"` it sleeps 2 s, which triggers a 504. `FakeBbvaOAuthResource` counts token requests (`CALLS`, compare relative values because the cached token survives between tests) and replies 401 unless `client_secret == "test-secret"`; `BbvaOAuthFailureTest` uses that to get 502 `upstreamAuthFailed`. `BulkPaymentsUnreachableTest` uses a `@TestProfile` that points the client at a closed port, which triggers a 502. Tests never hit the real sandbox.

## Keeping in sync with Node

If `../routes/bulkPaymentsDeferredSignature.js` changes, update this service to match. `../routes/bulkPayments.js` no longer has a Quarkus counterpart, because that API is proxied to BBVA. Shared logic belongs in `RemittanceValidator`/`RemittanceService`, and differences belong in `ApiVariant`. Keep error codes and message text identical, because the tests assert the exact strings.
