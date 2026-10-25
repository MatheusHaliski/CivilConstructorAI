package br.com.civilconstructorai.config;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.UsuarioAtual;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Lê o {@link UsuarioAtual} do SecurityContext. O principal é colocado ali pelo módulo de identidade no login
 * (RF19) e reidratado da sessão opaca a cada requisição.
 */
@Component
public class ContextoUsuarioWeb implements ContextoUsuario {

    @Override
    public Optional<UsuarioAtual> atual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UsuarioAtual u)) {
            return Optional.empty();
        }
        return Optional.of(u);
    }
}
