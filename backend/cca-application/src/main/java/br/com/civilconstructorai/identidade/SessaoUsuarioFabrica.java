package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.empresa.Repositorios.MembroEmpresaRepository;
import br.com.civilconstructorai.obra.Repositorios.MembroObraRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Monta o principal da sessão a partir dos vínculos da pessoa. Lê membro_empresa/membro_obra em modo sistema
 * porque, no login, ainda não há empresa ativa (o RLS filtra por tenant) — a consulta é restrita ao conta_id.
 */
@Component
public class SessaoUsuarioFabrica {

    private final MembroEmpresaRepository membrosEmpresa;
    private final MembroObraRepository membrosObra;
    private final TransactionTemplate tx;
    private final java.util.Set<String> suporte;

    public SessaoUsuarioFabrica(MembroEmpresaRepository membrosEmpresa, MembroObraRepository membrosObra, TransactionTemplate tx,
                                @org.springframework.beans.factory.annotation.Value("${app.plataforma.suporte:}") java.util.List<String> suporteEmails) {
        this.membrosEmpresa = membrosEmpresa;
        this.membrosObra = membrosObra;
        this.tx = tx;
        this.suporte = suporteEmails == null ? java.util.Set.of() : suporteEmails.stream().map(s -> s.trim().toLowerCase()).collect(java.util.stream.Collectors.toSet());
    }

    public record Vinculo(UUID empresaId, String papelEmpresa, HashMap<UUID, HashSet<String>> papeisPorObra) {
    }

    /** empresas em que a pessoa tem algum vínculo (de empresa ou de obra) */
    public List<UUID> empresasDaPessoa(UUID contaId) {
        return comoSistema(() -> {
            var ids = new java.util.LinkedHashSet<UUID>();
            membrosEmpresa.findByContaId(contaId).forEach(m -> ids.add(m.getTenantId()));
            membrosObra.findByContaId(contaId).forEach(m -> ids.add(m.getTenantId()));
            return List.copyOf(ids);
        });
    }

    public Vinculo vinculoNaEmpresa(UUID contaId, UUID empresaId) {
        return comoSistema(() -> {
            var papelEmpresa = membrosEmpresa.findByTenantIdAndContaId(empresaId, contaId).map(m -> m.getPapel().name()).orElse(null);
            var porObra = new HashMap<UUID, HashSet<String>>();
            for (var m : membrosObra.findByContaIdAndTenantId(contaId, empresaId)) {
                porObra.computeIfAbsent(m.getObraId(), k -> new HashSet<>()).add(m.getPapel().name());
            }
            return new Vinculo(empresaId, papelEmpresa, porObra);
        });
    }

    /** 2FA é obrigatório para DONO/ADMIN e para quem aprova (PapelObra.exigeMfa) em qualquer empresa */
    public boolean exigeMfa(UUID contaId) {
        return comoSistema(() -> membrosEmpresa.findByContaId(contaId).stream().anyMatch(m -> m.getPapel().exigeMfa())
                || membrosObra.findByContaId(contaId).stream().anyMatch(m -> m.getPapel().exigeMfa()));
    }

    public PrincipalSessao principal(Conta conta, Vinculo v, boolean mfaVerificado) {
        return new PrincipalSessao(conta.getId(), v == null ? null : v.empresaId(), v == null ? null : v.papelEmpresa(),
                v == null ? new HashMap<>() : v.papeisPorObra(), mfaVerificado, conta.seloVerificado(), 0L, suporte.contains(conta.getEmail()),
                conta.getEstado() != EstadoConta.PENDING_VERIFICATION);
    }

    private <T> T comoSistema(java.util.concurrent.Callable<T> acao) {
        try {
            return ContextoTenant.comoSistema(() -> tx.execute(status -> {
                try {
                    return acao.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean algumPapelExigeMfa(Iterable<PapelObra> papeis) {
        for (var p : papeis) {
            if (p.exigeMfa()) {
                return true;
            }
        }
        return false;
    }
}
