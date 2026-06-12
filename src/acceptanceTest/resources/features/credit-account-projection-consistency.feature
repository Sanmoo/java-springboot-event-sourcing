# language: pt
Funcionalidade: Consistência da projeção assíncrona

  Cenário: Consulta com minVersion maior que a versão projetada retorna 202
    Dado que uma conta de crédito foi aberta
    Quando eu consulto a conta com minVersion "99"
    Então a API deve retornar 202 com requiredVersion 99

  Cenário: minVersion 0 retorna 200 imediatamente
    Dado que uma conta de crédito foi aberta
    Quando eu consulto a conta com minVersion "0"
    Então a API deve retornar status 200 com mensagem contendo "projectedVersion"

  Cenário: Leitura sem minVersion retorna 200 com versão projetada corrente
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando eu consulto o resumo da conta
    Então a API deve retornar status 200 com mensagem contendo "creditLimit"

  Cenário: Sequência de comando e leitura eventual com minVersion correto
    Dado que uma conta de crédito foi aberta
    Quando o limite de crédito é alterado para "750.00"
    E eu consulto a conta com minVersion "2"
    Então eventualmente a API deve retornar 200 com projectedVersion >= 2
