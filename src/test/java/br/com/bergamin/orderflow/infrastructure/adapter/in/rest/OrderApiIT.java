package br.com.bergamin.orderflow.infrastructure.adapter.in.rest;

import br.com.bergamin.orderflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo da API contra PostgreSQL real.
 *
 * <p>Cada teste passa pela pilha inteira: HTTP -> JWT -> caso de uso -> JPA -> Postgres.
 * Nada e mockado, exceto o adquirente, que ja e simulado por design.</p>
 */
@AutoConfigureMockMvc
@DisplayName("API de pedidos (integracao)")
class OrderApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID clienteId;
    private UUID tecladoId;
    private UUID cadeiraId;
    private String tokenCliente;

    @BeforeEach
    void prepararCenario() throws Exception {
        limparBanco();

        clienteId = criarUsuario("cliente@teste.dev", "senha123", "CUSTOMER");
        criarUsuario("admin@teste.dev", "senha123", "ADMIN");

        tecladoId = criarProduto("TEC-001", "459.90", 10);
        cadeiraId = criarProduto("CAD-005", "1349.00", 2);

        tokenCliente = autenticar("cliente@teste.dev", "senha123");
    }

    private String autenticar(String email, String senha) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String corpoPedido(UUID produtoId, int quantidade) {
        return """
                {"items": [{"productId": "%s", "quantity": %d}]}
                """.formatted(produtoId, quantidade);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- autenticacao

    @Test
    @DisplayName("login com senha errada devolve 401 no formato problem+json")
    void loginInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "cliente@teste.dev", "password": "errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciais invalidas"));
    }

    @Test
    @DisplayName("sem token, criar pedido devolve 401")
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("catalogo e publico")
    void catalogoPublico() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ---------------------------------------------------------------- criacao

    @Test
    @DisplayName("cria o pedido, calcula o total no servidor e baixa o estoque")
    void criaPedido() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(919.80))
                .andExpect(jsonPath("$.customerId").value(clienteId.toString()))
                .andReturn();

        assertThat(json(result).get("items").get(0).get("subtotal").asDouble()).isEqualTo(919.80);
        assertThat(estoqueDe(tecladoId)).isEqualTo(8);
        assertThat(contarEventosNaOutbox("Order.Placed")).isEqualTo(1);
    }

    @Test
    @DisplayName("o evento guarda o trace da requisicao que o originou")
    void gravaTraceDaRequisicaoNaOutbox() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 1)))
                .andExpect(status().isCreated());

        // Preenchido a partir do span da requisicao HTTP. E o que permite, mais tarde,
        // achar todos os eventos gerados por uma requisicao especifica.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trace_id FROM outbox_event WHERE event_type = 'Order.Placed'", String.class))
                .isNotBlank();
    }

    @Test
    @DisplayName("repetir a Idempotency-Key devolve o mesmo pedido, sem duplicar nem baixar estoque de novo")
    void idempotenciaEvitaPedidoDuplicado() throws Exception {
        String chave = "compra-" + UUID.randomUUID();

        MvcResult primeira = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 3)))
                .andExpect(status().isCreated())
                .andReturn();

        // Mesma chamada de novo: e o que acontece quando a rede cai e o app do cliente repete.
        MvcResult segunda = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 3)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(segunda).get("id").asText()).isEqualTo(json(primeira).get("id").asText());
        assertThat(contarPedidos()).isEqualTo(1);
        assertThat(estoqueDe(tecladoId)).isEqualTo(7);
    }

    @Test
    @DisplayName("estoque insuficiente devolve 422 dizendo quanto ha disponivel")
    void estoqueInsuficiente() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(cadeiraId, 5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Estoque insuficiente"))
                .andExpect(jsonPath("$.sku").value("CAD-005"))
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.available").value(2));

        // A transacao foi desfeita: nada de estoque parcialmente reservado.
        assertThat(estoqueDe(cadeiraId)).isEqualTo(2);
        assertThat(contarPedidos()).isZero();
    }

    @Test
    @DisplayName("produto inexistente devolve 404")
    void produtoInexistente() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(UUID.randomUUID(), 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("quantidade invalida devolve 400 com a lista de campos")
    void validacaoDeCampos() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(tecladoId, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisicao invalida"))
                .andExpect(jsonPath("$.errors[0].message").value("quantidade minima e 1"));
    }

    // ---------------------------------------------------------------- pagamento

    @Test
    @DisplayName("pagamento aprovado marca PAID e gera o evento Order.Paid")
    void pagamentoAprovado() throws Exception {
        String pedidoId = criarPedido(tecladoId, 1);

        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/payment")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken": "tok_visa_4242"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentTransactionId").isNotEmpty());

        assertThat(estoqueDe(tecladoId)).isEqualTo(9);
        assertThat(contarEventosNaOutbox("Order.Paid")).isEqualTo(1);
    }

    @Test
    @DisplayName("pagamento recusado marca PAYMENT_FAILED e devolve o estoque")
    void pagamentoRecusado() throws Exception {
        String pedidoId = criarPedido(tecladoId, 4);
        assertThat(estoqueDe(tecladoId)).isEqualTo(6);

        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/payment")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken": "tok_decline"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.statusReason").value("saldo insuficiente"));

        // O estoque nao pode ficar preso em um pedido que nunca sera pago.
        assertThat(estoqueDe(tecladoId)).isEqualTo(10);
    }

    @Test
    @DisplayName("pagar duas vezes o mesmo pedido devolve 409")
    void naoPagaDuasVezes() throws Exception {
        String pedidoId = criarPedido(tecladoId, 1);
        pagar(pedidoId, "tok_visa_4242");

        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/payment")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken": "tok_visa_4242"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PAID"));
    }

    // ---------------------------------------------------------------- cancelamento

    @Test
    @DisplayName("cancelamento devolve o estoque")
    void cancelaPedido() throws Exception {
        String pedidoId = criarPedido(cadeiraId, 2);
        assertThat(estoqueDe(cadeiraId)).isZero();

        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/cancellation")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "achei mais barato"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(estoqueDe(cadeiraId)).isEqualTo(2);
    }

    @Test
    @DisplayName("nao cancela pedido ja pago")
    void naoCancelaPago() throws Exception {
        String pedidoId = criarPedido(tecladoId, 1);
        pagar(pedidoId, "tok_visa_4242");

        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/cancellation")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- visibilidade

    @Test
    @DisplayName("um cliente nao enxerga o pedido de outro")
    void naoEnxergaPedidoDeOutro() throws Exception {
        String pedidoId = criarPedido(tecladoId, 1);

        criarUsuario("intruso@teste.dev", "senha123", "CUSTOMER");
        String tokenIntruso = autenticar("intruso@teste.dev", "senha123");

        mockMvc.perform(get("/api/v1/orders/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("ADMIN enxerga os pedidos de todos os clientes")
    void adminEnxergaTudo() throws Exception {
        criarPedido(tecladoId, 1);
        String tokenAdmin = autenticar("admin@teste.dev", "senha123");

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("cadastrar produto exige perfil ADMIN")
    void cadastroDeProdutoExigeAdmin() throws Exception {
        String corpo = """
                {"sku": "NOVO-1", "name": "Webcam 4K", "price": 799.90, "stockQuantity": 5}
                """;

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + autenticar("admin@teste.dev", "senha123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("NOVO-1"));
    }

    // ---------------------------------------------------------------- auxiliares

    private String criarPedido(UUID produtoId, int quantidade) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPedido(produtoId, quantidade)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private void pagar(String pedidoId, String token) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + pedidoId + "/payment")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentToken": "%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }
}
