package br.com.bergamin.orderflow.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emissao e validacao dos tokens JWT (HS256).
 *
 * <p>A chave vem de configuracao e nunca do codigo -- em producao entra por variavel de
 * ambiente. O {@code application.yml} traz um valor apenas para desenvolvimento local, e a
 * aplicacao nao sobe se ele nao for substituido em outro perfil.</p>
 */
@Component
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;

    public JwtService(@Value("${orderflow.security.jwt.secret}") String secret,
                      @Value("${orderflow.security.jwt.expiration-minutes:120}") long expirationMinutes,
                      @Value("${orderflow.security.jwt.issuer:orderflow}") String issuer,
                      Clock clock) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "orderflow.security.jwt.secret precisa de no minimo 32 caracteres para HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.issuer = issuer;
        this.clock = clock;
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_NAME, user.name())
                .claim(CLAIM_ROLE, user.role())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida assinatura, emissor e expiracao, devolvendo o usuario.
     *
     * @throws io.jsonwebtoken.JwtException se o token for invalido, adulterado ou expirado
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_ROLE, String.class));
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}
