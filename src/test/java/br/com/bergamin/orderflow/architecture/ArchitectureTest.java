package br.com.bergamin.orderflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A arquitetura hexagonal virou teste automatizado.
 *
 * <p>Diagrama em README envelhece; regra que roda no CI, nao. Se alguem importar
 * {@code OrderJpaEntity} dentro do dominio ou chamar um repositorio direto do controller,
 * o build quebra com o nome exato da classe infratora -- em vez de virar divida tecnica
 * descoberta seis meses depois.</p>
 */
@AnalyzeClasses(
        packages = "br.com.bergamin.orderflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMINIO = "..domain..";
    private static final String APLICACAO = "..application..";
    private static final String INFRAESTRUTURA = "..infrastructure..";

    @ArchTest
    static final ArchRule dependencias_apontam_para_dentro = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Dominio").definedBy(DOMINIO)
            .layer("Aplicacao").definedBy(APLICACAO)
            .layer("Infraestrutura").definedBy(INFRAESTRUTURA)

            // Ninguem depende da infraestrutura: ela e a ponta descartavel da arquitetura.
            .whereLayer("Infraestrutura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacao").mayOnlyBeAccessedByLayers("Infraestrutura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacao", "Infraestrutura")
            .as("as dependencias devem apontar para o dominio");

    @ArchTest
    static final ArchRule dominio_nao_conhece_framework = noClasses()
            .that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.hibernate..",
                    "com.fasterxml..",
                    "io.swagger..")
            .as("o dominio deve ser Java puro, sem Spring, JPA ou Jackson")
            .because("regra de negocio testavel em milissegundos nao pode depender de container");

    @ArchTest
    static final ArchRule dominio_nao_conhece_as_camadas_de_fora = noClasses()
            .that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(APLICACAO, INFRAESTRUTURA);

    @ArchTest
    static final ArchRule aplicacao_nao_conhece_infraestrutura = noClasses()
            .that().resideInAPackage(APLICACAO)
            .should().dependOnClassesThat().resideInAPackage(INFRAESTRUTURA)
            .because("os casos de uso falam com portas, nao com JPA, Kafka ou HTTP");

    @ArchTest
    static final ArchRule portas_sao_interfaces = classes()
            .that().resideInAnyPackage("..application.port.in..", "..application.port.out..")
            .and().areTopLevelClasses()
            .should().beInterfaces()
            .as("portas devem ser interfaces");

    @ArchTest
    static final ArchRule entidades_jpa_so_na_infraestrutura = noClasses()
            .that().resideOutsideOfPackage(INFRAESTRUTURA)
            .should().beAnnotatedWith(Entity.class)
            .as("mapeamento JPA e detalhe de persistencia");

    @ArchTest
    static final ArchRule controllers_nao_acessam_repositorios = noClasses()
            .that().resideInAPackage("..adapter.in.rest..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..")
            .because("o controller conversa com casos de uso, nunca direto com o banco");

    @ArchTest
    static final ArchRule sem_dependencias_circulares = slices()
            .matching("br.com.bergamin.orderflow.(*)..")
            .should().beFreeOfCycles();
}
