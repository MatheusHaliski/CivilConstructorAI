package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.RelatorioAuditoria.Violacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laço 2 remoto: serviço Python ({@code geometria/api}, kernel CadQuery/OpenCASCADE) em
 * {@code POST /interno/auditoria/laco-geometria} e {@code /interno/auditoria/conclusao}. Ativado por
 * {@code app.geometria.remota=true}. Em falha de rede cai para o laço local (JTS) e marca aviso.
 */
@Component
@ConditionalOnProperty(name = "app.geometria.remota", havingValue = "true")
public class ServicoGeometriaHttp implements ServicoGeometria {

    private final RestClient http;
    private final EstadoJson json;
    private final ServicoGeometriaLocal local = new ServicoGeometriaLocal();

    public ServicoGeometriaHttp(@Value("${app.geometria.url}") String url, EstadoJson json) {
        this.http = RestClient.builder().baseUrl(url).build();
        this.json = json;
    }

    @Override
    public Resultado lacoGeometria(ModeloBim rascunho, DiffModelo diff) {
        try {
            var corpo = Map.of("estado", json.mapa(json.estado(rascunho.estado())),
                    "diff", Map.of("criados", diff.criados.stream().map(json::elemento).toList(), "alterados", diff.alterados.stream().map(json::elemento).toList(), "removidos", diff.removidos));
            var resp = http.post().uri("/interno/auditoria/laco-geometria").body(corpo).retrieve().body(RespostaRemota.class);
            return new Resultado(converter(resp.violacoes()), converter(resp.avisos()));
        } catch (RuntimeException e) {
            var r = local.lacoGeometria(rascunho, diff);
            var avisos = new ArrayList<>(r.avisos());
            avisos.add(new Violacao("G-KERNEL_INDISPONIVEL", Enums.Severidade.AVISO, "RF16 laço 2", null, "Kernel geométrico indisponível; validação local aplicada.", null, e.getMessage()));
            return new Resultado(r.violacoes(), avisos);
        }
    }

    @Override
    public List<Violacao> verificarConclusao(ModeloBim modelo, UUID pavimentoId) {
        try {
            var resp = http.post().uri("/interno/auditoria/conclusao").body(Map.of("estado", json.mapa(json.estado(modelo.estado())), "pavimentoId", pavimentoId.toString())).retrieve().body(RespostaRemota.class);
            var out = converter(resp.violacoes());
            out.addAll(converter(resp.avisos()));
            return out;
        } catch (RuntimeException e) {
            return local.verificarConclusao(modelo, pavimentoId);
        }
    }

    record RespostaRemota(List<Map<String, Object>> violacoes, List<Map<String, Object>> avisos) {
    }

    private static List<Violacao> converter(List<Map<String, Object>> l) {
        var out = new ArrayList<Violacao>();
        if (l == null) {
            return out;
        }
        for (var m : l) {
            out.add(new Violacao(String.valueOf(m.get("codigo")), Enums.Severidade.valueOf(String.valueOf(m.getOrDefault("severidade", "VIOLACAO"))), String.valueOf(m.get("regra")),
                    m.get("elementoRef") == null ? null : UUID.fromString(String.valueOf(m.get("elementoRef"))), String.valueOf(m.get("mensagem")),
                    m.get("esperado") == null ? null : String.valueOf(m.get("esperado")), m.get("obtido") == null ? null : String.valueOf(m.get("obtido"))));
        }
        return out;
    }
}
