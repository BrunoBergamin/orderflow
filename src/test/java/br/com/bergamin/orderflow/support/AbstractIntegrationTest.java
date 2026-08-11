package br.com.bergamin.orderflow.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base dos testes de integracao: PostgreSQL de verdade, via Testcontainers.
 *
 * <p>Rodar teste em H2 e testar outro banco. Coisas que este projeto usa --
 * {@code FOR UPDATE SKIP LOCKED}, indice parcial, {@code TIMESTAMPTZ}, o comportamento real
 * do lock otimista sob concorrencia -- ou nao existem no H2 ou se comportam diferente. O
 * container garante que o que passa aqui passa em producao.</p>
 *
 * <p>O container e iniciado uma unica vez para toda a suite (padrao singleton container),
 * em vez de um por classe: a suite inteira sobe um Postgres so.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderflow")
                    .withUsername("orderflow")
                    .withPassword("orderflow");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Zera o banco entre testes sem recriar o container nem reexecutar as migrations. */
    protected void limparBanco() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE order_items, orders, outbox_event, idempotency_record, products, app_user
                RESTART IDENTITY CASCADE
                """);
    }

    protected UUID criarUsuario(String email, String senha, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, password_hash, name, role, enabled)
                VALUES (?, ?, ?, ?, ?, true)
                """, id, email, passwordEncoder.encode(senha), "Usuario " + role, role);
        return id;
    }

    protected UUID criarProduto(String sku, String preco, int estoque) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO products (id, sku, name, price, stock_quantity, version)
                VALUES (?, ?, ?, ?, ?, 0)
                """, id, sku, "Produto " + sku, new BigDecimal(preco), estoque);
        return id;
    }

    protected int estoqueDe(UUID produtoId) {
        Integer estoque = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, produtoId);
        return estoque == null ? 0 : estoque;
    }

    protected long contarEventosNaOutbox(String eventType) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", Long.class, eventType);
        return total == null ? 0 : total;
    }

    protected long contarPedidos() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return total == null ? 0 : total;
    }
}
