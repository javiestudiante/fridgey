import { getFirestore, DocumentReference } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";

/**
 * Códigos de error de FCM que significan "este token ya no sirve" y, por tanto,
 * hay que PODARLO (borrar su doc en usuarios/{uid}/tokens). El set se valida
 * contra los typings de firebase-admin v13 instalado (ver functions/README.md):
 *  - registration-token-not-registered: la app se desinstaló / el token caducó.
 *  - invalid-argument: token malformado (p.ej. cadena corrupta en la ruta).
 * Cualquier otro código (unavailable, internal, quota…) es transitorio: solo log,
 * NUNCA se borra el token.
 */
const CODIGOS_TOKEN_MUERTO = new Set<string>([
  "messaging/registration-token-not-registered",
  "messaging/invalid-argument",
]);

/** Tipos de evento que el HITO 4 usa para enrutar el tap. */
export type TipoEvento =
  | "colaborador_alta"
  | "auto_salida"
  | "expulsion"
  | "nevera_borrada"
  | "producto_alta";

/** Carga lista para enviar. El llamador YA ha excluido al actor. */
export interface PushPayload {
  tipo: TipoEvento;
  neveraId: string;
  title: string;
  body: string;
}

/**
 * Reparte [payload] a CADA uid de [recipientUids] (no un multicast global): así
 * `data.destinatarioUid` viaja por destinatario y el HITO 4 puede filtrar, en un
 * dispositivo compartido, contra la sesión activa.
 *
 * - Early-return si no hay destinatarios (cubre el lote de uploadNevera
 *   LOCAL→SYNCED, donde `colaboradores` está vacío y el set queda vacío).
 * - Cada destinatario se procesa en su propio try/catch: un fallo de envío a uno
 *   NO aborta el resto.
 */
export async function fanOut(recipientUids: string[], payload: PushPayload): Promise<void> {
  const uids = [...new Set(recipientUids)].filter((u) => typeof u === "string" && u.length > 0);
  if (uids.length === 0) return;
  const db = getFirestore();
  await Promise.all(uids.map((uid) => enviarADestinatario(db, uid, payload)));
}

async function enviarADestinatario(
  db: FirebaseFirestore.Firestore,
  uid: string,
  payload: PushPayload,
): Promise<void> {
  try {
    const snap = await db.collection("usuarios").doc(uid).collection("tokens").get();
    if (snap.empty) return;

    // token -> ref (multi-device: un usuario puede tener varias instalaciones).
    const refPorToken = new Map<string, DocumentReference>();
    snap.forEach((doc) => {
      const token = doc.get("token");
      if (typeof token === "string" && token.length > 0) {
        refPorToken.set(token, doc.ref);
      }
    });
    const tokens = [...refPorToken.keys()];
    if (tokens.length === 0) return;

    const message: MulticastMessage = {
      tokens,
      notification: { title: payload.title, body: payload.body },
      data: {
        tipo: payload.tipo,
        neveraId: payload.neveraId,
        destinatarioUid: uid,
      },
      android: { priority: "high" },
      apns: { payload: { aps: { sound: "default" } } },
    };

    const respuesta = await getMessaging().sendEachForMulticast(message);
    if (respuesta.failureCount === 0) return;

    const podas: Promise<unknown>[] = [];
    respuesta.responses.forEach((res, i) => {
      if (res.success || !res.error) return;
      const code = res.error.code;
      if (CODIGOS_TOKEN_MUERTO.has(code)) {
        const ref = refPorToken.get(tokens[i]);
        if (ref) podas.push(ref.delete().catch((e) => logger.warn(`No se pudo podar token de ${uid}`, e)));
      } else {
        logger.warn(`Fallo de envío (no fatal) a ${uid}: ${code}`, res.error);
      }
    });
    await Promise.all(podas);
  } catch (e) {
    logger.error(`fanOut: error enviando a ${uid}`, e);
  }
}

/**
 * Nombre legible de un miembro a partir del array desnormalizado
 * `nevera.miembros` (uid/nombre/fotoUrl), sin reads extra cuando el doc ya está
 * disponible. Fallback "Alguien" si no se encuentra (p.ej. miembro recién salido
 * cuyo perfil ya no está en `after.miembros`; en esos casos se pasa el array
 * `before.miembros`).
 */
export function nombreMiembro(miembros: unknown, uid: string): string {
  if (!Array.isArray(miembros)) return "Alguien";
  const m = miembros.find((x) => x && typeof x === "object" && (x as { uid?: string }).uid === uid) as
    | { nombre?: string }
    | undefined;
  const nombre = m?.nombre;
  return typeof nombre === "string" && nombre.length > 0 ? nombre : "Alguien";
}
