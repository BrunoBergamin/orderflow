package br.com.bergamin.orderflow.infrastructure.ratelimit;

import br.com.bergamin.orderflow.infrastructure.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * Aplica o limite de vazao nas duas rotas que precisam.
 *
 * <p>Roda <b>depois</b> da autenticacao, o que permite usar chaves diferentes conforme a
 * rota: login e limitado por IP (nao ha usuario ainda, e o alvo e justamente quem tenta
 * adivinhar senha), criacao de pedido e limitada por cliente autenticado. Limitar pedido por
 * IP puniria uma empresa inteira atras do mesmo NAT.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ORDERS_PATH = "/api/v1/orders";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || resolvePolicy(request) == null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Policy policy = resolvePolicy(request);
        RateLimiter.Decision decision = rateLimiter.tryConsume(policy.key(), policy.limit());

        // Cabecalhos de cota em toda resposta, nao so quando bloqueia: assim o cliente
        // consegue se conter antes de levar 429.
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit().getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("limite de vazao atingido em {} para a chave {}", request.getRequestURI(), policy.key());
        writeTooManyRequests(response, request.getRequestURI(), decision.retryAfterSeconds());
    }

    /** @return a politica aplicavel, ou {@code null} quando a rota nao tem limite */
    private Policy resolvePolicy(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(path)) {
            return new Policy("login:" + clientIp(request), properties.getLogin());
        }
        if (HttpMethod.POST.matches(request.getMethod()) && ORDERS_PATH.equals(path)) {
            String customer = authenticatedCustomer();
            // Sem autenticacao a requisicao sera recusada adiante de qualquer forma;
            // deixar passar aqui evita gastar ficha de quem nem chegou a ser cliente.
            return customer == null ? null : new Policy("orders:" + customer, properties.getOrders());
        }
        return null;
    }

    private String authenticatedCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id().toString();
        }
        return null;
    }

    /**
     * IP do cliente, respeitando {@code X-Forwarded-For} quando ha proxy na frente.
     *
     * <p>O primeiro endereco da lista e o cliente original; os demais sao os proxies do
     * caminho. Vale lembrar que esse cabecalho e falsificavel se a aplicacao estiver exposta
     * direto -- em producao ele so deve ser considerado quando ha um proxy confiavel a
     * frente.</p>
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, String path, long retryAfterSeconds)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisicoes em pouco tempo. Tente novamente em %d segundo(s)."
                        .formatted(retryAfterSeconds));
        problem.setTitle("Limite de requisicoes excedido");
        problem.setType(URI.create("https://orderflow.dev/errors/limite-de-requisicoes"));
        problem.setInstance(URI.create(path));
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private record Policy(String key, RateLimitProperties.Limit limit) {
    }
}
