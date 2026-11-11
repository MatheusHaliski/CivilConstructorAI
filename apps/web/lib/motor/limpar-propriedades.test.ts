import { afterEach, describe, expect, it, vi } from "vitest";
import { ModeloBim, type Parede } from "@cca/notacao-vi";
import { get, post } from "@/lib/api";
import { useMotor } from "./estado";

vi.mock("@/lib/api", async importOriginal => ({ ...await importOriginal<typeof import("@/lib/api")>(), get: vi.fn(), post: vi.fn() }));
afterEach(() => { useMotor.setState({obraId:null}); vi.clearAllMocks(); });
describe("Remoção de propriedades opcionais com sincronização", () => {
  for (const campo of ["massaNucleo_kg_m2","apoioEstruturalId"] as const) it(`remove ${campo} também no JSON enviado e no recarregamento`, async () => {
    const parede: Parede={id:"p",tipo:"PAREDE",pavimentoId:"pav_terreo",camada:"arquitetura",origem:"CANVAS",materialId:"MAT_ALV_BLOCO14",p1:{x:0,y:0},p2:{x:5,y:0},altura:2.8,espessura:.14,externa:false,massaNucleo_kg_m2:4,apoioEstruturalId:"v"};
    const modelo=new ModeloBim({elementos:[parede]});
    useMotor.setState({modelo,obraId:"obra",pavimentoId:"pav_terreo",versaoServidor:0,podeEditar:true,comandosServidor:[]});
    vi.mocked(post).mockResolvedValueOnce({comandoId:"cmd"});
    vi.mocked(get).mockResolvedValueOnce({versao:1,pavimentos:modelo.pavimentos,eixos:[],elementos:[{...parede,[campo]:null}]});
    expect(await useMotor.getState().alterarPropriedade("p",campo,undefined)).toBe(true);
    const corpo=vi.mocked(post).mock.calls[0]![1] as {args:{props:Record<string,unknown>}};
    expect(JSON.parse(JSON.stringify(corpo)).args.props).toHaveProperty(campo,null);
    expect((useMotor.getState().modelo.elemento("p") as Parede)[campo]).toBeUndefined();
  });
});
