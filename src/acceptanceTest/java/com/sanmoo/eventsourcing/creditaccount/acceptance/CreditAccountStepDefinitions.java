package com.sanmoo.eventsourcing.creditaccount.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

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
        context.setLastResponse(null);
        context.setLastIdempotencyKey(null);
    }

    @Dado("que uma conta de crédito foi aberta")
    public void abrirConta() {
        Map<String, Object> response = http.openAccount();
        assertThat(context.getLastResponse().getStatusCode().value()).isEqualTo(201);
        UUID accountId = UUID.fromString((String) response.get("creditAccountId"));
        context.setCreditAccountId(accountId);
        context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
    }

    @E("o limite de crédito da conta é {string}")
    public void definirLimite(String limit) {
        Map<String, Object> response = http.assignCreditLimit(context.getCreditAccountId(), limit);
        if (response != null && response.containsKey("projectedVersion")) {
            context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
        }
    }

    @Quando("uma compra de {string} é autorizada no estabelecimento {string}")
    public void autorizarCompra(String amount, String merchantName) {
        Map<String, Object> response = http.authorizePurchase(context.getCreditAccountId(), amount, merchantName);
        if (response != null && response.containsKey("authorizationId")) {
            UUID authorizationId = UUID.fromString((String) response.get("authorizationId"));
            context.setAuthorizationId(authorizationId);
        }
        if (response != null && response.containsKey("projectedVersion")) {
            context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
        }
    }

    @Quando("a autorização da compra é capturada")
    public void capturarCompra() {
        Map<String, Object> response = http.capturePurchase(context.getCreditAccountId(), context.getAuthorizationId());
        if (response != null && response.containsKey("projectedVersion")) {
            context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
        }
    }

    @Quando("um pagamento de {string} é recebido")
    public void receberPagamento(String amount) {
        Map<String, Object> response = http.receivePayment(context.getCreditAccountId(), amount);
        if (response != null && response.containsKey("projectedVersion")) {
            context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
        }
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

    @Quando("o limite de crédito é alterado para {string}")
    public void alterarLimite(String limit) {
        Map<String, Object> response = http.assignCreditLimit(context.getCreditAccountId(), limit);
        if (response != null && response.containsKey("projectedVersion")) {
            context.setLastProjectedVersion(((Number) response.get("projectedVersion")).longValue());
        }
    }

    @Quando("o limite de crédito é alterado para {string} usando a chave {string}")
    public void alterarLimiteComChave(String limit, String idempotencyKey) {
        http.assignCreditLimitWithKey(context.getCreditAccountId(), limit, idempotencyKey);
        context.setLastIdempotencyKey(idempotencyKey);
    }

    @Quando("o limite de crédito é atribuído como {string} usando a chave {string}")
    public void atribuirLimiteComChave(String limit, String idempotencyKey) {
        http.assignCreditLimitWithKey(context.getCreditAccountId(), limit, idempotencyKey);
        context.setLastIdempotencyKey(idempotencyKey);
    }

    @Então("a API deve retornar status {int} com mensagem contendo {string}")
    public void apiDeveRetornarStatus(int expectedStatus, String expectedMessageSubstring) {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(expectedStatus);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = last.getBody();
        assertThat(body).isNotNull();
        if (expectedStatus >= 400) {
            String errorMessage = (String) body.get("error");
            assertThat(errorMessage).contains(expectedMessageSubstring);
        } else {
            assertThat(body).containsKey(expectedMessageSubstring);
        }
    }

    @Então("a API deve retornar 202 com requiredVersion {long}")
    public void apiDeveRetornar202ComRequiredVersion(long expectedRequiredVersion) {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(202);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = last.getBody();
        assertThat(body).isNotNull();
        Number required = (Number) body.get("requiredVersion");
        assertThat(required).isNotNull();
        assertThat(required.longValue()).isEqualTo(expectedRequiredVersion);
    }

    @Quando("eu consulto a conta com minVersion {string}")
    public void consultarContaComMinVersion(String minVersion) {
        http.getSummaryRaw(context.getCreditAccountId(), Long.parseLong(minVersion));
    }

    @Quando("eu consulto o resumo da conta")
    public void consultarResumo() {
        http.getSummaryRaw(context.getCreditAccountId(), null);
    }

    @Então("o resultado deve ser o mesmo")
    public void resultadoDeveSerOMesmo() {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(200);
    }

    @Então("a API deve retornar 409 de conflito de idempotência")
    public void apiDeveRetornar409() {
        ResponseEntity<Map> last = context.getLastResponse();
        assertThat(last).isNotNull();
        assertThat(last.getStatusCode().value()).isEqualTo(409);
    }

    @Então("eventualmente a API deve retornar 200 com projectedVersion >= {long}")
    public void eventualmenteApiDeveRetornar200ComProjectedVersionMaiorOuIgual(long expectedVersion) {
        Map<String, Object> summary = http.awaitProjectedSummary(context.getCreditAccountId(), expectedVersion);
        assertThat(summary).isNotNull();
        long projected = ((Number) summary.get("projectedVersion")).longValue();
        assertThat(projected).isGreaterThanOrEqualTo(expectedVersion);
    }
}
