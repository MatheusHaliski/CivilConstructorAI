package br.com.civilconstructorai.modelo;

/** Comando reprovado pelo auditor (422 com o relatório completo em {@code relatorio}). */
public class ComandoReprovadoException extends RuntimeException {

    private final transient RelatorioAuditoria relatorio;

    public ComandoReprovadoException(RelatorioAuditoria relatorio) {
        super("Comando reprovado pelo auditor: " + relatorio.todasAsViolacoes().size() + " violação(ões).");
        this.relatorio = relatorio;
    }

    public RelatorioAuditoria relatorio() {
        return relatorio;
    }
}
