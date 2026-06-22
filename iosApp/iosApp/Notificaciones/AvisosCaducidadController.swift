import Foundation
import UserNotifications
import Shared

/// Motor iOS de avisos locales de caducidad (Fase 1b).
///
/// iOS NO implementa las interfaces commonMain `NotificacionCaducidadPoster` /
/// `…Scheduler` (decisión D: son Android-only). El QUÉ programar vive en el
/// planner puro compartido (`PlanificarAvisosCaducidadUseCase`); el CÓMO
/// (UNUserNotificationCenter, permiso, triggers) vive aquí.
///
/// Estrategia: cancel-all + reschedule en cada reconciliación.
///  - futuros    → `UNCalendarNotificationTrigger` en `fechaDisparo` (identifier
///                 estable por producto; SIN columna de dedup).
///  - inmediatos → entrega inmediata + `marcarAvisado` (columna de 2B) para no
///                 re-dispararlos en cada reschedule.
final class AvisosCaducidadController {

    static let shared = AvisosCaducidadController()
    private init() {}

    private let center = UNUserNotificationCenter.current()
    private let productoRepository = KoinIosKt.getProductoRepository()
    private let preferenciasRepository = KoinIosKt.getPreferenciasRepository()
    private let planner = KoinIosKt.getPlanificadorAvisosCaducidad()

    private static let prefijoId = "caducidad-"
    private static let horaAviso = 9        // 09:00 local
    private static let limite: Int32 = 64   // espejo de PlanificarAvisosCaducidadUseCase.LIMITE_NOTIFICACIONES_IOS

    // MARK: - API pública

    /// Pide el permiso de notificaciones. Lo invoca la UI (HITO 3: 1ª aparición
    /// de la lista y al activar el toggle). Devuelve si quedó concedido.
    @discardableResult
    func solicitarPermiso() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Petición de permiso CONTEXTUAL de primera vez (1ª aparición de la lista).
    /// Gated por la preferencia `permiso_notif_solicitado` (compartida con
    /// Android): pide UNA sola vez. Tras el intento marca solicitado y reconcilia
    /// (si se concedió, ya programa).
    func pedirPermisoContextualSiProcede() async {
        let activado = (try? await preferenciasRepository.avisosCaducidadActivados())?.boolValue ?? true
        guard activado else { return }
        let yaSolicitado = (try? await preferenciasRepository.permisoNotifSolicitado())?.boolValue ?? false
        guard !yaSolicitado else { return }

        if await center.notificationSettings().authorizationStatus == .notDetermined {
            _ = await solicitarPermiso()
        }
        try? await preferenciasRepository.setPermisoNotifSolicitado(solicitado: true)
        await reconciliar()
    }

    /// Reconcilia las notificaciones locales con el estado actual
    /// (cancel-all + reschedule). Se llama en launch/foreground (iOSApp) y, en el
    /// HITO 3, tras cambios de toggle/permiso.
    func reconciliar() async {
        // 1. Gate por preferencia (default ON ante ausencia/fallo de lectura).
        let activado = (try? await preferenciasRepository.avisosCaducidadActivados())?.boolValue ?? true
        guard activado else {
            NSLog("%@", "[AVISOS] gate: preferencia OFF -> cancel pending, no programa")
            center.removeAllPendingNotificationRequests()
            return
        }

        // 2. Sin permiso no programamos ni entregamos (se reintenta al concederse).
        let settings = await center.notificationSettings()
        let autorizado = settings.authorizationStatus == .authorized
            || settings.authorizationStatus == .provisional
        guard autorizado else {
            NSLog("%@", "[AVISOS] gate: sin permiso (status=\(settings.authorizationStatus.rawValue)) -> cancel pending")
            center.removeAllPendingNotificationRequests()
            return
        }

        // 3. Planificar a partir de los datos locales.
        guard let productos = try? await productoRepository.getProductosParaAviso() else { return }
        let hoy = AvisosFechaBridgeKt.hoyLocalDate()
        let plan = planner.invoke(productos: productos, hoy: hoy, limite: Self.limite)
        NSLog("%@", "[AVISOS] reconcile: hoy=\(hoy) futuros=\(plan.futuros.count) inmediatos=\(plan.inmediatos.count)")

        // 4. Cancel-all + reschedule de los futuros (programados por adelantado).
        center.removeAllPendingNotificationRequests()
        for aviso in plan.futuros {
            let dias = Int(AvisosFechaBridgeKt.diasEntreFechas(desde: aviso.fechaDisparo, hasta: aviso.fechaCaducidad))
            let req = UNNotificationRequest(
                identifier: Self.prefijoId + aviso.productId,
                content: contenido(aviso, dias: dias),
                trigger: triggerCalendario(en: aviso.fechaDisparo)
            )
            try? await center.add(req)
            NSLog("%@", "[AVISOS] futuro programado id=\(Self.prefijoId + aviso.productId) disparo=\(aviso.fechaDisparo) 09:00 caducidad=\(aviso.fechaCaducidad)")
        }

        // 5. Entregar inmediatos y marcar el dedup SOLO si se entregaron.
        for aviso in plan.inmediatos {
            let dias = Int(AvisosFechaBridgeKt.diasEntreFechas(desde: hoy, hasta: aviso.fechaCaducidad))
            let req = UNNotificationRequest(
                identifier: Self.prefijoId + aviso.productId,
                content: contenido(aviso, dias: dias),
                trigger: nil   // nil ⇒ entrega inmediata
            )
            do {
                try await center.add(req)
                try await productoRepository.marcarAvisado(
                    productId: aviso.productId,
                    fechaCaducidad: aviso.fechaCaducidad
                )
                NSLog("%@", "[AVISOS] inmediato ENTREGADO + marcarAvisado id=\(Self.prefijoId + aviso.productId) caducidad=\(aviso.fechaCaducidad)")
            } catch {
                NSLog("%@", "[AVISOS] inmediato FALLO (no marcado) id=\(Self.prefijoId + aviso.productId) error=\(error)")
            }
        }
    }

    // MARK: - Privados

    private func triggerCalendario(en fecha: Kotlinx_datetimeLocalDate) -> UNCalendarNotificationTrigger {
        var comps = DateComponents()
        comps.year = Int(fecha.year)
        comps.month = Int(fecha.monthNumber)
        comps.day = Int(fecha.dayOfMonth)
        comps.hour = Self.horaAviso
        return UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
    }

    private func contenido(_ aviso: AvisoCaducidad, dias: Int) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = aviso.nombreProducto
        content.body = cuerpoRelativo(dias: dias)
        content.sound = .default
        content.userInfo = ["neveraId": aviso.neveraId]   // lo consume el tap (HITO 3)
        return content
    }

    /// Mismo copy que Android (`textoRelativo`), sin usar `Producto.diasRestantes`.
    private func cuerpoRelativo(dias: Int) -> String {
        switch dias {
        case ..<0: return dias == -1 ? "Caducó hace 1 día" : "Caducó hace \(-dias) días"
        case 0: return "Caduca hoy"
        case 1: return "Caduca mañana"
        default: return "Caduca en \(dias) días"
        }
    }
}
