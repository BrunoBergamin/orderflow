package br.com.bergamin.orderflow.infrastructure.security;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login por e-mail e senha, devolvendo um JWT.
 *
 * <p>E-mail inexistente, senha errada e conta desativada produzem exatamente a mesma
 * resposta. Mensagens diferentes deixariam a API responder "esse e-mail existe" para quem
 * so quer descobrir quem sao os clientes -- enumeracao de usuarios.</p>
 */
@Service
public class AuthenticationService {

    private static final String GENERIC_FAILURE = "e-mail ou senha invalidos";

    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(UserJpaRepository users,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public Token login(String email, String rawPassword) {
        UserJpaEntity user = users.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException(GENERIC_FAILURE));

        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException(GENERIC_FAILURE);
        }

        AuthenticatedUser authenticated = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getName(), user.getRole());

        return new Token(jwtService.generateToken(authenticated), jwtService.expirationSeconds(), authenticated);
    }

    public record Token(String accessToken, long expiresInSeconds, AuthenticatedUser user) {
    }
}
