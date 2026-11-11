package br.com.civilconstructorai.cimento;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consulta técnica sem alteração do modelo. Nenhuma resposta do LLM comprova dosagem. */
@Service
public class CimentoService {
    public enum Ambiente { INTERNO, EXTERNO, UMIDO, MARINHO }
    public enum Prioridade { GERAL, BAIXO_CALOR, RESISTENCIA_INICIAL }
    public record Pedido(UUID elementoId, Ambiente ambiente, Prioridade prioridade) { }
    public record Orientacao(List<String> cimentoIds, String origem, String mensagem, boolean liberacaoTecnica) { }
    static final String AVISO = "Orientação consultiva; sem dosagem validada e ensaios rastreáveis não há correspondência com fck nem liberação técnica. RS/BC exigem certificado do produto.";
    static final List<String> IDS;
    static {
        var ids = new ArrayList<String>();
        for (var tipo : List.of("CP_I", "CP_II_E", "CP_II_Z", "CP_II_F", "CP_III", "CP_IV"))
            for (var classe : List.of(25, 32, 40)) ids.add("CIM_" + tipo + "_" + classe);
        ids.add("CIM_CP_V_ARI");
        IDS = List.copyOf(ids);
    }
    static final String SYSTEM = """
        Você orienta seleção preliminar de ligante no Brasil. Responda somente JSON cimentoIds usando IDs do catálogo.
        CP I: comum; CP II-E/III: escória; CP II-Z/IV: pozolana; CP II-F: fíler; CP V-ARI: resistência inicial.
        Classes 25/32/40 são do cimento, nunca o fck do concreto. ARI não possui essas classes.
        Não proponha comandos, dosagem, homologação, equivalência ao fck, nem presuma RS/BC.
        Ambiente e prioridade são contexto consultivo insuficiente para impor produto.
        A resposta nunca aprova associação estrutural; exige memorial, ensaio e dosagem validados.
        """;
    private final ModeloLinguagem modelo;
    private final AcessoObra acesso;
    private final ObjectMapper mapper;
    private final boolean habilitada;
    private final DosagemCimentoService dosagens;
    public CimentoService(ModeloLinguagem modelo, AcessoObra acesso, ObjectMapper mapper,
                          @Value("${app.ia.habilitada:false}") boolean habilitada, DosagemCimentoService dosagens) {
        this.modelo = modelo; this.acesso = acesso; this.mapper = mapper; this.habilitada = habilitada; this.dosagens=dosagens;
    }
    public Orientacao recomendar(UUID obraId, Pedido pedido) {
        autorizar(obraId);
        if (pedido == null || pedido.elementoId() == null || pedido.ambiente() == null || pedido.prioridade() == null)
            throw new ExcecaoDominio.Validacao("contexto", "Informe tipo, ambiente e prioridade técnicos.");
        var tecnico=dosagens.elegiveis(obraId,pedido.elementoId());
        var permitidos=tecnico.cimentoIds().stream().filter(IDS::contains).toList();
        if(permitidos.isEmpty()) return new Orientacao(List.of(),"ORIENTACAO_TECNICA_SEM_IA","Sem dosagem documentada e confirmada compatível com o fck e contexto atuais. Não há cimento elegível; IA não consultada.",false);
        if (!habilitada) return local(pedido, permitidos, "Provider de IA desligado.");
        try {
            String schema = "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"cimentoIds\"],\"properties\":{\"cimentoIds\":{\"type\":\"array\",\"maxItems\":5,\"items\":{\"type\":\"string\",\"enum\":" + mapper.writeValueAsString(permitidos) + "}}}}";
            var entrada = mapper.writeValueAsString(Map.of("elementoTipo", tecnico.elementoTipo(),"fckProjetoMPa",tecnico.fckProjetoMPa(), "ambiente", pedido.ambiente(), "prioridade", pedido.prioridade(), "catalogoIds", permitidos));
            var resposta = modelo.gerarEstruturado(RotaIa.SELECAO_CIMENTO, SYSTEM, entrada, schema, Map.of("rota", "selecao-cimento"));
            if (!resposta.completa() || resposta.modelo() == null || resposta.modelo().startsWith("fake-")) return local(pedido, permitidos, "IA indisponível ou resposta incompleta.");
            var json = mapper.readTree(resposta.conteudoJson());
            if (!json.isObject() || json.size() != 1 || !json.has("cimentoIds") || !json.get("cimentoIds").isArray()) return local(pedido, permitidos, "Saída de IA inválida.");
            var ids = new ArrayList<String>();
            for (var id : json.get("cimentoIds")) {
                if (!id.isTextual() || !permitidos.contains(id.asString()) || ids.size() >= 5) return local(pedido, permitidos, "ID sem dosagem compatível ou saída inválida.");
                if (!ids.contains(id.asString())) ids.add(id.asString());
            }
            if (ids.isEmpty()) return local(pedido, permitidos, "IA sem orientação utilizável.");
            return new Orientacao(List.copyOf(ids), "IA", AVISO, false);
        } catch (RuntimeException e) { return local(pedido, permitidos, "Falha do provider ou saída de IA inválida."); }
    }
    private void autorizar(UUID obraId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.DONO, PapelObra.ADMIN, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO);
    }
    private Orientacao local(Pedido p, List<String> permitidos, String motivo) {
        var ids = switch (p.prioridade()) {
            case RESISTENCIA_INICIAL -> List.of("CIM_CP_V_ARI");
            case BAIXO_CALOR -> List.of("CIM_CP_III_32", "CIM_CP_IV_32");
            case GERAL -> List.of("CIM_CP_II_F_32", "CIM_CP_II_E_32", "CIM_CP_II_Z_32");
        };
        var filtrados=ids.stream().filter(permitidos::contains).toList();
        return new Orientacao(filtrados.isEmpty()?permitidos:filtrados, "ORIENTACAO_TECNICA_SEM_IA", motivo + " " + AVISO, false);
    }
}
