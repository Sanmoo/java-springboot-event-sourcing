# language: pt
Funcionalidade: Bloqueio de conta de crédito

  Cenário: Conta bloqueada recusa novas autorizações
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando a conta é bloqueada
    E uma compra de "100.00" é autorizada no estabelecimento "Store"
    Então a API deve retornar status 422 com mensagem contendo "blocked"

  Cenário: Pagamento é aceito enquanto a conta está bloqueada
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    E uma compra de "100.00" é autorizada no estabelecimento "Store"
    E a autorização da compra é capturada
    Quando a conta é bloqueada
    E um pagamento de "25.00" é recebido
    Então o pagamento deve ser aceito

  Cenário: Liberação de autorização existente é aceita enquanto a conta está bloqueada
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    E uma compra de "100.00" é autorizada no estabelecimento "Store"
    Quando a conta é bloqueada
    E a autorização da compra é liberada
    Então a liberação deve ser aceita

  Cenário: Conta desbloqueada aceita novas autorizações
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando a conta é bloqueada
    E a conta é desbloqueada
    E uma compra de "100.00" é autorizada no estabelecimento "Store"
    Então a autorização deve ser aceita

  Cenário: Bloquear uma conta já bloqueada retorna 422
    Dado que uma conta de crédito foi aberta
    E a conta é bloqueada
    Quando a conta é bloqueada
    Então a API deve retornar status 422 com mensagem contendo "already blocked"

  Cenário: Desbloquear uma conta ativa retorna 422
    Dado que uma conta de crédito foi aberta
    Quando a conta é desbloqueada
    Então a API deve retornar status 422 com mensagem contendo "not blocked"

  Cenário: Listagem filtra contas bloqueadas antes da paginação
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    E a conta é bloqueada
    Quando uma segunda conta de crédito ativa é aberta
    Então a listagem de contas com status "BLOCKED" deve conter somente a conta bloqueada
