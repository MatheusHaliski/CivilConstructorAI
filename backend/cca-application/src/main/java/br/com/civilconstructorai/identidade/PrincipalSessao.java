package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * O que vai para a sessão no Valkey (serialização JDK): só ids e papéis. {@link #getName()} devolve o id da
 * conta, que o Spring Session usa como índice para listar/encerrar sessões por usuário (RF23).
 */
public record PrincipalSessao(
        UUID contaId,
        UUID empresaAtivaId,
        String papelEmpresa,
        HashMap<UUID, HashSet<String>> papeisPorObra,
        boolean mfaVerificado,
        boolean seloVerificado,
        long sudoAteEpoch,
        boolean suportePlataforma,
        /* false enquanto a conta está PENDING_VERIFICATION: só as rotas de verificação/perfil respondem (RF18) */
        boolean emailVerificado,
        /* 2FA obrigatório para o papel (dono/admin/aprovador) e ainda não configurado: só /me/** responde (RF19 CA-06) */
        boolean mfaPendente) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    @Override
    public String getName() {
        return contaId.toString();
    }

    public UsuarioAtual paraUsuarioAtual(long agoraEpoch) {
        var mapa = new HashMap<UUID, Set<PapelObra>>();
        papeisPorObra.forEach((obra, papeis) -> {
            var s = new HashSet<PapelObra>();
            papeis.forEach(p -> s.add(PapelObra.valueOf(p)));
            mapa.put(obra, s);
        });
        return new UsuarioAtual(contaId, empresaAtivaId,
                Optional.ofNullable(papelEmpresa).map(PapelObra::valueOf),
                Map.copyOf(mapa), mfaVerificado, seloVerificado, sudoAteEpoch > agoraEpoch);
    }

    public PrincipalSessao comSudoAte(long epoch) {
        return new PrincipalSessao(contaId, empresaAtivaId, papelEmpresa, papeisPorObra, mfaVerificado, seloVerificado, epoch, suportePlataforma, emailVerificado, mfaPendente);
    }

    /** acabou de configurar o 2FA nesta sessão: a posse do segundo fator foi comprovada */
    public PrincipalSessao comMfaConfigurado() {
        return new PrincipalSessao(contaId, empresaAtivaId, papelEmpresa, papeisPorObra, true, seloVerificado, sudoAteEpoch, suportePlataforma, emailVerificado, false);
    }

    public PrincipalSessao comEmailVerificado() {
        return new PrincipalSessao(contaId, empresaAtivaId, papelEmpresa, papeisPorObra, mfaVerificado, seloVerificado, sudoAteEpoch, suportePlataforma, true, mfaPendente);
    }
}
