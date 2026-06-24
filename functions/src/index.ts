import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import {
  onDocumentUpdated,
  onDocumentDeleted,
  onDocumentCreated,
} from "firebase-functions/v2/firestore";
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
