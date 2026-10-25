package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.GeradorCombinacoes;
import br.com.civilconstructorai.estrutura.carga.NaturezaAcao;
import br.com.civilconstructorai.estrutura.carga.ParametrosVento;
import br.com.civilconstructorai.estrutura.carga.TipoCarga;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;
import br.com.civilconstructorai.estrutura.lancamento.EstadoLancamento;
import br.com.civilconstructorai.estrutura.lancamento.LancamentoEstrutural;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** RF04 · cargas, vento e combinações por pavimento. */
@Service
@Transactional
public class CargasService {

    private final Repositorios.Cargas cargas;
    private final Repositorios.Lancamentos lancamentos;
    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final Json json;
    private final br.com.civilconstructorai.modelo.DependentesDoModelo dependentes;

    public CargasService(Repositorios.Cargas cargas, Repositorios.Lancamentos lancamentos, ModeloEstruturalFonte modelo,
                         AcessoObra acesso, ContextoUsuario contexto, Json json, br.com.civilconstructorai.modelo.DependentesDoModelo dependentes) {
        this.dependentes = dependentes;
        this.cargas = cargas;
        this.lancamentos = lancamentos;
        this.modelo = modelo;
        this.acesso = acesso;
        this.contexto = contexto;
        this.json = json;
    }

    /** {@code posicao}: carga pontual em viga, distância em metros a partir do primeiro ponto (p1) da viga */
    public record NovaCarga(String pavimentoId, NaturezaAcao natureza, TipoCarga tipo, double valor, String elementoAlvoId, String descricao, String casoCarga, Double posicao) {
        public NovaCarga(String pavimentoId, NaturezaAcao natureza, TipoCarga tipo, double valor, String elementoAlvoId, String descricao, String casoCarga) {
            this(pavimentoId, natureza, tipo, valor, elementoAlvoId, descricao, casoCarga, null);
        }
    }

    @Transactional(readOnly = true)
    public List<Carga> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return cargas.findByObraIdOrderByPavimentoIdAscCriadoEmAsc(obraId);
    }

    public Carga lancar(UUID obraId, NovaCarga n) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var m = modelo.carregar(obraId);
        if (m.pavimento(n.pavimentoId()).isEmpty()) {
            throw new ExcecaoDominio.Validacao("pavimentoId", "Pavimento não existe no modelo.");
        }
        if (n.elementoAlvoId() != null && m.elemento(n.elementoAlvoId()).isEmpty()) {
            throw new ExcecaoDominio.Validacao("elementoAlvoId", "Elemento alvo não existe no modelo.");
        }
        var alvo = n.elementoAlvoId() == null ? null : m.elemento(n.elementoAlvoId()).orElseThrow();
        // RF04: carga pontual em viga precisa de posição (antes caía no nó do apoio e a viga não fletia)
        if (alvo != null && alvo.tipo() == TipoElementoEstrutural.VIGA && n.tipo() == TipoCarga.PONTUAL) {
            if (n.posicao() == null) {
                throw new ExcecaoDominio.Validacao("posicao", String.format(java.util.Locale.ROOT,
                        "Informe a posição da carga pontual: distância em metros a partir do início da viga (0 a %.2f m).", alvo.comprimento()));
            }
            if (n.posicao() < 0 || n.posicao() > alvo.comprimento() + 1e-6) {
                throw new ExcecaoDominio.Validacao("posicao", String.format(java.util.Locale.ROOT,
                        "A posição deve ficar entre 0 e %.2f m (comprimento da viga).", alvo.comprimento()));
            }
        } else if (n.posicao() != null) {
            throw new ExcecaoDominio.Validacao("posicao", "Posição só se aplica a carga pontual em viga.");
        }
        // descrição é opcional na tela; sem ela, um rótulo legível pela natureza e unidade
        var descricao = n.descricao() == null || n.descricao().isBlank()
                ? "Carga " + n.natureza().name().toLowerCase() + " " + switch (n.tipo()) { case AREA -> "(kN/m²)"; case LINEAR -> "(kN/m)"; default -> "(kN)"; }
                : n.descricao().trim();
        var c = cargas.save(new Carga(u.empresaAtivaId(), obraId, n.pavimentoId(), n.natureza(), n.tipo(), n.valor(), n.elementoAlvoId(), descricao, n.casoCarga(), n.posicao()));
        dependentes.cargasMudaram(obraId, n.pavimentoId());
        var l = lancamento(obraId, n.pavimentoId(), u.empresaAtivaId());
        if (l.getEstado() == EstadoLancamento.VAZIO || l.getEstado() == EstadoLancamento.SUGESTAO_IA) {
            l.transicionar(EstadoLancamento.EM_LANCAMENTO);
        }
        l.transicionar(EstadoLancamento.CARREGADO);
        regerarCombinacoes(obraId, l);
        return c;
    }

    public void remover(UUID obraId, UUID cargaId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var c = cargas.findByIdAndObraId(cargaId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Carga"));
        cargas.delete(c);
        dependentes.cargasMudaram(obraId, c.getPavimentoId());
        lancamentos.findByObraIdAndPavimentoId(obraId, c.getPavimentoId()).ifPresent(l -> regerarCombinacoes(obraId, l));
    }

    public LancamentoEstrutural definirVento(UUID obraId, String pavimentoId, ParametrosVento vento) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var l = lancamento(obraId, pavimentoId, contexto.exigir().empresaAtivaId());
        l.setVentoJson(json.escrever(vento));
        dependentes.cargasMudaram(obraId, pavimentoId);
        if (l.getEstado() == EstadoLancamento.VAZIO || l.getEstado() == EstadoLancamento.SUGESTAO_IA) {
            l.transicionar(EstadoLancamento.EM_LANCAMENTO);
        }
        regerarCombinacoes(obraId, l);
        return l;
    }

    @Transactional(readOnly = true)
    public List<Combinacao> combinacoes(UUID obraId, String pavimentoId) {
        acesso.exigirMembro(obraId);
        return lancamentos.findByObraIdAndPavimentoId(obraId, pavimentoId)
                .map(l -> json.ler(l.getCombinacoesJson(), new tools.jackson.core.type.TypeReference<List<Combinacao>>() { }))
                .orElseGet(() -> GeradorCombinacoes.gerar(false, false));
    }

    public LancamentoEstrutural marcarPronto(UUID obraId, String pavimentoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var l = lancamento(obraId, pavimentoId, contexto.exigir().empresaAtivaId());
        if (l.getEstado() == EstadoLancamento.VAZIO) {
            throw new ExcecaoDominio.RegraDeNegocio("lancamento_vazio", "Lance ao menos uma carga antes de marcar o pavimento como pronto para análise.");
        }
        l.transicionar(EstadoLancamento.PRONTO_PARA_ANALISE);
        return l;
    }

    @Transactional(readOnly = true)
    public List<LancamentoEstrutural> lancamentos(UUID obraId) {
        acesso.exigirMembro(obraId);
        return lancamentos.findByObraId(obraId);
    }

    public ParametrosVento ventoDe(UUID obraId) {
        return lancamentos.findByObraId(obraId).stream().map(LancamentoEstrutural::getVentoJson).filter(v -> v != null)
                .map(v -> json.ler(v, ParametrosVento.class)).findFirst().orElse(null);
    }

    LancamentoEstrutural lancamento(UUID obraId, String pavimentoId, UUID tenantId) {
        return lancamentos.findByObraIdAndPavimentoId(obraId, pavimentoId).orElseGet(() -> lancamentos.save(new LancamentoEstrutural(tenantId, obraId, pavimentoId)));
    }

    private void regerarCombinacoes(UUID obraId, LancamentoEstrutural l) {
        var todas = cargas.findByObraIdOrderByPavimentoIdAscCriadoEmAsc(obraId);
        boolean acidental = todas.stream().anyMatch(c -> c.getNatureza() == NaturezaAcao.ACIDENTAL);
        boolean vento = l.getVentoJson() != null || todas.stream().anyMatch(c -> c.getNatureza() == NaturezaAcao.VENTO);
        l.setCombinacoesJson(json.escrever(GeradorCombinacoes.gerar(acidental, vento)));
    }
}
