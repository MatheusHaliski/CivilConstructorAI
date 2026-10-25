package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.EstadoJob;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.Json;
import br.com.civilconstructorai.estrutura.ModeloEstruturalFonte;
import br.com.civilconstructorai.estrutura.Repositorios.Analises;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF10 · fundações a partir das reações (RF05) e do perfil de solo (RF09). */
@Service
@Transactional
public class FundacaoService {

    private final Repositorios.DimensionamentosFundacao dims;
    private final Repositorios.Propostas propostas;
    private final Analises analises;
    private final SondagemService sondagens;
    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;

    public FundacaoService(Repositorios.DimensionamentosFundacao dims, Repositorios.Propostas propostas, Analises analises, SondagemService sondagens,
                           ModeloEstruturalFonte modelo, AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, Json json) {
        this.dims = dims;
        this.propostas = propostas;
        this.analises = analises;
        this.sondagens = sondagens;
        this.modelo = modelo;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.json = json;
    }

    public DimensionamentoFundacao dimensionar(UUID obraId, UUID analiseId, double cotaAssentamento, double diametroEstaca) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var analise = analises.findByIdAndObraId(analiseId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Análise"));
        if (analise.getEstado() != EstadoJob.CONCLUIDO) {
            throw new ExcecaoDominio.RegraDeNegocio("analise_nao_concluida", "A análise precisa estar CONCLUIDA.");
        }
        var m = modelo.carregar(obraId);
        if (m.versao() != analise.getVersaoModelo()) {
            throw new ExcecaoDominio.Conflito("O modelo mudou depois da análise. Rode a análise de novo.");
        }
        for (var d : dims.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (d.getVersaoModelo() != m.versao()) {
                d.tornarObsoleto();
            }
        }
        var dim = dims.save(new DimensionamentoFundacao(u.empresaAtivaId(), obraId, analiseId, m.versao()));
        dim.iniciar();
        try {
            var resultado = json.ler(analise.getResultadoJson(), SolverMef.ResultadoAnalise.class);
            var solucao = calcular(dim, m, resultado, cotaAssentamento <= 0 ? 1.5 : cotaAssentamento, diametroEstaca <= 0 ? 0.30 : diametroEstaca, u.empresaAtivaId());
            dim.concluir(solucao);
        } catch (ExcecaoDominio e) {
            dim.falhar(e.getMessage());
        }
        registro.registrar(RegistroOperacoes.Operacao.de("FUNDACOES_DIMENSIONADAS", u.contaId(), u.empresaAtivaId(), obraId, "DimensionamentoFundacao", dim.getId()));
        return dim;
    }

    private String calcular(DimensionamentoFundacao dim, ModeloEstrutural m, SolverMef.ResultadoAnalise resultado, double cota, double diam, UUID tenantId) {
        var terreo = m.pavimentosOrdenados().get(0);
        var pilaresBase = m.doPavimento(terreo.id()).stream().filter(e -> e.tipo() == TipoElementoEstrutural.PILAR && !e.rascunhoIa()).toList();
        if (pilaresBase.isEmpty()) {
            throw new ExcecaoDominio.RegraDeNegocio("sem_pilares", "Não há pilares no pavimento mais baixo para apoiar fundações.");
        }
        // reação vertical característica por pilar: maior |Fz| entre combinações ELS raras/frequentes (serviço); senão ELU/1,4
        var reacaoPorPilar = new HashMap<String, Double>();
        var elu = resultado.porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELU_NORMAL).toList();
        for (var comb : elu) {
            for (var rb : comb.barras()) {
                if (pilaresBase.stream().anyMatch(p -> p.id().equals(rb.elementoId()))) {
                    reacaoPorPilar.merge(rb.elementoId(), rb.nMax() / 1.4, Math::max);
                }
            }
        }
        var sapatas = new ArrayList<Object[]>(); // [pilar, sapata]
        var lista = new ArrayList<FundacaoProposta>();
        double areaSapatas = 0;
        for (var p : pilaresBase) {
            double nk = Math.max(reacaoPorPilar.getOrDefault(p.id(), 0.0), 50.0);
            var perfilInterp = sondagens.perfilEm(dim.getObraId(), p.centro().x(), p.centro().y());
            var perfil = new CalculoFundacoes.Perfil(perfilInterp.camadas(), perfilInterp.leituras(), null);
            var sap = CalculoFundacoes.sapata(nk, p.b(), p.h(), cota, perfil);
            sapatas.add(new Object[]{p, sap, nk, perfil});
            if (sap.verifica()) {
                areaSapatas += sap.lado() * sap.lado();
            }
        }
        // sobreposição entre sapatas vizinhas → bloco sobre estacas; cobertura > 50 % → radier
        double areaPav = m.doPavimento(terreo.id()).stream().filter(e -> e.tipo() == TipoElementoEstrutural.LAJE).mapToDouble(e -> ElementoEstrutural.areaPoligono(e.contorno())).sum();
        if (areaPav <= 0) {
            var xs = pilaresBase.stream().mapToDouble(p -> p.centro().x()).summaryStatistics();
            var ys = pilaresBase.stream().mapToDouble(p -> p.centro().y()).summaryStatistics();
            areaPav = Math.max(1, (xs.getMax() - xs.getMin()) * (ys.getMax() - ys.getMin()));
        }
        boolean radier = areaPav > 0 && areaSapatas / areaPav > 0.5;
        String solucao = radier ? "RADIER" : "SAPATAS";
        for (var item : sapatas) {
            var p = (ElementoEstrutural) item[0];
            var sap = (CalculoFundacoes.Sapata) item[1];
            double nk = (double) item[2];
            var perfil = (CalculoFundacoes.Perfil) item[3];
            boolean sobrepoe = sapatas.stream().anyMatch(o -> o[0] != p && ((CalculoFundacoes.Sapata) o[1]).verifica() && sap.verifica()
                    && CalculoFundacoes.sapatasSeSobrepoem(sap.lado(), ((CalculoFundacoes.Sapata) o[1]).lado(), ((ElementoEstrutural) o[0]).centro().distancia(p.centro())));
            String rotulo = p.rotulo() == null ? "P" : p.rotulo();
            if (radier) {
                var mem = new ArrayList<>(sap.memoria());
                mem.add(String.format(java.util.Locale.ROOT, "Sapatas cobririam %.0f%% da área: adotado radier de %.2f m.", 100 * areaSapatas / areaPav, 0.40));
                lista.add(new FundacaoProposta(tenantId, dim.getId(), dim.getObraId(), p.id(), rotulo, "RADIER", p.centro().x(), p.centro().y(), 0, 0, 0.40, nk, null, null, null, json.escrever(mem), true));
            } else if (sap.verifica() && !sobrepoe) {
                lista.add(new FundacaoProposta(tenantId, dim.getId(), dim.getObraId(), p.id(), rotulo, "SAPATA", p.centro().x(), p.centro().y(), sap.lado(), sap.lado(), sap.altura(), nk, null, null, null, json.escrever(sap.memoria()), true));
            } else {
                double comp = CalculoFundacoes.comprimentoResistente(perfil);
                var est = CalculoFundacoes.estacas(nk, diam, comp, perfil);
                var mem = new ArrayList<>(sap.memoria());
                mem.add(sobrepoe ? "Sapata se sobreporia à vizinha: adotado bloco sobre estacas." : "Solo fraco para sapata: adotado bloco sobre estacas.");
                mem.addAll(est.memoria());
                lista.add(new FundacaoProposta(tenantId, dim.getId(), dim.getObraId(), p.id(), rotulo, "BLOCO", p.centro().x(), p.centro().y(), est.ladoBloco(), est.ladoBloco(), est.alturaBloco(), nk, est.estacas(), diam, comp, json.escrever(mem), true));
                solucao = radier ? "RADIER" : "MISTA";
            }
        }
        propostas.saveAll(lista);
        return solucao;
    }

    @Transactional(readOnly = true)
    public List<FundacaoProposta> propostas(UUID obraId, UUID dimId) {
        var d = obter(obraId, dimId);
        return propostas.findByDimensionamentoIdOrderByPilarRotuloAsc(d.getId());
    }

    @Transactional(readOnly = true)
    public DimensionamentoFundacao obter(UUID obraId, UUID dimId) {
        acesso.exigirMembro(obraId);
        return dims.findByIdAndObraId(dimId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Dimensionamento de fundação"));
    }

    @Transactional(readOnly = true)
    public List<DimensionamentoFundacao> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return dims.findByObraIdOrderByCriadoEmDesc(obraId);
    }

    public DimensionamentoFundacao decidir(UUID obraId, UUID dimId, boolean aprovar) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var d = dims.findByIdAndObraId(dimId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Dimensionamento de fundação"));
        if (aprovar) {
            if (!u.podeAprovar(obraId)) {
                throw new ExcecaoDominio.AcessoNegado("Aprovar fundações exige papel de engenheiro(a), selo CREA verificado e 2FA ativo.");
            }
            d.aprovar(u.contaId(), relogio.agora());
        } else {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
            d.rejeitar(u.contaId(), relogio.agora());
        }
        registro.registrar(RegistroOperacoes.Operacao.de(aprovar ? "FUNDACOES_APROVADAS" : "FUNDACOES_REJEITADAS", u.contaId(), u.empresaAtivaId(), obraId, "DimensionamentoFundacao", d.getId()));
        return d;
    }

    /** marca como aplicado no modelo (quem aplica os comandos FUNDACAO no modelo BIM é o módulo modelo) */
    public DimensionamentoFundacao marcarAplicado(UUID obraId, UUID dimId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var d = dims.findByIdAndObraId(dimId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Dimensionamento de fundação"));
        d.aplicarNoModelo();
        return d;
    }

    static Map<String, Object> vazio() {
        return Map.of();
    }
}
