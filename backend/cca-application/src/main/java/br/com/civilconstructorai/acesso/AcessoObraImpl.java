package br.com.civilconstructorai.acesso;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.obra.Repositorios.ObraRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Bean {@code obraAccess}: camada 2 do controle de acesso (RF21). Lê os papéis do {@link UsuarioAtual} da
 * sessão. Fora do escopo → 404; papel insuficiente → 403 com motivo em pt-BR.
 * <p>
 * Papel de empresa (DONO/ADMIN) vale para as obras <em>da empresa ativa</em>, não para qualquer id: sem a conferência
 * {@link #noEscopo}, uma conta da empresa B passava por {@code exigirMembro} com o id de uma obra da empresa A, o RLS
 * escondia as linhas e o serviço criava dados no tenant B apontando para a obra de A (o modelo da dona ficava
 * impossível de gravar por violar {@code modelo_bim.obra_id unique}). Membro explícito já vem filtrado pelo tenant
 * na montagem da sessão; o papel de empresa é conferido no banco (com RLS ligado).
 */
@Component("obraAccess")
public class AcessoObraImpl implements AcessoObra {

    private final ContextoUsuario contexto;
    private final ObraRepository obras;

    public AcessoObraImpl(ContextoUsuario contexto, ObraRepository obras) {
        this.contexto = contexto;
        this.obras = obras;
    }

    /** a obra é da empresa ativa e a pessoa é membro dela (explícito ou por papel de empresa) */
    boolean noEscopo(UsuarioAtual u, UUID obraId) {
        if (obraId == null || !u.membroDaObra(obraId)) {
            return false;
        }
        if (u.papeisPorObra().containsKey(obraId)) {
            return true;
        }
        return u.empresaAtivaId() != null && obras.findByIdAndTenantId(obraId, u.empresaAtivaId()).isPresent();
    }

    @Override
    public boolean hasObraRole(UUID obraId, String... papeis) {
        var u = contexto.atual().orElse(null);
        if (u == null || obraId == null) {
            return false;
        }
        var aceitos = Arrays.stream(papeis).map(PapelObra::valueOf).toArray(PapelObra[]::new);
        return u.temPapelNaObra(obraId, aceitos) && noEscopo(u, obraId);
    }

    @Override
    public boolean podeAprovar(UUID obraId) {
        return contexto.atual().map(u -> u.podeAprovar(obraId) && noEscopo(u, obraId)).orElse(false);
    }

    @Override
    public boolean editaModelo(UUID obraId) {
        return contexto.atual().map(u -> u.papeisNaObra(obraId).stream().anyMatch(PapelObra::editaModelo) && noEscopo(u, obraId)).orElse(false);
    }

    @Override
    public void exigirMembro(UUID obraId) {
        var u = contexto.exigir();
        if (!noEscopo(u, obraId)) {
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
