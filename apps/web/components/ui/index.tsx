"use client";
import { forwardRef, useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { AlertTriangle, CheckCircle2, Info, Loader2, X, XCircle } from "lucide-react";

export const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");

/* ---------------------------------------------------------------- Botão */
type Variante = "primario" | "secundario" | "perigo" | "fantasma" | "link";
export type BotaoProps = ButtonHTMLAttributes<HTMLButtonElement> & { variante?: Variante; carregando?: boolean; icone?: ReactNode; pequeno?: boolean };
const variantes: Record<Variante, string> = {
  primario: "bg-brand text-on-brand hover:brightness-110 border border-transparent",
  secundario: "bg-bg text-text border border-line hover:bg-surface",
  perigo: "bg-danger text-white border border-transparent hover:brightness-110",
  fantasma: "bg-transparent text-text-2 hover:bg-surface border border-transparent",
  link: "bg-transparent text-brand-text underline underline-offset-4 border border-transparent px-1",
};
export const Botao = forwardRef<HTMLButtonElement, BotaoProps>(function Botao({ variante = "primario", carregando, icone, pequeno, className, children, disabled, ...rest }, ref) {
  return (
    <button ref={ref} disabled={disabled || carregando} aria-busy={carregando || undefined}
      className={cx("inline-flex items-center justify-center gap-2 rounded-[var(--radius-sm)] font-medium transition-[background,filter] duration-[var(--dur-fast)] disabled:opacity-50 disabled:cursor-not-allowed", pequeno ? "min-h-9 px-3 text-sm" : "tap px-4 text-sm", variantes[variante], className)} {...rest}>
      {carregando ? <Loader2 className="size-4 animate-spin" aria-hidden /> : icone}
      {children}
    </button>
  );
});

/* ---------------------------------------------------------------- Campos */
type CampoBase = { rotulo: string; ajuda?: string; erro?: string; obrigatorio?: boolean };
export function Campo({ rotulo, ajuda, erro, obrigatorio, id, children }: CampoBase & { id: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-text">{rotulo}{obrigatorio && <span aria-hidden className="text-danger"> *</span>}</label>
      {children}
      {ajuda && !erro && <p id={`${id}-ajuda`} className="text-xs text-text-3">{ajuda}</p>}
      {erro && <p id={`${id}-erro`} role="alert" className="text-xs text-danger flex items-center gap-1"><XCircle className="size-3.5" aria-hidden />{erro}</p>}
    </div>
  );
}
const classeInput = "tap w-full rounded-[var(--radius-sm)] border border-line bg-bg px-3 text-sm text-text placeholder:text-text-3 aria-[invalid=true]:border-danger";
export type EntradaProps = InputHTMLAttributes<HTMLInputElement> & CampoBase;
export const Entrada = forwardRef<HTMLInputElement, EntradaProps>(function Entrada({ rotulo, ajuda, erro, obrigatorio, id, className, ...rest }, ref) {
  const gerado = useId(); const i = id ?? gerado;
  return (
    <Campo rotulo={rotulo} ajuda={ajuda} erro={erro} obrigatorio={obrigatorio} id={i}>
      <input ref={ref} id={i} aria-invalid={!!erro} aria-describedby={erro ? `${i}-erro` : ajuda ? `${i}-ajuda` : undefined} required={obrigatorio} className={cx(classeInput, className)} {...rest} />
    </Campo>
  );
});
export type SelecaoProps = SelectHTMLAttributes<HTMLSelectElement> & CampoBase & { opcoes: { valor: string; rotulo: string }[]; vazio?: string };
export function Selecao({ rotulo, ajuda, erro, obrigatorio, id, opcoes, vazio, className, ...rest }: SelecaoProps) {
  const gerado = useId(); const i = id ?? gerado;
  return (
    <Campo rotulo={rotulo} ajuda={ajuda} erro={erro} obrigatorio={obrigatorio} id={i}>
      <select id={i} aria-invalid={!!erro} required={obrigatorio} className={cx(classeInput, className)} {...rest}>
        {vazio !== undefined && <option value="">{vazio}</option>}
        {opcoes.map((o) => <option key={o.valor} value={o.valor}>{o.rotulo}</option>)}
      </select>
    </Campo>
  );
}
export function AreaTexto({ rotulo, ajuda, erro, obrigatorio, id, className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement> & CampoBase) {
  const gerado = useId(); const i = id ?? gerado;
  return (
    <Campo rotulo={rotulo} ajuda={ajuda} erro={erro} obrigatorio={obrigatorio} id={i}>
      <textarea id={i} aria-invalid={!!erro} required={obrigatorio} className={cx(classeInput, "min-h-24 py-2", className)} {...rest} />
    </Campo>
  );
}
export function Caixa({ rotulo, descricao, ...rest }: InputHTMLAttributes<HTMLInputElement> & { rotulo: ReactNode; descricao?: ReactNode }) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex items-start gap-3 tap py-2 cursor-pointer">
      <input id={id} type="checkbox" className="mt-1 size-5 accent-[var(--brand)]" {...rest} />
      <span className="text-sm text-text"><span className="font-medium">{rotulo}</span>{descricao && <span className="block text-text-3 text-xs mt-0.5">{descricao}</span>}</span>
    </label>
  );
}
export function Alternar({ rotulo, descricao, ligado, onChange, disabled }: { rotulo: string; descricao?: string; ligado: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4 py-3 border-b border-line-soft last:border-0">
      <div><p className="text-sm font-medium text-text">{rotulo}</p>{descricao && <p className="text-xs text-text-3 mt-0.5">{descricao}</p>}</div>
      <button type="button" role="switch" aria-checked={ligado} aria-label={rotulo} disabled={disabled} onClick={() => onChange(!ligado)}
        className={cx("relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors", ligado ? "bg-brand border-brand" : "bg-surface-2 border-line", disabled && "opacity-50")}>
        <span className={cx("size-5 rounded-full bg-white shadow transition-transform", ligado ? "translate-x-6" : "translate-x-1")} />
        <span className="sr-only">{ligado ? "ligado" : "desligado"}</span>
      </button>
    </div>
  );
}

/* ---------------------------------------------------------------- Superfícies */
export function Cartao({ titulo, descricao, acoes, children, className }: { titulo?: ReactNode; descricao?: ReactNode; acoes?: ReactNode; children?: ReactNode; className?: string }) {
  return (
    <section className={cx("rounded-[var(--radius-md)] border border-line-soft bg-bg p-4 sm:p-5 flex flex-col gap-4", className)}>
      {(titulo || acoes) && (
        <header className="flex items-start justify-between gap-3 flex-wrap">
          <div>{titulo && <h2 className="text-base font-semibold text-text">{titulo}</h2>}{descricao && <p className="text-sm text-text-3 mt-0.5">{descricao}</p>}</div>
          {acoes && <div className="flex gap-2 flex-wrap">{acoes}</div>}
        </header>
      )}
      {children}
    </section>
  );
}
export function CabecalhoPagina({ titulo, descricao, acoes, migalhas }: { titulo: ReactNode; descricao?: ReactNode; acoes?: ReactNode; migalhas?: ReactNode }) {
  return (
    <header className="flex flex-col gap-2 mb-6">
      {migalhas && <nav aria-label="Você está em" className="text-xs text-text-3">{migalhas}</nav>}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div><h1 className="text-2xl font-semibold text-text">{titulo}</h1>{descricao && <p className="text-sm text-text-3 mt-1 max-w-prose">{descricao}</p>}</div>
        {acoes && <div className="flex gap-2 flex-wrap">{acoes}</div>}
      </div>
    </header>
  );
}
type Tom = "neutro" | "sucesso" | "aviso" | "perigo" | "info" | "ia" | "marca";
const tons: Record<Tom, string> = {
  neutro: "bg-surface text-text-2 border-line-soft", sucesso: "bg-success-bg text-success border-transparent", aviso: "bg-warning-bg text-warning border-transparent",
  perigo: "bg-danger-bg text-danger border-transparent", info: "bg-info-bg text-info border-transparent", ia: "bg-ai-bg text-ai-text border-ai-line", marca: "bg-brand-soft text-brand-text border-transparent",
};
export function Etiqueta({ tom = "neutro", icone, children, className }: { tom?: Tom; icone?: ReactNode; children: ReactNode; className?: string }) {
  return <span className={cx("inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap", tons[tom], className)}>{icone}{children}</span>;
}
export function Aviso({ tom = "info", titulo, children, className }: { tom?: Exclude<Tom, "neutro" | "marca">; titulo?: ReactNode; children?: ReactNode; className?: string }) {
  const Icone = tom === "sucesso" ? CheckCircle2 : tom === "aviso" ? AlertTriangle : tom === "perigo" ? XCircle : Info;
  return (
    <div role={tom === "perigo" ? "alert" : "status"} className={cx("flex gap-3 rounded-[var(--radius-sm)] border p-3 text-sm", tons[tom], className)}>
      <Icone className="size-5 shrink-0 mt-0.5" aria-hidden />
      <div>{titulo && <p className="font-semibold">{titulo}</p>}{children && <div className="mt-0.5">{children}</div>}</div>
    </div>
  );
}
export function Vazio({ icone, titulo, descricao, acao }: { icone?: ReactNode; titulo: string; descricao?: string; acao?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center text-center gap-2 py-12 px-4 rounded-[var(--radius-md)] border border-dashed border-line-soft">
      {icone && <div className="text-text-3 mb-1">{icone}</div>}
      <p className="font-medium text-text">{titulo}</p>
      {descricao && <p className="text-sm text-text-3 max-w-sm">{descricao}</p>}
      {acao && <div className="mt-2">{acao}</div>}
    </div>
  );
}
export function Carregando({ texto = "Carregando…" }: { texto?: string }) {
  return <p role="status" className="flex items-center gap-2 text-sm text-text-3 py-6"><Loader2 className="size-4 animate-spin" aria-hidden />{texto}</p>;
}
export function Esqueleto({ linhas = 3 }: { linhas?: number }) {
  return <div aria-hidden className="flex flex-col gap-2">{Array.from({ length: linhas }, (_, i) => <div key={i} className="skeleton h-5" style={{ width: `${90 - i * 15}%` }} />)}</div>;
}

/* ---------------------------------------------------------------- Tabela */
export function Tabela<T>({ colunas, linhas, chave, vazio, className, rotulo }: { colunas: { titulo: string; celula: (l: T) => ReactNode; numerica?: boolean; largura?: string }[]; linhas: T[]; chave: (l: T) => string; vazio?: ReactNode; className?: string; rotulo?: string }) {
  if (!linhas.length && vazio) return <>{vazio}</>;
  return (
    <div tabIndex={0} role="region" aria-label={rotulo ?? `Tabela: ${colunas.map((c) => c.titulo).filter(Boolean).slice(0, 3).join(", ")}`} className={cx("overflow-x-auto focus-visible:outline-2 focus-visible:outline-brand rounded-[var(--radius-sm)] border border-line-soft", className)}>
      <table className="w-full text-sm">
        <thead className="bg-surface text-left text-xs uppercase tracking-wide text-text-3">
          <tr>{colunas.map((c) => <th key={c.titulo} scope="col" style={{ width: c.largura }} className={cx("px-3 py-2 font-semibold", c.numerica && "text-right")}>{c.titulo}</th>)}</tr>
        </thead>
        <tbody>
          {linhas.map((l) => (
            <tr key={chave(l)} className="border-t border-line-soft hover:bg-surface/60">
              {colunas.map((c) => <td key={c.titulo} className={cx("px-3 py-2 align-top", c.numerica && "text-right tabular")}>{c.celula(l)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ---------------------------------------------------------------- Abas */
export function Abas({ abas, ativa, onChange }: { abas: { id: string; rotulo: string; icone?: ReactNode }[]; ativa: string; onChange: (id: string) => void }) {
  return (
    <div role="tablist" className="flex gap-1 border-b border-line-soft overflow-x-auto">
      {abas.map((a) => (
        <button key={a.id} role="tab" aria-selected={ativa === a.id} onClick={() => onChange(a.id)}
          className={cx("tap inline-flex items-center gap-2 px-3 text-sm whitespace-nowrap border-b-2 -mb-px", ativa === a.id ? "border-brand text-brand-text font-medium" : "border-transparent text-text-2 hover:text-text")}>
          {a.icone}{a.rotulo}
        </button>
      ))}
    </div>
  );
}

/* ---------------------------------------------------------------- Diálogo */
export function Dialogo({ aberto, onFechar, titulo, children, acoes, perigo, largura = "max-w-lg" }: { aberto: boolean; onFechar: () => void; titulo: string; children: ReactNode; acoes?: ReactNode; perigo?: boolean; largura?: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { const d = ref.current; if (!d) return; if (aberto && !d.open) d.showModal(); if (!aberto && d.open) d.close(); }, [aberto]);
  return (
    <dialog ref={ref} onClose={onFechar} role={perigo ? "alertdialog" : "dialog"} aria-labelledby="dlg-titulo"
      className={cx("m-auto w-[calc(100%-32px)] rounded-[var(--radius-lg)] border border-line-soft bg-bg p-0 text-text shadow-xl backdrop:bg-black/50", largura)}>
      <form method="dialog" className="flex flex-col">
        <header className="flex items-center justify-between gap-3 border-b border-line-soft px-5 py-4">
          <h2 id="dlg-titulo" className="text-lg font-semibold">{titulo}</h2>
          <button type="button" onClick={onFechar} aria-label="Fechar" className="tap px-2 rounded-[var(--radius-sm)] hover:bg-surface"><X className="size-5" aria-hidden /></button>
        </header>
        <div className="px-5 py-4 flex flex-col gap-4">{children}</div>
        {acoes && <footer className="flex justify-end gap-2 border-t border-line-soft px-5 py-3">{acoes}</footer>}
      </form>
    </dialog>
  );
}

/* ---------------------------------------------------------------- Toasts */
type Toast = { id: number; tom: Exclude<Tom, "neutro" | "marca" | "ia">; texto: string };
const ouvintes = new Set<(t: Toast) => void>();
let seqToast = 0;
export const avisar = (texto: string, tom: Toast["tom"] = "info") => ouvintes.forEach((f) => f({ id: ++seqToast, tom, texto }));
export function Toasts() {
  const [lista, setLista] = useState<Toast[]>([]);
  useEffect(() => {
    const f = (t: Toast) => { setLista((l) => [...l, t]); setTimeout(() => setLista((l) => l.filter((x) => x.id !== t.id)), 6000); };
    ouvintes.add(f); return () => { ouvintes.delete(f); };
  }, []);
  return (
    <div aria-live="polite" className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      {lista.map((t) => <Aviso key={t.id} tom={t.tom} className="shadow-lg bg-bg">{t.texto}</Aviso>)}
    </div>
  );
}
