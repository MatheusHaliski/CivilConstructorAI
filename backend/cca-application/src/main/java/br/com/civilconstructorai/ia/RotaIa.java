package br.com.civilconstructorai.ia;

/**
 * Catálogo de rotas de IA (DN-04, ADR-003): só estas cinco no MVP. Cada rota declara finalidade, base legal,
 * {@code effort} e o que pode ser enviado. Mudar modelo, prompt ou effort exige o conjunto de avaliação da rota.
 */
public enum RotaIa {
    COMANDO_VI("comando-vi", "RF02", "traduzir texto natural em comandos VI", "Art. 7º, V", Effort.MEDIUM, "geometria, eixos, pavimentos, materiais"),
    LANCAMENTO_ESTRUTURAL("lancamento-estrutural", "RF04", "sugerir lançamento de pilares, vigas e lajes", "Art. 7º, V", Effort.HIGH, "arquitetura do pavimento, eixos, materiais"),
    EXTRACAO_BOLETIM_SPT("extracao-boletim-spt", "RF09", "ler boletim de sondagem (PDF)", "Art. 7º, V", Effort.HIGH, "boletim de sondagem sem dados pessoais"),
    ASSOCIACAO_SINAPI("associacao-sinapi", "RF12", "sugerir composição SINAPI por quantitativo", "Art. 7º, V", Effort.HIGH, "quantitativos, materiais, tabela SINAPI"),
    CORRECAO_AUDITORIA("correcao-auditoria", "RF16", "corrigir comando reprovado pelo auditor", "Art. 7º, V", Effort.HIGH, "comando VI e violações estruturadas");

    public enum Effort { LOW, MEDIUM, HIGH }

    public static final String MODELO_PADRAO = "claude-opus-5-5";

    private final String id;
    private final String rf;
    private final String finalidade;
    private final String baseLegal;
    private final Effort effort;
    private final String dadosPermitidos;

    RotaIa(String id, String rf, String finalidade, String baseLegal, Effort effort, String dadosPermitidos) {
        this.id = id;
        this.rf = rf;
        this.finalidade = finalidade;
        this.baseLegal = baseLegal;
        this.effort = effort;
        this.dadosPermitidos = dadosPermitidos;
    }

    public String id() { return id; }
    public String rf() { return rf; }
    public String finalidade() { return finalidade; }
    public String baseLegal() { return baseLegal; }
    public Effort effort() { return effort; }
    public String dadosPermitidos() { return dadosPermitidos; }
}
