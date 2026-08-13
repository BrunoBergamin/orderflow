# ---------- Etapa 1: build ----------
# As dependencias sao baixadas antes de copiar o codigo. Assim, alterar uma classe nao
# invalida a camada de dependencias e o rebuild leva segundos em vez de minutos.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------- Etapa 2: separacao em camadas ----------
# O jar do Spring Boot e quebrado nas suas camadas internas. Dependencias mudam raramente
# e viram uma camada estavel do Docker; o codigo da aplicacao, que muda a cada commit,
# fica sozinho na ultima camada -- deploys enviam poucos KB pela rede em vez de 60 MB.
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /build/target/orderflow-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Etapa 3: imagem final ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Rodar como root dentro do container e risco desnecessario: se a aplicacao for
# comprometida, o atacante ja comeca com privilegio maximo no namespace.
RUN addgroup -S orderflow && adduser -S orderflow -G orderflow
RUN apk add --no-cache curl

COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./

USER orderflow
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# MaxRAMPercentage faz a JVM respeitar o limite de memoria do container em vez de enxergar
# a RAM do host inteiro -- causa classica de OOMKilled em Kubernetes.
#
# O `jarmode=tools` da etapa anterior produz `app.jar` + `lib/`, com o Class-Path
# no manifesto: quem inicia e o `java -jar`. O JarLauncher pertence ao formato
# antigo (`jarmode=layertools`), que explodia as classes do loader na imagem.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "app.jar"]
