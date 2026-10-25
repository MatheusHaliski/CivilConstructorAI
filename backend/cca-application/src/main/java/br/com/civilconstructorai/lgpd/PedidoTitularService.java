package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.lgpd.Portas.ConfigLgpd;
import br.com.civilconstructorai.lgpd.Repositorios.PedidoTitularRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF25 · pedidos do titular (Art. 18) com prazo de 15 dias e fila da empresa controladora. */
@Service
public class PedidoTitularService {

    private final PedidoTitularRepository pedidos;
    private final ContextoUsuario contexto;
    private final Relogio relogio;
    private final RegistroOperacoes registro;
    private final EnviadorEmail email;
    private final ConfigLgpd config;

    public PedidoTitularService(PedidoTitularRepository pedidos, ContextoUsuario contexto, Relogio relogio, RegistroOperacoes registro, EnviadorEmail email, ConfigLgpd config) {
        this.pedidos = pedidos;
        this.contexto = contexto;
        this.relogio = relogio;
        this.registro = registro;
        this.email = email;
        this.config = config;
    }

    @Transactional
    public PedidoTitular abrir(PedidoTitular.Tipo tipo, UUID empresaId, String categoria, String descricao) {
        var u = contexto.exigir();
        if (descricao == null || descricao.isBlank()) {
            throw new ExcecaoDominio.Validacao("descricao", "Descreva o pedido.");
        }
        var p = pedidos.save(new PedidoTitular(u.contaId(), empresaId, tipo, categoria == null ? "GERAL" : categoria, descricao.trim(), relogio.agora()));
        registro.registrar(RegistroOperacoes.Operacao.de("PEDIDO_TITULAR_ABERTO", u.contaId(), empresaId, null, "pedido_titular", p.getId()));
        return p;
    }

    @Transactional(readOnly = true)
    public List<PedidoTitular> meus() {
        return pedidos.findByContaIdOrderByCriadoEmDesc(contexto.exigir().contaId());
    }

    /** fila da empresa: só DONO/ADMIN da empresa ativa */
    @Transactional(readOnly = true)
    public List<PedidoTitular> filaDaEmpresa(UUID empresaId) {
        exigirAdminDaEmpresa(empresaId);
        return pedidos.findByTenantIdOrderByPrazoEmAsc(empresaId);
    }

    @Transactional
    public PedidoTitular responder(UUID empresaId, UUID pedidoId, PedidoTitular.Estado novoEstado, String resposta) {
        var u = exigirAdminDaEmpresa(empresaId);
        var p = pedidos.findByIdAndTenantId(pedidoId, empresaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Pedido"));
        p.transicionar(novoEstado, u.contaId(), resposta, relogio.agora());
        registro.registrar(new RegistroOperacoes.Operacao("PEDIDO_TITULAR_" + novoEstado, u.contaId(), empresaId, null, "pedido_titular", p.getId(), Map.of()));
        return p;
    }

    /** marca atrasados e avisa Encarregado + admin faltando 3 dias (CA-06) — roda de hora em hora */
    @Scheduled(cron = "0 7 * * * *")
    @Transactional
    public void vigiarPrazos() {
        var agora = relogio.agora();
        for (var p : pedidos.vencidos(agora)) {
            if (p.marcarAtrasadoSe(agora)) {
                pedidos.save(p);
                if (!config.dpoEmail().isBlank()) {
                    email.enviar(config.dpoEmail(), "Pedido de titular atrasado", "O pedido " + p.getId() + " (" + p.getTipo() + ") passou do prazo de " + config.prazoRespostaDias() + " dias.");
                }
            }
        }
        for (var p : pedidos.findAll()) {
            if (p.getEstado().aberto() && p.getEstado() != PedidoTitular.Estado.ATRASADO
                    && Duration.between(agora, p.getPrazoEm()).toDays() <= 3 && Duration.between(agora, p.getPrazoEm()).toHours() >= 71
                    && !config.dpoEmail().isBlank()) {
                email.enviar(config.dpoEmail(), "Pedido de titular vence em 3 dias", "O pedido " + p.getId() + " (" + p.getTipo() + ") vence em " + p.getPrazoEm() + ".");
            }
        }
    }

    private br.com.civilconstructorai.comum.UsuarioAtual exigirAdminDaEmpresa(UUID empresaId) {
        var u = contexto.exigir();
        if (!empresaId.equals(u.empresaAtivaId())) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Empresa");
        }
        if (u.papelEmpresa().filter(p -> p == PapelObra.DONO || p == PapelObra.ADMIN).isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Só dono ou administrador da empresa atende pedidos de titulares.");
        }
        return u;
    }
}
