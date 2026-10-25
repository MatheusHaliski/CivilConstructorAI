package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.analise.AnaliseEstrutural;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.GeradorCombinacoes;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.lancamento.EstadoLancamento;
import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF05 · job de análise estrutural (MEF em Java puro). Execução síncrona dentro da requisição (🟡 fila futura). */
@Service
@Transactional
public class AnaliseService {

    private final Repositorios.Analises analises;
    private final Repositorios.Cargas cargas;
    private final CargasService cargasService;
    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final Relogio relogio;
    private final Json json;

    private final Repositorios.Lancamentos lancamentos;
    private final br.com.civilconstructorai.obra.BaseCalculoService bases;

    public AnaliseService(Repositorios.Analises analises, Repositorios.Cargas cargas, CargasService cargasService, ModeloEstruturalFonte modelo,
                          AcessoObra acesso, ContextoUsuario contexto, Relogio relogio, Json json, Repositorios.Lancamentos lancamentos,
                          br.com.civilconstructorai.obra.BaseCalculoService bases) {
        this.lancamentos = lancamentos;
        this.bases = bases;
        this.analises = analises;
        this.cargas = cargas;
        this.cargasService = cargasService;
        this.modelo = modelo;
        this.acesso = acesso;
        this.contexto = contexto;
        this.relogio = relogio;
        this.json = json;
    }

    public AnaliseEstrutural solicitar(UUID obraId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var m = modelo.carregar(obraId);
        // RF05 RN-03: só calcula lançamento liberado pelo engenheiro (auditoria 2026-10: aceitava sem o "pronto")
        var lancs = lancamentos.findByObraId(obraId);
        var naoProntos = lancs.stream().filter(l -> l.getEstado() != EstadoLancamento.PRONTO_PARA_ANALISE && l.getEstado() != EstadoLancamento.VAZIO)
                .map(l -> m.pavimento(l.getPavimentoId()).map(p -> p.nome()).orElse(l.getPavimentoId())).toList();
        if (lancs.stream().noneMatch(l -> l.getEstado() == EstadoLancamento.PRONTO_PARA_ANALISE) || !naoProntos.isEmpty()) {
            throw new ExcecaoDominio.RegraDeNegocio("lancamento_nao_pronto", naoProntos.isEmpty()
                    ? "Lance as cargas e marque o lançamento como pronto para análise (Estrutura › Cargas e combinações) antes de calcular."
                    : "Marque como pronto para análise o lançamento de: " + String.join(", ", naoProntos) + ".");
        }
        // análises anteriores de outra versão ficam obsoletas
        for (var a : analises.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (a.getVersaoModelo() != m.versao()) {
                a.tornarObsoleta();
            }
        }
        var analise = analises.save(new AnaliseEstrutural(u.empresaAtivaId(), obraId, m.versao(), u.contaId()));
        executar(analise, m);
        return analise;
    }

    void executar(AnaliseEstrutural analise, ModeloEstrutural m) {
        analise.iniciar(relogio.agora());
        var base = bases.snapshot(analise.getObraId());
        analise.registrarBaseCalculo(json.escrever(base));
        try {
            var todasCargas = cargas.findByObraIdOrderByPavimentoIdAscCriadoEmAsc(analise.getObraId());
            var vento = cargasService.ventoDe(analise.getObraId());
            var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, todasCargas, vento, modulos(m, base.agregado())));
            List<Combinacao> combinacoes = GeradorCombinacoes.gerar(ma.temCasoComCarga(NaturezaAcao.ACIDENTAL), ma.temCasoComCarga(NaturezaAcao.VENTO));
            var resultado = new SolverMef(ma).resolver(combinacoes);
            // resultado só vale se as reações equilibram as cargas aplicadas em toda combinação
            var desequilibrada = resultado.porCombinacao().values().stream().filter(c -> c.equilibrio() != null && !c.equilibrio().ok()).findFirst();
            if (desequilibrada.isPresent()) {
                analise.falhar(String.format(java.util.Locale.ROOT, "Resultado inconsistente: reações não equilibram as cargas em \"%s\" (resíduo relativo %.2e). Revise apoios e ligações.",
                        desequilibrada.get().combinacao().nome(), desequilibrada.get().equilibrio().residuoRelativo()), relogio.agora());
                return;
            }
            analise.concluir(json.escrever(resultado), json.escrever(resumo(resultado)), relogio.agora());
        } catch (SolverMef.EstruturaHipostaticaException e) {
            analise.falhar(e.getMessage(), relogio.agora());
        } catch (IllegalArgumentException e) {
            analise.falhar("Modelo analítico inválido: " + e.getMessage(), relogio.agora());
        }
    }

    /**
     * módulo de elasticidade (kN/m²) por material presente no modelo, lido do catálogo técnico: Ecs para concreto
     * (estimado, NBR 6118 8.2.8) e E para aço estrutural. Material fora do catálogo não entra no mapa — o montador
     * avisa e declara a premissa adotada, em vez de assumir C30 calado.
     */
    static Map<String, Double> modulos(ModeloEstrutural m, String agregado) {
        var cat = br.com.civilconstructorai.notacao.CatalogoMateriais.padrao();
        var out = new LinkedHashMap<String, Double>();
        for (var e : m.elementos()) {
            if (e.materialId() == null || out.containsKey(e.materialId())) {
                continue;
            }
            var ecs = cat.valorCalculo(e.materialId(), "Ecs", agregado);
            var eAco = cat.valorCalculo(e.materialId(), "E", agregado);
            if (ecs.isPresent()) {
                out.put(e.materialId(), ecs.getAsDouble() * 1000);
            } else if (eAco.isPresent()) {
                out.put(e.materialId(), eAco.getAsDouble() * 1000);
            }
        }
        return out;
    }

    private Map<String, Object> resumo(SolverMef.ResultadoAnalise r) {
        var out = new LinkedHashMap<String, Object>();
        out.put("nos", r.nos());
        out.put("barras", r.barras());
        var porComb = new LinkedHashMap<String, Object>();
        for (var e : r.porCombinacao().entrySet()) {
            double mMax = e.getValue().barras().stream().mapToDouble(SolverMef.ResultadoBarra::mMax).max().orElse(0);
            double vMax = e.getValue().barras().stream().mapToDouble(SolverMef.ResultadoBarra::vMax).max().orElse(0);
            double nMax = e.getValue().barras().stream().mapToDouble(SolverMef.ResultadoBarra::nMax).max().orElse(0);
            porComb.put(e.getKey(), Map.of("mMax", mMax, "vMax", vMax, "nMax", nMax, "deslocamentoMax", e.getValue().deslocamentoMax(), "estadoLimite", e.getValue().combinacao().estadoLimite().name()));
        }
        out.put("porCombinacao", porComb);
        out.put("avisos", r.avisos() == null ? 0 : r.avisos().size());
        return out;
    }

    @Transactional(readOnly = true)
    public AnaliseEstrutural obter(UUID obraId, UUID analiseId) {
        acesso.exigirMembro(obraId);
        return analises.findByIdAndObraId(analiseId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Análise"));
    }

    @Transactional(readOnly = true)
    public List<AnaliseEstrutural> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return analises.findByObraIdOrderByCriadoEmDesc(obraId);
    }

    @Transactional(readOnly = true)
    public SolverMef.ResultadoAnalise resultado(UUID obraId, UUID analiseId) {
        var a = obter(obraId, analiseId);
        if (a.getResultadoJson() == null) {
            throw new ExcecaoDominio.RegraDeNegocio("analise_sem_resultado", "A análise está em " + a.getEstado() + (a.getMensagemErro() != null ? ": " + a.getMensagemErro() : "."));
        }
        return json.ler(a.getResultadoJson(), SolverMef.ResultadoAnalise.class);
    }
}
