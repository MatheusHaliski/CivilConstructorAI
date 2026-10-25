package br.com.civilconstructorai.quantitativo;

import br.com.civilconstructorai.estrutura.modelo.ElementoEstrutural;
import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import br.com.civilconstructorai.estrutura.modelo.TipoElementoEstrutural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RF11 · quantitativos determinísticos (porte de packages/notacao-vi/src/quantitativos.ts): concreto (m³),
 * fôrma (m²), aço (kg) e alvenaria (m²) por elemento e pavimento, com prioridade pilar > viga > laje nas
 * interseções. Rascunhos de IA não entram. Mesmo modelo ⇒ mesmo hash.
 */
public final class Quantificador {

    /** taxas indicativas de aço (kg/m³) usadas até o dimensionamento (RF07) estar aprovado */
    public static final Map<TipoElementoEstrutural, Double> TAXA_ACO = Map.of(
            TipoElementoEstrutural.PILAR, 110.0, TipoElementoEstrutural.VIGA, 90.0, TipoElementoEstrutural.LAJE, 70.0,
            TipoElementoEstrutural.SAPATA, 60.0, TipoElementoEstrutural.BLOCO, 80.0, TipoElementoEstrutural.RADIER, 75.0);

    private Quantificador() {
    }

    public record Linha(String pavimentoId, String elementoId, TipoElementoEstrutural tipo, String rotulo, String materialId,
                        double concretoM3, double formaM2, double acoKg, double alvenariaM2, String origemAco) {
    }

    public record Totais(double concretoM3, double formaM2, double acoKg, double alvenariaM2) {
        Totais mais(Linha l) {
            return new Totais(r3(concretoM3 + l.concretoM3), r3(formaM2 + l.formaM2), r3(acoKg + l.acoKg), r3(alvenariaM2 + l.alvenariaM2));
        }
    }

    public record Resultado(long versaoModelo, List<Linha> linhas, Map<String, Totais> totaisPorPavimento, Totais totalGeral, String hash) {
    }

    /** @param acoAprovadoKg aço por elemento vindo do dimensionamento aprovado (RF07); vazio → taxa indicativa */
    public static Resultado quantificar(ModeloEstrutural m, Map<String, Double> acoAprovadoKg) {
        var linhas = new ArrayList<Linha>();
        var contadores = new LinkedHashMap<String, Integer>();
        for (var e : m.elementos()) {
            if (e.rascunhoIa()) {
                continue;
            }
            String rotulo = e.rotulo() != null ? e.rotulo() : rotuloAutomatico(e, contadores);
            double concreto = 0, forma = 0, aco = 0, alvenaria = 0;
            String origemAco = "taxa";
            switch (e.tipo()) {
                case PILAR -> {
                    double altura = m.alturaPilar(e);
                    double area = e.b() * e.h();
                    double per = 2 * (e.b() + e.h());
                    concreto = area * altura;
                    forma = per * altura;
                }
                case VIGA -> {
                    double l = e.comprimento();
                    double lajeH = m.doPavimento(e.pavimentoId()).stream().filter(x -> x.tipo() == TipoElementoEstrutural.LAJE).mapToDouble(ElementoEstrutural::h).findFirst().orElse(0);
                    double desconto = 0;
                    for (var p : m.doPavimento(e.pavimentoId())) {
                        if (p.tipo() == TipoElementoEstrutural.PILAR && (p.centro().distancia(e.p1()) < 0.3 || p.centro().distancia(e.p2()) < 0.3)) {
                            desconto += Math.max(p.b(), p.h()) / 2;
                        }
                    }
                    double lLiq = Math.max(0, l - desconto);
                    concreto = e.b() * e.h() * lLiq;
                    forma = (2 * e.h() + e.b()) * lLiq - lajeH * lLiq;
                }
                case LAJE -> {
                    double area = ElementoEstrutural.areaPoligono(e.contorno());
                    double areaVigas = m.doPavimento(e.pavimentoId()).stream().filter(x -> x.tipo() == TipoElementoEstrutural.VIGA).mapToDouble(v -> v.b() * v.comprimento()).sum();
                    double areaLiq = Math.max(0, area - Math.min(areaVigas, area * 0.3));
                    concreto = areaLiq * e.h();
                    forma = areaLiq + ElementoEstrutural.perimetroPoligono(e.contorno()) * e.h();
                }
                case PAREDE -> {
                    double l = e.comprimento();
                    double aberturas = m.elementos().stream().filter(x -> (x.tipo() == TipoElementoEstrutural.PORTA || x.tipo() == TipoElementoEstrutural.JANELA) && e.id().equals(x.paredeId())).mapToDouble(x -> x.b() * x.h()).sum();
                    alvenaria = Math.max(0, l * e.h() - aberturas);
                }
                case SAPATA, BLOCO, RADIER -> {
                    concreto = e.bx() * e.by() * e.h();
                    forma = 2 * (e.bx() + e.by()) * e.h();
                }
                default -> {
                    continue;
                }
            }
            if (concreto > 0) {
                if (acoAprovadoKg != null && acoAprovadoKg.containsKey(e.id())) {
                    aco = acoAprovadoKg.get(e.id());
                    origemAco = "dimensionamento aprovado";
                } else {
                    aco = concreto * TAXA_ACO.getOrDefault(e.tipo(), 0.0);
                }
            }
            linhas.add(new Linha(e.pavimentoId(), e.id(), e.tipo(), rotulo, e.materialId(), r3(concreto), r3(forma), r3(aco), r3(alvenaria), origemAco));
        }
        var totais = new LinkedHashMap<String, Totais>();
        var geral = new Totais(0, 0, 0, 0);
        for (var l : linhas) {
            totais.merge(l.pavimentoId(), new Totais(0, 0, 0, 0).mais(l), (a, b) -> a.mais(l));
            geral = geral.mais(l);
        }
        var sb = new StringBuilder();
        for (var l : linhas) {
            sb.append(l.elementoId()).append('|').append(l.tipo()).append('|').append(l.concretoM3).append('|').append(l.formaM2).append('|').append(l.acoKg).append('|').append(l.alvenariaM2).append(';');
        }
        return new Resultado(m.versao(), List.copyOf(linhas), totais, geral, fnv1a(sb.toString()));
    }

    private static String rotuloAutomatico(ElementoEstrutural e, Map<String, Integer> contadores) {
        String prefixo = switch (e.tipo()) {
            case PILAR -> "P"; case VIGA -> "V"; case LAJE -> "L"; case PAREDE -> "PAR"; case PORTA -> "PT"; case JANELA -> "J";
            case SAPATA -> "S"; case BLOCO -> "B"; case RADIER -> "R";
        };
        int n = contadores.merge(e.tipo() + "/" + e.pavimentoId(), 1, Integer::sum);
        return prefixo + n;
    }

    static double r3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** FNV-1a 32 bits, igual ao do pacote TypeScript */
    public static String fnv1a(String s) {
        int h = 0x811c9dc5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return String.format("%08x", h);
    }
}
