package br.com.bergamin.orderflow.infrastructure.security;

import br.com.bergamin.orderflow.application.port.in.FindOrderUseCase;

import java.util.UUID;

/**
 * Usuario autenticado extraido do token.
 *
 * <p>Vira o {@code principal} do Spring Security e e o unico ponto que traduz "quem esta
 * logado" para o {@code customerId} que os casos de uso entendem.</p>
 */
public record AuthenticatedUser(UUID id, String email, String name, String role) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public String authority() {
        return "ROLE_" + role;
    }

    public FindOrderUseCase.Requester asRequester() {
        return new FindOrderUseCase.Requester(id, isAdmin());
    }
}
