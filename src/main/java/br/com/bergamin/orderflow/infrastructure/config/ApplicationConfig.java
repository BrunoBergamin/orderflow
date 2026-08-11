package br.com.bergamin.orderflow.infrastructure.config;

import br.com.bergamin.orderflow.infrastructure.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class ApplicationConfig {

    /**
     * Relogio injetavel.
     *
     * <p>Nenhum ponto do dominio ou dos casos de uso chama {@code Instant.now()} direto.
     * Com o {@code Clock} injetado, um teste consegue congelar o tempo
     * ({@code Clock.fixed(...)}) e afirmar exatamente qual data foi gravada, em vez de
     * comparar com tolerancia.</p>
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
