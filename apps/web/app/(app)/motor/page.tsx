"use client";
import { Motor } from "@/components/motor/Motor";
import { Etiqueta } from "@/components/ui";

/** Área de treino do motor (RF28): modelo local, sem obra; nada é persistido. */
export default function PaginaMotorLocal() {
  return (
    <div className="flex flex-col gap-3">
      <header className="flex items-center justify-between gap-3 flex-wrap"><div><h1 className="text-xl font-semibold">Motor de desenho · área de treino</h1><p className="text-sm text-text-3">Pratique comandos, aliases e Notação VI sem gravar em nenhuma obra.</p></div><Etiqueta tom="aviso">modelo local</Etiqueta></header>
      <Motor obraId={null} />
    </div>
  );
}
