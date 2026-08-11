package br.com.bergamin.orderflow.application;

import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que prova a regra mais cara de errar: nao vender o que nao existe.
 *
 * <p>Vinte compradores disputam 5 unidades ao mesmo tempo. Sem lock otimista, todos leriam
 * "estoque = 5", todos gravariam "estoque = 4", e a loja venderia 20 unidades de um produto
 * com 5. O bug que so aparece na Black Friday e some quando voce tenta reproduzir.</p>
 *
 * <p>O criterio de sucesso nao e "exatamente 5 pedidos": sob concorrencia real, transacoes
 * que perdem o lock sao rejeitadas com 409 e o cliente repete. O que precisa valer sempre e
 * o invariante: <b>estoque final = estoque inicial - pedidos criados</b>, e nunca negativo.</p>
 */
@DisplayName("Concorrencia no estoque (integracao)")
class ConcurrentStockIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentStockIT.class);

    private static final int ESTOQUE_INICIAL = 5;
    private static final int COMPRADORES = 20;

    @Autowired
    private PlaceOrderUseCase placeOrder;

    @Test
    @DisplayName("20 compradores simultaneos para 5 unidades nunca geram estoque negativo")
    void naoVendeEstoqueInexistente() throws Exception {
        limparBanco();
        UUID cliente = criarUsuario("comprador@teste.dev", "senha123", "CUSTOMER");
        UUID produto = criarProduto("LIMITADO-1", "199.90", ESTOQUE_INICIAL);

        ExecutorService executor = Executors.newFixedThreadPool(COMPRADORES);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> tentativas = new ArrayList<>();

        for (int i = 0; i < COMPRADORES; i++) {
            tentativas.add(executor.submit(() -> {
                largada.await();
                try {
                    placeOrder.place(new PlaceOrderUseCase.Command(
                            cliente,
                            List.of(new PlaceOrderUseCase.Command.Line(produto, 1)),
                            null));
                    return true;
                } catch (RuntimeException e) {
                    // Estoque acabou ou o lock otimista foi perdido: ambos sao recusas legitimas.
                    return false;
                }
            }));
        }

        largada.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        long compras = 0;
        for (Future<Boolean> tentativa : tentativas) {
            if (tentativa.get()) {
                compras++;
            }
        }

        int estoqueFinal = estoqueDe(produto);
        log.info("{} de {} compradores levaram a peca; estoque final = {}", compras, COMPRADORES, estoqueFinal);

        assertThat(compras)
                .as("nenhuma venda alem do estoque disponivel")
                .isBetween(1L, (long) ESTOQUE_INICIAL);
        assertThat(estoqueFinal)
                .as("estoque nunca fica negativo")
                .isNotNegative()
                .isEqualTo(ESTOQUE_INICIAL - (int) compras);
        assertThat(contarPedidos())
                .as("cada pedido criado consumiu exatamente uma unidade")
                .isEqualTo(compras);
    }
}
