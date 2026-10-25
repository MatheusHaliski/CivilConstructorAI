package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.lgpd.Portas.ConfigLgpd;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF25 · "Seus dados": ver por categoria, corrigir (Art. 18, III) e saber com quem foi compartilhado (Art. 18, VII). */
@Service
public class DadosTitularService {

    public record Categoria(CategoriaDado categoria, String titulo, String finalidade, String baseLegal, String retencao, boolean sensivel, Map<String, Object> dados) {
    }

    public record Compartilhamento(String tipo, String nome, String papel, String finalidade, String baseLegal, boolean internacional) {
    }

    private final ContaLgpdDao contas;
    private final ConsentimentoService consentimentos;
    private final DocumentosLegaisService documentos;
    private final ConfigLgpd config;
    private final RegistroOperacoes registro;

    public DadosTitularService(ContaLgpdDao contas, ConsentimentoService consentimentos, DocumentosLegaisService documentos, ConfigLgpd config, RegistroOperacoes registro) {
        this.contas = contas;
        this.consentimentos = consentimentos;
        this.documentos = documentos;
        this.config = config;
        this.registro = registro;
    }

    @Transactional(readOnly = true)
    public List<Categoria> resumo(UUID contaId) {
        var c = contas.exigir(contaId);
        var out = new ArrayList<Categoria>();
        out.add(cat(CategoriaDado.CONTA, map("email", c.email(), "nome", c.nome(), "tipo", c.tipo(), "estado", c.estado().name(), "mfaAtivo", c.mfaAtivo(), "criadoEm", c.criadoEm())));
        out.add(cat(CategoriaDado.PERFIL_PROFISSIONAL, map("registroProfissional", c.registroProfissional(), "registroUf", c.registroUf(), "seloTipo", c.seloTipo(), "seloEstado", c.seloEstado())));
        out.add(cat(CategoriaDado.CONSENTIMENTOS, map("finalidades", consentimentos.listar(contaId).stream().map(i -> map("finalidade", i.finalidade().name(), "estado", i.estado().name(), "versaoTexto", i.versaoTexto())).toList(),
                "documentosAceitos", documentos.versoesAceitas(contaId))));
        out.add(cat(CategoriaDado.SESSOES_SEGURANCA, map("info", "sessões ativas e alertas de login ficam em /me/sessions (RF23)")));
        out.add(cat(CategoriaDado.CONTATO_OPERACIONAL, map("vinculos", contas.vinculos(contaId))));
        return out;
    }

    private static Categoria cat(CategoriaDado c, Map<String, Object> dados) {
        return new Categoria(c, c.titulo(), c.finalidade(), c.baseLegal(), c.descricaoPrazo(), c.sensivel(), dados);
    }

    private static Map<String, Object> map(Object... kv) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    @Transactional
    public void corrigir(UUID contaId, String campo, String valor) {
        if (valor == null || valor.isBlank() || valor.length() > 200) {
            throw new ExcecaoDominio.Validacao("valor", "Informe um valor com até 200 caracteres.");
        }
        contas.exigir(contaId);
        contas.corrigir(contaId, campo, valor.trim());
        registro.registrar(new RegistroOperacoes.Operacao("TITULAR_CORRECAO", contaId, null, null, "conta", contaId, Map.of("campo", campo)));
    }

    /** Art. 18, VII: empresas (controladoras) com que a pessoa tem vínculo, operadores e a transferência internacional da IA */
    @Transactional(readOnly = true)
    public List<Compartilhamento> compartilhamentos(UUID contaId) {
        var out = new ArrayList<Compartilhamento>();
        for (var v : contas.vinculos(contaId)) {
            out.add(new Compartilhamento("CONTROLADORA", String.valueOf(v.get("razao_social")), String.valueOf(v.get("papel")),
                    v.get("obra") == null ? "gestão da empresa" : "obra " + v.get("obra"), "Art. 7º, V (contrato)", false));
        }
        out.add(new Compartilhamento("OPERADOR", config.controladoraRazaoSocial().isBlank() ? "CivilConstructorAI" : config.controladoraRazaoSocial(), "plataforma", "operação do serviço", "Art. 7º, V", false));
        out.add(new Compartilhamento("SUBOPERADOR", "Anthropic (camada de IA)", "processamento de modelo", "rotas de IA catalogadas em /ia/uso — sem dados pessoais", "Art. 33, IX", true));
        out.add(new Compartilhamento("SUBOPERADOR", "AWS sa-east-1 (arquivos)", "armazenamento", "fotos, pranchas e exportações", "Art. 7º, V", false));
        return out;
    }
}
