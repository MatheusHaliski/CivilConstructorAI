package br.com.civilconstructorai.comum;

import br.com.civilconstructorai.acesso.PapelObra;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quem está agindo: vem da sessão opaca no Valkey (RF19). {@code papeisPorObra} é o que o avaliador
 * {@code hasObraRole} consulta (RF21); {@code papelEmpresa} é DONO/ADMIN quando houver.
 * <p>
 * É gravado na sessão (Spring Session), então precisa ser serializável: como {@link Optional} não é, a
 * serialização passa por {@link Forma}, uma cópia com campos simples ({@code writeReplace}/{@code readResolve}).
 */
public record UsuarioAtual(
        UUID contaId,
        UUID empresaAtivaId,
        Optional<PapelObra> papelEmpresa,
        Map<UUID, Set<PapelObra>> papeisPorObra,
        boolean mfaVerificado,
        boolean seloVerificado,
        boolean sudoAtivo) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public UsuarioAtual {
        papelEmpresa = papelEmpresa == null ? Optional.empty() : papelEmpresa;
        papeisPorObra = papeisPorObra == null ? Map.of() : Map.copyOf(papeisPorObra);
    }

    public Set<PapelObra> papeisNaObra(UUID obraId) {
        var set = new HashSet<>(papeisPorObra.getOrDefault(obraId, Set.of()));
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

    public UsuarioAtual comSudo(boolean ativo) {
        return new UsuarioAtual(contaId, empresaAtivaId, papelEmpresa, papeisPorObra, mfaVerificado, seloVerificado, ativo);
    }

    public UsuarioAtual comEmpresaAtiva(UUID empresaId, Optional<PapelObra> papelNaEmpresa, Map<UUID, Set<PapelObra>> papeis) {
        return new UsuarioAtual(contaId, empresaId, papelNaEmpresa, papeis, mfaVerificado, seloVerificado, sudoAtivo);
    }

    @Serial
    private Object writeReplace() {
        return new Forma(this);
    }

    /** forma serializável (sem Optional, com coleções mutáveis padrão do JDK) */
    static final class Forma implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final UUID contaId;
        private final UUID empresaAtivaId;
        private final PapelObra papelEmpresa;
        private final HashMap<UUID, HashSet<PapelObra>> papeisPorObra = new HashMap<>();
        private final boolean mfaVerificado;
        private final boolean seloVerificado;
        private final boolean sudoAtivo;

        Forma(UsuarioAtual u) {
            this.contaId = u.contaId;
            this.empresaAtivaId = u.empresaAtivaId;
            this.papelEmpresa = u.papelEmpresa.orElse(null);
            u.papeisPorObra.forEach((k, v) -> papeisPorObra.put(k, new HashSet<>(v)));
            this.mfaVerificado = u.mfaVerificado;
            this.seloVerificado = u.seloVerificado;
            this.sudoAtivo = u.sudoAtivo;
        }

        @Serial
        private Object readResolve() {
            var papeis = new HashMap<UUID, Set<PapelObra>>();
            papeisPorObra.forEach((k, v) -> papeis.put(k, Set.copyOf(v)));
            return new UsuarioAtual(contaId, empresaAtivaId, Optional.ofNullable(papelEmpresa), papeis, mfaVerificado, seloVerificado, sudoAtivo);
        }
    }
}
