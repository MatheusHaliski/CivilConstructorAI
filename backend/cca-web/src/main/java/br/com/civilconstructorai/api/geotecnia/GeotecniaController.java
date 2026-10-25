package br.com.civilconstructorai.api.geotecnia;

import br.com.civilconstructorai.geotecnia.CamadaSolo;
import br.com.civilconstructorai.geotecnia.DimensionamentoFundacao;
import br.com.civilconstructorai.geotecnia.ExtracaoBoletim;
import br.com.civilconstructorai.geotecnia.ExtracaoBoletimService;
import br.com.civilconstructorai.geotecnia.FundacaoProposta;
import br.com.civilconstructorai.geotecnia.FundacaoService;
import br.com.civilconstructorai.geotecnia.FuroSondagem;
import br.com.civilconstructorai.geotecnia.LeituraSpt;
import br.com.civilconstructorai.geotecnia.SondagemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF09 sondagens SPT e extração de boletim por IA · RF10 fundações. */
@RestController
@RequestMapping("/api/v1/obras/{obraId}")
public class GeotecniaController {

    private final SondagemService sondagens;
    private final ExtracaoBoletimService extracoes;
    private final FundacaoService fundacoes;

    public GeotecniaController(SondagemService sondagens, ExtracaoBoletimService extracoes, FundacaoService fundacoes) {
        this.sondagens = sondagens;
        this.extracoes = extracoes;
        this.fundacoes = fundacoes;
    }

    public record NovoFuroRequest(@NotBlank String codigo, double x, double y, Double longitude, Double latitude, double cotaBoca, Double nivelAgua,
                                  List<CamadaSolo> camadas, List<LeituraSpt> leituras) {
    }

    public record ExtracaoRequest(@NotBlank String chaveS3) {
    }

    public record ConfirmacaoRequest(double x, double y) {
    }

    public record FundacaoRequest(@NotNull UUID analiseId, Double cotaAssentamento, Double diametroEstaca) {
    }

    @GetMapping("/sondagens")
    public List<FuroSondagem> furos(@PathVariable UUID obraId) {
        return sondagens.listar(obraId);
    }

    @PostMapping("/sondagens")
    @ResponseStatus(HttpStatus.CREATED)
    public FuroSondagem cadastrar(@PathVariable UUID obraId, @Valid @RequestBody NovoFuroRequest r) {
        return sondagens.cadastrar(obraId, new SondagemService.NovoFuro(r.codigo(), r.x(), r.y(), r.longitude(), r.latitude(), r.cotaBoca(), r.nivelAgua(), r.camadas(), r.leituras()));
    }

    @PostMapping("/sondagens/{furoId}/confirmar")
    public FuroSondagem confirmar(@PathVariable UUID obraId, @PathVariable UUID furoId) {
        return sondagens.confirmar(obraId, furoId);
    }

    @PostMapping("/sondagens/{furoId}/boletim/presign")
    public Map<String, String> presign(@PathVariable UUID obraId, @PathVariable UUID furoId) {
        return Map.of("url", sondagens.presignBoletim(obraId, furoId).toString(), "validadeMinutos", "15", "contentType", "application/pdf");
    }

    @GetMapping("/sondagens/perfil")
    public SondagemService.PerfilInterpolado perfil(@PathVariable UUID obraId, @RequestParam double x, @RequestParam double y) {
        return sondagens.perfilEm(obraId, x, y);
    }

    @GetMapping("/sondagens/extracoes")
    public List<ExtracaoBoletim> extracoes(@PathVariable UUID obraId) {
        return extracoes.listar(obraId);
    }

    @PostMapping("/sondagens/extracoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExtracaoBoletim extrair(@PathVariable UUID obraId, @Valid @RequestBody ExtracaoRequest r) {
        return extracoes.extrair(obraId, r.chaveS3());
    }

    @PostMapping("/sondagens/extracoes/{id}/confirmar")
    public List<FuroSondagem> confirmarExtracao(@PathVariable UUID obraId, @PathVariable UUID id, @RequestBody(required = false) ConfirmacaoRequest r) {
        return extracoes.confirmar(obraId, id, r == null ? 0 : r.x(), r == null ? 0 : r.y());
    }

    @PostMapping("/sondagens/extracoes/{id}/rejeitar")
    public ExtracaoBoletim rejeitarExtracao(@PathVariable UUID obraId, @PathVariable UUID id) {
        return extracoes.rejeitar(obraId, id);
    }

    @PostMapping("/fundacoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DimensionamentoFundacao dimensionar(@PathVariable UUID obraId, @Valid @RequestBody FundacaoRequest r) {
        return fundacoes.dimensionar(obraId, r.analiseId(), r.cotaAssentamento() == null ? 0 : r.cotaAssentamento(), r.diametroEstaca() == null ? 0 : r.diametroEstaca());
    }

    @GetMapping("/fundacoes")
    public List<DimensionamentoFundacao> listarFundacoes(@PathVariable UUID obraId) {
        return fundacoes.listar(obraId);
    }

    @GetMapping("/fundacoes/{id}")
    public DimensionamentoFundacao obterFundacao(@PathVariable UUID obraId, @PathVariable UUID id) {
        return fundacoes.obter(obraId, id);
    }

    @GetMapping("/fundacoes/{id}/propostas")
    public List<FundacaoProposta> propostas(@PathVariable UUID obraId, @PathVariable UUID id) {
        return fundacoes.propostas(obraId, id);
    }

    @PostMapping("/fundacoes/{id}/aprovar")
    public DimensionamentoFundacao aprovar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return fundacoes.decidir(obraId, id, true);
    }

    @PostMapping("/fundacoes/{id}/rejeitar")
    public DimensionamentoFundacao rejeitar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return fundacoes.decidir(obraId, id, false);
    }

    @PostMapping("/fundacoes/{id}/aplicado")
    public DimensionamentoFundacao aplicado(@PathVariable UUID obraId, @PathVariable UUID id) {
        return fundacoes.marcarAplicado(obraId, id);
    }
}
