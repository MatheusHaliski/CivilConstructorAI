package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.empresa.Cnpj;
import br.com.civilconstructorai.empresa.Convite;
import br.com.civilconstructorai.empresa.EstadoConvite;
import br.com.civilconstructorai.obra.EstadoObra;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DominioIdentidadeTest {

    @Test
    void politicaDeSenhaDevolveTodosOsProblemas() {
        var erros = PoliticaSenha.problemas("curta", "ana@x.com");
        assertThat(erros).extracting(ExcecaoDominio.Validacao.Erro::mensagem).anyMatch(m -> m.contains("12 caracteres"));
        assertThat(PoliticaSenha.problemas("construcao123", "x@y.com")).extracting(ExcecaoDominio.Validacao.Erro::mensagem).anyMatch(m -> m.contains("muito comum"));
        assertThat(PoliticaSenha.problemas("ana@x.com-e-mais-coisas", "ana@x.com")).extracting(ExcecaoDominio.Validacao.Erro::mensagem).anyMatch(m -> m.contains("e-mail"));
        assertThat(PoliticaSenha.problemas("uma frase longa e segura", "ana@x.com")).isEmpty();
    }

    @Test
    void maquinaDaConta() {
        var c = Conta.nova("a@b.com", "hash", "Ana", TipoConta.PESSOA, LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1));
        assertThat(c.getEstado()).isEqualTo(EstadoConta.PENDING_VERIFICATION);
        c.ativar();
        c.pedirExclusao(Instant.now());
        c.reativar();
        assertThat(c.getEstado()).isEqualTo(EstadoConta.ACTIVE);
        c.pedirExclusao(Instant.now());
        c.transicionar(EstadoConta.ANONYMOUS_LGPD_DELETED);
        assertThatThrownBy(() -> c.transicionar(EstadoConta.ACTIVE)).isInstanceOf(ExcecaoDominio.TransicaoInvalida.class);
        assertThat(EstadoConta.MAQUINA.terminal(EstadoConta.ANONYMOUS_LGPD_DELETED)).isTrue();
    }

    @Test
    void menorDeIdadeNaoCriaConta() {
        assertThatThrownBy(() -> Conta.nova("a@b.com", "h", "Ana", TipoConta.PESSOA, LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ExcecaoDominio.Validacao.class);
    }

    @Test
    void seloVoltaAPendingAoTrocarNomeOuRegistro() {
        var c = Conta.nova("a@b.com", "hash", "Ana", TipoConta.PESSOA, LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1));
        c.solicitarSelo(TipoSelo.CREA, "PR-12345", "pr");
        assertThat(c.getSeloEstado()).isEqualTo(EstadoSelo.PENDING);
        c.decidirSelo(true, null, Instant.now());
        assertThat(c.seloVerificado()).isTrue();
        c.alterarNome("Ana Maria");
        assertThat(c.getSeloEstado()).isEqualTo(EstadoSelo.PENDING);
        assertThatThrownBy(() -> c.decidirSelo(false, " ", Instant.now())).isInstanceOf(br.com.civilconstructorai.comum.ExcecaoDominio.Validacao.class);
        c.decidirSelo(false, "Documento ilegível", Instant.now());
        assertThat(c.getSeloEstado()).isEqualTo(EstadoSelo.REJECTED);
        assertThat(c.getSeloMotivo()).isEqualTo("Documento ilegível");
    }

    @Test
    void conviteNaoPromoveAcimaEExpiraEm7Dias() {
        var agora = Instant.parse("2026-10-25T00:00:00Z");
        assertThatThrownBy(() -> Convite.novo(UUID.randomUUID(), UUID.randomUUID(), "x@y.com", PapelObra.ADMIN, UUID.randomUUID(), PapelObra.ENGENHEIRO, "h", agora))
                .isInstanceOf(ExcecaoDominio.AcessoNegado.class);
        // RF20 CA-04: ninguém entra como DONO por convite, nem convidado por outro DONO
        assertThatThrownBy(() -> Convite.novo(UUID.randomUUID(), null, "x@y.com", PapelObra.DONO, UUID.randomUUID(), PapelObra.DONO, "h", agora))
                .isInstanceOf(ExcecaoDominio.AcessoNegado.class).hasMessageContaining("dono");
        var c = Convite.novo(UUID.randomUUID(), UUID.randomUUID(), "x@y.com", PapelObra.MESTRE_OBRAS, UUID.randomUUID(), PapelObra.ENGENHEIRO, "h", agora);
        assertThat(c.getExpiraEm()).isEqualTo(agora.plus(Convite.VALIDADE));
        assertThatThrownBy(() -> c.aceitar(UUID.randomUUID(), "outro@y.com", agora)).isInstanceOf(ExcecaoDominio.AcessoNegado.class);
        assertThatThrownBy(() -> c.aceitar(UUID.randomUUID(), "x@y.com", agora.plus(Convite.VALIDADE).plusSeconds(1))).hasMessageContaining("expirou");
        c.aceitar(UUID.randomUUID(), "X@Y.com", agora);
        assertThat(c.getEstado()).isEqualTo(EstadoConvite.ACEITO);
        assertThatThrownBy(c::revogar).isInstanceOf(ExcecaoDominio.TransicaoInvalida.class);
    }

    @Test
    void maquinaDaObraEPapeis() {
        assertThat(EstadoObra.MAQUINA.permite(EstadoObra.PLANEJADA, EstadoObra.EM_ANDAMENTO)).isTrue();
        assertThat(EstadoObra.MAQUINA.permite(EstadoObra.EM_ANDAMENTO, EstadoObra.ARQUIVADA)).isFalse();
        assertThat(EstadoObra.ARQUIVADA.somenteLeitura()).isTrue();
        assertThat(PapelObra.DONO.podeConvidar(PapelObra.ADMIN)).isTrue();
        assertThat(PapelObra.ENGENHEIRO.podeConvidar(PapelObra.ADMIN)).isFalse();
        assertThat(PapelObra.MESTRE_OBRAS.podeConvidar(PapelObra.TRABALHADOR)).isFalse();
        assertThat(PapelObra.ENGENHEIRO.exigeMfa()).isTrue();
        assertThat(PapelObra.CLIENTE.exigeMfa()).isFalse();
        // RF19 CA-06: todo papel aprovador exige 2FA (a auditoria 2026-10 achou ARQUITETO aprovador sem 2FA)
        for (var p : PapelObra.values()) {
            assertThat(!p.aprovador() || p.exigeMfa()).as(p.name()).isTrue();
        }
    }

    @Test
    void cnpjValido() {
        assertThat(Cnpj.valido("11222333000181")).isTrue();
        assertThat(Cnpj.valido("11222333000180")).isFalse();
        assertThat(Cnpj.valido("11111111111111")).isFalse();
        assertThat(Cnpj.normalizar("11.222.333/0001-81")).isEqualTo("11222333000181");
    }
}
