package br.com.civilconstructorai.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integração ({@code *IT}, {@code mvn verify}): PostGIS e Valkey reais em Testcontainers.
 * O Flyway migra como superusuário ({@code cca_migracao}); a aplicação conecta como {@code cca_app}, papel sem
 * privilégio de bypass — assim o RLS é exercido de verdade nos testes de isolamento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class AbstractIT {

    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.withDatabaseName("cca");
        POSTGIS.withUsername("cca_migracao");
        POSTGIS.withPassword("cca_migracao");
        POSTGIS.withInitScript("it/papel_app.sql");
    }

    static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:9")).withExposedPorts(6379);

    @BeforeAll
    static void subirContainers() {
        POSTGIS.start();
        VALKEY.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        r.add("spring.datasource.username", () -> "cca_app");
        r.add("spring.datasource.password", () -> "cca_app");
        r.add("spring.flyway.url", POSTGIS::getJdbcUrl);
        r.add("spring.flyway.user", POSTGIS::getUsername);
        r.add("spring.flyway.password", POSTGIS::getPassword);
        r.add("spring.data.redis.url", () -> "redis://" + VALKEY.getHost() + ":" + VALKEY.getMappedPort(6379));
    }
}
