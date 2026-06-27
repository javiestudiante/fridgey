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
    // --- Eliminación de cuenta ---
    @Published var eliminandoCuenta: Bool = false
    // No-nil → diálogo de bloqueo con las neveras compartidas a resolver primero.
    @Published var neverasBloqueadas: [NeveraBloqueada]? = nil
    // No-nil → mensaje de error legible del borrado.
    @Published var errorEliminarCuenta: String? = nil

    private let preferencias = KoinIosKt.getPreferenciasRepository()
    private let eliminarCuentaUseCase = KoinIosKt.getEliminarCuentaUseCase()
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

    /// Confirma la eliminación de cuenta (RGPD). Server-authoritative: invoca el
    /// MISMO `EliminarCuentaUseCase` de commonMain. En éxito el caso de uso ya cerró
    /// sesión (en su scope de proceso) y borró el espejo local; `AuthSession` detecta
    /// `Unauthenticated` y `ContentView` enruta a login solo, desmontando esta vista
    /// — por eso no se limpia `eliminandoCuenta` en el éxito.
    func onEliminarCuentaConfirmado() async {
        if eliminandoCuenta { return }
        eliminandoCuenta = true
        errorEliminarCuenta = nil
        do {
            let resultado = try await eliminarCuentaUseCase.invoke()
            if resultado is ResultadoEliminarCuentaExito {
                return // la nav a login se encarga
            } else if let bloqueada = resultado as? ResultadoEliminarCuentaBloqueada {
                eliminandoCuenta = false
                neverasBloqueadas = bloqueada.neveras
            } else if let error = resultado as? ResultadoEliminarCuentaError {
                eliminandoCuenta = false
                errorEliminarCuenta = error.mensaje
            } else {
                eliminandoCuenta = false
            }
        } catch {
            eliminandoCuenta = false
            errorEliminarCuenta = error.localizedDescription
        }
    }

    func dismissBloqueo() { neverasBloqueadas = nil }
    func dismissErrorEliminar() { errorEliminarCuenta = nil }

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
