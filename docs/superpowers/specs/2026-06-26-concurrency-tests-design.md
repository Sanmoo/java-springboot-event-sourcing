# Design: Testes de Concorrência Determinísticos para Use Cases

## Contexto

O projeto implementa event sourcing manual em PostgreSQL com locking otimista via `UNIQUE(aggregate_id, aggregate_version)`. Os comandos passam pelo `CreditAccountUseCaseSupport`, que reidrata o agregado, executa o comando, grava eventos no `EventStore` e persiste o resultado de idempotência. Quando duas transações concorrentes tentam gravar a mesma versão do agregado, a segunda falha com `ConcurrencyConflictException`.

Hoje existe apenas um teste de concorrência no `JdbcIdempotencyAdapterIT`, que valida a serialização de lock de idempotência. Não há cobertura sistemática para:

- Comandos concorrentes no mesmo agregado.
- Replay idempotente sob requisições duplicadas simultâneas.
- Projeção assíncrona e *read-your-writes* (`minVersion`) sob carga concorrente.

## Objetivo

Adicionar uma suite de testes determinísticos no nível de use case que verifique:

1. Conflitos de concorrência são detectados corretamente (`ConcurrencyConflictException`).
2. Comandos que falharam por conflito podem ser retentados e resultam no estado final correto.
3. Idempotência, projeção e *read-your-writes* se mantêm consistentes sob concorrência.

## Escopo

- Criar um harness reutilizável para execução de comandos concorrentes com retry.
- Adicionar testes de use case cobrindo os cenários de concorrência principais.
- Executar os testes contra PostgreSQL real via Testcontainers.
- Manter o retry apenas na camada de teste; a produção continua propagando `ConcurrencyConflictException`.

## Fora do escopo

- Adicionar retry automático na camada de produção.
- Testes estatísticos ou de stress com milhares de iterações.
- Mudar contratos REST ou lógica de negócio dos use cases.
- Extrair source set separado; os testes ficam em `src/test`.

## Componentes

### ConcurrentCommandHarness

Classe utilitária em `src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/`.

```java
public final class ConcurrentCommandHarness {
    public <T> ConcurrentCommandResult<T> runConcurrently(
        List<Callable<T>> commands,
        int maxRetries
    ) { ... }
}
```

Responsabilidades:

- Submeter os comandos para um `ExecutorService` com pool fixo.
- Aguardar a conclusão de todos.
- Coletar resultados, conflitos (`ConcurrencyConflictException`) e outras falhas.
- Retentar comandos conflitantes sequencialmente até `maxRetries`.
- Retornar `ConcurrentCommandResult` com estatísticas e lista de resultados bem-sucedidos.

### ConcurrentCommandResult

Record com os dados da execução:

```java
public record ConcurrentCommandResult<T>(
    List<T> successfulResults,
    int conflictCount,
    int failureCount,
    int retryCount,
    long finalAggregateVersion
) {}
```

### CreditAccountUseCaseConcurrencyIT

Classe de teste de integração anotada com `@SpringBootTest` e `@Import(TestcontainersConfiguration.class)`. Contém um cenário por método de teste.

## Cenários de teste

### 1. Autorizações simultâneas no mesmo agregado

N threads tentam autorizar compras com IDs diferentes no mesmo agregado, com limite suficiente para todas.

**Verificações:**

- `conflictCount >= 0` (algumas threads podem conflitar).
- Após retry, `successfulResults.size() == N`.
- `finalAggregateVersion == N + versão inicial`.
- Soma dos valores autorizados == `authorizedAmount` do agregado.
- `authorizedAmount + outstandingBalance <= creditLimit`.

### 2. Autorização e captura simultâneas

Uma thread autoriza uma compra e outra tenta capturar a mesma autorização imediatamente.

**Verificações:**

- Se a captura for processada antes da autorização, deve falhar com `AuthorizationNotFoundException`.
- Após retry da captura, o estado final mostra a autorização como `CAPTURED`.
- `outstandingBalance` reflete o valor capturado.

### 3. Requisições idempotentes duplicadas

N threads enviam o mesmo comando com a mesma `Idempotency-Key` e mesmo hash de request.

**Verificações:**

- Apenas uma thread processa o comando.
- As demais retornam replay (`ExecutionResult.replayed() == true`).
- O estado final do agregado reflete exatamente uma aplicação do comando.
- Respostas de replay são idênticas à resposta original.

### 4. Escrita e leitura com `minVersion`

Uma thread executa um comando enquanto outra tenta ler a conta com `minVersion` maior que a versão projetada atual.

**Verificações:**

- O comando é bem-sucedido.
- A leitura com `minVersion` só retorna após a projeção alcançar a versão desejada (o teste força a projeção antes de ler, ou verifica que `ProjectionNotReadyException` ocorre quando a projeção está atrasada).
- `projectedVersion >= minVersion` no resultado final.

### 5. Comandos mistos simultâneos

Threads executam comandos diferentes no mesmo agregado: autorizar, capturar, liberar e receber pagamento.

**Verificações:**

- Nenhum comando viola invariantes de domínio.
- Estado final é consistente com os comandos que tiveram sucesso.
- `availableLimit >= 0` e `outstandingBalance >= 0`.

## Setup dos testes

- `@SpringBootTest` com `TestcontainersConfiguration` para PostgreSQL real.
- Limpar tabelas relevantes no `@BeforeEach` (`event_store`, `outbox_events`, `outbox_deliveries`, `idempotency_records`, `credit_account_summary`, `projection_checkpoints`).
- Criar conta e atribuir limite no `setUp`.
- Usar UUIDs determinísticos para IDs de autorização, evitando colisões acidentais entre threads.

## Invariantes verificadas

- `authorizedAmount + outstandingBalance <= creditLimit`
- `outstandingBalance >= 0`
- `availableLimit >= 0`
- `finalAggregateVersion == versão inicial + comandos bem-sucedidos`
- Nenhum evento duplicado com a mesma `(aggregate_id, aggregate_version)`.

## Estrutura de arquivos

```
src/test/java/com/sanmoo/eventsourcing/creditaccount/concurrency/
  ConcurrentCommandHarness.java
  ConcurrentCommandResult.java
  CreditAccountUseCaseConcurrencyIT.java
```

## Execução

- Os testes ficam em `src/test` e rodam com `./gradlew test`.
- Opcionalmente, os métodos podem ser anotados com `@Tag("concurrency")` para permitir execução filtrada futuramente, sem criar source set separado neste momento.

## Critérios de aceitação

1. `ConcurrentCommandHarness` executa comandos concorrentes e retenta conflitos de forma determinística.
2. Todos os cinco cenários possuem testes implementados e passando.
3. Os testes rodam contra PostgreSQL real via Testcontainers.
4. Nenhuma mudança de comportamento na produção; retry permanece apenas nos testes.
5. Build do projeto (`./gradlew check`) continua passando.
6. Testes são determinísticos: falhas reproduzem o mesmo resultado a cada execução.
