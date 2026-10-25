package br.com.civilconstructorai.support;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Para testes de serviço/controlador que não passam pelo login real: coloca um {@link UsuarioAtual} no
 * SecurityContext (mesmo principal que o login do RF19 produz). Testes de API ponta a ponta usam
 * {@code ApiClient}, que faz login de verdade com CSRF e sessão.
 */
public final class TesteSeguranca {

    private TesteSeguranca() {
    }

    public static Authentication autenticacao(UsuarioAtual u) {
        return new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
    }

    public static UsuarioAtual usuario(UUID contaId, UUID empresaId, UUID obraId, PapelObra papel, boolean mfa, boolean selo) {
        var papelEmpresa = papel.escopoEmpresa() ? Optional.of(papel) : Optional.<PapelObra>empty();
        var porObra = obraId == null || papel.escopoEmpresa() ? Map.<UUID, Set<PapelObra>>of() : Map.of(obraId, Set.of(papel));
        return new UsuarioAtual(contaId, empresaId, papelEmpresa, porObra, mfa, selo, false);
    }

    public static void autenticar(UsuarioAtual u) {
        SecurityContextHolder.getContext().setAuthentication(autenticacao(u));
    }

    public static void limpar() {
        SecurityContextHolder.clearContext();
    }
}
