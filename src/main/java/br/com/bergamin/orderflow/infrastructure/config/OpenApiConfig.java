package br.com.bergamin.orderflow.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacao OpenAPI, servida em {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow API")
                        .version("1.0.0")
                        .description("""
                                API de pedidos com arquitetura hexagonal, idempotencia de criacao,
                                reserva de estoque com lock otimista e publicacao de eventos via
                                Transactional Outbox + Kafka.

                                **Como testar:** faca login em `POST /api/v1/auth/login` com
                                `cliente@orderflow.dev` / `cliente123`, clique em *Authorize* e cole o token.
                                """)
                        .contact(new Contact()
                                .name("Bruno Alves Bergamin")
                                .url("https://www.linkedin.com/in/bruno-alves-bergamin-6b711a347"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token obtido em POST /api/v1/auth/login")));
    }
}
