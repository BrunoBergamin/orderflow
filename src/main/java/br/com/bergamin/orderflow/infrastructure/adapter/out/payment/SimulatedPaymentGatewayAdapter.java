package br.com.bergamin.orderflow.infrastructure.adapter.out.payment;

import br.com.bergamin.orderflow.application.port.out.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adquirente simulado.
 *
 * <p>Nao existe integracao real com Stripe/Pagar.me aqui de proposito: o objetivo do projeto
 * e mostrar o fluxo transacional em volta do pagamento, e uma chave de sandbox de terceiro
 * so tornaria os testes nao reproduziveis. O contrato ({@link PaymentGatewayPort}) e o que
 * importa. Trocar por um cliente HTTP real e escrever outra classe nesta pasta, sem tocar
 * em caso de uso nem em dominio.</p>
 *
 * <p>O resultado e deterministico pelo token, o que deixa os testes de aprovacao e de recusa
 * escritos sem mock de rede:</p>
 * <ul>
 *   <li>{@code tok_decline} -> recusado por saldo insuficiente</li>
 *   <li>{@code tok_fraud} -> recusado por suspeita de fraude</li>
 *   <li>qualquer outro token -> aprovado</li>
 * </ul>
 */
@Component
public class SimulatedPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGatewayAdapter.class);

    private static final String DECLINE_TOKEN = "tok_decline";
    private static final String FRAUD_TOKEN = "tok_fraud";

    @Override
    public PaymentResult charge(PaymentRequest request) {
        log.debug("cobrando {} do pedido {}", request.amount(), request.orderId());

        return switch (request.paymentToken()) {
            case DECLINE_TOKEN -> PaymentResult.declined("saldo insuficiente");
            case FRAUD_TOKEN -> PaymentResult.declined("transacao suspeita de fraude");
            default -> PaymentResult.approved("tx_" + UUID.randomUUID());
        };
    }
}
