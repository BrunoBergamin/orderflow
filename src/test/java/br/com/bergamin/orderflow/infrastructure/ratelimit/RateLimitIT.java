package br.com.bergamin.orderflow.infrastructure.ratelimit;

import br.com.bergamin.orderflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limite de vazao nas duas rotas sensiveis.
 *
 * <p>Os limites sao reduzidos aqui para que o teste caiba em poucas chamadas. O que se
 * verifica e o comportamento, nao o numero configurado em producao.</p>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "orderflow.rate-limit.enabled=true",
        "orderflow.rate-limit.login.capacity=3",
        "orderflow.rate-limit.login.period=1m",
        "orderflow.rate-limit.orders.capacity=2",
        "orderflow.rate-limit.orders.period=1m"
})
@DisplayName("Limite de vazao (integracao)")
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID produtoId;

    /**
     * IP proprio por teste, tirado de um contador.
     *
     * <p>Os baldes vivem em um cache compartilhado pelo contexto, entao sem isso um teste
     * gastaria a cota do seguinte. Usar {@code X-Forwarded-For} isola cada um e, de quebra,
     * exercita o tratamento do cabecalho de proxy.</p>
     *
     * <p>Contador, e nao sorteio: a primeira versao usava um numero aleatorio entre 250
     * valores e dois testes acabavam sorteando o mesmo IP de vez em quando. Passava na
     * maquina e quebrava no CI. Teste instavel e pior do que teste nenhum, porque ensina
     * o time a ignorar build vermelho.</p>
     */
    private static final AtomicInteger CONTADOR_DE_IP = new AtomicInteger();

    private String ipDoTeste;

    @BeforeEach
    void prepararCenario() {
        limparBanco();
        criarUsuario("cliente@teste.dev", "senha123", "CUSTOMER");
        produtoId = criarProduto("TEC-001", "100.00", 500);

        int sequencial = CONTADOR_DE_IP.incrementAndGet();
        ipDoTeste = "10.%d.%d.%d".formatted(sequencial / 65536 % 256, sequencial / 256 % 256, sequencial % 256);
    }

    @Test
    @DisplayName("tentativas de login sao limitadas por IP")
    void limitaTentativasDeLogin() throws Exception {
        // Tres tentativas com senha errada passam pelo limite (e falham por credencial).
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            mockMvc.perform(loginCom("errada"))
                    .andExpect(status().isUnauthorized());
        }

        // A quarta nem chega a consultar o banco: e barrada antes.
        mockMvc.perform(loginCom("errada"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Limite de requisicoes excedido"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    @DisplayName("a senha correta tambem e barrada depois do limite")
    void limiteNaoDependeDoResultado() throws Exception {
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            mockMvc.perform(loginCom("errada")).andExpect(status().isUnauthorized());
        }

        // Se acertar a senha liberasse, o limite nao serviria para nada: bastaria continuar
        // tentando ate acertar.
        mockMvc.perform(loginCom("senha123")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("criacao de pedidos e limitada por cliente, nao por IP")
    void limitaPedidosPorCliente() throws Exception {
        String token = autenticar();

        for (int pedido = 1; pedido <= 2; pedido++) {
            mockMvc.perform(criarPedido(token)).andExpect(status().isCreated());
        }

        mockMvc.perform(criarPedido(token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        // Outro cliente, mesmo IP: tem a propria cota. Limitar por IP puniria uma empresa
        // inteira atras do mesmo NAT.
        criarUsuario("outro@teste.dev", "senha123", "CUSTOMER");
        String tokenDoOutro = autenticarComo("outro@teste.dev");
        mockMvc.perform(criarPedido(tokenDoOutro)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a resposta informa quanto ainda resta da cota")
    void informaCotaRestante() throws Exception {
        String token = autenticar();

        mockMvc.perform(criarPedido(token))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
    }

    @Test
    @DisplayName("consultas nao consomem cota")
    void consultasNaoSaoLimitadas() throws Exception {
        String token = autenticar();

        // O limite protege escrita; leitura pode ser chamada a vontade.
        for (int consulta = 1; consulta <= 10; consulta++) {
            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Forwarded-For", ipDoTeste))
                    .andExpect(status().isOk());
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private MockHttpServletRequestBuilder loginCom(String senha) {
        return post("/api/v1/auth/login")
                .header("X-Forwarded-For", ipDoTeste)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "cliente@teste.dev", "password": "%s"}
                        """.formatted(senha));
    }

    private MockHttpServletRequestBuilder criarPedido(String token) {
        return post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", ipDoTeste)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items": [{"productId": "%s", "quantity": 1}]}
                        """.formatted(produtoId));
    }

    private String autenticar() throws Exception {
        return autenticarComo("cliente@teste.dev");
    }

    private String autenticarComo(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ipDoTeste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "senha123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
