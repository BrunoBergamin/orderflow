package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Tabela {@code app_user}.
 *
 * <p>Fica na infraestrutura, e nao no dominio, porque autenticacao aqui e um detalhe de
 * entrega: o dominio so conhece {@code customerId}. Trocar JWT por OAuth2 amanha nao
 * encosta em nenhuma regra de pedido.</p>
 */
@Entity
@Table(name = "app_user")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    protected UserJpaEntity() {
        // exigido pelo JPA
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
