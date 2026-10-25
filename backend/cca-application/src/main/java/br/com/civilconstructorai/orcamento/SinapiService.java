package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF12 · tabelas SINAPI versionadas e importação por CSV (código;descrição;unidade;custo;grupo). */
@Service
@Transactional
public class SinapiService {

    public record Importacao(String uf, LocalDate mesReferencia, boolean desonerado, String csv) {
    }

    private final Repositorios.Tabelas tabelas;
    private final Repositorios.Composicoes composicoes;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;

    public SinapiService(Repositorios.Tabelas tabelas, Repositorios.Composicoes composicoes, ContextoUsuario contexto, RegistroOperacoes registro) {
        this.tabelas = tabelas;
        this.composicoes = composicoes;
        this.contexto = contexto;
        this.registro = registro;
    }

    public TabelaSinapi importar(Importacao imp) {
        var u = contexto.exigir();
        // 🟡 enquanto não existe papel de administrador da plataforma, só DONO da empresa ativa importa
        if (u.papelEmpresa().filter(p -> p == PapelObra.DONO).isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Importar tabela SINAPI é restrito a administradores.");
        }
        if (imp.uf() == null || !imp.uf().matches("[A-Za-z]{2}")) {
            throw new ExcecaoDominio.Validacao("uf", "Informe a UF com duas letras.");
        }
        var mes = imp.mesReferencia().withDayOfMonth(1);
        if (tabelas.findByUfAndDesoneradoOrderByMesReferenciaDesc(imp.uf().toUpperCase(), imp.desonerado()).stream().anyMatch(t -> t.getMesReferencia().equals(mes))) {
            throw new ExcecaoDominio.Conflito("Já existe a tabela SINAPI " + imp.uf().toUpperCase() + " " + mes.getMonthValue() + "/" + mes.getYear()
                    + (imp.desonerado() ? " desonerada" : " não desonerada") + ". Orçamentos já feitos guardam a versão usada; importe outro mês de referência.");
        }
        var tabela = tabelas.save(new TabelaSinapi(imp.uf(), imp.mesReferencia(), imp.desonerado()));
        var lote = new ArrayList<ComposicaoSinapi>();
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        try (var br = new BufferedReader(new StringReader(imp.csv() == null ? "" : imp.csv()))) {
            String linha;
            int n = 0;
            while ((linha = br.readLine()) != null) {
                n++;
                if (linha.isBlank() || (n == 1 && linha.toLowerCase().startsWith("codigo"))) {
                    continue;
                }
                var p = linha.split(";", -1);
                if (p.length < 4) {
                    erros.add(new ExcecaoDominio.Validacao.Erro("linha " + n, "Esperado codigo;descricao;unidade;custo[;grupo]."));
                    continue;
                }
                try {
                    lote.add(new ComposicaoSinapi(tabela.getId(), p[0], p[1], p[2], new BigDecimal(p[3].trim().replace(".", "").replace(",", ".")), p.length > 4 ? p[4] : ""));
                } catch (NumberFormatException e) {
                    erros.add(new ExcecaoDominio.Validacao.Erro("linha " + n, "Custo inválido: " + p[3]));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        if (lote.isEmpty()) {
            throw new ExcecaoDominio.Validacao("csv", "Nenhuma composição no arquivo.");
        }
        composicoes.saveAll(lote);
        tabela.setComposicoes(lote.size());
        registro.registrar(new RegistroOperacoes.Operacao("SINAPI_IMPORTADA", u.contaId(), u.empresaAtivaId(), null, "TabelaSinapi", tabela.getId(), Map.of("composicoes", String.valueOf(lote.size()))));
        return tabela;
    }

    @Transactional(readOnly = true)
    public List<TabelaSinapi> tabelas() {
        return tabelas.findAllByOrderByMesReferenciaDesc();
    }

    @Transactional(readOnly = true)
    public TabelaSinapi vigente(String uf, boolean desonerado) {
        // tabela importada da UF e do regime da obra tem precedência; a de demonstração só entra se não houver outra, e o
        // rótulo diz isso. Auditoria 2026-10: a semeada (09/2026) passava à frente da importada e, sem tabela da UF, valia
        // a de qualquer outro estado — preços errados em silêncio.
        var daUf = tabelas.findByUfAndDesoneradoOrderByMesReferenciaDesc(uf.toUpperCase(), desonerado);
        return daUf.stream().filter(t -> !t.isDemonstracao()).findFirst().or(() -> daUf.stream().findFirst())
                .orElseThrow(() -> new ExcecaoDominio.RegraDeNegocio("sem_tabela_sinapi", "Não há tabela SINAPI de " + uf.toUpperCase()
                        + (desonerado ? " desonerada" : " não desonerada") + ". Importe a tabela da Caixa para a UF e o regime da obra antes de orçar."));
    }

    @Transactional(readOnly = true)
    public List<ComposicaoSinapi> buscar(UUID tabelaId, String termo) {
        return composicoes.findByTabelaIdAndDescricaoContainingIgnoreCase(tabelaId, termo == null ? "" : termo.trim()).stream().limit(50).toList();
    }

    /** consulta sem exceção: usada dentro de laços que descartam sugestões inválidas sem derrubar a transação */
    @Transactional(readOnly = true)
    public java.util.Optional<ComposicaoSinapi> buscarPorCodigo(UUID tabelaId, String codigo) {
        return composicoes.findByTabelaIdAndCodigo(tabelaId, codigo == null ? "" : codigo.trim());
    }

    @Transactional(readOnly = true)
    public ComposicaoSinapi porCodigo(UUID tabelaId, String codigo) {
        return composicoes.findByTabelaIdAndCodigo(tabelaId, codigo.trim()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Composição SINAPI " + codigo));
    }

    @Transactional(readOnly = true)
    public List<ComposicaoSinapi> todas(UUID tabelaId) {
        return composicoes.findByTabelaId(tabelaId);
    }
}
