package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.email.EnviadorEmailLog;
import br.com.civilconstructorai.support.AbstractIdentidadeIT;
import br.com.civilconstructorai.support.ApiClient;
import br.com.civilconstructorai.support.CnpjDeTeste;
import br.com.civilconstructorai.support.EmailDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF20/RF21/RF22 · convites (7 dias, uso único, não promove acima, e-mail bate), matriz de acesso
 * (404 fora da empresa, 403 papel insuficiente), estados da obra (409) e isolamento por RLS (API + SQL).
 */
class ConviteEAcessoIT extends AbstractIdentidadeIT {

    @LocalServerPort
    int porta;

    @Autowired
    EnviadorEmailLog emails;

    @Autowired
    JdbcTemplate jdbc;

    private ApiClient cliente() {
        return new ApiClient(porta, e -> EmailDeTeste.ultimoCodigo(emails, e));
    }

    /** cria uma empresa com dono autenticado e 2FA configurado (sem 2FA só a própria conta responde — RF19 CA-06) */
    private ApiClient dono(String cnpj) {
        var email = "dono-" + UUID.randomUUID() + "@teste.cca";
        var c = cliente();
        var r = c.registrarEAtivar(email, "Dono " + cnpj, Map.of("tipo", "EMPRESA", "cnpj", cnpj, "razaoSocial", "Construtora " + cnpj));
        assertThat(r.status()).as("%s", r).isEqualTo(201);
        assertThat(c.empresaAtiva()).isNotBlank();
        // antes do 2FA a empresa fica bloqueada para o dono
        assertThat(c.post("/obras", Map.of("nome", "Antes do 2FA")).status()).isEqualTo(403);
        c.ativarMfa();
        return c;
    }

    private String criarObra(ApiClient dono, String nome) {
        var r = dono.post("/obras", Map.of("nome", nome, "municipio", "Curitiba", "uf", "PR",
                "perimetro", List.of(List.of(-49.27, -25.43), List.of(-49.26, -25.43), List.of(-49.26, -25.42), List.of(-49.27, -25.42))));
        assertThat(r.status()).as("%s", r).isEqualTo(201);
        return r.texto("id");
    }

    private ApiClient convidarEAceitar(ApiClient dono, String obraId, String papel) {
        var email = papel.toLowerCase() + "-" + UUID.randomUUID() + "@teste.cca";
        var corpo = new java.util.HashMap<String, Object>(Map.of("email", email, "papel", papel));
        if (obraId != null) {
            corpo.put("obraId", obraId);
        }
        var conv = dono.post("/companies/" + dono.empresaAtiva() + "/invites", corpo);
        assertThat(conv.status()).as("%s", conv).isEqualTo(201);
        var token = EmailDeTeste.ultimoTokenDeConvite(emails, email);
        var convidado = cliente();
        // resumo público, sem sessão
        var publico = convidado.get("/convites/" + token + "/publico");
        assertThat(publico.status()).as("%s", publico).isEqualTo(200);
        assertThat(publico.texto("papel")).isEqualTo(papel);
        convidado.registrarEAtivar(email, "Pessoa " + papel, Map.of());
        var aceite = convidado.post("/invites/" + token + "/accept", null);
        assertThat(aceite.status()).as("%s", aceite).isEqualTo(200);
        // o aceite já troca a empresa ativa e devolve a obra (antes a pessoa caía em "sem empresa ativa")
        assertThat(convidado.empresaAtiva()).isEqualTo(dono.empresaAtiva());
        if (obraId != null) {
            assertThat(aceite.texto("obraId")).isEqualTo(obraId);
        }
        // uso único
        assertThat(convidado.post("/invites/" + token + "/accept", null).status()).isEqualTo(410);
        if (br.com.civilconstructorai.acesso.PapelObra.valueOf(papel).exigeMfa()) {
            convidado.ativarMfa();
        }
        return convidado;
    }

    @Test
    void conviteNaoPromoveAcimaDoConvidanteEExigeOMesmoEmail() {
        var dono = dono(CnpjDeTeste.aleatorio());
        var obra = criarObra(dono, "Residencial A");
        var eng = convidarEAceitar(dono, obra, "ENGENHEIRO");
        // engenheiro convida para a obra, mas não pode criar ADMIN
        var acima = eng.post("/companies/" + dono.empresaAtiva() + "/invites", Map.of("email", "x-" + UUID.randomUUID() + "@teste.cca", "papel", "ADMIN"));
        assertThat(acima.status()).isEqualTo(403);
        assertThat(acima.corpo().get("type").asString()).endsWith("/convite_acima_do_convidante");
        // convite para outro e-mail não pode ser aceito por esta conta
        var emailAlvo = "alvo-" + UUID.randomUUID() + "@teste.cca";
        dono.post("/companies/" + dono.empresaAtiva() + "/invites", Map.of("email", emailAlvo, "obraId", obra, "papel", "MESTRE_OBRAS"));
        var token = EmailDeTeste.ultimoTokenDeConvite(emails, emailAlvo);
        assertThat(eng.post("/invites/" + token + "/accept", null).status()).isEqualTo(403);
        // revogado → 410 no resumo público
        var lista = dono.get("/companies/" + dono.empresaAtiva() + "/invites?estado=PENDENTE");
        var id = lista.corpo().get(0).get("id").asString();
        assertThat(dono.delete("/companies/" + dono.empresaAtiva() + "/invites/" + id).status()).isEqualTo(204);
        assertThat(cliente().get("/convites/" + token + "/publico").status()).isEqualTo(410);
    }

    @Test
    void matrizDeAcesso404ForaDaEmpresaE403PapelInsuficiente() {
        var donoA = dono(CnpjDeTeste.aleatorio());
        var obraA = criarObra(donoA, "Obra A");
        var donoB = dono(CnpjDeTeste.aleatorio());
        criarObra(donoB, "Obra B");
        // outra empresa: 404, sem revelar
        assertThat(donoB.get("/obras/" + obraA).status()).isEqualTo(404);
        assertThat(donoB.get("/obras/" + obraA + "/membros").status()).isEqualTo(404);
        // mestre de obras da obra A: lê, mas não muda estado (403 com motivo)
        var mestre = convidarEAceitar(donoA, obraA, "MESTRE_OBRAS");
        assertThat(mestre.get("/obras/" + obraA).status()).isEqualTo(200);
        var r = mestre.post("/obras/" + obraA + "/transicoes", Map.of("para", "EM_ANDAMENTO"));
        assertThat(r.status()).isEqualTo(403);
        // não membro da obra A (mas da empresa B) não vê a lista com a obra A
        var obrasB = donoB.get("/obras");
        assertThat(obrasB.corpo().valueStream().map(o -> o.get("id").asString()).toList()).doesNotContain(obraA);
    }

    @Test
    void estadosDaObraEResponsavelTecnico() {
        var dono = dono(CnpjDeTeste.aleatorio());
        var obra = criarObra(dono, "Obra Estados");
        // sem engenheiro: não inicia
        var semEng = dono.post("/obras/" + obra + "/transicoes", Map.of("para", "EM_ANDAMENTO"));
        assertThat(semEng.status()).isEqualTo(422);
        assertThat(semEng.corpo().get("type").asString()).endsWith("/obra_sem_responsavel");
        var eng = convidarEAceitar(dono, obra, "ENGENHEIRO");
        var engId = eng.sessao().texto("contaId");
        assertThat(dono.put("/obras/" + obra + "/responsavel-tecnico", Map.of("contaId", engId, "artRrt", "ART-123")).status()).isEqualTo(204);
        assertThat(dono.post("/obras/" + obra + "/transicoes", Map.of("para", "EM_ANDAMENTO")).status()).isEqualTo(200);
        // transição inválida: EM_ANDAMENTO → ARQUIVADA = 409
        var invalida = dono.post("/obras/" + obra + "/transicoes", Map.of("para", "ARQUIVADA"));
        assertThat(invalida.status()).isEqualTo(409);
        assertThat(invalida.corpo().get("type").asString()).endsWith("/transicao_invalida");
        assertThat(dono.post("/obras/" + obra + "/transicoes", Map.of("para", "CONCLUIDA")).status()).isEqualTo(200);
        assertThat(dono.post("/obras/" + obra + "/transicoes", Map.of("para", "ARQUIVADA")).status()).isEqualTo(200);
        // arquivada é somente leitura
        assertThat(dono.patch("/obras/" + obra, Map.of("nome", "Novo nome")).status()).isEqualTo(409);
    }

    @Test
    void isolamentoPorRlsNaApiENoBanco() {
        var donoA = dono(CnpjDeTeste.aleatorio());
        var obraA = criarObra(donoA, "Isolada A");
        var donoB = dono(CnpjDeTeste.aleatorio());
        var empresaB = UUID.fromString(donoB.empresaAtiva());
        // SQL direto como cca_app (sem bypass) com o tenant de B: zero linhas da obra de A
        Integer linhas = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) con -> {
            try (var st = con.createStatement()) {
                st.execute("select set_config('app.tenant_id', '" + empresaB + "', false)");
                st.execute("select set_config('app.sistema', 'off', false)");
                try (var rs = st.executeQuery("select count(*) from obra where id = '" + obraA + "'")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                try (var st = con.createStatement()) {
                    st.execute("select set_config('app.tenant_id', '', false)");
                }
            }
        });
        assertThat(linhas).isZero();
        // e pela API
        assertThat(donoB.get("/obras/" + obraA).status()).isEqualTo(404);
        assertThat(donoA.get("/obras/" + obraA).status()).isEqualTo(200);
    }
}
