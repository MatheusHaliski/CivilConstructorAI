package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.comum.UsuarioAtual;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Transient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Autenticação de teste <em>transiente</em> para os ITs de modelo/prancha/interop: o Spring Security não a grava
 * na sessão (Valkey), então o {@link UsuarioAtual} (record não serializável) passa pelo filtro de sessão.
 * O {@code ContextoUsuario} da aplicação aceita um {@link UsuarioAtual} como principal.
 */
@Transient
public final class ComoUsuario extends AbstractAuthenticationToken {

    private final UsuarioAtual usuario;

    private ComoUsuario(UsuarioAtual usuario) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        this.usuario = usuario;
        setAuthenticated(true);
    }

    /** {@code mvc.perform(get(...).with(ComoUsuario.como(usuario)))} */
    public static RequestPostProcessor como(UsuarioAtual usuario) {
        return authentication(new ComoUsuario(usuario));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return usuario;
    }
}
