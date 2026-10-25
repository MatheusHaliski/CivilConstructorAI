/** Conversão entre o JSON do backend (elemento com `props`) e o EstadoModelo do pacote @cca/notacao-vi. */
import type { Elemento, EstadoModelo } from "@cca/notacao-vi";
import type { VisaoModelo } from "@/lib/tipos";

export function deServidor(v: VisaoModelo): EstadoModelo {
  const elementos = v.elementos.map((e) => {
    const { props, ...resto } = e as { props?: Record<string, unknown> } & Record<string, unknown>;
    const camada = String(resto["camada"] ?? "desenho").replace("_", "-");
    return { ...(props ?? {}), ...resto, camada } as unknown as Elemento;
  });
  return { versao: v.versao, pavimentos: v.pavimentos.map((p) => ({ ...p, tipo: p.tipo as EstadoModelo["pavimentos"][number]["tipo"] })), eixos: v.eixos, elementos };
}
