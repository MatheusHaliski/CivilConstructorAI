package br.com.civilconstructorai.comum;

import br.com.civilconstructorai.acesso.PapelObra;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioAtualTest {

    @Test
    void sobreviveASerializacaoDaSessao() throws Exception {
        var obra = UUID.randomUUID();
        var original = new UsuarioAtual(UUID.randomUUID(), UUID.randomUUID(), Optional.of(PapelObra.ADMIN),
                Map.of(obra, Set.of(PapelObra.ENGENHEIRO)), true, true, false);

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        UsuarioAtual lido;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            lido = (UsuarioAtual) in.readObject();
        }

        assertThat(lido).isEqualTo(original);
        assertThat(lido.papeisNaObra(obra)).containsExactlyInAnyOrder(PapelObra.ENGENHEIRO, PapelObra.ADMIN);
        assertThat(lido.podeAprovar(obra)).isTrue();
    }

    @Test
    void aprovarExigePapelAprovadorSeloEMfa() {
        var obra = UUID.randomUUID();
        var semSelo = new UsuarioAtual(UUID.randomUUID(), UUID.randomUUID(), Optional.empty(), Map.of(obra, Set.of(PapelObra.ENGENHEIRO)), true, false, false);
        var mestre = new UsuarioAtual(UUID.randomUUID(), UUID.randomUUID(), Optional.empty(), Map.of(obra, Set.of(PapelObra.MESTRE_OBRAS)), true, true, false);
        var ok = new UsuarioAtual(UUID.randomUUID(), UUID.randomUUID(), Optional.empty(), Map.of(obra, Set.of(PapelObra.ENGENHEIRO)), true, true, false);
        assertThat(semSelo.podeAprovar(obra)).isFalse();
        assertThat(mestre.podeAprovar(obra)).isFalse();
        assertThat(ok.podeAprovar(obra)).isTrue();
        assertThat(ok.membroDaObra(UUID.randomUUID())).isFalse();
    }
}
