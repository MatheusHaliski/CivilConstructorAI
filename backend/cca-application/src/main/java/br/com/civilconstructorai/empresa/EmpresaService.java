package br.com.civilconstructorai.empresa;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.empresa.Repositorios.EmpresaRepository;
import br.com.civilconstructorai.empresa.Repositorios.MembroEmpresaRepository;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF22 · empresa (tenant) e membros de escopo de empresa (DONO/ADMIN). */
@Service
public class EmpresaService {

    public record Resumo(UUID id, String razaoSocial, String nomeFantasia, String cnpj, boolean cnpjVerificado, String encarregadoContato, String meuPapel) {
    }

    public record Membro(UUID contaId, String nome, PapelObra papel) {
    }

    private final EmpresaRepository empresas;
    private final MembroEmpresaRepository membros;
    private final ContaRepository contas;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final TransactionTemplate tx;

    public EmpresaService(EmpresaRepository empresas, MembroEmpresaRepository membros, ContaRepository contas, ContextoUsuario contexto, RegistroOperacoes registro, TransactionTemplate tx) {
        this.empresas = empresas;
        this.membros = membros;
        this.contas = contas;
        this.contexto = contexto;
        this.registro = registro;
        this.tx = tx;
    }

    /** quem cria vira DONO; roda no tenant novo para passar no with check do RLS */
    public Resumo criar(String razaoSocial, String nomeFantasia, String cnpj) {
        var u = contexto.exigir();
        var empresa = Empresa.nova(razaoSocial, nomeFantasia, cnpj);
        try {
            return ContextoTenant.comTenant(empresa.getId(), () -> tx.execute(st -> {
                if (empresas.existsByCnpj(empresa.getCnpj())) {
                    throw new ExcecaoDominio.Conflito("Já existe uma empresa com este CNPJ.");
                }
                empresas.save(empresa);
                membros.save(new MembroEmpresa(empresa.getId(), u.contaId(), PapelObra.DONO));
                registro.registrar(RegistroOperacoes.Operacao.de("EMPRESA_CRIADA", u.contaId(), empresa.getId(), null, "Empresa", empresa.getId()));
                return new Resumo(empresa.getId(), empresa.getRazaoSocial(), empresa.getNomeFantasia(), empresa.getCnpj(), false, null, "DONO");
            }));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Resumo obter(UUID empresaId) {
        var u = exigirEmpresaAtiva(empresaId);
        var e = empresas.findById(empresaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Empresa"));
        return new Resumo(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(), e.isCnpjVerificado(), e.getEncarregadoContato(), u.papelEmpresa().map(Enum::name).orElse(null));
    }

    public Resumo atualizar(UUID empresaId, String razaoSocial, String nomeFantasia, String encarregadoContato) {
        exigirAdmin(empresaId);
        return tx.execute(st -> {
            var e = empresas.findById(empresaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Empresa"));
            e.atualizar(razaoSocial, nomeFantasia, encarregadoContato);
            return obter(empresaId);
        });
    }

    public List<Membro> membros(UUID empresaId) {
        exigirAdmin(empresaId);
        return membros.findByTenantId(empresaId).stream()
                .map(m -> new Membro(m.getContaId(), contas.findById(m.getContaId()).map(c -> c.getNome()).orElse("(removido)"), m.getPapel()))
                .toList();
    }

    public void mudarPapel(UUID empresaId, UUID contaId, PapelObra papel) {
        var u = exigirAdmin(empresaId);
        if (papel == PapelObra.DONO && u.papelEmpresa().orElse(null) != PapelObra.DONO) {
            throw new ExcecaoDominio.AcessoNegado("Só o(a) dono(a) pode nomear outro(a) dono(a).");
        }
        tx.executeWithoutResult(st -> {
            var m = membros.findByTenantIdAndContaId(empresaId, contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Membro"));
            m.mudarPapel(papel);
            registro.registrar(new RegistroOperacoes.Operacao("PAPEL_EMPRESA_ALTERADO", u.contaId(), empresaId, null, "MembroEmpresa", m.getId(), Map.of("papel", papel.name())));
        });
    }

    /** empresa ativa da sessão tem que ser esta; caso contrário 404 (não revela existência) */
    br.com.civilconstructorai.comum.UsuarioAtual exigirEmpresaAtiva(UUID empresaId) {
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null || !u.empresaAtivaId().equals(empresaId)) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Empresa");
        }
        return u;
    }

    br.com.civilconstructorai.comum.UsuarioAtual exigirAdmin(UUID empresaId) {
        var u = exigirEmpresaAtiva(empresaId);
        if (u.papelEmpresa().isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Esta ação exige o papel DONO ou ADMIN na empresa.");
        }
        return u;
    }
}
