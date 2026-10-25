package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.EstadoJob;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.EstadoRascunho;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.dimensionamento.Bitolas;
import br.com.civilconstructorai.estrutura.dimensionamento.Dimensionamento;
import br.com.civilconstructorai.estrutura.dimensionamento.Nbr6118;
import br.com.civilconstructorai.estrutura.mef.MontadorModeloAnalitico;
import br.com.civilconstructorai.estrutura.mef.SolverMef;
import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF07 · dimensionamento NBR 6118 a partir dos esforços de uma análise concluída. */
@Service
@Transactional
public class DimensionamentoService {

    private static final double COBRIMENTO = 0.03;
    private static final double FCK_PADRAO = 30;

    public record ResultadoElemento(String elementoId, String rotulo, String tipo, boolean verifica, String motivo,
                                    double mdKnm, double vdKn, double ndKn, String armaduraLongitudinal, String estribos,
                                    double asCm2, double acoKg, List<String> memoria) {
    }

    private final Repositorios.Dimensionamentos dimensionamentos;
    private final Repositorios.Analises analises;
    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;

    public DimensionamentoService(Repositorios.Dimensionamentos dimensionamentos, Repositorios.Analises analises, ModeloEstruturalFonte modelo,
                                  AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, Json json) {
        this.dimensionamentos = dimensionamentos;
        this.analises = analises;
        this.modelo = modelo;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.json = json;
    }

    public Dimensionamento solicitar(UUID obraId, UUID analiseId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var analise = analises.findByIdAndObraId(analiseId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Análise"));
        if (analise.getEstado() != EstadoJob.CONCLUIDO) {
            throw new ExcecaoDominio.RegraDeNegocio("analise_nao_concluida", "A análise precisa estar CONCLUIDA (está " + analise.getEstado() + ").");
        }
        var m = modelo.carregar(obraId);
        if (m.versao() != analise.getVersaoModelo()) {
            throw new ExcecaoDominio.Conflito("O modelo mudou (versão " + m.versao() + ") depois da análise (versão " + analise.getVersaoModelo() + "). Rode a análise de novo.");
        }
        for (var d : dimensionamentos.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (d.getVersaoModelo() != m.versao()) {
                d.tornarObsoleto();
            }
        }
        var dim = dimensionamentos.save(new Dimensionamento(u.empresaAtivaId(), obraId, analiseId, m.versao()));
        dim.iniciar();
        try {
            var resultadoAnalise = json.ler(analise.getResultadoJson(), SolverMef.ResultadoAnalise.class);
            var resultados = dimensionar(m, resultadoAnalise);
            double aco = resultados.stream().mapToDouble(ResultadoElemento::acoKg).sum();
            int nao = (int) resultados.stream().filter(r -> !r.verifica()).count();
            dim.concluir(json.escrever(resultados), Math.round(aco * 10) / 10.0, nao);
        } catch (RuntimeException e) {
            dim.falhar("Falha no dimensionamento: " + e.getMessage());
        }
        return dim;
    }

    List<ResultadoElemento> dimensionar(ModeloEstrutural m, SolverMef.ResultadoAnalise analise) {
        var elu = analise.porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELU_NORMAL).toList();
        var qp = analise.porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELS_QUASE_PERMANENTE).findFirst();
        var freq = analise.porCombinacao().values().stream().filter(c -> c.combinacao().estadoLimite() == Combinacao.EstadoLimite.ELS_FREQUENTE).findFirst();
        var out = new ArrayList<ResultadoElemento>();
        // envoltória de esforços por elemento (máximos entre combinações ELU)
        var envoltoria = new LinkedHashMap<String, double[]>(); // [Mk+, |Mk-|, Vk, Nk]
        for (var comb : elu) {
            for (var rb : comb.barras()) {
                var env = envoltoria.computeIfAbsent(rb.elementoId(), k -> new double[4]);
                env[0] = Math.max(env[0], rb.mMax() / Nbr6118.GAMA_F);
                env[1] = Math.max(env[1], Math.abs(Math.min(0, rb.mMin())) / Nbr6118.GAMA_F);
                env[2] = Math.max(env[2], rb.vMax() / Nbr6118.GAMA_F);
                env[3] = Math.max(env[3], rb.nMax() / Nbr6118.GAMA_F);
            }
        }
        for (var e : m.elementos()) {
            if (e.rascunhoIa()) {
                continue;
            }
            var env = envoltoria.get(e.id());
            switch (e.tipo()) {
                case VIGA -> out.add(viga(m, e, env, qp.orElse(null), freq.orElse(null)));
                case PILAR -> out.add(pilar(m, e, env));
                case LAJE -> out.add(laje(m, e));
                default -> { }
            }
        }
        return out;
    }

    private ResultadoElemento viga(ModeloEstrutural m, ElementoEstrutural v, double[] env, SolverMef.ResultadoCombinacao qp, SolverMef.ResultadoCombinacao freq) {
        double fck = fckDe(v.materialId());
        double d = v.h() - COBRIMENTO - 0.015;
        double mk = env == null ? 0 : Math.max(env[0], env[1]);
        double vk = env == null ? 0 : env[2];
        var memoria = new ArrayList<String>();
        var flexao = Nbr6118.flexaoSimples(Math.max(mk, 0.01), v.b(), v.h(), d, fck);
        memoria.addAll(flexao.memoria().linhas());
        var cis = Nbr6118.cisalhamento(vk, v.b(), d, fck);
        memoria.addAll(cis.memoria().linhas());
        boolean ok = flexao.verifica() && cis.verifica();
        String motivo = !flexao.verifica() ? flexao.motivo() : !cis.verifica() ? cis.motivo() : "Verifica";
        var arranjo = Bitolas.escolher(Double.isNaN(flexao.asCm2()) ? flexao.asMinCm2() : flexao.asCm2(), 2, 10);
        var estribo = Bitolas.estribo(Double.isNaN(cis.aswPorMetroCm2()) ? cis.aswMinPorMetroCm2() : cis.aswPorMetroCm2(), cis.sMaxCm() > 0 ? cis.sMaxCm() : 20);
        double l = v.comprimento();
        if (qp != null && ok) {
            double mQp = qp.barras().stream().filter(b -> b.elementoId().equals(v.id())).mapToDouble(SolverMef.ResultadoBarra::mMax).max().orElse(0);
            double qEq = 8 * mQp / Math.max(l * l, 1e-6);
            var flecha = Nbr6118.flecha(qEq, l, v.b(), v.h(), d, arranjo.areaCm2(), fck, MontadorModeloAnalitico.moduloConcreto(fck));
            memoria.addAll(flecha.memoria().linhas());
            if (!flecha.verifica()) {
                ok = false;
                motivo = "Flecha " + flecha.flechaMm() + " mm > L/250 = " + flecha.limiteMm() + " mm.";
            }
        }
        if (freq != null && ok) {
            double mF = freq.barras().stream().filter(b -> b.elementoId().equals(v.id())).mapToDouble(SolverMef.ResultadoBarra::mMax).max().orElse(0);
            var fis = Nbr6118.fissuracao(mF, d, arranjo.areaCm2(), arranjo.bitola().mm(), fck, 0.3);
            memoria.addAll(fis.memoria().linhas());
            if (!fis.verifica()) {
                ok = false;
                motivo = "Abertura de fissura " + fis.wkMm() + " mm > 0,3 mm (CAA II).";
            }
        }
        double kgLong = arranjo.barras() * arranjo.bitola().kgPorMetro() * l * 1.10 + 2 * 0.617 * l; // + porta-estribos 2 Ø 10
        double kgEstribo = (2 * (v.b() + v.h()) - 8 * COBRIMENTO + 0.2) * (l / (estribo.espacamentoCm() / 100)) * kgPorMetro(estribo.mm());
        memoria.add(String.format(java.util.Locale.ROOT, "Aço: %s + %s → %.1f kg", arranjo.descricao(), estribo.descricao(), kgLong + kgEstribo));
        return new ResultadoElemento(v.id(), v.rotulo() == null ? "V" : v.rotulo(), "VIGA", ok, motivo, r2(mk * Nbr6118.GAMA_F), r2(vk * Nbr6118.GAMA_F), 0,
                arranjo.descricao(), estribo.descricao(), arranjo.areaCm2(), r1(kgLong + kgEstribo), memoria);
    }

    private ResultadoElemento pilar(ModeloEstrutural m, ElementoEstrutural p, double[] env) {
        double fck = fckDe(p.materialId());
        double l = m.alturaPilar(p);
        double nk = env == null ? 0 : env[3];
        var res = Nbr6118.pilar(Math.max(nk, 1), p.b(), p.h(), l, fck);
        var arranjo = Bitolas.escolher(Double.isNaN(res.asCm2()) ? res.asMinCm2() : res.asCm2(), 4, 10);
        var estribo = Bitolas.estribo(0.5, Math.min(Math.min(20, 12 * arranjo.bitola().mm() / 10), Math.min(p.b(), p.h()) * 100));
        double kg = arranjo.barras() * arranjo.bitola().kgPorMetro() * (l + 0.6) + (2 * (p.b() + p.h()) - 8 * COBRIMENTO + 0.2) * (l / (estribo.espacamentoCm() / 100)) * kgPorMetro(estribo.mm());
        var memoria = new ArrayList<>(res.memoria().linhas());
        memoria.add(String.format(java.util.Locale.ROOT, "Aço: %s + estribos %s → %.1f kg", arranjo.descricao(), estribo.descricao(), kg));
        return new ResultadoElemento(p.id(), p.rotulo() == null ? "P" : p.rotulo(), "PILAR", res.verifica(), res.motivo(), 0, 0, r2(nk * Nbr6118.GAMA_F),
                arranjo.descricao(), estribo.descricao(), Double.isNaN(res.asCm2()) ? 0 : res.asCm2(), r1(kg), memoria);
    }

    private ResultadoElemento laje(ModeloEstrutural m, ElementoEstrutural s) {
        double fck = fckDe(s.materialId());
        var pts = s.contorno();
        double xMin = pts.stream().mapToDouble(p -> p.x()).min().orElse(0), xMax = pts.stream().mapToDouble(p -> p.x()).max().orElse(0);
        double yMin = pts.stream().mapToDouble(p -> p.y()).min().orElse(0), yMax = pts.stream().mapToDouble(p -> p.y()).max().orElse(0);
        double lx = Math.min(xMax - xMin, yMax - yMin), ly = Math.max(xMax - xMin, yMax - yMin);
        double q = s.h() * 25 + 1.0 + 1.5 + 2.0; // peso próprio + revestimento 1,0 + alvenaria 1,5 + sobrecarga 2,0 kN/m² (residencial)
        // laje armada em uma direção (ly/lx ≥ 2) ou em duas (coeficiente 0,0625 aproximado por faixa)
        double mk = ly / Math.max(lx, 1e-6) >= 2 ? q * lx * lx / 8 : 0.0625 * q * lx * lx;
        double d = s.h() - 0.02 - 0.004;
        var flexao = Nbr6118.flexaoSimples(mk, 1.0, s.h(), d, fck);
        var arranjo = Bitolas.escolher(Double.isNaN(flexao.asCm2()) ? flexao.asMinCm2() : flexao.asCm2(), 5, 5);
        double area = ElementoEstrutural.areaPoligono(pts);
        double kg = arranjo.areaCm2() / 1e4 * 7850 * area * 2 * 1.15; // duas direções + 15 % de recobrimento/ancoragem
        var memoria = new ArrayList<>(flexao.memoria().linhas());
        memoria.add(0, String.format(java.util.Locale.ROOT, "Laje %.2f × %.2f m, q = %.2f kN/m², Mk por metro = %.2f kN·m", lx, ly, q, mk));
        memoria.add(String.format(java.util.Locale.ROOT, "Aço: %s por metro nas duas direções → %.1f kg", arranjo.descricao(), kg));
        return new ResultadoElemento(s.id(), s.rotulo() == null ? "L" : s.rotulo(), "LAJE", flexao.verifica(), flexao.motivo(), r2(mk * Nbr6118.GAMA_F), 0, 0,
                arranjo.descricao() + "/m", "-", arranjo.areaCm2(), r1(kg), memoria);
    }

    private static double fckDe(String materialId) {
        if (materialId == null) {
            return FCK_PADRAO;
        }
        var m = java.util.regex.Pattern.compile("FCK(\\d+)").matcher(materialId);
        return m.find() ? Double.parseDouble(m.group(1)) : FCK_PADRAO;
    }

    private static double kgPorMetro(double mm) {
        return Bitolas.CA50.stream().filter(b -> b.mm() == mm).mapToDouble(Bitolas.Bitola::kgPorMetro).findFirst().orElse(0.395);
    }

    private static double r2(double v) { return Math.round(v * 100) / 100.0; }
    private static double r1(double v) { return Math.round(v * 10) / 10.0; }

    public Dimensionamento decidir(UUID obraId, UUID dimId, boolean aprovar, String artRrt) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var d = dimensionamentos.findByIdAndObraId(dimId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Dimensionamento"));
        if (aprovar) {
            if (!u.podeAprovar(obraId)) {
                throw new ExcecaoDominio.AcessoNegado("Aprovar um dimensionamento exige papel de engenheiro(a), selo CREA verificado e 2FA ativo.");
            }
            d.aprovar(u.contaId(), artRrt, relogio.agora());
        } else {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
            d.rejeitar(u.contaId(), relogio.agora());
        }
        registro.registrar(new RegistroOperacoes.Operacao(aprovar ? "DIMENSIONAMENTO_APROVADO" : "DIMENSIONAMENTO_REJEITADO", u.contaId(), u.empresaAtivaId(), obraId, "Dimensionamento", d.getId(), aprovar ? Map.of("art", artRrt == null ? "" : artRrt) : Map.of()));
        return d;
    }

    @Transactional(readOnly = true)
    public Dimensionamento obter(UUID obraId, UUID dimId) {
        acesso.exigirMembro(obraId);
        return dimensionamentos.findByIdAndObraId(dimId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Dimensionamento"));
    }

    @Transactional(readOnly = true)
    public List<Dimensionamento> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return dimensionamentos.findByObraIdOrderByCriadoEmDesc(obraId);
    }

    @Transactional(readOnly = true)
    public List<ResultadoElemento> resultados(UUID obraId, UUID dimId) {
        var d = obter(obraId, dimId);
        if (d.getResultadosJson() == null) {
            return List.of();
        }
        return json.ler(d.getResultadosJson(), new tools.jackson.core.type.TypeReference<List<ResultadoElemento>>() { });
    }

    /** aço por elemento do último dimensionamento APROVADO na versão atual do modelo (consumido pelo RF11) */
    @Transactional(readOnly = true)
    public Map<String, Double> acoAprovadoPorElemento(UUID obraId, long versaoModelo) {
        for (var d : dimensionamentos.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (d.getEstado() == EstadoRascunho.APROVADO && d.getVersaoModelo() == versaoModelo && d.getResultadosJson() != null) {
                var out = new LinkedHashMap<String, Double>();
                for (var r : json.ler(d.getResultadosJson(), new tools.jackson.core.type.TypeReference<List<ResultadoElemento>>() { })) {
                    out.put(r.elementoId(), r.acoKg());
                }
                return out;
            }
        }
        return Map.of();
    }
}
