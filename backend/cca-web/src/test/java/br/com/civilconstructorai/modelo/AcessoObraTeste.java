package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.UUID;

/**
 * Avaliador de acesso para os ITs deste módulo, baseado só no {@code UsuarioAtual} do SecurityContext
 * (mesma semântica da porta: 404 fora do escopo, 403 papel insuficiente). Substitui o bean {@code obraAccess}
 * real quando {@code spring.main.allow-bean-definition-overriding=true}.
 */
@TestConfiguration
public class AcessoObraTeste {

    @Bean("obraAccess")
    @Primary
    AcessoObra obraAccess(ContextoUsuario contexto) {
        return new AcessoObra() {
            @Override
            public boolean hasObraRole(UUID obraId, String... papeis) {
                var u = contexto.exigir();
                return u.papeisNaObra(obraId).stream().anyMatch(p -> Arrays.asList(papeis).contains(p.name()));
            }

            @Override
            public boolean podeAprovar(UUID obraId) {
                return contexto.exigir().podeAprovar(obraId);
            }

            @Override
            public boolean editaModelo(UUID obraId) {
                return contexto.exigir().papeisNaObra(obraId).stream().anyMatch(PapelObra::editaModelo);
            }

            @Override
            public void exigirMembro(UUID obraId) {
                var u = contexto.exigir();
                if (!u.membroDaObra(obraId)) {
                    throw new ExcecaoDominio.RecursoNaoEncontrado("Obra");
                }
            }

            @Override
            public void exigirPapel(UUID obraId, PapelObra... papeis) {
                exigirMembro(obraId);
                if (!contexto.exigir().temPapelNaObra(obraId, papeis)) {
                    throw new ExcecaoDominio.AcessoNegado("Seu papel nesta obra não permite esta ação.");
                }
            }
        };
    }
}
