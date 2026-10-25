package br.com.civilconstructorai.acesso;

import java.util.UUID;

/**
 * Camada 2 do controle de acesso (RF21): verificação de papel na obra no serviço, nunca só na tela.
 * Uso: {@code @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'ENGENHEIRO')")}. Implementação registrada
 * como bean {@code obraAccess} pelo módulo de identidade. Fora do escopo (não membro da obra/empresa) o
 * chamador recebe 404 — {@link #exigirMembro(UUID)} —, papel insuficiente recebe 403.
 */
public interface AcessoObra {

    boolean hasObraRole(UUID obraId, String... papeis);

    boolean podeAprovar(UUID obraId);

    boolean editaModelo(UUID obraId);

    /** lança {@code RecursoNaoEncontrado} quando a obra não é da empresa ativa ou a pessoa não é membro */
    void exigirMembro(UUID obraId);

    /** lança {@code AcessoNegado} com o motivo em pt-BR quando o papel é insuficiente */
    void exigirPapel(UUID obraId, PapelObra... papeis);
}
