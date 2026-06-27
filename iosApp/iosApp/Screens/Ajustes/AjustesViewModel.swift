import Foundation
import UserNotifications
import Shared

/// Estado y lógica de la pantalla de Ajustes (Fase 1b iOS). El toggle refleja la
/// INTENCIÓN del usuario (preferencia); el permiso del SO es una compuerta aparte.
@MainActor
final class AjustesViewModel: ObservableObject {

    @Published var avisosCaducidad: Bool = true   // default ON
    @Published var permisoDenegado: Bool = false  // authorizationStatus == .denied
    // Modo con el que el botón "+" de una nevera abre el alta de producto.
    @Published var modoAnadir: ModoAnadirProducto = .manual

    private let preferencias = KoinIosKt.getPreferenciasRepository()
    private let controller = AvisosCaducidadController.shared
    private let center = UNUserNotificationCenter.current()

    func cargar() async {
        avisosCaducidad = (try? await preferencias.avisosCaducidadActivados())?.boolValue ?? true
        modoAnadir = (try? await preferencias.modoAnadirProducto()) ?? .manual
        await refrescarPermiso()
    }

    /// Persiste el modo de añadido por defecto del botón "+". Preferencia LOCAL,
    /// sin efectos secundarios (no programa nada): solo guarda la elección.
    func onModoAnadirSeleccionado(_ modo: ModoAnadirProducto) async {
        modoAnadir = modo
        try? await preferencias.setModoAnadirProducto(modo: modo)
    }

    /// Refresca el flag de denegación (para el banner). Se llama al cargar y al
    /// volver de los ajustes del sistema (scenePhase .active).
    func refrescarPermiso() async {
        permisoDenegado = await center.notificationSettings().authorizationStatus == .denied
    }

    /// Persiste la intención y (des)programa. Al activar sin permiso decidido,
    /// pide el permiso (en iOS la petición es directa, no necesita launcher como
    /// Android). El Worker/controlador ya gestiona el caso "sin permiso".
    func onToggle(_ activado: Bool) async {
        avisosCaducidad = activado
        try? await preferencias.setAvisosCaducidadActivados(activado: activado)
        if activado {
            if await center.notificationSettings().authorizationStatus == .notDetermined {
                _ = await controller.solicitarPermiso()
            }
            await refrescarPermiso()
        }
        await controller.reconciliar()
    }
}
