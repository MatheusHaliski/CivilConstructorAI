package br.com.civilconstructorai.ia;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.ia.Repositorios.PedidoRevisaoHumanaRepository;
import br.com.civilconstructorai.ia.Repositorios.RascunhoIaRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF27 · ciclo de vida do rascunho gerado por IA. Aprovar exige papel aprovador + selo VERIFIED + 2FA
 * ({@link UsuarioAtual#podeAprovar}) e ART/RRT; tudo vai para o registro de operações (Art. 37).
 */
@Service
public class RascunhoIaService {

    private final RascunhoIaRepository rascunhos;
    private final PedidoRevisaoHumanaRepository revisoes;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final AcessoObra acesso;
    private final org.springframework.beans.factory.ObjectProvider<EfeitoDoRascunho> efeitos;

    public RascunhoIaService(RascunhoIaRepository rascunhos, PedidoRevisaoHumanaRepository revisoes, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, AcessoObra acesso,
                             org.springframework.beans.factory.ObjectProvider<EfeitoDoRascunho> efeitos) {
        this.efeitos = efeitos;
        this.acesso = acesso;
        this.rascunhos = rascunhos;
        this.revisoes = revisoes;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
    }

    /** chamado pelos módulos que usam IA (comando VI, lançamento, SINAPI, SPT, correção) */
    @Transactional
    public RascunhoIa criar(UUID tenantId, UUID obraId, String tipo, RotaIa rota, String entidadeRef, String conteudoJson, UUID criadoPor, UUID chamadaId, boolean recusado) {
        // um novo rascunho para a mesma entidade torna os pendentes anteriores obsoletos
        if (entidadeRef != null) {
            for (var antigo : rascunhos.findByObraIdAndEntidadeRefAndEstadoIn(obraId, entidadeRef, List.of(EstadoRascunhoIa.GERADO_POR_IA, EstadoRascunhoIa.EM_REVISAO))) {
                antigo.obsoletar("substituído por novo rascunho");
                rascunhos.save(antigo);
            }
        }
        var r = rascunhos.save(new RascunhoIa(tenantId, obraId, tipo, rota.id(), entidadeRef, conteudoJson, criadoPor, chamadaId, recusado));
        registro.registrar(new RegistroOperacoes.Operacao("RASCUNHO_IA_CRIADO", criadoPor, tenantId, obraId, "rascunho_ia", r.getId(), Map.of("tipo", tipo, "rota", rota.id())));
        return r;
    }

    @Transactional(readOnly = true)
    public List<RascunhoIa> listar(UUID obraId, EstadoRascunhoIa estado) {
        var u = exigirMembro(obraId);
        // DN-02: cliente e fornecedor nunca veem rascunho de IA (auditoria 2026-10: a lista aparecia para o cliente)
        if (soClienteOuFornecedor(u, obraId)) {
            return List.of();
        }
        return estado == null ? rascunhos.findByObraIdOrderByCriadoEmDesc(obraId) : rascunhos.findByObraIdAndEstadoOrderByCriadoEmDesc(obraId, estado);
    }

    @Transactional(readOnly = true)
    public RascunhoIa obter(UUID obraId, UUID id) {
        var u = exigirMembro(obraId);
        if (soClienteOuFornecedor(u, obraId)) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Rascunho");
        }
        return rascunhos.findByIdAndObraId(id, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Rascunho"));
    }

    @Transactional
    public RascunhoIa enviarParaRevisao(UUID obraId, UUID id) {
        var u = exigirMembro(obraId);
        var r = obter(obraId, id);
        r.enviarParaRevisao();
        registro.registrar(RegistroOperacoes.Operacao.de("RASCUNHO_IA_EM_REVISAO", u.contaId(), u.empresaAtivaId(), obraId, "rascunho_ia", id));
        return rascunhos.save(r);
    }

    @Transactional
    public RascunhoIa aprovar(UUID obraId, UUID id, String artRrt) {
        var u = exigirMembro(obraId);
        if (!u.podeAprovar(obraId)) {
            throw new ExcecaoDominio.AcessoNegado(motivoNaoAprova(u, obraId));
        }
        var r = obter(obraId, id);
        if (r.getEstado() == EstadoRascunhoIa.GERADO_POR_IA) {
            r.enviarParaRevisao();
        }
        r.aprovar(u.contaId(), artRrt, relogio.agora());
        registro.registrar(new RegistroOperacoes.Operacao("RASCUNHO_IA_APROVADO", u.contaId(), u.empresaAtivaId(), obraId, "rascunho_ia", id, Map.of("artRrt", "informada")));
        var salvo = rascunhos.save(r);
        efeitos.orderedStream().filter(e -> e.tipo().equals(r.getTipo())).forEach(e -> e.aoAprovar(obraId, salvo));
        return salvo;
    }

    @Transactional
    public RascunhoIa rejeitar(UUID obraId, UUID id, String motivo) {
        var u = exigirMembro(obraId);
        if (!u.papeisNaObra(obraId).stream().anyMatch(p -> p.aprovador() || p.editaModelo())) {
            throw new ExcecaoDominio.AcessoNegado("Só engenheiro(a), arquiteto(a) ou administrador rejeita um rascunho.");
        }
        var r = obter(obraId, id);
        r.rejeitar(u.contaId(), motivo);
        registro.registrar(RegistroOperacoes.Operacao.de("RASCUNHO_IA_REJEITADO", u.contaId(), u.empresaAtivaId(), obraId, "rascunho_ia", id));
        var salvo = rascunhos.save(r);
        efeitos.orderedStream().filter(e -> e.tipo().equals(r.getTipo())).forEach(e -> e.aoRejeitar(obraId, salvo));
        return salvo;
    }

    @Transactional
    public void obsoletarPorEntidade(UUID obraId, String entidadeRef, String motivo) {
        for (var r : rascunhos.findByObraIdAndEntidadeRefAndEstadoIn(obraId, entidadeRef, List.of(EstadoRascunhoIa.GERADO_POR_IA, EstadoRascunhoIa.EM_REVISAO, EstadoRascunhoIa.APROVADO))) {
            r.obsoletar(motivo);
            rascunhos.save(r);
        }
    }

    /** Art. 20: qualquer membro pode pedir revisão humana de um rascunho que o afete */
    @Transactional
    public PedidoRevisaoHumana pedirRevisaoHumana(UUID obraId, UUID id, String motivo) {
        var u = exigirMembro(obraId);
        var r = obter(obraId, id);
        if (motivo == null || motivo.isBlank()) {
            throw new ExcecaoDominio.Validacao("motivo", "Explique o que deve ser revisado.");
        }
        var p = revisoes.save(new PedidoRevisaoHumana(r.getTenantId(), r.getId(), u.contaId(), motivo.trim(), relogio.agora()));
        registro.registrar(RegistroOperacoes.Operacao.de("REVISAO_HUMANA_PEDIDA", u.contaId(), u.empresaAtivaId(), obraId, "pedido_revisao_humana", p.getId()));
        return p;
    }

    @Transactional
    public PedidoRevisaoHumana responderRevisao(UUID obraId, UUID rascunhoId, UUID pedidoId, String resposta) {
        var u = exigirMembro(obraId);
        if (!u.podeAprovar(obraId)) {
            throw new ExcecaoDominio.AcessoNegado(motivoNaoAprova(u, obraId));
        }
        obter(obraId, rascunhoId);
        var p = revisoes.findById(pedidoId).filter(x -> x.getRascunhoId().equals(rascunhoId)).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Pedido de revisão"));
        p.responder(u.contaId(), resposta, relogio.agora());
        registro.registrar(RegistroOperacoes.Operacao.de("REVISAO_HUMANA_RESPONDIDA", u.contaId(), u.empresaAtivaId(), obraId, "pedido_revisao_humana", p.getId()));
        return revisoes.save(p);
    }

    @Transactional(readOnly = true)
    public List<PedidoRevisaoHumana> revisoes(UUID obraId, UUID rascunhoId) {
        obter(obraId, rascunhoId);
        return revisoes.findByRascunhoIdOrderByCriadoEmDesc(rascunhoId);
    }

    private UsuarioAtual exigirMembro(UUID obraId) {
        acesso.exigirMembro(obraId);
        return contexto.exigir();
    }

    private static boolean soClienteOuFornecedor(UsuarioAtual u, UUID obraId) {
        var papeis = u.papeisNaObra(obraId);
        return !papeis.isEmpty() && papeis.stream().allMatch(p -> p == br.com.civilconstructorai.acesso.PapelObra.CLIENTE || p == br.com.civilconstructorai.acesso.PapelObra.FORNECEDOR);
    }

    private static String motivoNaoAprova(UsuarioAtual u, UUID obraId) {
        if (u.papeisNaObra(obraId).stream().noneMatch(p -> p.aprovador())) {
            return "Só engenheiro(a) responsável, arquiteto(a) ou administrador aprova um rascunho gerado por IA.";
        }
        if (!u.seloVerificado()) {
            return "Seu registro profissional (CREA/CAU) ainda não foi verificado; sem o selo não é possível aprovar.";
        }
        return "Ative a verificação em duas etapas para aprovar resultados gerados por IA.";
    }
}
