# language: pt
Funcionalidade: Ciclo de vida de uma conta de crédito

  Cenário: Cliente usa uma conta de crédito do início ao pagamento parcial
    Dado que uma conta de crédito foi aberta
    E o limite de crédito da conta é "500.00"
    Quando uma compra de "100.00" é autorizada no estabelecimento "Store"
    Então eventualmente o resumo da conta deve mostrar:
      | limite de crédito | 500.00 |
      | valor autorizado  | 100.00 |
      | limite disponível | 400.00 |
      | saldo em aberto   | 0.00   |
    Quando a autorização da compra é capturada
    Então eventualmente o resumo da conta deve mostrar:
      | valor autorizado  | 0.00   |
      | limite disponível | 400.00 |
      | saldo em aberto   | 100.00 |
    Quando um pagamento de "50.00" é recebido
    Então eventualmente o resumo da conta deve mostrar:
      | valor autorizado  | 0.00   |
      | limite disponível | 450.00 |
      | saldo em aberto   | 50.00  |
