package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Base de cálculo da obra: o que o projeto declara e de que dependem as verificações — finalidade, sistema
 * construtivo, classe de agressividade ambiental (CAA, NBR 6118 6.4: cobrimento e classe mínima de concreto),
 * agregado graúdo (αE do módulo de elasticidade), versão do catálogo técnico e referências adotadas. Cada análise e
 * dimensionamento grava uma cópia ({@link Snapshot}); mudar a base invalida os resultados, não os reescreve.
 */
@Entity
@Table(name = "base_calculo_obra")
public class BaseCalculo extends EntidadeTenant {

    public static final Set<String> FINALIDADES = Set.of("RESIDENCIAL", "COMERCIAL", "INSTITUCIONAL", "INDUSTRIAL", "MISTA");
    public static final Set<String> SISTEMAS = Set.of("CONCRETO_ARMADO_MOLDADO_NO_LOCAL", "CONCRETO_PRE_MOLDADO", "ESTRUTURA_METALICA", "ALVENARIA_ESTRUTURAL", "MISTO");
    public static final Set<String> CAAS = Set.of("I", "II", "III", "IV");
    public static final Set<String> AGREGADOS = Set.of("basalto", "diabasio", "granito", "gnaisse", "calcario", "arenito");

    @Column(name = "obra_id", nullable = false, unique = true)
    private UUID obraId;

    @Column
    private String finalidade;

    @Column(name = "sistema_construtivo")
    private String sistemaConstrutivo;

    @Column(name = "classe_agressividade")
    private String classeAgressividade;

    @Column
    private String agregado;

    @Column(name = "versao_catalogo", nullable = false)
    private String versaoCatalogo;

    @Column(name = "referencias_json", nullable = false)
    private String referenciasJson = "[]";

    @Column(name = "atualizado_por")
    private UUID atualizadoPor;

    protected BaseCalculo() {
    }

    public BaseCalculo(UUID tenantId, UUID obraId, String versaoCatalogo) {
        super(tenantId);
        this.obraId = obraId;
        this.versaoCatalogo = versaoCatalogo;
    }

    public void atualizar(String finalidade, String sistemaConstrutivo, String caa, String agregado, String versaoCatalogo, String referenciasJson, UUID por) {
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (finalidade != null && !FINALIDADES.contains(finalidade)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("finalidade", "Finalidade inválida: use " + String.join(", ", FINALIDADES.stream().sorted().toList()) + "."));
        }
        if (sistemaConstrutivo != null && !SISTEMAS.contains(sistemaConstrutivo)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("sistemaConstrutivo", "Sistema construtivo inválido."));
        }
        if (caa != null && !CAAS.contains(caa)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("classeAgressividade", "Classe de agressividade ambiental: I, II, III ou IV (NBR 6118, tabela 6.1)."));
        }
        if (agregado != null && !AGREGADOS.contains(agregado)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("agregado", "Agregado graúdo: basalto, diabásio, granito, gnaisse, calcário ou arenito."));
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        this.finalidade = finalidade;
        this.sistemaConstrutivo = sistemaConstrutivo;
        this.classeAgressividade = caa;
        this.agregado = agregado;
        this.versaoCatalogo = versaoCatalogo;
        this.referenciasJson = referenciasJson;
        this.atualizadoPor = por;
    }

    /** cópia gravada com cada resultado; {@code naoInformado}: campos que o cálculo teve de assumir */
    public record Snapshot(String finalidade, String sistemaConstrutivo, String classeAgressividade, String agregado, String versaoCatalogo,
                           List<String> referencias, String uf, String municipio, List<String> naoInformado, Instant registradaEm) {
    }

    public Snapshot snapshot(List<String> referencias, String uf, String municipio, Instant agora) {
        var faltam = new ArrayList<String>();
        if (finalidade == null) faltam.add("finalidade");
        if (sistemaConstrutivo == null) faltam.add("sistema construtivo");
        if (classeAgressividade == null) faltam.add("classe de agressividade ambiental (CAA)");
        if (agregado == null) faltam.add("agregado graúdo");
        if (uf == null) faltam.add("UF");
        return new Snapshot(finalidade, sistemaConstrutivo, classeAgressividade, agregado, versaoCatalogo, List.copyOf(referencias), uf, municipio, List.copyOf(faltam), agora);
    }

    public UUID getObraId() { return obraId; }
    public String getFinalidade() { return finalidade; }
    public String getSistemaConstrutivo() { return sistemaConstrutivo; }
    public String getClasseAgressividade() { return classeAgressividade; }
    public String getAgregado() { return agregado; }
    public String getVersaoCatalogo() { return versaoCatalogo; }
    public String getReferenciasJson() { return referenciasJson; }
    public UUID getAtualizadoPor() { return atualizadoPor; }
}
