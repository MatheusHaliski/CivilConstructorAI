package br.com.civilconstructorai.acesso;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.UsuarioAtual;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Bean {@code obraAccess}: camada 2 do controle de acesso (RF21). Lê os papéis do {@link UsuarioAtual} da
 * sessão — por isso nunca toca o banco. Fora do escopo → 404; papel insuficiente → 403 com motivo em pt-BR.
 */
@Component("obraAccess")
public class AcessoObraImpl implements AcessoObra {

    private final ContextoUsuario contexto;

    public AcessoObraImpl(ContextoUsuario contexto) {
        this.contexto = contexto;
    }

    @Override
    public boolean hasObraRole(UUID obraId, String... papeis) {
        var u = contexto.atual().orElse(null);
        if (u == null || obraId == null) {
            return false;
        }
        var aceitos = Arrays.stream(papeis).map(PapelObra::valueOf).toArray(PapelObra[]::new);
        return u.temPapelNaObra(obraId, aceitos);
    }

    @Override
    public boolean podeAprovar(UUID obraId) {
        return contexto.atual().map(u -> u.podeAprovar(obraId)).orElse(false);
    }

    @Override
    public boolean editaModelo(UUID obraId) {
        return contexto.atual().map(u -> u.papeisNaObra(obraId).stream().anyMatch(PapelObra::editaModelo)).orElse(false);
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
        var u = contexto.exigir();
        if (!u.temPapelNaObra(obraId, papeis)) {
            throw new ExcecaoDominio.AcessoNegado("Esta ação exige o papel " + nomes(papeis) + " nesta obra.");
        }
    }

    private static String nomes(PapelObra... papeis) {
        return String.join(" ou ", Arrays.stream(papeis).map(Enum::name).toList());
    }
}
