package br.com.civilconstructorai.cimento;
import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CimentoServiceTest {
    final ModeloLinguagem modelo=mock(ModeloLinguagem.class);
    final AcessoObra acesso=mock(AcessoObra.class);
    final DosagemCimentoService dosagens=mock(DosagemCimentoService.class);
    final UUID obra=UUID.randomUUID(), elemento=UUID.randomUUID();
    final CimentoService.Pedido pedido=new CimentoService.Pedido(elemento,CimentoService.Ambiente.INTERNO,CimentoService.Prioridade.GERAL);
    CimentoService service(boolean enabled) { return new CimentoService(modelo,acesso,new ObjectMapper(),enabled,dosagens); }
    @Test void semDosagemNaoChamaProviderNemRecomendaIDs() {
        when(dosagens.elegiveis(obra,elemento)).thenReturn(new DosagemCimentoService.ContextoElegivel("PILAR",30,List.of()));
        var r=service(true).recomendar(obra,pedido);
        assertThat(r.cimentoIds()).isEmpty(); assertThat(r.origem()).contains("SEM_IA"); assertThat(r.liberacaoTecnica()).isFalse();
        verifyNoInteractions(modelo); verify(acesso).exigirMembro(obra);
    }
    @Test void providerDesligadoEhTransparenteEPermaneceNoConjuntoDocumentado() {
        when(dosagens.elegiveis(obra,elemento)).thenReturn(new DosagemCimentoService.ContextoElegivel("PILAR",30,List.of("CIM_CP_I_40")));
        var r=service(false).recomendar(obra,pedido);
        assertThat(r.cimentoIds()).containsExactly("CIM_CP_I_40"); assertThat(r.origem()).contains("SEM_IA"); verifyNoInteractions(modelo);
    }
    @Test void chamaPortaRealComEsquemaFechadoERejeitaCimentoSemEvidenciaMesmoExistenteNoCatalogo() {
        when(dosagens.elegiveis(obra,elemento)).thenReturn(new DosagemCimentoService.ContextoElegivel("PILAR",30,List.of("CIM_CP_II_F_32")));
        when(modelo.gerarEstruturado(eq(RotaIa.SELECAO_CIMENTO),anyString(),anyString(),anyString(),anyMap()))
            .thenReturn(new ModeloLinguagem.Resposta("{\"cimentoIds\":[\"CIM_CP_V_ARI\"]}","end_turn",false,1,1,0,0,1,"claude"));
        var r=service(true).recomendar(obra,pedido);
        assertThat(r.cimentoIds()).containsExactly("CIM_CP_II_F_32"); assertThat(r.origem()).contains("SEM_IA");
        verify(modelo).gerarEstruturado(eq(RotaIa.SELECAO_CIMENTO),eq(CimentoService.SYSTEM),contains("fckProjetoMPa"),contains("CIM_CP_II_F_32"),anyMap());
    }
    @Test void respostaValidaIdentificaIAsemLiberarExecucao() {
        when(dosagens.elegiveis(obra,elemento)).thenReturn(new DosagemCimentoService.ContextoElegivel("PILAR",30,List.of("CIM_CP_II_F_32")));
        when(modelo.gerarEstruturado(any(),anyString(),anyString(),anyString(),anyMap()))
            .thenReturn(new ModeloLinguagem.Resposta("{\"cimentoIds\":[\"CIM_CP_II_F_32\"]}","end_turn",false,1,1,0,0,1,"claude"));
        var r=service(true).recomendar(obra,pedido);
        assertThat(r.origem()).isEqualTo("IA"); assertThat(r.liberacaoTecnica()).isFalse();
    }
}
