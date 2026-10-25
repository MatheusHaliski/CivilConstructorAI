package br.com.civilconstructorai.modelo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** Invalida, por SQL na transação do comando (RLS ativo), os resultados que dependiam da versão anterior do modelo. */
@Component
public class DependentesDoModeloJdbc implements DependentesDoModelo {

    private final JdbcTemplate jdbc;

    public DependentesDoModeloJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void modeloMudou(UUID obraId, long versaoNova, boolean estrutural, boolean construtivo, Set<String> pavimentos) {
        // pranchas geradas ou emitidas de versões anteriores deixam de representar o modelo (RF14 CA-03)
        jdbc.update("update prancha set estado = 'OBSOLETA' where obra_id = ? and versao_modelo < ? and estado in ('GERADA','EMITIDA')", obraId, versaoNova);
        if (construtivo || estrutural) {
            jdbc.update("update levantamento_quantitativo set estado = 'OBSOLETO', atualizado_em = now() where obra_id = ? and versao_modelo < ? and estado = 'CONCLUIDO'", obraId, versaoNova);
        }
        if (estrutural) {
            obsoletarCalculos(obraId, versaoNova);
            if (pavimentos.isEmpty()) {
                jdbc.update("update lancamento_estrutural set estado = 'EM_LANCAMENTO', atualizado_em = now() where obra_id = ? and estado = 'PRONTO_PARA_ANALISE'", obraId);
            } else {
                for (var p : pavimentos) {
                    jdbc.update("update lancamento_estrutural set estado = 'EM_LANCAMENTO', atualizado_em = now() where obra_id = ? and pavimento_id = ? and estado = 'PRONTO_PARA_ANALISE'", obraId, p);
                }
            }
        }
    }

    @Override
    public void cargasMudaram(UUID obraId, String pavimentoId) {
        obsoletarCalculos(obraId, Long.MAX_VALUE);
        jdbc.update("update lancamento_estrutural set estado = 'CARREGADO', atualizado_em = now() where obra_id = ? and pavimento_id = ? and estado = 'PRONTO_PARA_ANALISE'", obraId, pavimentoId);
    }

    @Override
    public void baseCalculoMudou(UUID obraId) {
        obsoletarCalculos(obraId, Long.MAX_VALUE);
    }

    private void obsoletarCalculos(UUID obraId, long versaoNova) {
        jdbc.update("update analise_estrutural set estado = 'OBSOLETO', atualizado_em = now() where obra_id = ? and versao_modelo < ? and estado in ('PENDENTE','CONCLUIDO')", obraId, versaoNova);
        jdbc.update("update dimensionamento set estado = 'OBSOLETO', atualizado_em = now() where obra_id = ? and versao_modelo < ? "
                + "and estado in ('CALCULADO','GERADO_POR_IA','EM_REVISAO','APLICADO_NO_MODELO','APROVADO')", obraId, versaoNova);
        jdbc.update("update dimensionamento_fundacao set estado = 'OBSOLETO', atualizado_em = now() where obra_id = ? and versao_modelo < ? "
                + "and estado in ('CALCULADO','GERADO_POR_IA','EM_REVISAO','APROVADO')", obraId, versaoNova);
    }
}
