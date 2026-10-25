package br.com.civilconstructorai.interop;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Leitor mínimo de IFC (STEP) para importação de referência (RF15): valida cabeçalho, extrai schema e conta
 * entidades por tipo. Não reconstrói geometria — isso é o serviço Python (IfcOpenShell).
 */
public final class LeitorIfc {

    private static final Pattern ENTIDADE = Pattern.compile("^#\\d+\\s*=\\s*([A-Z0-9_]+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern SCHEMA = Pattern.compile("FILE_SCHEMA\\(\\('([A-Z0-9_]+)'\\)\\)");

    private LeitorIfc() {
    }

    public record Resumo(String schema, int totalEntidades, Map<String, Integer> porTipo, List<String> problemas) {
        public boolean valido() {
            return problemas.isEmpty();
        }
    }

    public static Resumo ler(byte[] conteudo) {
        var texto = new String(conteudo, StandardCharsets.UTF_8);
        var problemas = new java.util.ArrayList<String>();
        if (!texto.startsWith("ISO-10303-21;")) {
            problemas.add("Arquivo não começa com ISO-10303-21.");
        }
        if (!texto.contains("END-ISO-10303-21;")) {
            problemas.add("Arquivo sem END-ISO-10303-21 (truncado?).");
        }
        var ms = SCHEMA.matcher(texto);
        var schema = ms.find() ? ms.group(1) : null;
        if (schema == null) {
            problemas.add("FILE_SCHEMA ausente.");
        } else if (!schema.startsWith("IFC")) {
            problemas.add("Schema não é IFC: " + schema);
        }
        var porTipo = new LinkedHashMap<String, Integer>();
        var m = ENTIDADE.matcher(texto);
        int total = 0;
        while (m.find()) {
            total++;
            porTipo.merge(m.group(1), 1, Integer::sum);
        }
        if (!porTipo.containsKey("IFCPROJECT")) {
            problemas.add("Sem IfcProject.");
        }
        return new Resumo(schema, total, porTipo, List.copyOf(problemas));
    }
}
