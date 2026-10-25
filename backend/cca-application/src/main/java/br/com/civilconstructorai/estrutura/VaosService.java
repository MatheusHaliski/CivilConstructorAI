package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.estrutura.vaos.CalculadoraVaos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** RF06 · relatório de vãos e apoios por pavimento (Caixa Cartesiana e Fencepost). */
@Service
@Transactional(readOnly = true)
public class VaosService {

    private final ModeloEstruturalFonte modelo;
    private final AcessoObra acesso;

    public VaosService(ModeloEstruturalFonte modelo, AcessoObra acesso) {
        this.modelo = modelo;
        this.acesso = acesso;
    }

    public List<CalculadoraVaos.RelatorioVaos> relatorio(UUID obraId) {
        acesso.exigirMembro(obraId);
        var m = modelo.carregar(obraId);
        return m.pavimentosOrdenados().stream().map(p -> CalculadoraVaos.verificar(m, p.id())).toList();
    }
}
