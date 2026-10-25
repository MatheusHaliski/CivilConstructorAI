const fmtData = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const fmtDia = new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" });
const fmtMoeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export const data = (v?: string | number | Date | null) => (v ? fmtData.format(new Date(v)) : "—");
export const dia = (v?: string | number | Date | null) => (v ? fmtDia.format(new Date(v)) : "—");
export const moeda = (v?: number | string | null) => (v === undefined || v === null || v === "" ? "—" : fmtMoeda.format(Number(v)));
export const numero = (v?: number | null, casas = 2) => (v === undefined || v === null ? "—" : new Intl.NumberFormat("pt-BR", { minimumFractionDigits: casas, maximumFractionDigits: casas }).format(v));
export const metros = (v?: number | null, casas = 2) => (v === undefined || v === null ? "—" : `${numero(v, casas)} m`);
export const bytes = (v?: number | null) => (v === undefined || v === null ? "—" : v > 1e6 ? `${(v / 1e6).toFixed(1)} MB` : `${Math.round(v / 1e3)} kB`);
export const mascararCnpj = (v: string) => v.replace(/\D/g, "").replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2}).*/, "$1.$2.$3/$4-$5");
export const estadoHumano = (e?: string) => (e ?? "").replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
