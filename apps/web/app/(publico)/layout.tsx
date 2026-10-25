import Link from "next/link";
import { Logomarca } from "@/components/marca/IconesMarca";

export default function LayoutPublico({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh grid lg:grid-cols-[1.1fr_1fr]">
      <aside className="hidden lg:flex flex-col justify-between bg-marca-grafite text-white p-10">
        <Link href="/" className="flex items-center gap-3"><Logomarca tamanho={48} /><span className="text-lg font-semibold">CivilConstructor<span className="text-marca-laranja">AI</span></span></Link>
        <div className="max-w-md flex flex-col gap-4">
          <h2 className="text-3xl font-semibold leading-tight">CAD/BIM generativo com auditoria paramétrica.</h2>
          <p className="text-white/80">Desenhe em Notação VI ou em texto natural, deixe o auditor corrigir vãos e níveis, e saia com estrutura, fundações, quantitativos e pranchas rastreáveis.</p>
          <ul className="text-sm text-white/70 flex flex-col gap-1">
            <li>• Toda saída de IA é rascunho até a aprovação de profissional habilitado.</li>
            <li>• Seus dados: consentimentos por finalidade, exportação e exclusão com carência (LGPD).</li>
          </ul>
        </div>
        <p className="text-xs text-white/50">© {new Date().getFullYear()} CivilConstructorAI</p>
      </aside>
      <main className="flex items-center justify-center p-4 sm:p-8">
        <div className="w-full max-w-md">
          <Link href="/" className="lg:hidden flex items-center gap-2 mb-2 font-semibold text-text"><Logomarca tamanho={32} /> CivilConstructorAI</Link>
          <p className="lg:hidden mb-6 text-sm text-text-3">CAD/BIM com auditoria: projete, calcule, orce e entregue pranchas rastreáveis; toda saída de IA é rascunho até um profissional aprovar.</p>
          {children}
        </div>
      </main>
    </div>
  );
}
