package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.UsuarioAtual;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Transient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Autenticação de teste <em>transiente</em>: o Spring Security não a grava na sessão (Valkey), então o
 * {@link UsuarioAtual} não precisa ser serializável para os testes de controlador passarem pelo filtro de sessão.
 */
@Transient
public final class AutenticacaoTeste extends AbstractAuthenticationToken {

    private final UsuarioAtual usuario;

    private AutenticacaoTeste(UsuarioAtual usuario) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        this.usuario = usuario;
        setAuthenticated(true);
    }

    /** {@code mvc.perform(get(...).with(AutenticacaoTeste.como(usuario)))} */
    public static RequestPostProcessor como(UsuarioAtual usuario) {
        return authentication(new AutenticacaoTeste(usuario));
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
