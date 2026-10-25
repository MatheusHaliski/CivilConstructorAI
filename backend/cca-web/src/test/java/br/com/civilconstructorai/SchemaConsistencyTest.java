package br.com.civilconstructorai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code backend/schema.sql} é a concatenação exata das migrações Flyway (V1..Vn em ordem numérica).
 * Regenerar com {@code scripts/gerar_schema.py}. Cada migração nova começa com uma linha em branco.
 */
class SchemaConsistencyTest {

    private static final Path MIGRACOES = Path.of("..", "cca-infrastructure", "src", "main", "resources", "db", "migration");
    private static final Path SCHEMA = Path.of("..", "schema.sql");
    private static final Pattern VERSAO = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void schemaSqlEhConcatenacaoExataDasMigracoes() throws IOException {
        List<Path> arquivos;
        try (var s = Files.list(MIGRACOES)) {
            arquivos = s.filter(p -> VERSAO.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(SchemaConsistencyTest::numero))
                    .toList();
        }
        assertThat(arquivos).isNotEmpty();
        var sb = new StringBuilder();
        for (var p : arquivos) {
            var conteudo = Files.readString(p, StandardCharsets.UTF_8);
            assertThat(conteudo).as("%s deve começar com linha em branco", p.getFileName()).startsWith("\n");
            sb.append("-- ").append(p.getFileName()).append('\n').append(conteudo);
            if (!conteudo.endsWith("\n")) {
                sb.append('\n');
            }
        }
        assertThat(Files.exists(SCHEMA)).as("backend/schema.sql existe (gere com scripts/gerar_schema.py)").isTrue();
        assertThat(Files.readString(SCHEMA, StandardCharsets.UTF_8)).isEqualTo(sb.toString());
    }

    @Test
    void numerosDeVersaoSaoUnicosEContinuos() throws IOException {
        List<Integer> numeros;
        try (var s = Files.list(MIGRACOES)) {
            numeros = s.map(p -> p.getFileName().toString()).filter(n -> VERSAO.matcher(n).matches())
                    .map(n -> Integer.parseInt(VERSAO.matcher(n).results().findFirst().orElseThrow().group(1)))
                    .sorted().toList();
        }
        for (int i = 0; i < numeros.size(); i++) {
            assertThat(numeros.get(i)).as("migrações contínuas a partir de V1").isEqualTo(i + 1);
        }
    }

    private static int numero(Path p) {
        var m = VERSAO.matcher(p.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }
}
