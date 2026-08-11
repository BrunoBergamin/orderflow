package br.com.bergamin.orderflow.infrastructure.config;

import br.com.bergamin.orderflow.infrastructure.ratelimit.RateLimitFilter;
import br.com.bergamin.orderflow.infrastructure.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.net.URI;

/**
 * Configuracao de seguranca da API.
 *
 * <p>CSRF fica desligado porque a API e stateless e autentica por cabecalho
 * {@code Authorization}, nao por cookie de sessao. Sem cookie automatico, nao existe o
 * ataque que o token CSRF previne. Se um dia a autenticacao virar cookie, isso precisa
 * voltar.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Catalogo e publico: da para navegar antes de criar conta.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        // Cadastrar produto e operacao de back-office.
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) ->
                                writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "Nao autenticado",
                                        "Envie um token valido no cabecalho Authorization: Bearer <token>.",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN,
                                        "Acesso negado",
                                        "Seu usuario nao tem permissao para esta operacao.",
                                        request.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Depois da autenticacao: e o que permite limitar pedido por cliente e
                // login por IP, cada um com a chave que faz sentido.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }

    /**
     * Erros de autenticacao respondem no mesmo formato RFC 7807 do resto da API.
     *
     * <p>Sem isto, 401 e 403 sairiam com o HTML padrao do container e quebrariam o cliente,
     * que espera JSON em todas as respostas.</p>
     */
    private void writeProblem(HttpServletResponse response, ObjectMapper objectMapper,
                              HttpStatus status, String title, String detail, String path) throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://orderflow.dev/errors/" + status.value()));
        problem.setInstance(URI.create(path));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /**
     * Impede o registro automatico dos filtros pelo Boot.
     *
     * <p>Um {@code Filter} anotado com {@code @Component} e registrado pelo Spring Boot no
     * container servlet <b>e</b> adicionado por nos na cadeia de seguranca, ou seja, roda
     * duas vezes por requisicao. No filtro JWT isso passaria despercebido, porque
     * autenticar duas vezes da no mesmo. No de limite de vazao seria um bug de verdade:
     * cada chamada consumiria duas fichas e o limite efetivo seria metade do configurado.</p>
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        return disableAutoRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> disableRateLimitFilterAutoRegistration(
            RateLimitFilter filter) {
        return disableAutoRegistration(filter);
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disableAutoRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
