# bulk-payments-service

Microservicio Quarkus con dos APIs:
- `/api/bulk-payments`: **proxy al sandbox BBVA** (`https://sbx.pe.bbvaapimarket.com/pe/bulk-payments/v1`). No valida ni persiste; devuelve status, cuerpo y `Location` de BBVA tal cual (502 `upstreamUnavailable` si no hay conexión, 502 `upstreamAuthFailed` si no se obtiene el token, 504 `upstreamTimeout` si no responde).
- `/api/bulk-payments-deferred-signature`: mock que porta `routes/bulkPaymentsDeferredSignature.js`. Persiste en H2 (en memoria) con Hibernate ORM Panache: los datos se pierden al reiniciar.

## Credenciales BBVA
Se leen de variables de entorno (nunca en el repo). El token se pide solo a `https://sbx.pe.bbvaapimarket.com/auth/oauth/v2/token` (client_credentials) y se cachea hasta 60 s antes de vencer (dura 1800 s); si BBVA responde 401 se descarta y se pide otro en la siguiente llamada. Cada petición se firma en `client/BbvaRequestSigner` (port del pre-request de Postman): `Digest` = SHA-512 del cuerpo minificado que se envía, `Date` con el formato de `Date.toUTCString()`, `Host` del URL del client y `Signature` = RSA-SHA512 sobre `(request-target) host date digest` con la clave privada:
```
export BBVA_CLIENT_ID=...
export BBVA_CLIENT_SECRET=...
export BBVA_PRIVATE_KEY_BASE64='LS0tLS1CRUdJTi...'   # base64 del PEM (contenido PKCS#8)
```

Los dos clients (`bbva-bulk-payments`, `bbva-oauth`) usan la configuración TLS `bbva`, que por defecto **no valida el certificado ni el hostname** (sandbox). Para volver a validarlos: `export BBVA_TLS_TRUST_ALL=false BBVA_TLS_HOSTNAME_VERIFICATION=HTTPS`.

## Requisitos
- JDK 17, Maven 3.9+

## Comandos
```
mvn quarkus:dev     # http://localhost:4001 (dev UI en /q/dev)
mvn test            # @QuarkusTest + RestAssured; BBVA se simula con FakeBbvaResource
mvn package         # target/quarkus-app/quarkus-run.jar
```
Con varios JDK instalados: `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`.

Swagger UI: `/q/swagger-ui`, OpenAPI: `/openapi`, salud: `/q/health`.

El contrato de `/api/bulk-payments` se escribe a mano en `src/main/resources/META-INF/openapi.yaml` (fuente de verdad, con ejemplos); deferred-signature se genera desde sus anotaciones. Actualizar el YAML al cambiar los endpoints de bulk-payments.

## Endpoints
Misma forma bajo dos bases: `/api/bulk-payments` (proxy BBVA) y `/api/bulk-payments-deferred-signature` (mock).

| Método | Ruta | Respuesta |
|---|---|---|
| POST | `/remittance` | 201 + `Location` + `{data:{id,name,header,status,remittances}}` |
| PATCH | `/remittances/{id}` | 204 (agrega pagos) |
| POST | `/remittances/{id}/closing-settlement` | 204 (liquida / envía a firma) |

Errores del mock: `{"messages":[{"code","message","type":"ERROR"}]}` con status 400. En el proxy, los errores son los de BBVA.

Diferencias de deferred-signature: el nombre se guarda con prefijo `OB-`, `debtor.identity` es opcional, `name` no se exige en PATCH, el cierre solo envía la remesa a pendiente de firma.

## Estructura
- `resource/`: dos recursos JAX-RS delgados. `BulkPaymentsResource` → `service/BbvaBulkPaymentsService` → `client/BbvaBulkPaymentsClient` (token OAuth en `service/BbvaTokenProvider` vía `client/BbvaOAuthClient`; Digest/Signature/Date/Host por petición en `client/BbvaRequestSigner`; config tipada en `client/BbvaAuthConfig`). Timeouts: conexión 5 s, lectura 30 s; sin reintentos (POST no idempotente). Errores propios del proxy: 502 `upstreamUnavailable`, 502 `upstreamAuthFailed`, 504 `upstreamTimeout`. No se registran cabeceras ni cuerpos.
- `service/RemittanceService`: ciclo de vida del mock (hoy solo DEFERRED; `ApiVariant.BULK` queda sin uso).
- `service/RemittanceValidator`: validación de forma y reglas de negocio (mensajes idénticos al mock).
- `domain/`: entidades Panache. `exception/`: `ApiException` y mappers al envelope de error.

## Ejemplo
```
curl -i -X POST localhost:4001/api/bulk-payments-deferred-signature/remittance -H 'Content-Type: application/json' -d '{
  "name":"Planilla","status":"DRAFT",
  "header":{"numberOfTransactions":1,"controlSum":[{"currency":"PEN","amount":100}]},
  "remittances":[{"instructionId":"I1",
    "debtor":{"identity":{"id":"20100070970","documentType":"RUC"}},
    "debtorAccount":{"number":"00110123456789012345"},
    "payments":[{"paymentType":"INTERNAL_PAYMENT",
      "paymentAmount":{"currency":"PEN","amount":100},
      "creditor":{"name":"Maria"},"creditorAccount":{"number":"001101234567890123"}}]}]}'
```
