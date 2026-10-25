package br.com.civilconstructorai.config;

import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.arquivo.ArmazenamentoS3;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.email.EnviadorEmailSmtp;
import br.com.civilconstructorai.estrutura.ModeloEstruturalFonte;
import br.com.civilconstructorai.estrutura.ModeloEstruturalJdbc;
import br.com.civilconstructorai.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contexto sobe fora do perfil de teste: todas as portas têm adaptador real (S3/Garage, SMTP, modelo estrutural
 * JDBC). Só os testes usam as implementações em memória do perfil {@code it}.
 */
@ActiveProfiles(value = "dev", inheritProfiles = false)
class ContextoPerfilDevIT extends AbstractIT {

    @Autowired
    ArmazenamentoArquivos armazenamento;

    @Autowired
    EnviadorEmail email;

    @Autowired
    ModeloEstruturalFonte modeloEstrutural;

    @Test
    void portasUsamAdaptadoresReais() {
        assertThat(armazenamento).isInstanceOf(ArmazenamentoS3.class);
        assertThat(email).isInstanceOf(EnviadorEmailSmtp.class);
        assertThat(modeloEstrutural).isInstanceOf(ModeloEstruturalJdbc.class);
        // a URL pré-assinada aponta para o endpoint configurado (Garage em dev), com o bucket no caminho
        assertThat(armazenamento.presignUpload("uploads/x.pdf", "application/pdf", 1024, ArmazenamentoArquivos.VALIDADE_PADRAO).toString())
                .startsWith("http://localhost:3900/cca/uploads/x.pdf");
    }
}
