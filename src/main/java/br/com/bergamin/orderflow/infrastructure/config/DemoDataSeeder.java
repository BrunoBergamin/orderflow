package br.com.bergamin.orderflow.infrastructure.config;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.ProductJpaRepository;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Popula o banco para a demonstracao (usuarios e catalogo).
 *
 * <p>Fica fora das migrations de proposito: hash de senha e gerado em runtime pelo
 * {@link PasswordEncoder}, entao nenhum hash BCrypt fica versionado no repositorio -- e o
 * mesmo motivo pelo qual isso pode ser desligado com
 * {@code orderflow.demo-data.enabled=false} em qualquer ambiente que nao seja demo.</p>
 *
 * <p>Roda uma unica vez: se ja houver usuario cadastrado, nao faz nada.</p>
 */
@Configuration
@ConditionalOnProperty(name = "orderflow.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    @Bean
    public ApplicationRunner seedDemoData(UserJpaRepository users,
                                          ProductJpaRepository products,
                                          PasswordEncoder passwordEncoder,
                                          JdbcTemplate jdbcTemplate) {
        return args -> seed(users, products, passwordEncoder, jdbcTemplate);
    }

    @Transactional
    void seed(UserJpaRepository users, ProductJpaRepository products,
              PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        if (users.count() > 0) {
            log.debug("dados de demonstracao ja existem, nada a fazer");
            return;
        }

        insertUser(jdbcTemplate, "admin@orderflow.dev", "Administrador", "ADMIN",
                passwordEncoder.encode("admin123"));
        insertUser(jdbcTemplate, "cliente@orderflow.dev", "Cliente Demo", "CUSTOMER",
                passwordEncoder.encode("cliente123"));

        if (products.count() == 0) {
            insertProduct(jdbcTemplate, "TEC-001", "Teclado mecanico 75%", new BigDecimal("459.90"), 25);
            insertProduct(jdbcTemplate, "MOU-002", "Mouse sem fio 8000dpi", new BigDecimal("219.90"), 40);
            insertProduct(jdbcTemplate, "MON-003", "Monitor 27\" 144Hz", new BigDecimal("1899.00"), 8);
            insertProduct(jdbcTemplate, "HEA-004", "Headset com cancelamento de ruido", new BigDecimal("649.00"), 15);
            insertProduct(jdbcTemplate, "CAD-005", "Cadeira ergonomica", new BigDecimal("1349.00"), 3);
        }

        log.info("dados de demonstracao criados: admin@orderflow.dev/admin123 e cliente@orderflow.dev/cliente123");
    }

    private void insertUser(JdbcTemplate jdbcTemplate, String email, String name, String role, String passwordHash) {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, email, password_hash, name, role, enabled)
                VALUES (?, ?, ?, ?, ?, true)
                """, UUID.randomUUID(), email, passwordHash, name, role);
    }

    private void insertProduct(JdbcTemplate jdbcTemplate, String sku, String name, BigDecimal price, int stock) {
        jdbcTemplate.update("""
                INSERT INTO products (id, sku, name, price, stock_quantity, version)
                VALUES (?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID(), sku, name, price, stock);
    }
}
