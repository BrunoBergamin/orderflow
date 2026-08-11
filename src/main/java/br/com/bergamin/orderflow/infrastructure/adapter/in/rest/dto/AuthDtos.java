package br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto;

import br.com.bergamin.orderflow.infrastructure.security.AuthenticationService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** Contratos HTTP de autenticacao. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Schema(example = "cliente@orderflow.dev")
            @NotBlank(message = "email e obrigatorio")
            @Email(message = "email invalido") String email,

            @Schema(example = "cliente123")
            @NotBlank(message = "password e obrigatorio") String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, User user) {

        public record User(UUID id, String name, String email, String role) {
        }

        public static LoginResponse from(AuthenticationService.Token token) {
            var user = token.user();
            return new LoginResponse(
                    token.accessToken(),
                    "Bearer",
                    token.expiresInSeconds(),
                    new User(user.id(), user.name(), user.email(), user.role()));
        }
    }
}
