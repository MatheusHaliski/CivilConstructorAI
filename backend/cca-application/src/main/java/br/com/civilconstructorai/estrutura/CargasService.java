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

    public CargasService(Repositorios.Cargas cargas, Repositorios.Lancamentos lancamentos, ModeloEstruturalFonte modelo,
                         AcessoObra acesso, ContextoUsuario contexto, Json json) {
        this.cargas = cargas;
        this.lancamentos = lancamentos;
        this.modelo = modelo;
        this.acesso = acesso;
        this.contexto = contexto;
        this.json = json;
    }

    public record NovaCarga(String pavimentoId, NaturezaAcao natureza, TipoCarga tipo, double valor, String elementoAlvoId, String descricao, String casoCarga) {
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
        var c = cargas.save(new Carga(u.empresaAtivaId(), obraId, n.pavimentoId(), n.natureza(), n.tipo(), n.valor(), n.elementoAlvoId(), n.descricao(), n.casoCarga()));
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
        lancamentos.findByObraIdAndPavimentoId(obraId, c.getPavimentoId()).ifPresent(l -> regerarCombinacoes(obraId, l));
    }

    public LancamentoEstrutural definirVento(UUID obraId, String pavimentoId, ParametrosVento vento) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var l = lancamento(obraId, pavimentoId, contexto.exigir().empresaAtivaId());
        l.setVentoJson(json.escrever(vento));
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
