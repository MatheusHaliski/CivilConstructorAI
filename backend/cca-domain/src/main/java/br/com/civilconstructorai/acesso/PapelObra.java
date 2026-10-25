package br.com.civilconstructorai.acesso;

import java.util.Set;

/**
 * Papéis por obra (RF21). O nível define quem pode convidar quem: um convite nunca promove alguém acima do
 * papel de quem convidou (RF20). {@code aprovador} marca quem pode aprovar orçamento, medição, cálculo e
 * checklist — e, por isso, precisa de 2FA e de selo profissional verificado (RF21, RF27).
 */
public enum PapelObra {
    DONO(4, true, true),
    ADMIN(3, true, true),
    ENGENHEIRO(2, true, true),
    ARQUITETO(2, true, false),
    ORCAMENTISTA(1, false, false),
    MESTRE_OBRAS(1, false, false),
    TECNICO_SEGURANCA(1, false, false),
    CLIENTE(0, false, false),
    FORNECEDOR(0, false, false),
    TRABALHADOR(0, false, false);

    private final int nivel;
    private final boolean aprovador;
    private final boolean exigeMfa;

    PapelObra(int nivel, boolean aprovador, boolean exigeMfa) {
        this.nivel = nivel;
        this.aprovador = aprovador;
        this.exigeMfa = exigeMfa;
    }

    public int nivel() {
        return nivel;
    }

    public boolean aprovador() {
        return aprovador;
    }

    public boolean exigeMfa() {
        return exigeMfa;
    }

    /** papel de escopo de empresa (vale para todas as obras) */
    public boolean escopoEmpresa() {
        return this == DONO || this == ADMIN;
    }

    public boolean podeConvidar(PapelObra alvo) {
        return nivel >= 2 && alvo.nivel <= nivel;
    }

    public boolean editaModelo() {
        return this == ENGENHEIRO || this == ARQUITETO || escopoEmpresa();
    }

    public boolean veDadoDeSaude() {
        return this == TECNICO_SEGURANCA;
    }

    public static final Set<PapelObra> TODOS = Set.of(values());
}
