package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.modelo.DependentesDoModelo;
import br.com.civilconstructorai.notacao.CatalogoMateriais;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base de cálculo da obra. A leitura nunca grava (obra sem base → base vazia, todos os campos "não informado");
 * a gravação valida os valores permitidos e torna obsoletos os cálculos que dependiam da base anterior.
 */
@Service
@Transactional
public class BaseCalculoService {

    /** referências que os motores implementados usam, com a edição que o software segue */
    public static final List<String> REFERENCIAS_MOTORES = List.of("NBR6118", "NBR8681", "NBR6120", "NBR6123", "NBR8953", "NBR7480");

    public record Visao(UUID obraId, String finalidade, String sistemaConstrutivo, String classeAgressividade, String agregado, String versaoCatalogo,
                        String versaoCatalogoAtual, List<Map<String, Object>> referencias, List<String> naoInformado, boolean gravada) {
    }

    public record Alteracao(String finalidade, String sistemaConstrutivo, String classeAgressividade, String agregado) {
    }

    private final Repositorios.BaseCalculoRepository bases;
    private final Repositorios.ObraRepository obras;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final DependentesDoModelo dependentes;
    private final Relogio relogio;

    public BaseCalculoService(Repositorios.BaseCalculoRepository bases, Repositorios.ObraRepository obras, AcessoObra acesso, ContextoUsuario contexto,
                              RegistroOperacoes registro, DependentesDoModelo dependentes, Relogio relogio) {
        this.bases = bases;
        this.obras = obras;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.dependentes = dependentes;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Visao obter(UUID obraId) {
        acesso.exigirMembro(obraId);
        var b = bases.findByObraId(obraId);
        var snap = snapshot(obraId);
        return new Visao(obraId, snap.finalidade(), snap.sistemaConstrutivo(), snap.classeAgressividade(), snap.agregado(), snap.versaoCatalogo(),
                CatalogoMateriais.padrao().versao(), referencias(), snap.naoInformado(), b.isPresent());
    }

    public Visao atualizar(UUID obraId, Alteracao a) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var obra = obras.findById(obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Obra"));
        var b = bases.findByObraId(obraId).orElseGet(() -> new BaseCalculo(obra.getTenantId(), obraId, CatalogoMateriais.padrao().versao()));
        b.atualizar(vazio(a.finalidade()), vazio(a.sistemaConstrutivo()), vazio(a.classeAgressividade()), vazio(a.agregado()), CatalogoMateriais.padrao().versao(),
                String.join(",", REFERENCIAS_MOTORES), u.contaId());
        bases.save(b);
        dependentes.baseCalculoMudou(obraId);
        registro.registrar(new RegistroOperacoes.Operacao("BASE_CALCULO_ALTERADA", u.contaId(), obra.getTenantId(), obraId, "BaseCalculo", b.getId(),
                Map.of("caa", String.valueOf(b.getClasseAgressividade()), "agregado", String.valueOf(b.getAgregado()), "catalogo", b.getVersaoCatalogo())));
        return obter(obraId);
    }

    /** cópia para gravar junto de um resultado de cálculo */
    @Transactional(readOnly = true)
    public BaseCalculo.Snapshot snapshot(UUID obraId) {
        var obra = obras.findById(obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Obra"));
        var b = bases.findByObraId(obraId).orElseGet(() -> new BaseCalculo(obra.getTenantId(), obraId, CatalogoMateriais.padrao().versao()));
        var cat = CatalogoMateriais.padrao();
        var refs = REFERENCIAS_MOTORES.stream().map(id -> cat.referencias().get(id)).filter(java.util.Objects::nonNull).map(CatalogoMateriais.Referencia::rotulo).toList();
        return b.snapshot(refs, obra.getUf(), obra.getMunicipio(), relogio.agora());
    }

    private static List<Map<String, Object>> referencias() {
        var cat = CatalogoMateriais.padrao();
        return REFERENCIAS_MOTORES.stream().map(id -> cat.referencias().get(id)).filter(java.util.Objects::nonNull).map(r -> Map.<String, Object>of(
                "id", r.id(), "identificador", r.identificador(), "edicao", r.edicao(), "titulo", r.titulo(), "situacao", r.situacao(), "tipo", r.tipo())).toList();
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
