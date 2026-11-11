package br.com.civilconstructorai.estrutura;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModeloEstruturalAcabamentosTest {
    private final ModeloEstruturalJdbc fonte=new ModeloEstruturalJdbc(null,new ObjectMapper());
    @Test void recorteTransportaComposicaoIntegracaoEApoio() {
        var e=fonte.converter("w","t","PAREDE","MAT_ALV_BLOCO14","PAR1","""
                {"p1":{"x":0,"y":0},"p2":{"x":5,"y":0},"altura":2.8,"espessura":0.14,
                "integrarPesoAcabamentos":true,"apoioEstruturalId":"v",
                "acabamentos":[{"id":"r","materialId":"MAT_REBOCO","face":"LADO_A","espessura_m":0.02,"pesoEspecifico_kN_m3":20,"perda_percentual":10}]}
                """,false);
        assertThat(e.acabamentos()).hasSize(1);assertThat(e.integrarPesoAcabamentos()).isTrue();
        assertThat(e.apoioEstruturalId()).isEqualTo("v");assertThat(e.rotulo()).isEqualTo("PAR1");
        assertThat(e.acabamentos().getFirst().pesoPorArea()).isEqualTo(.4);
    }
    @Test void recorteConservaPosicaoNormalizadaEPeitorilDoVao() {
        var e=fonte.converter("j","t","JANELA",null,null,"{\"paredeId\":\"w\",\"posicao\":0.5,\"peitoril\":1,\"largura\":1.2,\"altura\":1.2}",false);
        assertThat(e.posicao()).isEqualTo(.5);assertThat(e.peitoril()).isEqualTo(1);
    }
    @Test void legadoSemComposicaoMantemPesoAutomaticoDesativado() {
        var e=fonte.converter("w","t","PAREDE","MAT_ALV_BLOCO14",null,"{}",true);
        assertThat(e.acabamentos()).isEmpty();assertThat(e.integrarPesoAcabamentos()).isFalse();assertThat(e.rascunhoIa()).isTrue();
    }
}
