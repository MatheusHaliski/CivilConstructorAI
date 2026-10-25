package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.comum.EntidadeTenant;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Empresa = tenant raiz ({@code tenant_id = id}). Controladora dos dados dos próprios trabalhadores (DN-01). */
@Entity
@Table(name = "empresa")
public class Empresa extends EntidadeTenant {

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(nullable = false)
    private String cnpj;

    @Column(name = "cnpj_verificado", nullable = false)
    private boolean cnpjVerificado;

    @Column(name = "encarregado_contato")
    private String encarregadoContato;

    protected Empresa() {
    }

    private Empresa(UUID id) {
        super(id);
        setId(id);
    }

    public static Empresa nova(String razaoSocial, String nomeFantasia, String cnpj) {
        var e = new Empresa(Ids.novo());
        if (razaoSocial == null || razaoSocial.isBlank()) {
            throw new ExcecaoDominio.Validacao("razaoSocial", "Informe a razão social.");
        }
        e.razaoSocial = razaoSocial.trim();
        e.nomeFantasia = nomeFantasia == null ? null : nomeFantasia.trim();
        e.cnpj = Cnpj.normalizar(cnpj);
        return e;
    }

    public void atualizar(String razaoSocial, String nomeFantasia, String encarregadoContato) {
        if (razaoSocial != null && !razaoSocial.isBlank()) {
            this.razaoSocial = razaoSocial.trim();
        }
        if (nomeFantasia != null) {
            this.nomeFantasia = nomeFantasia.trim();
        }
        if (encarregadoContato != null) {
            this.encarregadoContato = encarregadoContato.trim();
        }
    }

    public void marcarCnpjVerificado() {
        this.cnpjVerificado = true;
    }

    public String getRazaoSocial() { return razaoSocial; }
    public String getNomeFantasia() { return nomeFantasia; }
    public String getCnpj() { return cnpj; }
    public boolean isCnpjVerificado() { return cnpjVerificado; }
    public String getEncarregadoContato() { return encarregadoContato; }
}
