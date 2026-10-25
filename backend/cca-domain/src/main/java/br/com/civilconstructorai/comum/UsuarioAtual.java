package br.com.civilconstructorai.comum;

import br.com.civilconstructorai.acesso.PapelObra;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quem está agindo: vem da sessão opaca no Valkey (RF19). {@code papeisPorObra} é o que o avaliador
 * {@code hasObraRole} consulta (RF21); {@code papelEmpresa} é DONO/ADMIN quando houver.
 */
public record UsuarioAtual(
        UUID contaId,
        UUID empresaAtivaId,
        Optional<PapelObra> papelEmpresa,
        Map<UUID, Set<PapelObra>> papeisPorObra,
        boolean mfaVerificado,
        boolean seloVerificado,
        boolean sudoAtivo) {

    public Set<PapelObra> papeisNaObra(UUID obraId) {
        var set = new java.util.HashSet<>(papeisPorObra.getOrDefault(obraId, Set.of()));
        papelEmpresa.ifPresent(set::add);
        return set;
    }

    public boolean temPapelNaObra(UUID obraId, PapelObra... aceitos) {
        var meus = papeisNaObra(obraId);
        for (var p : aceitos) {
            if (meus.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean membroDaObra(UUID obraId) {
        return papelEmpresa.isPresent() || papeisPorObra.containsKey(obraId);
    }

    /** aprovar orçamento, medição, cálculo ou checklist: papel aprovador + selo + 2FA (RF21, RF27) */
    public boolean podeAprovar(UUID obraId) {
        return papeisNaObra(obraId).stream().anyMatch(PapelObra::aprovador) && seloVerificado && mfaVerificado;
    }
}
