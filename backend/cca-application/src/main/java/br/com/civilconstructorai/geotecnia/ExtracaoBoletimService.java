package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.Json;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF09 · leitura de boletim SPT por IA em duas chamadas separadas: structured outputs (dados) e depois
 * citações (página e trecho). O resultado é rascunho até o engenheiro confirmar campo a campo.
 */
@Service
@Transactional
public class ExtracaoBoletimService {

    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["furos"],
             "properties":{"furos":{"type":"array","items":{"type":"object","additionalProperties":false,
               "required":["codigo","cotaBoca","nivelAgua","camadas","leituras"],
               "properties":{
                 "codigo":{"type":"string"},"cotaBoca":{"type":"number"},"nivelAgua":{"type":["number","null"]},
                 "camadas":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["cotaTopo","cotaBase","tipo","descricao"],
                    "properties":{"cotaTopo":{"type":"number"},"cotaBase":{"type":"number"},
                      "tipo":{"type":"string","enum":["AREIA","AREIA_SILTOSA","AREIA_ARGILOSA","SILTE","SILTE_ARENOSO","SILTE_ARGILOSO","ARGILA","ARGILA_ARENOSA","ARGILA_SILTOSA","ATERRO","ROCHA_ALTERADA"]},
                      "descricao":{"type":"string"}}}},
                 "leituras":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["profundidade","nspt","impenetravel"],
                    "properties":{"profundidade":{"type":"number"},"nspt":{"type":"integer","minimum":0},"impenetravel":{"type":"boolean"}}}}}}}}}
            """;

    static final String SYSTEM = """
            Você lê boletins de sondagem SPT (NBR 6484) e extrai, por furo: código, cota da boca, nível d'água,
            camadas (cotas de topo e base, tipo de solo do enum, descrição) e leituras Nspt por metro, marcando
            'impenetravel' quando o boletim indicar limite de sondagem. Não invente valores: omita o que não estiver
            legível. Responda SOMENTE com o JSON do esquema.
            """;

    private final Repositorios.Extracoes extracoes;
    private final SondagemService sondagens;
    private final ArmazenamentoArquivos arquivos;
    private final ModeloLinguagem ia;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;

    public ExtracaoBoletimService(Repositorios.Extracoes extracoes, SondagemService sondagens, ArmazenamentoArquivos arquivos, ModeloLinguagem ia,
                                  AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio, Json json) {
        this.extracoes = extracoes;
        this.sondagens = sondagens;
        this.arquivos = arquivos;
        this.ia = ia;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.json = json;
    }

    public ExtracaoBoletim extrair(UUID obraId, String chaveS3) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        if (chaveS3 == null || !chaveS3.startsWith("obras/" + obraId + "/")) {
            throw new ExcecaoDominio.Validacao("chaveS3", "Chave do boletim inválida para esta obra.");
        }
        var pdf = arquivos.ler(chaveS3).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Boletim"));
        var ext = extracoes.save(new ExtracaoBoletim(u.empresaAtivaId(), obraId, chaveS3));
        ext.iniciar();
        try {
            // 1ª chamada: dados estruturados
            var dados = ia.gerarEstruturado(RotaIa.EXTRACAO_BOLETIM_SPT, SYSTEM, "Boletim em PDF anexado como documento (" + pdf.length + " bytes).", SCHEMA, Map.of("rf", "RF09"));
            if (!dados.completa()) {
                ext.falhar(dados.recusa() ? "O modelo recusou a leitura do boletim." : "Resposta interrompida: " + dados.stopReason());
                return ext;
            }
            validar(dados.conteudoJson());
            // 2ª chamada: citações (nunca na mesma chamada que structured outputs)
            var cit = ia.responderComCitacoes(RotaIa.EXTRACAO_BOLETIM_SPT, SYSTEM, pdf, "Para cada camada e leitura extraída, aponte a página e o trecho do boletim.");
            ext.concluir(dados.conteudoJson(), cit.completa() ? cit.conteudoJson() : "[]", dados.modelo(),
                    dados.inputTokens() + cit.inputTokens(), dados.outputTokens() + cit.outputTokens());
        } catch (ExcecaoDominio.Validacao e) {
            ext.falhar("A IA devolveu dados inválidos: " + e.getMessage());
        }
        registro.registrar(RegistroOperacoes.Operacao.de("IA_EXTRACAO_BOLETIM", u.contaId(), u.empresaAtivaId(), obraId, "ExtracaoBoletim", ext.getId()));
        return ext;
    }

    void validar(String conteudo) {
        JsonNode raiz = json.mapper().readTree(conteudo);
        for (JsonNode furo : raiz.path("furos")) {
            var camadas = json.ler(furo.path("camadas").toString(), new TypeReference<List<CamadaSolo>>() { });
            if (!camadas.isEmpty()) {
                FuroSondagem.validarCamadas(camadas);
            }
            for (JsonNode l : furo.path("leituras")) {
                if (l.path("nspt").asInt() < 0) {
                    throw new ExcecaoDominio.Validacao("leituras", "Nspt negativo.");
                }
            }
        }
    }

    /** o engenheiro confirma: cada furo do rascunho vira FuroSondagem (origemIa = true) */
    public List<FuroSondagem> confirmar(UUID obraId, UUID extracaoId, double xBase, double yBase) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var ext = extracoes.findByIdAndObraId(extracaoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Extração"));
        if (ext.getDadosJson() == null) {
            throw new ExcecaoDominio.RegraDeNegocio("extracao_sem_dados", "A extração está em " + ext.getEstado() + ".");
        }
        var criados = new java.util.ArrayList<FuroSondagem>();
        JsonNode raiz = json.mapper().readTree(ext.getDadosJson());
        int i = 0;
        for (JsonNode f : raiz.path("furos")) {
            var camadas = json.ler(f.path("camadas").toString(), new TypeReference<List<CamadaSolo>>() { });
            var leituras = json.ler(f.path("leituras").toString(), new TypeReference<List<LeituraSpt>>() { });
            var novo = new SondagemService.NovoFuro(f.path("codigo").asText(), xBase + 5.0 * i, yBase, null, null, f.path("cotaBoca").asDouble(),
                    f.path("nivelAgua").isNull() ? null : f.path("nivelAgua").asDouble(), camadas, leituras);
            var furo = sondagens.cadastrar(obraId, novo);
            furo.setOrigemIa(true);
            furo.setBoletimChaveS3(ext.getChaveS3());
            criados.add(furo);
            i++;
        }
        ext.decidir(true, u.contaId(), relogio.agora());
        return criados;
    }

    public ExtracaoBoletim rejeitar(UUID obraId, UUID extracaoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var ext = extracoes.findByIdAndObraId(extracaoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Extração"));
        ext.decidir(false, contexto.exigir().contaId(), relogio.agora());
        return ext;
    }

    @Transactional(readOnly = true)
    public List<ExtracaoBoletim> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return extracoes.findByObraIdOrderByCriadoEmDesc(obraId);
    }
}
