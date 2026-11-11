package br.com.civilconstructorai.cimento;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Evidência declarada por profissional, imutável após confirmação e vinculada a uma versão do modelo. */
@Entity
@Table(name = "dosagem_cimento")
public class DosagemCimento extends EntidadeTenant {
    @Column(name="obra_id", nullable=false) private UUID obraId;
    @Column(name="versao_modelo", nullable=false) private long versaoModelo;
    @Column(name="dados_json", nullable=false, columnDefinition="text") private String dadosJson;
    @Column(name="contexto_hash", nullable=false) private String contextoHash;
    @Column(nullable=false) private String estado = "PENDENTE";
    @Column(name="criado_por", nullable=false) private UUID criadoPor;
    @Column(name="confirmado_por") private UUID confirmadoPor;
    @Column(name="confirmado_em") private Instant confirmadoEm;
    @Column(name="referencia_validacao") private String referenciaValidacao;
    protected DosagemCimento() { }
    public DosagemCimento(UUID tenant, UUID obra, long versao, String dados, UUID autor, String hash) {
        super(tenant); obraId=obra; versaoModelo=versao; dadosJson=dados; criadoPor=autor; contextoHash=hash;
    }
    public void confirmar(UUID profissional, Instant data, String referencia) {
        if (!"PENDENTE".equals(estado)) throw new ExcecaoDominio.RegraDeNegocio("dosagem_estado", "Dosagem já confirmada; registre nova evidência para alterações.");
        confirmadoPor=profissional; confirmadoEm=data; referenciaValidacao=referencia; estado="VALIDADO";
    }
    public UUID getObraId() { return obraId; }
    public long getVersaoModelo() { return versaoModelo; }
    public String getDadosJson() { return dadosJson; }
    public String getContextoHash() { return contextoHash; }
    public String getEstado() { return estado; }
    public UUID getCriadoPor() { return criadoPor; }
    public UUID getConfirmadoPor() { return confirmadoPor; }
    public Instant getConfirmadoEm() { return confirmadoEm; }
    public String getReferenciaValidacao() { return referenciaValidacao; }
}
