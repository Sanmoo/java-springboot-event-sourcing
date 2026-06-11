package com.sanmoo.eventsourcing.creditaccount.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CreditAccountStepDefinitions {

    @Autowired
    private AcceptanceHttpClient http;

    @Autowired
    private AcceptanceTestContext context;

    @Before
    public void resetContext() {
        context.setCreditAccountId(null);
        context.setAuthorizationId(null);
        context.setLastProjectedVersion(null);
    }

    @Dado("que uma conta de crédito foi aberta")
    public void abrirConta() {
        Map<String, Object> response = http.openAccount();
        UUID accountId = UUID.fromString((String) response.get("creditAccountId"));
        context.setCreditAccountId(accountId);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @E("o limite de crédito da conta é {string}")
    public void definirLimite(String limit) {
        Map<String, Object> response = http.assignCreditLimit(context.getCreditAccountId(), limit);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("uma compra de {string} é autorizada no estabelecimento {string}")
    public void autorizarCompra(String amount, String merchantName) {
        Map<String, Object> response = http.authorizePurchase(context.getCreditAccountId(), amount, merchantName);
        UUID authorizationId = UUID.fromString((String) response.get("authorizationId"));
        context.setAuthorizationId(authorizationId);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("a autorização da compra é capturada")
    public void capturarCompra() {
        Map<String, Object> response = http.capturePurchase(context.getCreditAccountId(), context.getAuthorizationId());
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @Quando("um pagamento de {string} é recebido")
    public void receberPagamento(String amount) {
        Map<String, Object> response = http.receivePayment(context.getCreditAccountId(), amount);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    private static final Map<String, String> FIELD_MAPPING = new HashMap<>();
    static {
        FIELD_MAPPING.put("limite de crédito", "creditLimit");
        FIELD_MAPPING.put("valor autorizado", "authorizedAmount");
        FIELD_MAPPING.put("limite disponível", "availableLimit");
        FIELD_MAPPING.put("saldo em aberto", "outstandingBalance");
    }

    @Então("eventualmente o resumo da conta deve mostrar:")
    public void resumirConta(DataTable dataTable) {
        Map<String, String> expected = dataTable.asMap(String.class, String.class);
        Map<String, Object> summary = http.awaitProjectedSummary(
                context.getCreditAccountId(),
                context.getLastProjectedVersion()
        );

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String fieldPath = FIELD_MAPPING.get(entry.getKey());
            if (fieldPath == null) {
                throw new IllegalArgumentException("Unknown field: " + entry.getKey());
            }
            assertThat((String) summary.get(fieldPath))
                    .as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }
}
