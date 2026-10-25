package br.com.civilconstructorai.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Mesmos containers do {@link AbstractIT}, mas com a aplicação de teste {@link AppIdentidadeTeste}. */
@SpringBootTest(classes = AppIdentidadeTeste.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class AbstractIdentidadeIT {

    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
    static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:9")).withExposedPorts(6379);

    static {
        POSTGIS.withDatabaseName("cca");
        POSTGIS.withUsername("cca_migracao");
        POSTGIS.withPassword("cca_migracao");
        POSTGIS.withInitScript("it/papel_app.sql");
    }

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
