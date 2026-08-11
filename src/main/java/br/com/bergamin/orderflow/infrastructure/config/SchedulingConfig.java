package br.com.bergamin.orderflow.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador que move a outbox.
 *
 * <p>Fica isolado em uma classe propria para que os testes de API possam desligar o relay
 * (via {@code spring.task.scheduling.enabled=false}) e rodar sem broker, sem que isso
 * afete o resto da configuracao.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
