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


    /** resultado por elemento (domínio): estado das verificações, armaduras, detalhamento e memória */
    public record ResultadoElemento(String elementoId, String rotulo, String tipo, String estado, boolean verifica, String motivo,
                                    double mdKnm, double vdKn, double ndKn, String armaduraLongitudinal, String estribos,
                                    double asCm2, double acoKg, List<String> memoria, List<br.com.civilconstructorai.estrutura.dimensionamento.Verificacao> verificacoes,
                                    br.com.civilconstructorai.estrutura.dimensionamento.Detalhamento.Resultado detalhamento, String material,
                                    Double asInferiorCm2, Double asSuperiorCm2, Double massaPerfilKg, String estadoDetalhamento) {
        static ResultadoElemento de(br.com.civilconstructorai.estrutura.dimensionamento.DimensionadorNbr6118.ResultadoElemento r) {
            return new ResultadoElemento(r.elementoId(), r.rotulo(), r.tipo(), r.estado(), r.verifica(), r.motivo(), r.mdKnm(), r.vdKn(), r.ndKn(),
                    r.armaduraLongitudinal(), r.estribos(), r.asCm2(), r.acoKg(), r.memoria(), r.verificacoes(), r.detalhamento(), r.material(),
                    r.asInferiorCm2(), r.asSuperiorCm2(), r.massaPerfilKg(), r.estadoDetalhamento());
        }
    }

    private final Repositorios.Dimensionamentos dimensionamentos;
    private final Repositorios.Analises analises;
    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;
    private final br.com.civilconstructorai.obra.BaseCalculoService bases;
    private final Repositorios.Cargas cargas;
    private final br.com.civilconstructorai.material.MateriaisService materiais;

    public DimensionamentoService(Repositorios.Dimensionamentos dimensionamentos, Repositorios.Analises analises, ModeloEstruturalFonte modelo,
                                  AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, Json json,
                                  br.com.civilconstructorai.obra.BaseCalculoService bases, Repositorios.Cargas cargas,
                                  br.com.civilconstructorai.material.MateriaisService materiais) {
        this.bases = bases;
        this.cargas = cargas;
        this.materiais = materiais;
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
        // RF07 CA-01 / RF10 CA-01: 409 com o próximo passo em linguagem de pessoa (antes: 422 "está OBSOLETO")
        if (analise.getEstado() == EstadoJob.OBSOLETO) {
            throw new ExcecaoDominio.Conflito("O modelo ou as cargas mudaram depois desta análise. Rode a análise estrutural da versão atual antes de dimensionar.");
        }
        if (analise.getEstado() != EstadoJob.CONCLUIDO) {
            throw new ExcecaoDominio.RegraDeNegocio("analise_nao_concluida", analise.getEstado() == EstadoJob.FALHOU
                    ? "Esta análise falhou; corrija o modelo conforme a mensagem dela e rode de novo."
                    : "A análise ainda está em execução; aguarde a conclusão para dimensionar.");
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
        var base = bases.snapshot(obraId);
        dim.registrarBaseCalculo(json.escrever(base));
        dim.iniciar();
        try {
            var resultadoAnalise = json.ler(analise.getResultadoJson(), SolverMef.ResultadoAnalise.class);
            var resultados = dimensionar(m, resultadoAnalise, cargas.findByObraIdOrderByPavimentoIdAscCriadoEmAsc(obraId), base);
            double aco = resultados.stream().mapToDouble(ResultadoElemento::acoKg).sum();
            // "não verifica" conta todo elemento que não está ATENDIDO: não atendido, dados insuficientes ou não verificado
            int nao = (int) resultados.stream().filter(r -> !r.verifica()).count();
            dim.concluir(json.escrever(resultados), Math.round(aco * 10) / 10.0, nao);
        } catch (RuntimeException e) {
            dim.falhar("Falha no dimensionamento: " + e.getMessage());
        }
        return dim;
    }

    List<ResultadoElemento> dimensionar(ModeloEstrutural m, SolverMef.ResultadoAnalise analise, java.util.List<br.com.civilconstructorai.estrutura.carga.Carga> todasCargas,
                                        br.com.civilconstructorai.obra.BaseCalculo.Snapshot base) {
        var cat = br.com.civilconstructorai.notacao.CatalogoMateriais.padrao();
        // armadura: CA-50 do catálogo (premissa — o elemento ainda não especifica a categoria do aço das barras)
        var ca50 = cat.item("MAT.ACO_ARMADURA.CA50").orElseThrow();
        double fyk = ca50.numero("fyk").orElse(500), eta1 = ca50.numero("eta1").orElse(2.25);
        var entrada = new br.com.civilconstructorai.estrutura.dimensionamento.DimensionadorNbr6118.Entrada(m, analise, todasCargas, base.classeAgressividade(),
                materiais::info, fyk, eta1, "CA-50",
                fck -> br.com.civilconstructorai.notacao.CatalogoMateriais.ALFA_E.getOrDefault(base.agregado() == null ? "granito" : base.agregado(), 1.0) * 5600 * Math.sqrt(fck)
                        * Math.min(1.0, 0.8 + 0.2 * fck / 80) * 1000);
        return new br.com.civilconstructorai.estrutura.dimensionamento.DimensionadorNbr6118(entrada).dimensionar().stream().map(ResultadoElemento::de).toList();
    }

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
