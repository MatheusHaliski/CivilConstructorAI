import { redirect } from "next/navigation";

/** A aba abre na primeira sub-aba; as demais ficam visíveis na barra lateral. */
export default async function Pagina() {
  redirect("/conta/privacidade/consentimentos");
}
