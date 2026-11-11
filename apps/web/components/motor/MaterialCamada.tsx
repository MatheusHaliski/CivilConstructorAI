"use client";
import { useEffect, useMemo } from "react";
import * as THREE from "three";
import type { CamadaAcabamento } from "@cca/notacao-vi";
import { parametrosTexturaModulo } from "@/lib/motor/geometria-cena";

type Props = {
  camada: CamadaAcabamento; cor: string; selecionado: boolean;
  superficie: Parameters<typeof parametrosTexturaModulo>[3];
};

/** Paginação diagramática local: cada repetição representa uma peça e sua junta, sem foto ou plano de corte. */
export function MaterialCamada({ camada, cor, selecionado, superficie }: Props) {
  const { larguraPeca_m, alturaPeca_m, junta_m } = camada;
  const p = larguraPeca_m && alturaPeca_m ? parametrosTexturaModulo(larguraPeca_m, alturaPeca_m, junta_m, superficie) : undefined;
  const repeatX = p?.repeat[0], repeatY = p?.repeat[1], offsetX = p?.offset[0], offsetY = p?.offset[1], juntaU = p?.juntaU, juntaV = p?.juntaV;
  const textura = useMemo(() => {
    if (repeatX === undefined || repeatY === undefined || juntaU === undefined || juntaV === undefined) return undefined;
    const lado = 256, dados = new Uint8Array(lado * lado * 4);
    for (let y = 0; y < lado; y++) for (let x = 0; x < lado; x++) {
      const u = (x + .5) / lado, v = (y + .5) / lado;
      const junta = u < juntaU / 2 || u > 1 - juntaU / 2 || v < juntaV / 2 || v > 1 - juntaV / 2;
      const i = (y * lado + x) * 4, tom = junta ? 110 : 255;
      dados[i] = tom; dados[i + 1] = tom; dados[i + 2] = tom; dados[i + 3] = 255;
    }
    const mapa = new THREE.DataTexture(dados, lado, lado, THREE.RGBAFormat);
    mapa.wrapS = mapa.wrapT = THREE.RepeatWrapping;
    mapa.repeat.set(repeatX, repeatY); mapa.offset.set(offsetX ?? 0, offsetY ?? 0);
    mapa.colorSpace = THREE.SRGBColorSpace;
    mapa.magFilter = THREE.LinearFilter; mapa.minFilter = THREE.LinearMipmapLinearFilter;
    mapa.generateMipmaps = true; mapa.needsUpdate = true;
    return mapa;
  }, [repeatX, repeatY, offsetX, offsetY, juntaU, juntaV]);
  useEffect(() => () => textura?.dispose(), [textura]);
  return <meshStandardMaterial color={selecionado ? "#F06000" : cor} map={textura ?? null} roughness={.8} />;
}
