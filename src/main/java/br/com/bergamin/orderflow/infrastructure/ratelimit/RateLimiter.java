package br.com.bergamin.orderflow.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token bucket por chave.
 *
 * <p>Escolhi balde de fichas em vez de contador por janela fixa porque a janela fixa deixa
 * passar o dobro do limite na virada: com 30 por minuto, alguem manda 30 no segundo 59 e
 * mais 30 no segundo 61. O balde recarrega de forma continua e nao tem essa borda.</p>
 *
 * <p><b>Limitacao consciente:</b> os baldes vivem na memoria desta instancia. Com varias
 * replicas, cada uma aplica o proprio limite e o teto efetivo e multiplicado pelo numero de
 * instancias. Para limite global seria preciso mover o estado para o Redis. O Bucket4j
 * suporta, e a troca seria so a implementacao desta classe. Para o objetivo aqui, que e
 * conter forca bruta e abuso obvio, o limite por instancia ja resolve.</p>
 */
@Component
public class RateLimiter {

    private final Cache<String, Bucket> buckets;

    public RateLimiter() {
        this.buckets = Caffeine.newBuilder()
                // Expira por acesso: quem parou de chamar libera a entrada. Sem isso o mapa
                // acumularia uma entrada por IP para sempre.
                .expireAfterAccess(Duration.ofMinutes(15))
                .maximumSize(100_000)
                .build();
    }

    /**
     * Tenta consumir uma ficha.
     *
     * @return resultado com a permissao e, se negada, quanto falta para liberar
     */
    public Decision tryConsume(String key, RateLimitProperties.Limit limit) {
        Bucket bucket = buckets.get(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return Decision.allowed(probe.getRemainingTokens());
        }
        long secondsToWait = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        return Decision.denied(secondsToWait);
    }

    private Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.getCapacity())
                        .refillGreedy(limit.getCapacity(), limit.getPeriod())
                        .build())
                .build();
    }

    public record Decision(boolean allowed, long remainingTokens, long retryAfterSeconds) {

        static Decision allowed(long remaining) {
            return new Decision(true, remaining, 0);
        }

        static Decision denied(long retryAfterSeconds) {
            return new Decision(false, 0, retryAfterSeconds);
        }
    }
}
