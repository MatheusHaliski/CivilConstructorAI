package br.com.civilconstructorai.lgpd;

/**
 * Categorias de dado pessoal tratadas, com finalidade, base legal e retenção mínima (DN-03). A empresa pode
 * configurar prazos maiores ({@code retencao_empresa}), nunca menores.
 */
public enum CategoriaDado {
    CONTA("Dados da conta", "autenticação e segurança da conta", "Art. 7º, V (contrato)", 0, "até a exclusão da conta", false, false),
    PERFIL_PROFISSIONAL("Perfil profissional", "verificação de CREA/CAU/CNPJ e responsabilidade técnica", "Art. 7º, II e V", 60, "5 anos após o desligamento (prazo de ações da obra)", false, false),
    CONSENTIMENTOS("Consentimentos", "prova do consentimento e da revogação", "Art. 8º §5º", 60, "5 anos após a revogação", false, false),
    SESSOES_SEGURANCA("Sessões e alertas de segurança", "proteção da conta", "Art. 7º, IX · Art. 46", 6, "6 meses", false, false),
    SAUDE_OCUPACIONAL("Saúde ocupacional (ASO, afastamentos)", "obrigação legal da empresa (NR-7)", "Art. 11, II, a · NR-7", 240, "20 anos após o desligamento", true, true),
    TREINAMENTO_SEGURANCA("Treinamentos e registros de segurança", "obrigação legal da empresa (NR-1, NR-18)", "Art. 7º, II · NR-1", 240, "20 anos após o desligamento", false, true),
    DOCUMENTOS_TRABALHISTAS("Documentos trabalhistas, presença e ponto", "obrigação legal da empresa (CLT)", "Art. 7º, II · CLT art. 11", 60, "5 anos após o desligamento", false, true),
    FOTOS_CANTEIRO("Fotos do canteiro com pessoas identificáveis", "registro da execução da obra", "Art. 7º, V e IX", 60, "duração da obra + 5 anos", false, true),
    CONTATO_OPERACIONAL("Contato operacional (telefone, e-mail)", "operação da obra", "Art. 7º, V", 3, "enquanto for membro + 90 dias", false, true);

    private final String titulo;
    private final String finalidade;
    private final String baseLegal;
    private final int minimoMeses;
    private final String descricaoPrazo;
    private final boolean sensivel;
    private final boolean deTrabalhador;

    CategoriaDado(String titulo, String finalidade, String baseLegal, int minimoMeses, String descricaoPrazo, boolean sensivel, boolean deTrabalhador) {
        this.titulo = titulo;
        this.finalidade = finalidade;
        this.baseLegal = baseLegal;
        this.minimoMeses = minimoMeses;
        this.descricaoPrazo = descricaoPrazo;
        this.sensivel = sensivel;
        this.deTrabalhador = deTrabalhador;
    }

    public String titulo() { return titulo; }
    public String finalidade() { return finalidade; }
    public String baseLegal() { return baseLegal; }
    /** retenção mínima legal em meses (0 = enquanto a conta existir) */
    public int minimoMeses() { return minimoMeses; }
    public String descricaoPrazo() { return descricaoPrazo; }
    /** dado de saúde: só técnico(a) de segurança e RH da obra; no inventário aparece só a contagem */
    public boolean sensivel() { return sensivel; }
    /** categoria que a construtora guarda sobre trabalhadores (ela é a controladora) */
    public boolean deTrabalhador() { return deTrabalhador; }
}
