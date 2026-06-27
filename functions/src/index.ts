import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp, FieldValue } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import {
  onDocumentUpdated,
  onDocumentDeleted,
  onDocumentCreated,
} from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { fanOut, nombreMiembro } from "./fanout";

initializeApp();

// Región europe-west1 para acercarse al Firestore multirregión eur3
// (europe-west1 + europe-west2). retry desactivado por defecto en cada trigger
// (ver más abajo): un fallo transitorio NO debe re-disparar el fan-out.
setGlobalOptions({ region: "europe-west1" });

/** Sin reintentos: re-disparar duplicaría notificaciones (no son idempotentes). */
const SIN_RETRY = { retry: false };

const COL_NEVERAS = "neveras";
const COL_USUARIOS = "usuarios";

/**
 * ¿El marcador de expulsión es FRESCO? Es expulsión/dejar-de-compartir SII
 * `ultimoEventoColabAt` se estampó en el MISMO write que `updatedAt` (ambos
 * serverTimestamp → idénticos). La auto-salida no puede tocar `ultimoEventoColabAt`
 * (hasOnly de esAutoSalidaValida) y el renombrado no lo estampa, así que solo un
 * write de baja del dueño los deja iguales. Compara con `.isEqual()` (NUNCA ===).
 */
function marcadorExpulsionFresco(after: FirebaseFirestore.DocumentData): boolean {
  const at = after.ultimoEventoColabAt;
  const upd = after.updatedAt;
  if (!(at instanceof Timestamp) || !(upd instanceof Timestamp)) return false;
  return at.isEqual(upd);
}

// =============================================================================
// A) Membresía: UNA sola onDocumentUpdated sobre el doc de nevera. Diff del
//    array `colaboradores` (before vs after) como conjuntos.
// =============================================================================
export const onMembresiaCambiada = onDocumentUpdated(
  { document: "neveras/{neveraId}", ...SIN_RETRY },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const neveraId = event.params.neveraId;
    const colabBefore: string[] = Array.isArray(before.colaboradores) ? before.colaboradores : [];
    const colabAfter: string[] = Array.isArray(after.colaboradores) ? after.colaboradores : [];
    const setBefore = new Set(colabBefore);
    const setAfter = new Set(colabAfter);

    const nuevos = colabAfter.filter((u) => !setBefore.has(u));
    const retirados = colabBefore.filter((u) => !setAfter.has(u));

    // Sin cambios en colaboradores → renombrado o bump de updatedAt por sync.
    if (nuevos.length === 0 && retirados.length === 0) return;

    const idPropietario: string = after.idPropietario ?? before.idPropietario ?? "";
    const titulo: string = after.nombre ?? "Nevera";

    // --- ALTA: alguien se unió. Actor = nuevos; destinatarios = miembros previos.
    if (nuevos.length > 0) {
      const destinatarios = [...colabBefore, idPropietario].filter((u) => !nuevos.includes(u));
      // Normalmente un único nuevo (auto-unión por invitación).
      await Promise.all(
        nuevos.map((nuevo) =>
          fanOut(destinatarios, {
            tipo: "colaborador_alta",
            neveraId,
            title: titulo,
            body: `${nombreMiembro(after.miembros, nuevo)} se ha unido a la nevera.`,
          }),
        ),
      );
    }

    // --- BAJA: salieron uids. La FUENTE DE VERDAD de a quién afecta es `retirados`.
    if (retirados.length > 0) {
      if (marcadorExpulsionFresco(after)) {
        // Expulsión / dejar-de-compartir. Actor = dueño (excluido de "los que quedan").
        const quedan = colabAfter.filter((u) => u !== idPropietario);
        await Promise.all([
          // A cada expulsado.
          fanOut(retirados, {
            tipo: "expulsion",
            neveraId,
            title: titulo,
            body: "Ya no formas parte de la nevera.",
          }),
          // A los que quedan, un aviso por cada expulsado (nombre desde before.miembros,
          // su perfil ya no está en after.miembros).
          ...retirados.map((r) =>
            fanOut(quedan, {
              tipo: "expulsion",
              neveraId,
              title: titulo,
              body: `${nombreMiembro(before.miembros, r)} ya no forma parte de la nevera.`,
            }),
          ),
        ]);
      } else {
        // Auto-salida. Actor = el uid retirado (ya fuera de colaboradores). El dueño
        // NO es el actor aquí, así que SÍ se le notifica.
        const quedan = [...colabAfter, idPropietario];
        await Promise.all(
          retirados.map((r) =>
            fanOut(
              quedan.filter((u) => u !== r),
              {
                tipo: "auto_salida",
                neveraId,
                title: titulo,
                body: `${nombreMiembro(before.miembros, r)} ha salido de la nevera.`,
              },
            ),
          ),
        );
      }
    }
  },
);

// =============================================================================
// B) Nevera borrada. Actor = idPropietario (solo el dueño puede borrar).
//    Destinatarios = colaboradores del SNAPSHOT eliminado (excluye al dueño).
// =============================================================================
export const onNeveraBorrada = onDocumentDeleted(
  { document: "neveras/{neveraId}", ...SIN_RETRY },
  async (event) => {
    const snap = event.data?.data();
    if (!snap) return;
    const neveraId = event.params.neveraId;
    const colaboradores: string[] = Array.isArray(snap.colaboradores) ? snap.colaboradores : [];
    const nombre: string = snap.nombre ?? "";
    await fanOut(colaboradores, {
      tipo: "nevera_borrada",
      neveraId,
      title: "Nevera eliminada",
      body: `El propietario ha eliminado la nevera ${nombre}.`.replace(/\s+\./, "."),
    });
  },
);

// =============================================================================
// C) Producto añadido (onCreate del subdocumento). El padre no viene en el
//    evento: se lee neveras/{neveraId} para colaboradores/idPropietario/miembros.
//    Actor = producto.creadoPor.
// =============================================================================
export const onProductoAnadido = onDocumentCreated(
  { document: "neveras/{neveraId}/productos/{productoId}", ...SIN_RETRY },
  async (event) => {
    const producto = event.data?.data();
    if (!producto) return;
    const neveraId = event.params.neveraId;

    const neveraSnap = await getFirestore().collection(COL_NEVERAS).doc(neveraId).get();
    if (!neveraSnap.exists) {
      logger.warn(`producto_alta: la nevera ${neveraId} ya no existe; sin fan-out.`);
      return;
    }
    const nevera = neveraSnap.data() as FirebaseFirestore.DocumentData;
    const colaboradores: string[] = Array.isArray(nevera.colaboradores) ? nevera.colaboradores : [];
    const idPropietario: string = nevera.idPropietario ?? "";
    const creadoPor: string = typeof producto.creadoPor === "string" ? producto.creadoPor : "";

    const miembros = [...colaboradores, idPropietario];
    // Si creadoPor viene vacío (productos antiguos/offline), no se excluye a nadie.
    const destinatarios = creadoPor ? miembros.filter((u) => u !== creadoPor) : miembros;

    const nombreActor = creadoPor ? nombreMiembro(nevera.miembros, creadoPor) : "Alguien";
    const nombreProducto: string = producto.nombre ?? "un producto";

    await fanOut(destinatarios, {
      tipo: "producto_alta",
      neveraId,
      title: nevera.nombre ?? "Nevera",
      body: `${nombreActor} ha añadido ${nombreProducto}.`,
    });
  },
);

// =============================================================================
// D) Eliminar cuenta (callable). RGPD — derecho de supresión, server-authoritative.
//    Borra TODO el dato personal del usuario AUTENTICADO (uid del contexto, nunca
//    de un argumento del cliente):
//      - sus neveras EN SOLITARIO (con su subcolección de productos),
//      - su presencia en neveras AJENAS (sale de `colaboradores` y se borra su
//        perfil denormalizado de `miembros`),
//      - su documento usuarios/{uid} y su subcolección de tokens FCM,
//      - su cuenta de Firebase Auth.
//
//    GUARD (antes de tocar nada): una nevera PROPIA con ≥1 colaborador (compartida)
//    BLOQUEA el borrado entero. No existe "transferir propiedad" en el proyecto, así
//    que borrarla dejaría a los colaboradores sin acceso de golpe: se exige que el
//    usuario las resuelva primero.
//
//    CONTRATO de retorno (NO se usa HttpsError para el guard, a propósito): el SDK
//    cliente GitLive 2.1.0 NO propaga a iOS ni el `code` ni los `details` de un
//    HttpsError (quedan como UNKNOWN / null por el bug firebase-ios-sdk #11862). Por
//    eso el conflicto viaja en el PAYLOAD NORMAL del resultado, legible idéntico en
//    Android e iOS vía `result.data<T>()`:
//      - bloqueado:  { ok: false, neveras: [{ id, nombre }, ...] }   (no borra nada)
//      - completado: { ok: true,  neveras: [] }
//    El único throw que queda es 'unauthenticated' (no debería ocurrir: el cliente
//    siempre llama autenticado; si ocurre, el cliente lo trata como error genérico).
//
//    Sobre `miembros[].fotoUrl`: NO es un blob propio. Es la URL del avatar del
//    proveedor OAuth (Google/Apple); el proyecto no usa Firebase Storage. No hay
//    nada que borrar ahí — el avatar lo gobierna el proveedor.
//
//    Idempotente / reentrante ante reintento tras fallo parcial: si hay alguna
//    nevera compartida el guard corta ANTES de borrar nada (no hay estado a medias);
//    si el guard pasa, `recursiveDelete` sobre algo ya borrado es no-op, `arrayRemove`
//    y el filtrado de `miembros` son idempotentes, y `deleteUser` sobre un uid ya
//    borrado se trata como hecho.
// =============================================================================
export const eliminarCuenta = onCall(async (request) => {
  // 1. Auth obligatoria. uid = contexto de auth (NUNCA un parámetro del cliente).
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión para eliminar la cuenta.");
  }

  const db = getFirestore();

  // 2. Neveras propias y neveras donde el uid es colaborador.
  const [propiasSnap, colaborandoSnap] = await Promise.all([
    db.collection(COL_NEVERAS).where("idPropietario", "==", uid).get(),
    db.collection(COL_NEVERAS).where("colaboradores", "array-contains", uid).get(),
  ]);

  // 3. GUARD: de las propias, las compartidas (≥1 colaborador; el array excluye al
  //    dueño, así que length>0 == compartida) bloquean. No se borra NADA si hay alguna:
  //    se devuelve la lista en el payload (ver CONTRATO arriba), no como HttpsError.
  const compartidas = propiasSnap.docs.filter((d) => {
    const colab = d.get("colaboradores");
    return Array.isArray(colab) && colab.length > 0;
  });
  if (compartidas.length > 0) {
    return {
      ok: false,
      neveras: compartidas.map((d) => ({ id: d.id, nombre: d.get("nombre") ?? "" })),
    };
  }

  // 4. Neveras propias en solitario → recursiveDelete (arrastra la subcolección
  //    `productos`). Dispara onNeveraBorrada, que hace fan-out a [] (sin
  //    colaboradores): no-op, sin notificaciones espurias.
  await Promise.all(propiasSnap.docs.map((d) => db.recursiveDelete(d.ref)));

  // 5. Neveras ajenas donde soy colaborador → salir. Transacción POR NEVERA para
  //    reescribir `colaboradores` y `miembros` de forma atómica sobre el snapshot
  //    fresco, sin pisar cambios concurrentes de otros miembros (read-modify-write
  //    seguro). `colaboradores` es array de strings → arrayRemove(uid); `miembros`
  //    es array de objetos → se filtra mi objeto por uid (RGPD: se borra también el
  //    perfil denormalizado). Esto dispara onMembresiaCambiada (deliberado previamente) → aviso de "auto-salida"
  //    a los que quedan; es el comportamiento esperado al abandonar una nevera.
  await Promise.all(
    colaborandoSnap.docs.map((d) =>
      db.runTransaction(async (tx) => {
        const snap = await tx.get(d.ref);
        if (!snap.exists) return;
        const miembros: FirebaseFirestore.DocumentData[] =
          Array.isArray(snap.get("miembros")) ? snap.get("miembros") : [];
        tx.update(d.ref, {
          colaboradores: FieldValue.arrayRemove(uid),
          miembros: miembros.filter((m) => m?.uid !== uid),
        });
      }),
    ),
  );

  // 6. usuarios/{uid} + su subcolección `tokens` (FCM) en una sola pasada.
  await db.recursiveDelete(db.collection(COL_USUARIOS).doc(uid));

  // 7. Cuenta de Auth. Si ya no existe (reintento), se considera hecho.
  try {
    await getAuth().deleteUser(uid);
  } catch (e: unknown) {
    const code = (e as { code?: string })?.code;
    if (code !== "auth/user-not-found") throw e;
    logger.info(`eliminarCuenta: el uid ${uid} ya no existía en Auth (reintento).`);
  }

  logger.info(`eliminarCuenta: cuenta ${uid} eliminada (RGPD).`);
  // 8. Completado (ver CONTRATO arriba).
  return { ok: true, neveras: [] };
});
