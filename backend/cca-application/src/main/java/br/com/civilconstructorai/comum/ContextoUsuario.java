package br.com.civilconstructorai.comum;

import java.util.Optional;

/** Porta: quem é o usuário desta requisição (implementada pela camada web a partir da sessão). */
public interface ContextoUsuario {

    Optional<UsuarioAtual> atual();

    default UsuarioAtual exigir() {
        return atual().orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre para continuar."));
    }
}
