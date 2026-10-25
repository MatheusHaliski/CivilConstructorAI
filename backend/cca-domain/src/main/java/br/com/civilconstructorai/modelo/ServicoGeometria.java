package br.com.civilconstructorai.modelo;

import java.util.List;

/**
 * Laço 2 do loop duplo (RF16): validação da geometria do sólido. Implementações: local em Java/JTS
 * ({@link ServicoGeometriaLocal}) e remota no serviço Python (kernel CadQuery/OpenCASCADE), escolhida por
 * {@code app.geometria.remota}. Recebe o modelo rascunho e o diff; devolve violações e avisos.
 */
public interface ServicoGeometria {

    record Resultado(List<RelatorioAuditoria.Violacao> violacoes, List<RelatorioAuditoria.Violacao> avisos) {
    }

    /** laço 2 incremental (edição): envoltória aberta é AVISO */
    Resultado lacoGeometria(ModeloBim rascunho, DiffModelo diff);

    /** verificação de conclusão sobre o pavimento inteiro: envoltória aberta é VIOLAÇÃO */
    List<RelatorioAuditoria.Violacao> verificarConclusao(ModeloBim modelo, java.util.UUID pavimentoId);
}
