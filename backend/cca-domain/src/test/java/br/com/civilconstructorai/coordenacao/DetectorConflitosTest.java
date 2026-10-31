package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.notacao.Ponto2D;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorConflitosTest {

    private static Ponto2D p(double x, double y) {
        return new Ponto2D(x, y);
    }

    /** pórtico limpo: 2 pilares, viga apoiada nos dois, parede com porta de 2,10 m sob viga de 40 cm em pé-direito 3 m */
    private static ModeloBim limpo() {
        var m = new ModeloBim();
        m.aplicarAuditado("PAVIMENTO", Map.of("nome", "1º", "z", 3), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PILAR", Map.of("centro", p(0, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PILAR", Map.of("centro", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 0), "p2", p(5, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PORTA", Map.of("pos", p(2.5, 0)), OrigemComando.CANVAS, null);
        return m;
    }

    private static void vigaNoPrimeiro(ModeloBim m, Ponto2D a, Ponto2D b, double h) {
        var pav1 = m.pavimentosOrdenados().get(1).id();
        m.executar(new br.com.civilconstructorai.modelo.ComandoVi(java.util.UUID.randomUUID(), "VIGA", Map.of("p1", a, "p2", b, "h", h), OrigemComando.CANVAS, m.versao(), null, java.time.Instant.now()), pav1);
    }

    private static void forcar(ModeloBim m, String codigo, Map<String, Object> args) {
        m.executar(new br.com.civilconstructorai.modelo.ComandoVi(java.util.UUID.randomUUID(), codigo, args, OrigemComando.CANVAS, m.versao(), null, java.time.Instant.now()), null);
    }

    @Test
    void modeloSemDefeitosNaoTemConflitos() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 0.4);
        assertThat(DetectorConflitos.detectar(m)).isEmpty();
    }

    @Test
    void pilaresSobrepostosVigaBaixaEPortaNoPilar() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 1.0); // fundo a 2,00 m < topo da porta 2,10 m
        // o auditor de comandos recusa estes pilares; o detector precisa achá-los mesmo assim (ex.: modelo vindo de IFC)
        forcar(m, "PILAR", Map.of("centro", p(0.1, 0))); // sobre P1
        forcar(m, "PILAR", Map.of("centro", p(2.5, 0))); // no vão da porta
        var c = DetectorConflitos.detectar(m);
        assertThat(c).extracting(DetectorConflitos.Conflito::regra).contains("GEO-01", "CON-01", "CON-02");
        var baixa = c.stream().filter(x -> x.regra().equals("CON-02")).findFirst().orElseThrow();
        assertThat(baixa.tipo()).isEqualTo(DetectorConflitos.Tipo.CONSTRUTIVO);
        assertThat(baixa.evidencia()).contains("z = 2.00").contains("z = 2.10");
        // nenhuma mensagem propõe abrir furo em elemento estrutural
        assertThat(c).noneMatch(x -> x.mensagem().toLowerCase().contains("furo"));
    }

    @Test
    void vigaSemApoioEVigaDuplicada() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 0.4);
        vigaNoPrimeiro(m, p(1, 0), p(4, 0), 0.4); // duplicada sobre a primeira
        vigaNoPrimeiro(m, p(5, 0), p(5, 4), 0.4); // ponta (5;4) sem pilar nem viga
        var c = DetectorConflitos.detectar(m);
        assertThat(c).extracting(DetectorConflitos.Conflito::regra).contains("GEO-02", "LIG-01");
        assertThat(c.stream().filter(x -> x.regra().equals("LIG-01")).map(DetectorConflitos.Conflito::evidencia)).anyMatch(e -> e.contains("(5.00; 4.00)"));
        assertThat(c).extracting(DetectorConflitos.Conflito::chave).doesNotHaveDuplicates();
    }

    @Test
    void chaveEhEstavelEntreExecucoes() {
        var m = limpo();
        vigaNoPrimeiro(m, p(0, 0), p(5, 0), 1.0);
        var a = DetectorConflitos.detectar(m);
        var b = DetectorConflitos.detectar(m.copia());
        assertThat(a).extracting(DetectorConflitos.Conflito::chave).containsExactlyElementsOf(b.stream().map(DetectorConflitos.Conflito::chave).toList());
        assertThat(a.get(0).chave()).hasSize(24);
        assertThat(List.copyOf(a.get(0).elementos())).isSorted();
    }

    private static java.util.UUID id(ModeloBim m, String codigo, Map<String, Object> args) {
        return m.aplicarAuditado(codigo, args, OrigemComando.CANVAS, null).diff().criados.getFirst().getId();
    }

    private static void noPrimeiro(ModeloBim m, String codigo, Map<String, Object> args) {
        var pav1 = m.pavimentosOrdenados().get(1).id();
        m.executar(new br.com.civilconstructorai.modelo.ComandoVi(java.util.UUID.randomUUID(), codigo, args, OrigemComando.CANVAS, m.versao(), null, java.time.Instant.now()), pav1);
    }

    /** dois ambientes (x 0–4 e 4–8) separados por parede com porta em (4; 2,5); laje e viga (y = 2, h 0,50) no pavimento de cima */
    private static ModeloBim apartamento() {
        var m = new ModeloBim();
        m.aplicarAuditado("PAVIMENTO", Map.of("nome", "1º", "z", 3), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 0), "p2", p(8, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(8, 0), "p2", p(8, 5)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(8, 5), "p2", p(0, 5)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(0, 5), "p2", p(0, 0)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PAREDE", Map.of("p1", p(4, 0), "p2", p(4, 5)), OrigemComando.CANVAS, null);
        m.aplicarAuditado("PORTA", Map.of("pos", p(4, 2.5)), OrigemComando.CANVAS, null);
        noPrimeiro(m, "LAJE", Map.of("contorno", List.of(p(0, 0), p(8, 0), p(8, 5), p(0, 5)), "h", 0.12));
        noPrimeiro(m, "VIGA", Map.of("p1", p(0, 2), "p2", p(8, 2), "h", 0.5));
        return m;
    }

    private static List<DetectorConflitos.Conflito> daRegra(List<DetectorConflitos.Conflito> c, String regra) {
        return c.stream().filter(x -> x.regra().equals(regra)).toList();
    }

    @Test
    void objetosBemPosicionadosNaoGeramConflitosDeObjeto() {
        var m = apartamento();
        var tom = id(m, "OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", p(1, 0.1)));
        var gel = id(m, "OBJETO", Map.of("item", "COZINHA.GELADEIRA", "pos", p(1, 0.52)));
        id(m, "OBJETO", Map.of("item", "SALA.SOFA_3L", "pos", p(6, 4.45)));
        id(m, "OBJETO", Map.of("item", "DECORACAO.TAPETE", "pos", p(6, 3.8)));
        id(m, "OBJETO", Map.of("item", "PESSOAS.ADULTO", "pos", p(4, 2.5))); // passando pela porta
        id(m, "CONECTAR", Map.of("de", tom.toString(), "para", gel.toString()));
        var c = DetectorConflitos.detectar(m);
        assertThat(c).filteredOn(x -> x.regra().startsWith("OBJ") || x.regra().startsWith("MEP") || x.regra().startsWith("HUM")).isEmpty();
    }

    @Test
    void cenarioAud05TubulacaoNaVigaEletrodutoNaTubulacaoPortaNaGeladeiraLuminariaNaLajeEquipamentoEmOutroAmbiente() {
        var m = apartamento();
        // geladeira dentro do giro da porta (a folha abre para x < 4, entre y 2,1 e 2,9)
        var gel = id(m, "OBJETO", Map.of("item", "COZINHA.GELADEIRA", "pos", p(3.4, 2.6)));
        // pendente erguido até entrar na laje do pavimento de cima (teto livre 2,88 m)
        var pend = id(m, "OBJETO", Map.of("item", "ILUMINACAO.PENDENTE", "pos", p(2, 4)));
        m.aplicarAuditado("ALTERAR", Map.of("id", pend.toString(), "props", Map.of("elevacao", 2.5)), OrigemComando.CANVAS, null);
        // água fria da parede sul até o lavatório na parede norte: o trecho no forro cruza a viga em y = 2
        var ent = id(m, "OBJETO", Map.of("item", "INSTALACOES.ENTRADA_AGUA", "pos", p(1.5, 0.1)));
        var lav = id(m, "OBJETO", Map.of("item", "BANHEIRO.LAVATORIO", "pos", p(1.5, 4.8)));
        var af = id(m, "CONECTAR", Map.of("de", ent.toString(), "para", lav.toString()));
        // eletroduto: quadro na parede oeste, tomada na parede norte sobre o ramal do lavatório — desce cruzando a água fria
        var qd = id(m, "OBJETO", Map.of("item", "INSTALACOES.QUADRO_DISTRIBUICAO", "pos", p(0.1, 3.5)));
        var tom = id(m, "OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", p(1.6, 4.9)));
        id(m, "CONECTAR", Map.of("de", qd.toString(), "para", tom.toString()));
        // máquina ligada à tomada da sala da esquerda e depois levada para a sala da direita
        var maq = id(m, "OBJETO", Map.of("item", "SERVICO.MAQUINA_LAVAR", "pos", p(2.5, 0.52)));
        var tom2 = id(m, "OBJETO", Map.of("item", "INSTALACOES.TOMADA_10A", "pos", p(3, 0.1)));
        id(m, "CONECTAR", Map.of("de", tom2.toString(), "para", maq.toString()));
        m.aplicarAuditado("MOVER", Map.of("sel", List.of(maq.toString()), "base", p(0, 0), "destino", p(4, 0)), OrigemComando.CANVAS, null);

        var c = DetectorConflitos.detectar(m);
        // a viga do cenário não tem apoio nas duas pontas: dois LIG-01 com chaves diferentes (a gravação exige chave única)
        assertThat(daRegra(c, "LIG-01")).hasSize(2);
        assertThat(c).extracting(DetectorConflitos.Conflito::chave).doesNotHaveDuplicates();
        assertThat(daRegra(c, "OBJ-03")).singleElement().satisfies(x -> {
            assertThat(x.elementos()).contains(gel);
            assertThat(x.tipo()).isEqualTo(DetectorConflitos.Tipo.FUNCIONAL);
            assertThat(x.mensagem()).contains("Geladeira").contains("porta");
        });
        assertThat(daRegra(c, "OBJ-04")).anySatisfy(x -> {
            assertThat(x.elementos()).contains(pend);
            assertThat(x.mensagem()).contains("laje");
            assertThat(x.evidencia()).contains("z = 3.10").contains("z = 2.88");
        });
        assertThat(daRegra(c, "MEP-01")).anySatisfy(x -> {
            assertThat(x.elementos()).contains(af);
            assertThat(x.mensagem()).contains("água fria").contains("viga").contains("não abre furos");
        });
        assertThat(daRegra(c, "MEP-02")).anySatisfy(x -> {
            assertThat(x.elementos()).contains(af);
            assertThat(x.mensagem()).contains("elétrica").contains("água fria");
        });
        assertThat(daRegra(c, "MEP-03")).singleElement().satisfies(x -> {
            assertThat(x.elementos()).contains(maq, tom2);
            assertThat(x.mensagem()).contains("outro ambiente");
        });
        assertThat(c).noneMatch(x -> x.mensagem().toLowerCase().contains("abra um furo"));
    }

    @Test
    void pessoaDentroDaParedeEObjetosSobrepostos() {
        var m = apartamento();
        var pessoa = id(m, "OBJETO", Map.of("item", "PESSOAS.ADULTO", "pos", p(4, 1)));
        var a = id(m, "OBJETO", Map.of("item", "SALA.POLTRONA", "pos", p(6, 3)));
        var b = id(m, "OBJETO", Map.of("item", "SALA.POLTRONA", "pos", p(6.3, 3)));
        var c = DetectorConflitos.detectar(m);
        assertThat(daRegra(c, "HUM-01")).singleElement().satisfies(x -> assertThat(x.elementos()).contains(pessoa));
        assertThat(daRegra(c, "OBJ-02")).singleElement().satisfies(x -> assertThat(x.elementos()).containsExactlyInAnyOrder(a, b));
    }

    @Test
    void escadaQueAtravessaALajeDeCimaEhApontada() {
        var m = apartamento();
        var esc = id(m, "ESCADA", Map.of("p1", p(5, 2.6), "p2", p(5, 4.6), "desnivel", 3));
        var c = DetectorConflitos.detectar(m);
        assertThat(daRegra(c, "CIR-01")).singleElement().satisfies(x -> {
            assertThat(x.elementos()).contains(esc);
            assertThat(x.tipo()).isEqualTo(DetectorConflitos.Tipo.CONSTRUTIVO);
            assertThat(x.mensagem()).contains("abertura").contains("engenheiro estrutural").doesNotContain("furo");
        });
    }
}
