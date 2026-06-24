# Design: DTOs Explícitos para Responses REST

## Contexto

O `CreditAccountController` atualmente retorna `ResponseEntity<Map<String, Object>>` para praticamente todos os endpoints. O shape da resposta é montado manualmente no método privado `toMap(CreditAccountOutput)`, que cria `LinkedHashMap`s e popula campo a campo. O `PageResponse` também usa `List<Map<String, Object>> items`, e o `RestExceptionHandler` usa `Map<String, String>` para payloads de erro.

Isso torna o contrato da API implícito, dificulta a descoberta de campos, quebra type safety e polui o controller com lógica de serialização.

## Objetivo

Substituir todos os `Map<String, Object>` e `Map<String, String>` usados como payloads REST por records Java explícitos no pacote `adapter.in.rest.dto`, mantendo o contrato JSON idêntico ao atual.

## Escopo

- Criar DTOs de response tipados.
- Refatorar `CreditAccountController` para retornar esses DTOs.
- Refatorar `RestExceptionHandler` para usar DTO de erro.
- Atualizar `PageResponse` para usar lista tipada.
- Remover o método privado `toMap` do controller.
- Atualizar testes de integração para usar os novos DTOs.

## Fora do escopo

- Alterar request DTOs (já existem e já são tipados).
- Mudar lógica de negócio ou use cases.
- Alterar status HTTP ou shape JSON dos endpoints.

## Componentes

### Novos DTOs

| Arquivo | Campos | Uso |
|---|---|---|
| `CreditAccountResponse` | `String creditAccountId`, `boolean opened`, `String creditLimit`, `String outstandingBalance`, `String authorizedAmount`, `String availableLimit`, `Long projectedVersion`, `List<AuthorizationResponse> authorizations` | Resposta padrão da conta para todos os endpoints, exceto autorização. |
| `AuthorizationResponse` | `String authorizationId`, `String amount`, `String status`, `String merchantName` | Item dentro de `CreditAccountResponse.authorizations`. |
| `AuthorizePurchaseResponse` | `CreditAccountResponse account`, `String authorizationId` | Endpoint de autorização de compra, que retorna a conta + id da autorização. |
| `ErrorResponse` | `String error` | Payload padrão de erro no `RestExceptionHandler`. |
| `CreditAccountDtoMapper` | métodos estáticos | Conversão pura de `CreditAccountOutput` → `CreditAccountResponse` e `PurchaseAuthorizationOutput` → `AuthorizationResponse`. |

### DTOs alterados

- `PageResponse`: campo `items` passa de `List<Map<String, Object>>` para `List<CreditAccountResponse>`.

## Refatoração do Controller

Assinaturas dos endpoints após a mudança:

```java
ResponseEntity<CreditAccountResponse> openCreditAccount(...)
ResponseEntity<CreditAccountResponse> assignCreditLimit(...)
ResponseEntity<AuthorizePurchaseResponse> authorizePurchase(...)
ResponseEntity<CreditAccountResponse> capturePurchase(...)
ResponseEntity<CreditAccountResponse> releasePurchaseAuthorization(...)
ResponseEntity<CreditAccountResponse> receivePayment(...)
ResponseEntity<CreditAccountResponse> getAccount(...)
ResponseEntity<PageResponse> listAccounts(...)
```

A construção manual de mapas é substituída por chamadas ao mapper:

```java
return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreditAccountDtoMapper.toResponse(output.account()));
```

E para autorização:

```java
return ResponseEntity.ok(new AuthorizePurchaseResponse(
        CreditAccountDtoMapper.toResponse(output.account()),
        output.authorizationId()
));
```

## Error Handling

Handlers que hoje retornam `ResponseEntity<Map<String, String>>` passam a retornar `ResponseEntity<ErrorResponse>`:

```java
@ExceptionHandler(AccountAlreadyExistsException.class)
public ResponseEntity<ErrorResponse> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(ex.getMessage()));
}
```

O handler de `ProjectionNotReadyException` já usa `ProjectionNotReadyResponse`, então não será alterado.

## Testes

- `CreditAccountControllerIT`: substituir `Map.class` por `CreditAccountResponse.class` / `AuthorizePurchaseResponse.class` e acessar campos tipados.
- `CreditAccountControllerListIT`: substituir `Map.class` por `CreditAccountResponse.class` / `PageResponse.class`.

As asserções de valor permanecem as mesmas; apenas a forma de acesso aos campos muda.

## Critérios de Aceitação

1. `CreditAccountController` não referencia `Map<String, Object>` em nenhuma assinatura de endpoint.
2. `PageResponse` usa `List<CreditAccountResponse>`.
3. `RestExceptionHandler` não usa `Map<String, String>`.
4. Não há mais método `toMap` no controller.
5. Todos os testes de integração passam.
6. Build do projeto (`./mvnw verify` ou equivalente) passa sem erros.
