package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.modelo.Enums.EstadoCiclo;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloRepositorio.CicloRegistro;
import br.com.civilconstructorai.notacao.FormatadorVi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestra o loop duplo (RF16): executa o comando num rascunho, roda o {@link Auditor} e, quando a origem é
 * IA e algo reprova, pede correção ao modelo (rota {@code correcao-auditoria}) com as violações estruturadas,
 * no máximo 3 vezes. Cada iteração fica registrada no {@link CicloRegistro} (tokens e stop_reason, sem o texto
 * do prompt). Comando de usuário reprovado não vai ao LLM: volta 422 com o relatório.
 */
@Service
public class AuditorService {

    public static final int MAX_ITERACOES_LLM = 3;

    private final Auditor auditor;
    private final ServicoGeometria geometria;
    private final ModeloLinguagem llm;
    private final ModeloRepositorio repositorio;
    private final ObjectMapper mapper;
    private final double shortCurveTolerance;
    private final TransactionTemplate txNova;
    private final br.com.civilconstructorai.material.MateriaisService materiais;

    public AuditorService(ServicoGeometria geometria, ModeloLinguagem llm, ModeloRepositorio repositorio, ObjectMapper mapper,
                          @Value("${app.auditoria.short-curve-tolerance:0.05}") double shortCurveTolerance, PlatformTransactionManager tm,
                          br.com.civilconstructorai.material.MateriaisService materiais) {
        this.materiais = materiais;
        this.txNova = new TransactionTemplate(tm);
        this.txNova.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.geometria = geometria;
        this.auditor = new Auditor(geometria);
        this.llm = llm;
        this.repositorio = repositorio;
        this.mapper = mapper;
        this.shortCurveTolerance = shortCurveTolerance;
    }

    public record Resultado(ModeloBim rascunho, DiffModelo diff, RelatorioAuditoria relatorio, ComandoVi comandoFinal, CicloRegistro ciclo) {
    }

    /** Executa + audita; devolve o rascunho aprovado ou lança {@link ComandoReprovadoException}. */
    public Resultado auditar(UUID obraId, ModeloBim atual, ComandoVi comando, UUID pavimentoId) {
        var historico = new ArrayList<CicloRegistro.Iteracao>();
        var inicio = Instant.now();
        var cmd = comando;
        RelatorioAuditoria relatorio = null;
        for (int iteracao = 0; iteracao <= MAX_ITERACOES_LLM; iteracao++) {
            var rascunho = atual.copia();
            var diff = rascunho.executar(cmd, pavimentoId);
            relatorio = auditor.auditar(rascunho, diff, new Auditor.Opcoes(shortCurveTolerance, false, Auditor.PoliticaCaixa.RECORTAR, materiais::personalizadoAplicavel)).comIteracoes(iteracao);
            historico.add(new CicloRegistro.Iteracao(iteracao, relatorio.laco1().aprovado() ? "LACO_GEOMETRIA" : "LACO_CODIGO", relatorio.todasAsViolacoes().size(), 0, 0, null, 0));
            if (relatorio.aprovado()) {
                var ciclo = new CicloRegistro(Ids.novo(), obraId, cmd.id(), EstadoCiclo.APROVADO, iteracao, List.copyOf(historico), inicio, Instant.now());
                return new Resultado(rascunho, diff, relatorio, cmd, ciclo);
            }
            if (cmd.origem() != OrigemComando.IA || iteracao == MAX_ITERACOES_LLM) {
                break;
            }
            // CORRIGINDO_COM_LLM: devolve as violações estruturadas e pede nova versão do comando
            var corrigido = pedirCorrecao(cmd, relatorio, historico, iteracao + 1);
            if (corrigido == null) {
                break;
            }
            cmd = corrigido;
        }
        var ciclo = new CicloRegistro(Ids.novo(), obraId, cmd.id(), EstadoCiclo.REPROVADO, historico.size() - 1, List.copyOf(historico), inicio, Instant.now());
        txNova.executeWithoutResult(st -> repositorio.salvarCiclo(ciclo));
        throw new ComandoReprovadoException(relatorio);
    }

    private ComandoVi pedirCorrecao(ComandoVi cmd, RelatorioAuditoria relatorio, List<CicloRegistro.Iteracao> historico, int numero) {
        var violacoes = relatorio.todasAsViolacoes().stream().map(v -> Map.of(
                "codigo", v.codigo(), "elementoRef", v.elementoRef() == null ? "" : v.elementoRef().toString(), "regra", v.regra(),
                "esperado", v.esperado() == null ? "" : v.esperado(), "obtido", v.obtido() == null ? "" : v.obtido())).toList();
        var entrada = mapper.writeValueAsString(Map.of("comando", FormatadorVi.formatar(cmd.codigo(), cmd.args()), "violacoes", violacoes));
        ModeloLinguagem.Resposta r;
        try {
            r = llm.gerarEstruturado(RotaIa.CORRECAO_AUDITORIA, PROMPT_CORRECAO, entrada, SCHEMA_COMANDO, Map.of("iteracao", String.valueOf(numero)));
        } catch (RuntimeException e) {
            historico.add(new CicloRegistro.Iteracao(numero, "CORRIGINDO_COM_LLM", violacoes.size(), 0, 0, "erro_provedor", 0));
            return null; // sem correção: o comando segue reprovado, nada é gravado
        }
        historico.add(new CicloRegistro.Iteracao(numero, "CORRIGINDO_COM_LLM", violacoes.size(), r.inputTokens(), r.outputTokens(), r.stopReason(), r.latenciaMs()));
        if (!r.completa()) {
            return null;
        }
        try {
            var arvore = mapper.readTree(r.conteudoJson());
            var comandos = arvore.get("comandos");
            if (comandos == null || !comandos.isArray() || comandos.isEmpty()) {
                return null;
            }
            var primeiro = comandos.get(0);
            if (!cmd.codigo().equalsIgnoreCase(primeiro.get("codigo").asString())) {
                return null; // a correção não pode trocar o tipo do comando (ex.: PILAR → APAGAR)
            }
            Map<String, Object> args = mapper.convertValue(primeiro.get("args"), new tools.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
            return new ComandoVi(cmd.id(), primeiro.get("codigo").asString().toUpperCase(), ModeloBim.resolverCoordenadas(args), OrigemComando.IA, cmd.versaoBase(), cmd.autorId(), cmd.criadoEm());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public List<RelatorioAuditoria.Violacao> verificarConclusao(ModeloBim modelo, UUID pavimentoId) {
        return geometria.verificarConclusao(modelo, pavimentoId);
    }

    static final String PROMPT_CORRECAO = """
            Você corrige comandos da Notação VI reprovados pelo auditor determinístico. Receba o comando e as
            violações estruturadas (codigo, elementoRef, regra, esperado, obtido) e devolva SOMENTE o JSON com a
            lista "comandos" corrigida, no mesmo esquema. Não invente elementos fora da envoltória dos eixos;
            respeite seções mínimas (pilar ≥ 19 cm e ≥ 360 cm², viga b ≥ 12 cm, laje h ≥ 8 cm) e a tolerância de
            0,05 m. Sem texto discursivo.""";

    public static final String SCHEMA_COMANDO = """
            {"type":"object","additionalProperties":false,"required":["comandos"],"properties":{"comandos":{"type":"array","items":
            {"type":"object","additionalProperties":false,"required":["codigo","args"],"properties":{"codigo":{"type":"string"},"args":{"type":"object"}}}}}}""";
}
