package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.lancamento.EstadoLancamento;
import br.com.civilconstructorai.estrutura.lancamento.RascunhoLancamento;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF04 · sugestão de lançamento estrutural pela IA (rota {@code lancamento-estrutural}, effort high,
 * structured outputs). Só geometria vai ao modelo; o resultado é um rascunho que o engenheiro aprova.
 */
@Service
@Transactional
public class LancadorAutomatico {

    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["pilares","vigas","lajes"],
             "properties":{
              "pilares":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["x","y","b","h"],
                 "properties":{"x":{"type":"number"},"y":{"type":"number"},"b":{"type":"number"},"h":{"type":"number"}}}},
              "vigas":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["x1","y1","x2","y2","b","h"],
                 "properties":{"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"b":{"type":"number"},"h":{"type":"number"}}}},
              "lajes":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["contorno","h"],
                 "properties":{"contorno":{"type":"array","items":{"type":"array","items":{"type":"number"},"minItems":2,"maxItems":2}},"h":{"type":"number"}}}}}}
            """;

    static final String SYSTEM = """
            Você é um engenheiro estrutural. Receberá a arquitetura de um pavimento (paredes, eixos, pavimentos) em
            metros e deve propor o lançamento estrutural em concreto armado: pilares nas interseções de eixos
            (seção mínima 19x40 cm), vigas entre pilares sob as paredes (b >= 12 cm, h ~ L/12), lajes maciças por
            contorno fechado (h >= 8 cm, ~ L/40). Responda SOMENTE com o JSON do esquema, sem texto.
            """;

    private final Repositorios.RascunhosLancamento rascunhos;
    private final ModeloEstruturalFonte modelo;
    private final ModeloLinguagem ia;
    private final CargasService cargasService;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;

    public LancadorAutomatico(Repositorios.RascunhosLancamento rascunhos, ModeloEstruturalFonte modelo, ModeloLinguagem ia, CargasService cargasService,
                              AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, Json json) {
        this.rascunhos = rascunhos;
        this.modelo = modelo;
        this.ia = ia;
        this.cargasService = cargasService;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.json = json;
    }

    public RascunhoLancamento sugerir(UUID obraId, String pavimentoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var m = modelo.carregar(obraId);
        var pav = m.pavimento(pavimentoId).orElseThrow(() -> new ExcecaoDominio.Validacao("pavimentoId", "Pavimento não existe no modelo."));
        // só geometria vai para o modelo: nenhum dado pessoal (ADR-004)
        var paredes = m.doPavimento(pavimentoId).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PAREDE)
                .map(e -> Map.of("x1", e.p1().x(), "y1", e.p1().y(), "x2", e.p2().x(), "y2", e.p2().y(), "esp", e.b(), "altura", e.h())).toList();
        var entrada = json.escrever(Map.of("pavimento", Map.of("nome", pav.nome(), "elevacao", pav.elevacao()), "paredes", paredes,
                "pilaresExistentes", m.doPavimento(pavimentoId).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PILAR).count()));
        var resposta = ia.gerarEstruturado(RotaIa.LANCAMENTO_ESTRUTURAL, SYSTEM, entrada, SCHEMA, Map.of("rf", "RF04"));
        if (!resposta.completa()) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_sem_resposta", resposta.recusa() ? "O modelo recusou a solicitação." : "A resposta da IA foi interrompida (" + resposta.stopReason() + ").");
        }
        var conteudo = validar(resposta.conteudoJson());
        var r = rascunhos.save(new RascunhoLancamento(u.empresaAtivaId(), obraId, pavimentoId, json.escrever(conteudo), resposta.modelo(), resposta.inputTokens(), resposta.outputTokens(), resposta.stopReason()));
        var l = cargasService.lancamento(obraId, pavimentoId, u.empresaAtivaId());
        if (l.getEstado() == EstadoLancamento.VAZIO) {
            l.transicionar(EstadoLancamento.SUGESTAO_IA);
        }
        registro.registrar(RegistroOperacoes.Operacao.de("IA_SUGESTAO_LANCAMENTO", u.contaId(), u.empresaAtivaId(), obraId, "RascunhoLancamento", r.getId()));
        return r;
    }

    /** valida o JSON da IA de novo no backend: seções mínimas, contornos com 3+ vértices, nada fora da Tabela C */
    JsonNode validar(String conteudoJson) {
        JsonNode raiz = json.mapper().readTree(conteudoJson);
        var erros = new java.util.ArrayList<ExcecaoDominio.Validacao.Erro>();
        for (JsonNode p : raiz.path("pilares")) {
            if (Math.min(p.path("b").asDouble(), p.path("h").asDouble()) < 0.19) {
                erros.add(new ExcecaoDominio.Validacao.Erro("pilares", "Pilar sugerido com lado menor que 19 cm."));
            }
        }
        for (JsonNode v : raiz.path("vigas")) {
            if (v.path("b").asDouble() < 0.12) {
                erros.add(new ExcecaoDominio.Validacao.Erro("vigas", "Viga sugerida com b < 12 cm."));
            }
        }
        for (JsonNode l : raiz.path("lajes")) {
            if (l.path("contorno").size() < 3) {
                erros.add(new ExcecaoDominio.Validacao.Erro("lajes", "Laje sugerida sem contorno fechado."));
            }
            if (l.path("h").asDouble() < 0.08) {
                erros.add(new ExcecaoDominio.Validacao.Erro("lajes", "Laje sugerida com h < 8 cm."));
            }
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        return raiz;
    }

    public RascunhoLancamento decidir(UUID obraId, UUID rascunhoId, boolean aprovar, String motivo) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var r = rascunhos.findByIdAndObraId(rascunhoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Rascunho de lançamento"));
        if (aprovar) {
            if (!u.podeAprovar(obraId)) {
                throw new ExcecaoDominio.AcessoNegado("Aprovar um lançamento exige papel de engenheiro(a), selo profissional verificado e 2FA ativo.");
            }
            r.aprovar(u.contaId(), relogio.agora());
        } else {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
            r.rejeitar(u.contaId(), relogio.agora(), motivo);
        }
        registro.registrar(RegistroOperacoes.Operacao.de(aprovar ? "LANCAMENTO_IA_APROVADO" : "LANCAMENTO_IA_REJEITADO", u.contaId(), u.empresaAtivaId(), obraId, "RascunhoLancamento", r.getId()));
        return r;
    }

    @Transactional(readOnly = true)
    public List<RascunhoLancamento> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return rascunhos.findByObraIdOrderByCriadoEmDesc(obraId);
    }
}
