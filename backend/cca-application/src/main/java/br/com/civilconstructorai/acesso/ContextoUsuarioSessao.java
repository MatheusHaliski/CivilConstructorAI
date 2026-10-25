package br.com.civilconstructorai.acesso;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.identidade.PrincipalSessao;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Fonte do {@link UsuarioAtual}: o principal da sessão ({@link PrincipalSessao}, serializável no Valkey) ou,
 * nos testes de serviço, um {@link UsuarioAtual} colocado direto no SecurityContext. {@code @Primary} porque a
 * camada web já registra um ContextoUsuario mínimo.
 */
@Component
@Primary
public class ContextoUsuarioSessao implements ContextoUsuario {

    @Override
    public Optional<UsuarioAtual> atual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        var p = auth.getPrincipal();
        if (p instanceof PrincipalSessao ps) {
            return Optional.of(ps.paraUsuarioAtual(Instant.now().getEpochSecond()));
        }
        if (p instanceof UsuarioAtual u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }
}
