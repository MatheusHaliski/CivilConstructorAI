package br.com.civilconstructorai.geotecnia;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.estrutura.Json;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** RF09 · furos de sondagem, camadas, leituras SPT, boletim no S3 e perfil interpolado. */
@Service
@Transactional
public class SondagemService {

    public record NovoFuro(String codigo, double x, double y, Double longitude, Double latitude, double cotaBoca, Double nivelAgua,
                           List<CamadaSolo> camadas, List<LeituraSpt> leituras) {
    }

    public record PerfilInterpolado(double x, double y, List<CamadaSolo> camadas, List<LeituraSpt> leituras, String furoMaisProximo, double distancia) {
    }

    private final Repositorios.Furos furos;
    private final PerimetroObra perimetro;
    private final ArmazenamentoArquivos arquivos;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Json json;

    public SondagemService(Repositorios.Furos furos, PerimetroObra perimetro, ArmazenamentoArquivos arquivos, AcessoObra acesso,
                           ContextoUsuario contexto, RegistroOperacoes registro, Json json) {
        this.furos = furos;
        this.perimetro = perimetro;
        this.arquivos = arquivos;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<FuroSondagem> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return furos.findByObraIdOrderByCodigoAsc(obraId);
    }

    public FuroSondagem cadastrar(UUID obraId, NovoFuro n) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (furos.existsByObraIdAndCodigo(obraId, n.codigo())) {
            erros.add(new ExcecaoDominio.Validacao.Erro("codigo", "Já existe um furo com este código na obra."));
        }
        if (n.longitude() != null && n.latitude() != null) {
            Boolean dentro = perimetro.contem(obraId, n.longitude(), n.latitude());
            if (Boolean.FALSE.equals(dentro)) {
                erros.add(new ExcecaoDominio.Validacao.Erro("localizacao", "O furo está fora do perímetro cadastrado da obra."));
            }
        }
        if (n.leituras() != null) {
            for (int i = 0; i < n.leituras().size(); i++) {
                var l = n.leituras().get(i);
                if (l.nspt() < 0) {
                    erros.add(new ExcecaoDominio.Validacao.Erro("leituras[" + i + "]", "Nspt deve ser inteiro ≥ 0."));
                }
                if (l.profundidade() < 0) {
                    erros.add(new ExcecaoDominio.Validacao.Erro("leituras[" + i + "]", "Profundidade deve ser ≥ 0 m."));
                }
            }
        }
        // camadas e leituras voltam juntas num só 422 (antes as camadas só eram conferidas depois das leituras)
        if (n.camadas() != null) {
            try {
                FuroSondagem.validarCamadas(n.camadas());
            } catch (ExcecaoDominio.Validacao v) {
                erros.addAll(v.erros());
            }
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        var furo = new FuroSondagem(u.empresaAtivaId(), obraId, n.codigo(), n.x(), n.y(), n.longitude(), n.latitude(), n.cotaBoca(), n.nivelAgua());
        furo.setCamadasJson(json.escrever(n.camadas() == null ? List.of() : n.camadas()));
        furo.setLeiturasJson(json.escrever(n.leituras() == null ? List.of() : n.leituras()));
        furo = furos.save(furo);
        registro.registrar(RegistroOperacoes.Operacao.de("FURO_CADASTRADO", u.contaId(), u.empresaAtivaId(), obraId, "FuroSondagem", furo.getId()));
        return furo;
    }

    public FuroSondagem confirmar(UUID obraId, UUID furoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var f = furos.findByIdAndObraId(furoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Furo de sondagem"));
        f.confirmar();
        return f;
    }

    /** URL pré-assinada para subir o boletim (PDF, 15 min, até 32 MB) */
    public URI presignBoletim(UUID obraId, UUID furoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var f = furos.findByIdAndObraId(furoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Furo de sondagem"));
        var chave = "obras/" + obraId + "/sondagens/" + f.getCodigo() + ".pdf";
        f.setBoletimChaveS3(chave);
        return arquivos.presignUpload(chave, "application/pdf", 32L * 1024 * 1024, Duration.ofMinutes(15));
    }

    public CalculoFundacoes.Perfil perfilDe(FuroSondagem f) {
        var camadas = json.ler(f.getCamadasJson(), new TypeReference<List<CamadaSolo>>() { });
        var leituras = json.ler(f.getLeiturasJson(), new TypeReference<List<LeituraSpt>>() { });
        return new CalculoFundacoes.Perfil(camadas, leituras, f.getNivelAgua());
    }

    /** perfil no ponto (x, y): inverso da distância entre os dois furos confirmados mais próximos (Nspt), camadas do mais próximo */
    @Transactional(readOnly = true)
    public PerfilInterpolado perfilEm(UUID obraId, double x, double y) {
        acesso.exigirMembro(obraId);
        var lista = furos.findByObraIdOrderByCodigoAsc(obraId).stream().filter(f -> f.getEstado() == FuroSondagem.Estado.CONFIRMADO).toList();
        if (lista.isEmpty()) {
            throw new ExcecaoDominio.RegraDeNegocio("sem_sondagem", "Cadastre e confirme ao menos um furo de sondagem.");
        }
        var ordenados = lista.stream().sorted((a, b) -> Double.compare(d(a, x, y), d(b, x, y))).toList();
        var f1 = ordenados.get(0);
        var p1 = perfilDe(f1);
        if (ordenados.size() == 1 || d(f1, x, y) < 0.5) {
            return new PerfilInterpolado(x, y, p1.camadas(), p1.leituras(), f1.getCodigo(), d(f1, x, y));
        }
        var f2 = ordenados.get(1);
        var p2 = perfilDe(f2);
        double w1 = 1 / Math.max(d(f1, x, y), 0.5), w2 = 1 / Math.max(d(f2, x, y), 0.5);
        var leituras = new ArrayList<LeituraSpt>();
        for (var l : p1.leituras()) {
            int n2 = p2.nsptEm(l.profundidade());
            leituras.add(new LeituraSpt(l.profundidade(), (int) Math.round((l.nspt() * w1 + n2 * w2) / (w1 + w2)), l.impenetravel()));
        }
        return new PerfilInterpolado(x, y, p1.camadas(), leituras, f1.getCodigo() + "+" + f2.getCodigo(), d(f1, x, y));
    }

    private static double d(FuroSondagem f, double x, double y) {
        return Math.hypot(f.getX() - x, f.getY() - y);
    }
}
