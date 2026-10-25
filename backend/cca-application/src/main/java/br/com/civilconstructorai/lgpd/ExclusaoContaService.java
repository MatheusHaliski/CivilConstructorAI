package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.lgpd.Portas.ConfigLgpd;
import br.com.civilconstructorai.lgpd.Portas.SessoesDoUsuario;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF26 · pedir exclusão (modo sudo) → carência de 30 dias → reativação no login ou saga de eliminação. */
@Service
public class ExclusaoContaService {

    public record Consequencias(Instant dataFinal, int carenciaDias, List<String> oQueFica, List<String> oQueSai) {
    }

    private final ContaLgpdDao contas;
    private final ContextoUsuario contexto;
    private final ConfigLgpd config;
    private final Relogio relogio;
    private final RegistroOperacoes registro;
    private final EnviadorEmail email;
    private final SessoesDoUsuario sessoes;

    public ExclusaoContaService(ContaLgpdDao contas, ContextoUsuario contexto, ConfigLgpd config, Relogio relogio, RegistroOperacoes registro, EnviadorEmail email, SessoesDoUsuario sessoes) {
        this.contas = contas;
        this.contexto = contexto;
        this.config = config;
        this.relogio = relogio;
        this.registro = registro;
        this.email = email;
        this.sessoes = sessoes;
    }

    /** o que o alertdialog mostra antes de confirmar */
    @Transactional(readOnly = true)
    public Consequencias consequencias() {
        var u = contexto.exigir();
        var dataFinal = relogio.agora().plus(config.carenciaDias(), ChronoUnit.DAYS);
        var rts = contas.responsabilidadesTecnicas(u.contaId());
        var fica = new java.util.ArrayList<String>();
        if (!rts.isEmpty()) {
            fica.add("Nome e registro profissional nas " + rts.size() + " obra(s) em que você é responsável técnico (ART/RRT) — obrigação legal da construtora.");
        }
        fica.add("Registros que a construtora é obrigada a guardar (documentos trabalhistas e de segurança) pelos prazos legais, com base legal registrada.");
        fica.add("Histórico de consentimentos, pseudonimizado.");
        return new Consequencias(dataFinal, config.carenciaDias(), fica,
                List.of("Perfil, e-mail, foto, preferências e sessões.", "Vínculos com empresas e obras.", "Sua chave de dados é destruída: o que ficou cifrado vira ilegível, inclusive em backups."));
    }

    @Transactional
    public Consequencias solicitar(boolean entendi) {
        var u = contexto.exigir();
        if (!u.sudoAtivo()) {
            throw new ExcecaoDominio.SudoNecessario();
        }
        if (!entendi) {
            throw new ExcecaoDominio.Validacao("entendi", "Marque \"Entendo\" para confirmar a exclusão.");
        }
        var c = contas.exigir(u.contaId());
        if (!c.estado().podeSolicitarExclusao()) {
            throw new ExcecaoDominio.TransicaoInvalida("Conta", c.estado(), EstadoContaLgpd.PENDING_DELETION);
        }
        var dataFinal = relogio.agora().plus(config.carenciaDias(), ChronoUnit.DAYS);
        contas.solicitarExclusao(u.contaId(), dataFinal);
        registro.registrar(new RegistroOperacoes.Operacao("CONTA_EXCLUSAO_SOLICITADA", u.contaId(), null, null, "conta", u.contaId(), Map.of("dataFinal", dataFinal.toString())));
        email.enviar(c.email(), "Exclusão da conta agendada",
                "Sua conta será eliminada em " + dataFinal + ". Para desistir, basta entrar de novo antes dessa data e escolher \"Reativar\".");
        sessoes.encerrarTodas(u.contaId());
        return consequenciasApos(dataFinal);
    }

    private Consequencias consequenciasApos(Instant dataFinal) {
        var base = consequencias();
        return new Consequencias(dataFinal, base.carenciaDias(), base.oQueFica(), base.oQueSai());
    }

    /** oferta no login durante a carência (chamado pelo módulo de identidade em POST /auth/reactivate) */
    @Transactional
    public void reativar(UUID contaId) {
        var c = contas.exigir(contaId);
        if (c.estado() != EstadoContaLgpd.PENDING_DELETION) {
            throw new ExcecaoDominio.TransicaoInvalida("Conta", c.estado(), EstadoContaLgpd.ACTIVE);
        }
        if (c.eliminacaoPrevistaEm() != null && relogio.agora().isAfter(c.eliminacaoPrevistaEm())) {
            throw new ExcecaoDominio.Conflito("A carência terminou; a conta já está em eliminação.");
        }
        contas.reativar(contaId);
        registro.registrar(RegistroOperacoes.Operacao.de("CONTA_REATIVADA", contaId, null, null, "conta", contaId));
    }
}
