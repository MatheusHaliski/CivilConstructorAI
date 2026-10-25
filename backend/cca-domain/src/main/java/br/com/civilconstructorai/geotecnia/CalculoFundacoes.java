package br.com.civilconstructorai.geotecnia;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RF10 · dimensionamento geotécnico (NBR 6122): sapata por tensão admissível (σadm ≈ 20·Nspt kPa, ≤ 400 kPa),
 * estaca por Aoki-Velloso e Décourt-Quaresma (adota o menor), bloco rígido, radier quando as sapatas cobrem
 * mais da metade da área. Unidades: kN, m, kPa.
 */
public final class CalculoFundacoes {

    public static final double SIGMA_ADM_MAX_KPA = 400;
    public static final double FS_ESTACA = 2.0;

    private CalculoFundacoes() {
    }

    public record Perfil(List<CamadaSolo> camadas, List<LeituraSpt> leituras, Double nivelAgua) {
        public int nsptEm(double profundidade) {
            LeituraSpt melhor = null;
            for (var l : leituras) {
                if (melhor == null || Math.abs(l.profundidade() - profundidade) < Math.abs(melhor.profundidade() - profundidade)) {
                    melhor = l;
                }
            }
            return melhor == null ? 0 : melhor.nsptCalculo();
        }

        public TipoSolo tipoEm(double profundidade) {
            for (var c : camadas) {
                if (c.contem(-profundidade)) {
                    return c.tipo();
                }
            }
            return camadas.isEmpty() ? TipoSolo.SILTE : camadas.get(camadas.size() - 1).tipo();
        }

        /** média do Nspt no bulbo de tensões (de z até z + 2B) */
        public double nsptMedio(double de, double ate) {
            double soma = 0;
            int n = 0;
            for (var l : leituras) {
                if (l.profundidade() >= de - 0.5 && l.profundidade() <= ate + 0.5) {
                    soma += l.nsptCalculo();
                    n++;
                }
            }
            return n == 0 ? nsptEm(de) : soma / n;
        }
    }

    public record Sapata(double lado, double altura, double sigmaAdmKpa, double pressaoKpa, boolean verifica, List<String> memoria) {
    }

    public record Estaqueamento(double diametro, double comprimento, double qultAokiKn, double qultDecourtKn, double qadmKn, int estacas,
                                double ladoBloco, double alturaBloco, List<String> memoria) {
    }

    /** sapata quadrada sob carga Nk (kN) para pilar bp×hp (m) assente a cota (m) */
    public static Sapata sapata(double nk, double bp, double hp, double cotaAssentamento, Perfil perfil) {
        var mem = new ArrayList<String>();
        double b0 = Math.sqrt(1.1 * nk / 200.0);
        double nMedio = perfil.nsptMedio(cotaAssentamento, cotaAssentamento + 2 * Math.max(b0, 1));
        double sigmaAdm = Math.min(SIGMA_ADM_MAX_KPA, 20.0 * nMedio);
        if (sigmaAdm < 50) {
            mem.add(String.format(Locale.ROOT, "Nspt médio %.1f no bulbo → σadm = %.0f kPa < 50 kPa: solo fraco, sapata não recomendada.", nMedio, sigmaAdm));
            return new Sapata(0, 0, sigmaAdm, 0, false, mem);
        }
        double area = 1.1 * nk / sigmaAdm; // 10 % de peso próprio da sapata + solo
        double lado = Math.ceil(Math.sqrt(area) * 20) / 20.0; // múltiplo de 5 cm
        double altura = Math.max(0.3, Math.ceil(Math.max((lado - bp) / 3, (lado - hp) / 3) * 20) / 20.0 + 0.05);
        double pressao = 1.1 * nk / (lado * lado);
        mem.add(String.format(Locale.ROOT, "Nspt médio no bulbo (%.1f a %.1f m) = %.1f → σadm = 20·Nspt = %.0f kPa (≤ 400)", cotaAssentamento, cotaAssentamento + 2 * lado, nMedio, sigmaAdm));
        mem.add(String.format(Locale.ROOT, "A = 1,1·Nk/σadm = %.2f m² → B = %.2f m; h ≥ (B − b)/3 → %.2f m (sapata rígida, NBR 6118 22.6.1)", area, lado, altura));
        mem.add(String.format(Locale.ROOT, "Pressão de contato = %.0f kPa ≤ σadm", pressao));
        return new Sapata(lado, altura, Math.round(sigmaAdm), Math.round(pressao), pressao <= sigmaAdm + 1e-6, mem);
    }

    /** Aoki-Velloso (1975) para estaca pré-moldada de concreto (F1 = 1,75; F2 = 3,5) */
    public static double aokiVelloso(double diametro, double comprimento, Perfil perfil) {
        double ap = Math.PI * diametro * diametro / 4;
        double u = Math.PI * diametro;
        double f1 = 1.75, f2 = 3.5;
        double ponta = perfil.tipoEm(comprimento).kKpa() * perfil.nsptEm(comprimento) * ap / f1;
        double lateral = 0;
        for (double z = 1; z <= comprimento; z += 1) {
            var tipo = perfil.tipoEm(z);
            lateral += tipo.alfaPct() / 100 * tipo.kKpa() * perfil.nsptEm(z) * u * 1.0 / f2;
        }
        return ponta + lateral;
    }

    /** Décourt-Quaresma (1978/1996) para estaca cravada (α = β = 1) */
    public static double decourtQuaresma(double diametro, double comprimento, Perfil perfil) {
        double ap = Math.PI * diametro * diametro / 4;
        double u = Math.PI * diametro;
        double np = (perfil.nsptEm(comprimento - 1) + perfil.nsptEm(comprimento) + perfil.nsptEm(comprimento + 1)) / 3.0;
        double ponta = perfil.tipoEm(comprimento).cKpa() * np * ap;
        double somaN = 0;
        int n = 0;
        for (double z = 1; z <= comprimento - 1; z += 1) {
            somaN += Math.min(50, Math.max(3, perfil.nsptEm(z)));
            n++;
        }
        double nl = n == 0 ? 3 : somaN / n;
        double lateral = 10 * (nl / 3 + 1) * u * comprimento;
        return ponta + lateral;
    }

    /** estaqueamento para Nk (kN) com diâmetro (m) e comprimento até camada resistente (m) */
    public static Estaqueamento estacas(double nk, double diametro, double comprimento, Perfil perfil) {
        var mem = new ArrayList<String>();
        double aoki = aokiVelloso(diametro, comprimento, perfil);
        double decourt = decourtQuaresma(diametro, comprimento, perfil);
        double qadm = Math.min(aoki, decourt) / FS_ESTACA;
        int n = Math.max(1, (int) Math.ceil(nk / Math.max(qadm, 1e-6)));
        double espacamento = 3 * diametro;
        double ladoBloco = n == 1 ? Math.max(0.6, 3 * diametro) : (Math.ceil(Math.sqrt(n)) - 1) * espacamento + 2 * diametro + 0.3;
        double alturaBloco = Math.max(0.5, 0.6 * espacamento);
        mem.add(String.format(Locale.ROOT, "Aoki-Velloso: Qult = %.0f kN; Décourt-Quaresma: Qult = %.0f kN; Qadm = mín/%.1f = %.0f kN", aoki, decourt, FS_ESTACA, qadm));
        mem.add(String.format(Locale.ROOT, "n = ⌈Nk/Qadm⌉ = ⌈%.0f/%.0f⌉ = %d estaca(s) Ø %.2f m × %.1f m; bloco rígido %.2f × %.2f m, h = %.2f m", nk, qadm, n, diametro, comprimento, ladoBloco, ladoBloco, alturaBloco));
        return new Estaqueamento(diametro, comprimento, Math.round(aoki), Math.round(decourt), Math.round(qadm), n, Math.round(ladoBloco * 100) / 100.0, Math.round(alturaBloco * 100) / 100.0, mem);
    }

    /** comprimento de estaca: primeira profundidade com 3 leituras seguidas ≥ 15 golpes (ou impenetrável) */
    public static double comprimentoResistente(Perfil perfil) {
        var ls = perfil.leituras().stream().sorted((a, b) -> Double.compare(a.profundidade(), b.profundidade())).toList();
        for (int i = 0; i + 2 < ls.size(); i++) {
            if (ls.get(i).nsptCalculo() >= 15 && ls.get(i + 1).nsptCalculo() >= 15 && ls.get(i + 2).nsptCalculo() >= 15) {
                return ls.get(i + 2).profundidade();
            }
            if (ls.get(i).impenetravel()) {
                return ls.get(i).profundidade();
            }
        }
        return ls.isEmpty() ? 8.0 : ls.get(ls.size() - 1).profundidade();
    }

    public static boolean sapatasSeSobrepoem(double ladoA, double ladoB, double distanciaCentros) {
        return distanciaCentros < (ladoA + ladoB) / 2 + 0.10;
    }
}
