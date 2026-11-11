package br.com.civilconstructorai.cimento;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.modelo.ComoUsuario;
import br.com.civilconstructorai.modelo.ObraDeTeste;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API completa: persistência/RLS, signoff e defesa contra associação VI forjada. */
@AutoConfigureMockMvc
class CimentoIT extends AbstractIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired ObjectMapper mapper;
    ObraDeTeste.Cenario c;
    UsuarioAtual engenheiro;
    @BeforeEach void preparar() { c=ObraDeTeste.criar(jdbc,tm); engenheiro=ObraDeTeste.membro(jdbc,tm,c,PapelObra.ENGENHEIRO,true,true); }
    String base() { return "/api/v1/obras/"+c.obraId(); }
    MvcResult enviar(UsuarioAtual u,String caminho,Object body) throws Exception {
        return mvc.perform(post(base()+caminho).with(ComoUsuario.como(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn();
    }
    JsonNode json(MvcResult r) { return mapper.readTree(r.getResponse().getContentAsByteArray()); }
    String criarPilar() throws Exception {
        var p=enviar(engenheiro,"/modelo/comandos",Map.of("codigo","PILAR","args",Map.of("centro",Map.of("x",0,"y",0),"b",.2,"h",.4,"materialId","MAT_CONC_FCK30")));
        assertThat(p.getResponse().getStatus()).as(p.getResponse().getContentAsString()).isEqualTo(201);
        return json(p).get("diff").get("criados").get(0).get("id").asString();
    }
    Object registro() { return Map.ofEntries(
        Map.entry("cimentoId","CIM_CP_II_F_32"),Map.entry("produto","Produto/lote/fabricante documentado"),Map.entry("fckProjetoMPa",30),Map.entry("fckComprovadoMPa",34),
        Map.entry("referenciaEnsaio","Relatório laboratório123"),Map.entry("origemMemorial","MemorialM1"),Map.entry("dataEnsaio",LocalDate.now().minusDays(1).toString()),
        Map.entry("validoAte",LocalDate.now().plusMonths(6).toString()),Map.entry("metodoEstatistico","fck comprovado no relatório com análise estatística auditada"),Map.entry("versaoModelo",1),
        Map.entry("composicao",Map.of("aguaLigante",.45,"agregado","Agregados/lotes/origem","consumoCimentoKgM3",350,"procedimentoCura","Cura identificada no relatório")));
    }
    @Test void semEvidenciaNaoSugereCimentoENemPermiteForjarVI() throws Exception {
        var id=criarPilar();
        var ia=enviar(engenheiro,"/cimentos/recomendacao",Map.of("elementoId",id,"ambiente","MARINHO","prioridade","GERAL"));
        assertThat(ia.getResponse().getStatus()).isEqualTo(200); assertThat(json(ia).get("cimentoIds")).isEmpty();
        var forjado=enviar(engenheiro,"/modelo/comandos",Map.of("codigo","ALTERAR","args",Map.of("id",id,"props",Map.of("cimentoId","CIM_CP_II_F_32","dosagemCimentoId",UUID.randomUUID().toString()))));
        assertThat(forjado.getResponse().getStatus()).as(forjado.getResponse().getContentAsString()).isEqualTo(422);
        var modelo=mvc.perform(get(base()+"/modelo").with(ComoUsuario.como(engenheiro))).andReturn();
        assertThat(json(modelo).get("versao").asLong()).isEqualTo(1);
    }
    @Test void registraConfirmaAssociaAtomicamenteEPreservaCorrespondenciaAposBumpDeVersao() throws Exception {
        var id=criarPilar();
        var criada=enviar(engenheiro,"/cimentos/dosagens",registro());
        assertThat(criada.getResponse().getStatus()).as(criada.getResponse().getContentAsString()).isEqualTo(200);
        var dose=json(criada).get("id").asString();
        var associacao=Map.of("dosagemId",dose,"elementoId",id,"cimentoId","CIM_CP_II_F_32","versaoModelo",1);
        assertThat(enviar(engenheiro,"/cimentos/associacao",associacao).getResponse().getStatus()).isEqualTo(422);
        var confirmada=enviar(engenheiro,"/cimentos/dosagens/"+dose+"/confirmar",Map.of("referenciaValidacao","ART123 e relatório conferido"));
        assertThat(confirmada.getResponse().getStatus()).as(confirmada.getResponse().getContentAsString()).isEqualTo(200);
        var aplicada=enviar(engenheiro,"/modelo/comandos",Map.of("codigo","ALTERAR","args",Map.of("id",id,"props",Map.of("cimentoId","CIM_CP_II_F_32","dosagemCimentoId",dose))));
        assertThat(aplicada.getResponse().getStatus()).as(aplicada.getResponse().getContentAsString()).isEqualTo(201);
        var dosagens=mvc.perform(get(base()+"/cimentos/dosagens?versaoModelo=2").with(ComoUsuario.como(engenheiro))).andExpect(status().isOk()).andReturn();
        assertThat(json(dosagens).get(0).get("contextoCompativel").asBoolean()).isTrue();
        var fckForjado=enviar(engenheiro,"/modelo/comandos",Map.of("codigo","ALTERAR","args",Map.of("id",id,"props",Map.of("materialId","MAT_CONC_FCK25"))));
        assertThat(fckForjado.getResponse().getStatus()).isEqualTo(422);
        var outraObra=ObraDeTeste.criar(jdbc,tm);
        var outro=ObraDeTeste.membro(jdbc,tm,outraObra,PapelObra.ENGENHEIRO,true,true);
        mvc.perform(get(base()+"/cimentos/dosagens?versaoModelo=2").with(ComoUsuario.como(outro))).andExpect(status().isNotFound());
    }
    @Test void arquitetoNaoPodeRegistrarNemConfirmarEvidenciaComoEngenheiro() throws Exception {
        criarPilar();
        var arquiteto=ObraDeTeste.membro(jdbc,tm,c,PapelObra.ARQUITETO,true,true);
        assertThat(enviar(arquiteto,"/cimentos/dosagens",registro()).getResponse().getStatus()).isEqualTo(403);
        assertThat(enviar(arquiteto,"/cimentos/dosagens/"+UUID.randomUUID()+"/confirmar",Map.of("referenciaValidacao","ART forjada")).getResponse().getStatus()).isEqualTo(403);
    }
}
