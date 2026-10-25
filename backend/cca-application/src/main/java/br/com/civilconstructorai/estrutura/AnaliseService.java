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

    public AnaliseService(Repositorios.Analises analises, Repositorios.Cargas cargas, CargasService cargasService, ModeloEstruturalFonte modelo,
                          AcessoObra acesso, ContextoUsuario contexto, Relogio relogio, Json json) {
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
        try {
            var todasCargas = cargas.findByObraIdOrderByPavimentoIdAscCriadoEmAsc(analise.getObraId());
            var vento = cargasService.ventoDe(analise.getObraId());
            var ma = MontadorModeloAnalitico.montar(new MontadorModeloAnalitico.Entrada(m, todasCargas, vento, Map.of(
                    "MAT_CONC_FCK30", MontadorModeloAnalitico.moduloConcreto(30), "MAT_CONC_FCK25", MontadorModeloAnalitico.moduloConcreto(25))));
            List<Combinacao> combinacoes = GeradorCombinacoes.gerar(ma.temCasoComCarga(NaturezaAcao.ACIDENTAL), ma.temCasoComCarga(NaturezaAcao.VENTO));
            var resultado = new SolverMef(ma).resolver(combinacoes);
            analise.concluir(json.escrever(resultado), json.escrever(resumo(resultado)), relogio.agora());
        } catch (SolverMef.EstruturaHipostaticaException e) {
            analise.falhar(e.getMessage(), relogio.agora());
        } catch (IllegalArgumentException e) {
            analise.falhar("Modelo analítico inválido: " + e.getMessage(), relogio.agora());
        }
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
