package br.com.civilconstructorai.notacao;

import br.com.civilconstructorai.notacao.CatalogoComandos.Perfil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoComandosTest {

    @Test
    void resolveAliasPorPerfil() {
        assertThat(CatalogoComandos.resolver("BR", Perfil.CIVILCONSTRUCTOR).orElseThrow().codigo()).isEqualTo("CONTRAVENTAMENTO");
        assertThat(CatalogoComandos.resolver("BR", Perfil.AUTOCAD).orElseThrow().codigo()).isEqualTo("QUEBRAR");
        assertThat(CatalogoComandos.resolver("WALL", Perfil.AUTOCAD).orElseThrow().codigo()).isEqualTo("PAREDE");
        assertThat(CatalogoComandos.resolver("parede", Perfil.REVIT).orElseThrow().codigo()).isEqualTo("PAREDE");
        assertThat(CatalogoComandos.resolver("EL", Perfil.AUTOCAD).orElseThrow().codigo()).isEqualTo("ELIPSE");
        assertThat(CatalogoComandos.resolver("EL", Perfil.REVIT).orElseThrow().codigo()).isEqualTo("COTA_NIVEL");
    }

    @Test
    void listaConflitosETeclasReservadas() {
        assertThat(CatalogoComandos.conflitos().stream().map(CatalogoComandos.Conflito::alias)).contains("BR", "EL");
        assertThat(CatalogoComandos.TECLAS_RESERVADAS_NAVEGADOR).containsExactly("F1", "F5", "F11", "F12");
        assertThat(CatalogoComandos.prioridadeOsnap().get(0)).isEqualTo(CatalogoComandos.ModoOsnap.EIX);
    }

    @Test
    void buscaPorPrefixoENome() {
        assertThat(CatalogoComandos.buscar("mo", Perfil.CIVILCONSTRUCTOR, 5).get(0).codigo()).isEqualTo("MOVER");
        assertThat(CatalogoComandos.tabelaB()).extracting(CatalogoComandos.Comando::codigo).contains("PAREDE", "PILAR", "VIGA", "LAJE", "MOVER", "COTA_LINEAR").doesNotContain("ZOOM", "OSNAP");
    }
}
