# Fridgey — Cloud Functions (notificaciones push colaborativas)

Fan-out de notificaciones push para eventos colaborativos de Fridgey. Reaccionan
a cambios en Firestore y envían un push a los **miembros afectados excepto al que
provoca el evento (el actor)**.

- **Stack:** Node 22, TypeScript, `firebase-functions` v7 (triggers v2),
  `firebase-admin` v13 (`getMessaging().sendEachForMulticast`).
- **Región:** `europe-west1` (`setGlobalOptions`), para acercarse al Firestore
  multirregión `eur3` (europe-west1 + europe-west2).
- **Proyecto:** `fridgeytfg`.

## Por qué Firebase CLI y NO Terraform (excepción justificada al IaC)

El resto de la infraestructura de Fridgey se gestiona con Terraform
(`infrastructure/terraform`): APIs, base Firestore, índices y reglas. Las Cloud
Functions se despliegan con **Firebase CLI** (`firebase deploy --only functions`),
una excepción deliberada al IaC, por el mismo criterio que el README de
infraestructura ya documenta para otros pasos manuales (clave `.p8` de APNs,
credenciales OAuth): el despliegue de Functions empaqueta y sube código (build +
artefacto + Eventarc/Cloud Run), un flujo que el provider de Terraform cubre de
forma frágil y que la CLI de Firebase resuelve de forma idempotente y estándar.
Las funciones leen `usuarios/{uid}/tokens` con el **Admin SDK**, que se salta las
reglas de seguridad, así que la regla de tokens permanece *owner-only* sin
ensancharse.

## Arquitectura del fan-out (`src/fanout.ts`)

`fanOut(recipientUids, payload)` — el llamador YA excluye al actor. **Por cada**
destinatario (no un multicast global):

1. Lee `usuarios/{uid}/tokens` (puede haber varios docs: multi-dispositivo).
2. Si no tiene tokens, lo salta.
3. Envía con `sendEachForMulticast`, incluyendo `data.destinatarioUid = uid` para
   que el cliente (HITO 4) pueda filtrar, en un dispositivo compartido, contra la
   sesión activa. `android.priority = "high"`, `apns.payload.aps.sound = "default"`.
4. Recorre el `BatchResponse` y **poda** los tokens muertos: borra el doc de token
   cuyo `error.code` sea `messaging/registration-token-not-registered` o
   `messaging/invalid-argument` (verificado contra los typings de
   `firebase-admin@13.10.0`: `FirebaseMessagingError` antepone el prefijo
   `messaging/` a los códigos `registration-token-not-registered` /
   `invalid-argument`). Cualquier otro código es transitorio → solo log.

Cada destinatario va en su propio `try/catch`: un fallo a uno no aborta el resto.
Early-return si no hay destinatarios (cubre el lote de `uploadNevera`
LOCAL→SYNCED, donde `colaboradores` está vacío). Los nombres para los mensajes
salen del array desnormalizado `nevera.miembros` (uid→nombre), sin reads extra.

**Sin reintentos** (`retry: false` en cada trigger): un fallo transitorio no debe
re-disparar el fan-out y duplicar notificaciones (no son idempotentes).

## Las funciones (`src/index.ts`)

### A) `onMembresiaCambiada` — `onDocumentUpdated("neveras/{neveraId}")`
Una sola función que diffea el array `colaboradores` (before vs after) como
conjuntos. Si no cambió → return (cubre renombrados y bumps de `updatedAt` por
sync).

- **Alta** (`nuevos = after − before`): actor = el que se unió. Destinatarios =
  `(before.colaboradores ∪ {idPropietario}) − nuevos`. *"{nombre} se ha unido a la
  nevera."* (`tipo: colaborador_alta`).
- **Baja** (`retirados = before − after`, **fuente de verdad** de a quién afecta):
  - **Contrato de desambiguación:** es **expulsión / dejar-de-compartir** *si y solo
    si* el marcador está **fresco**, es decir
    `after.ultimoEventoColabAt.isEqual(after.updatedAt)` (ambos `serverTimestamp`
    del mismo write del dueño → idénticos; comparación con `.isEqual()`, nunca
    `===`). Si no hay marcador fresco → **auto-salida**.
    - La auto-salida no puede tocar `ultimoEventoColabAt` (`hasOnly` de
      `esAutoSalidaValida`) y el renombrado no lo estampa, así que el timestamp
      fresco no tiene la carrera que tendría comparar `objetivos == retirados`.
      `objetivos`/`actorUid` del marcador son **metadato informativo**, no
      condicionan nada.
  - **Auto-salida** (`tipo: auto_salida`): actor = el uid retirado; notifica a los
    que quedan (`after.colaboradores ∪ {idPropietario}`). *"{nombre} ha salido de la
    nevera."*
  - **Expulsión** (`tipo: expulsion`): actor = dueño (excluido de "los que quedan");
    a los expulsados *"Ya no formas parte de la nevera."* y a los que quedan
    *"{nombre} ya no forma parte de la nevera."* (dos envíos, mensajes distintos).

### B) `onNeveraBorrada` — `onDocumentDeleted("neveras/{neveraId}")`
Actor = `idPropietario` (solo el dueño puede borrar). Destinatarios =
`colaboradores` del **snapshot eliminado** (excluye al dueño). *"El propietario ha
eliminado la nevera {nombre}."* (`tipo: nevera_borrada`).
`BorrarNeveraUseCase` borra el doc directamente sin vaciar `colaboradores` antes,
así que el snapshot conserva los destinatarios.

### C) `onProductoAnadido` — `onDocumentCreated("neveras/{neveraId}/productos/{productoId}")`
Lee el doc padre `neveras/{neveraId}` (no viene en el evento) para
`colaboradores`/`idPropietario`/`miembros`. Actor = `producto.creadoPor`;
destinatarios = `(colaboradores ∪ {idPropietario}) − creadoPor`. Si `creadoPor`
viene vacío (productos antiguos/offline), notifica a todos los miembros.
*"{nombreActor} ha añadido {nombreProducto}."* (`tipo: producto_alta`).

## Payload (para el HITO 4)
`notification: { title, body }` (español) + `data: { tipo, neveraId,
destinatarioUid }`, con `tipo ∈ { colaborador_alta, auto_salida, expulsion,
nevera_borrada, producto_alta }`. El cliente enruta por `tipo` y abre la nevera
por `neveraId`.

## Build y despliegue

```bash
cd functions
npm install
npm run build          # compila TS a lib/ (predeploy lo repite)

# Desplegar (region europe-west1, proyecto fridgeytfg vía .firebaserc):
firebase deploy --only functions
# (equivalente explícito) firebase deploy --only functions --project fridgeytfg
```

> Si al desplegar Eventarc se queja de la *location* del trigger frente al
> multirregión `eur3`, anota el error exacto y la location que pide antes de
> cambiar nada (no resolver a ciegas).
