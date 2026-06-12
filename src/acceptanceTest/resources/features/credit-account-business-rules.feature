# language: pt
Funcionalidade: Regras de negócio da conta de crédito

  Cenário: Autorização acima do limite disponível retorna erro de regra de negócio
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "1000.00" é autorizada no estabelecimento "Store"
    Então a API deve retornar status 422 com mensagem contendo "available"

  Cenário: Pagamento maior que saldo em aberto retorna erro de regra de negócio
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "100.00" é autorizada no estabelecimento "Store"
    E a autorização da compra é capturada
    E um pagamento de "200.00" é recebido
    Então a API deve retornar status 422 com mensagem contendo "outstanding"

  Cenário: Comando repetido com mesma Idempotency-Key e mesmo payload retorna o mesmo resultado
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é atribuído como "500.00" usando a chave "stable-key-aaa"
    E o limite de crédito é atribuído como "500.00" usando a chave "stable-key-aaa"
    Então o resultado deve ser o mesmo

  Cenário: Comando repetido com mesma Idempotency-Key mas payload diferente retorna conflito
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é atribuído como "500.00" usando a chave "conflict-key-bbb"
    E o limite de crédito é atribuído como "800.00" usando a chave "conflict-key-bbb"
    Então a API deve retornar 409 de conflito de idempotência

  Cenário: Aumentar limite com autorizações pendentes preserva as autorizações e libera mais limite
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "200.00" é autorizada no estabelecimento "Store"
    E o limite de crédito é alterado para "1000.00"
    Então eventualmente o resumo da conta deve mostrar:
      | limite de crédito | 1000.00 |
      | valor autorizado  | 200.00  |
      | limite disponível | 800.00  |
      | saldo em aberto   | 0.00    |

  Cenário: Diminuir limite abaixo de saldo mais autorizado falha
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "1000.00"
    Quando uma compra de "500.00" é autorizada no estabelecimento "Store"
    E a autorização da compra é capturada
    E o limite de crédito é alterado para "300.00"
    Então a API deve retornar status 422 com mensagem contendo "limit"

  Cenário: Diminuir limite respeitando saldo mais autorizado funciona
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "1000.00"
    Quando uma compra de "200.00" é autorizada no estabelecimento "Store"
    E o limite de crédito é alterado para "600.00"
    Então eventualmente o resumo da conta deve mostrar:
      | limite de crédito | 600.00  |
      | valor autorizado  | 200.00  |
      | limite disponível | 400.00  |
      | saldo em aberto   | 0.00    |
