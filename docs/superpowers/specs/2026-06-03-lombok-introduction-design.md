# Introdução segura de Lombok

## Contexto

O projeto é uma aplicação Java 25 com Spring Boot 4 e Gradle Kotlin DSL. Há boilerplate manual em classes Spring com dependências `final`, por exemplo construtores de controllers, adapters e use cases. O objetivo é reduzir esse boilerplate com Lombok, incluindo usos seguros na camada de domínio.

## Objetivo

Introduzir Lombok como annotation processor de compilação e refatorar código repetitivo sem alterar comportamento, contratos públicos relevantes, invariantes de domínio ou arquitetura de ports and adapters.

## Escopo

- Adicionar Lombok ao `build.gradle.kts` como dependência `compileOnly` e `annotationProcessor`.
- Adicionar configuração equivalente para testes se necessária para compilação de fixtures/testes.
- Substituir construtores manuais de injeção por `@RequiredArgsConstructor` em classes Spring com campos `final`.
- Aplicar Lombok na camada de domínio apenas para boilerplate mecânico seguro.
- Preservar records existentes quando Lombok não reduzir código de forma clara.

## Fora de escopo

- Reescrever modelo de domínio para padrão anêmico.
- Introduzir setters públicos em aggregates, value objects ou entidades de domínio.
- Trocar records por classes Lombok sem ganho claro.
- Alterar endpoints, payloads, schema de banco ou comportamento de negócio.

## Regras de uso de Lombok

- Preferir `@RequiredArgsConstructor` para dependências obrigatórias e campos `final`.
- Usar `@Getter` quando reduzir getters triviais sem abrir mutabilidade.
- Considerar `@Accessors(fluent = true)` apenas se preservar a API pública atual de métodos como `version()`.
- Evitar `@Data` em domínio, pois gera setters, `equals`, `hashCode` e `toString` amplos demais.
- Evitar construtores públicos gerados quando a criação precisar passar por fábricas ou validações.
- Não usar Lombok onde ele reduza legibilidade ou esconda regras de negócio.

## Design por camada

### Adapters e use cases Spring

Classes como controllers, adapters JDBC e use cases com dependências `final` terão construtores manuais removidos e receberão `@RequiredArgsConstructor`. O Spring continuará usando constructor injection, agora com o construtor gerado por Lombok.

### Domínio

A camada de domínio poderá usar Lombok para reduzir código mecânico, mas mantendo encapsulamento. Aggregates e entidades ricas não receberão setters públicos. Construtores privados ou fábricas estáticas continuarão controlando criação quando forem parte das invariantes.

### DTOs, inputs e outputs

Records existentes devem permanecer como records por padrão. Lombok será considerado apenas para classes não-record que tenham boilerplate claro e seguro.

## Validação

A implementação deve verificar:

1. Compilação com annotation processing habilitado pelo Gradle.
2. Testes unitários e de integração existentes, conforme ambiente permitir.
3. Checks estáticos relevantes. Se `./gradlew check` for custoso por PIT/Testcontainers, a execução ou limitação deve ser reportada com evidência.

## Riscos e mitigação

- **Quebra de API pública por mudança de getters:** preservar nomes atuais ou ajustar de forma controlada com testes.
- **Mutabilidade acidental no domínio:** não usar `@Data` nem setters em aggregates/value objects.
- **Conflito com ferramentas estáticas:** rodar compilação/testes e ajustar imports/anotações conforme necessário.
- **Dependência runtime desnecessária:** Lombok será `compileOnly` e `annotationProcessor`, não `implementation`.
