package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.lgpd.Portas.HashProtegido;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Monta {@code export.json} (schema {@code cca-export/v1}) com todas as categorias do titular e empacota em ZIP.
 * Dados de <strong>outras</strong> pessoas viram {@code pessoa:<pseudônimo>} (RF25 RN-03).
 */
@Component
public class ExportadorTitular {

    public static final String SCHEMA = "cca-export/v1";
    public static final List<String> CAMPOS_OBRIGATORIOS = List.of("schema", "geradoEm", "titular", "categorias");

    private final ContaLgpdDao contas;
    private final ConsentimentoService consentimentos;
    private final DocumentosLegaisService documentos;
    private final Repositorios.PedidoTitularRepository pedidos;
    private final HashProtegido hash;
    private final ObjectMapper mapper;

    public ExportadorTitular(ContaLgpdDao contas, ConsentimentoService consentimentos, DocumentosLegaisService documentos,
                             Repositorios.PedidoTitularRepository pedidos, HashProtegido hash, ObjectMapper mapper) {
        this.contas = contas;
        this.consentimentos = consentimentos;
        this.documentos = documentos;
        this.pedidos = pedidos;
        this.hash = hash;
        this.mapper = mapper;
    }

    public Map<String, Object> coletar(UUID contaId, Instant agora) {
        var c = contas.exigir(contaId);
        var raiz = new LinkedHashMap<String, Object>();
        raiz.put("schema", SCHEMA);
        raiz.put("geradoEm", agora.toString());
        raiz.put("titular", Map.of("id", c.id().toString(), "nome", c.nome(), "email", c.email()));
        var categorias = new LinkedHashMap<String, Object>();
        categorias.put("conta", mapa("tipo", c.tipo(), "estado", c.estado().name(), "mfaAtivo", c.mfaAtivo(), "criadoEm", c.criadoEm().toString(),
                "eliminacaoPrevistaEm", c.eliminacaoPrevistaEm() == null ? null : c.eliminacaoPrevistaEm().toString()));
        categorias.put("perfilProfissional", mapa("registroProfissional", c.registroProfissional(), "registroUf", c.registroUf(), "seloTipo", c.seloTipo(), "seloEstado", c.seloEstado()));
        categorias.put("documentosAceitos", documentos.versoesAceitas(contaId).entrySet().stream().collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey().name(), e.getValue()), Map::putAll));
        categorias.put("consentimentos", consentimentos.listar(contaId).stream().map(i -> mapa("finalidade", i.finalidade().name(), "estado", i.estado().name(), "versaoTexto", i.versaoTexto())).toList());
        categorias.put("historicoConsentimentos", consentimentos.historico(contaId).stream().map(e -> mapa("finalidade", e.getFinalidade().name(), "acao", e.getAcao().name(), "versaoTexto", e.getVersaoTexto(), "ocorridoEm", e.getOcorridoEm().toString())).toList());
        categorias.put("vinculos", contas.vinculos(contaId).stream().map(v -> mapa("empresa", v.get("razao_social"), "obra", v.get("obra"), "papel", v.get("papel"), "desde", String.valueOf(v.get("criado_em")))).toList());
        categorias.put("pedidos", pedidos.findByContaIdOrderByCriadoEmDesc(contaId).stream().map(p -> mapa("tipo", p.getTipo().name(), "categoria", p.getCategoria(), "estado", p.getEstado().name(), "prazoEm", p.getPrazoEm().toString(), "resposta", p.getResposta())).toList());
        categorias.put("preferencias", contas.preferencias(contaId));
        categorias.put("operacoes", contas.operacoesDoAutor(contaId).stream().map(o -> {
            var m = mapa("tipo", o.get("tipo"), "entidade", o.get("entidade"), "ocorridoEm", String.valueOf(o.get("ocorrido_em")));
            // ids de outras pessoas nunca saem em claro
            if (o.get("entidade_id") != null && "conta".equals(o.get("entidade")) && !contaId.toString().equals(String.valueOf(o.get("entidade_id")))) {
                m.put("referencia", "pessoa:" + hash.hash("export", String.valueOf(o.get("entidade_id"))));
            }
            return m;
        }).toList());
        raiz.put("categorias", categorias);
        return raiz;
    }

    public byte[] zip(Map<String, Object> export) {
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entrada(zip, "export.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(export));
            entrada(zip, "LEIA-ME.txt", """
                    Exportação de dados pessoais · CivilConstructorAI (LGPD, Art. 18, V)
                    export.json segue o schema cca-export/v1. Dados de outras pessoas aparecem como pessoa:<pseudônimo>.
                    Este arquivo veio dentro de um pacote cifrado (.zip.cca, AES-256-GCM, chave PBKDF2 da senha exibida uma única vez).
                    """);
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean valido(Map<String, Object> export) {
        return CAMPOS_OBRIGATORIOS.stream().allMatch(export::containsKey) && SCHEMA.equals(export.get("schema"))
                && export.get("categorias") instanceof Map<?, ?> cat && cat.containsKey("conta") && cat.containsKey("consentimentos");
    }

    private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Map<String, Object> mapa(Object... kv) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        var limpos = new ArrayList<String>();
        m.forEach((k, v) -> { if (v == null) limpos.add(k); });
        limpos.forEach(m::remove);
        return m;
    }
}
