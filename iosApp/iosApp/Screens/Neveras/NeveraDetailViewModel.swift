import Foundation
import Shared

/// SwiftUI ObservableObject mirroring the Android `NeveraDetailViewModel`.
///
/// The product list is reactive: `ProductoRepository.getProductosByNevera`
/// is a Kotlin `Flow` and we subscribe via [ProductoListBinder] (the
/// commonMain/iosMain binder is the same pattern used for `AuthState`).
/// Inserts and deletes go straight to `ProductoRepository`.
@MainActor
final class NeveraDetailViewModel: ObservableObject {

    struct State {
        var isLoading: Bool = true
        var error: String? = nil
        var productos: [Producto] = []
        /// Texto de búsqueda FTS5 en curso (vacío = lista completa de la nevera).
        var query: String = ""
        var neveraNombre: String = ""
        var miembros: [Usuario] = []
        // --- ejes nube/colaboración (paridad con Android) ---
        var esPropietario: Bool = false
        /// Eje de persistencia: LOCAL (solo este dispositivo) o SYNCED (nube).
        var modo: ModoNeveraUI = .local
        /// Eje de colaboración DERIVADO: hay al menos un colaborador (count > 0).
        var tieneColaboradores: Bool = false
        /// "Guardar en mi cuenta" (LOCAL→SYNCED) en curso.
        var guardando: Bool = false
        /// "Dejar de compartir" (vaciar colaboradores, sigue SYNCED) en curso.
        var dejandoDeCompartir: Bool = false
        /// "Quitar de mi cuenta" (SYNCED→LOCAL) en curso.
        var quitando: Bool = false
        var errorCompartir: String? = nil
        // --- borrar / salir / expulsar (gestión de miembros) ---
        /// uid del propietario — distingue su fila en la hoja de miembros.
        var idPropietario: String = ""
        /// "Borrar nevera" (dueño) o "Salir de la nevera" (colaborador) en curso.
        var borrandoOSaliendo: Bool = false
        /// Error del diálogo de confirmación de borrar/salir.
        var errorBorrado: String? = nil
        /// La nevera ya no existe en este dispositivo (borrada o salida): volver atrás.
        var neveraCerrada: Bool = false
        /// uid del colaborador cuya expulsión está en curso (spinner por fila).
        var expulsandoUid: String? = nil
        /// Error de la hoja de miembros (expulsión fallida).
        var errorMiembros: String? = nil
    }

    @Published var state = State()

    private let neveraId: String
    private let currentUserId: String

    private let neveraRepository = KoinIosKt.getNeveraRepository()
    private let productoRepository = KoinIosKt.getProductoRepository()
    private let binder: ProductoListBinder = KoinIosKt.getProductoListBinder()
    private let subirANubeUseCase = KoinIosKt.getSubirANubeUseCase()
    private let dejarDeCompartirUseCase = KoinIosKt.getDejarDeCompartirUseCase()
    private let quitarDeNubeUseCase = KoinIosKt.getQuitarDeNubeUseCase()
    private let borrarNeveraUseCase = KoinIosKt.getBorrarNeveraUseCase()
    private let salirDeNeveraUseCase = KoinIosKt.getSalirDeNeveraUseCase()
    private let expulsarColaboradorUseCase = KoinIosKt.getExpulsarColaboradorUseCase()

    /// Tope de las transiciones que NO se autolimitan dentro del use case
    /// (dejar de compartir, quitar de la cuenta). Espejo del
    /// `withTimeoutOrNull(15s)` del VM Android. SubirANube se autolimita.
    private static let transitionTimeoutSeconds: Double = 15

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        self.currentUserId = currentUserId
    }

    func start() {
        state.query = ""  // reset al (re)entrar — el binder arranca también en blanco
        loadNeveraName()
        loadMiembros()
        binder.start(
            neveraId: neveraId,
            onValue: { [weak self] productos in
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.state.isLoading = false
                    self.state.error = nil
                    self.state.productos = (productos as? [Producto]) ?? []
                }
            },
            onError: { [weak self] error in
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.state.isLoading = false
                    self.state.error = error.message
                }
            }
        )
    }

    func stop() {
        binder.dispose()
    }

    /// El campo de búsqueda cambió: actualiza el texto y se lo pasa al binder,
    /// que re-filtra la lista en vivo vía `searchProductos` (FTS5). Espejo del
    /// `onQueryChange` de Android.
    func onQueryChange(_ q: String) {
        state.query = q
        binder.setQuery(query: q)
    }

    /// Owner + collaborators for the detail header avatars + "N MIEMBROS".
    /// Reloads the derived `tieneColaboradores` flag (collaborator count > 0)
    /// alongside, mirroring Android's refreshNevera.
    private func loadMiembros() {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.neveraRepository.getMiembros(neveraId: self.neveraId)
                self.state.miembros = (result as? [Usuario]) ?? []
            } catch {
                // best-effort; header just shows fewer avatars
            }
            do {
                let count = try await self.neveraRepository.getColaboradorCount(neveraId: self.neveraId)
                self.state.tieneColaboradores = count.intValue > 0
            } catch {
                // best-effort; the dialog just omits "Dejar de compartir"
            }
        }
    }

    private func loadNeveraName() {
        neveraRepository.getNeveraById(
            neveraId: neveraId,
            currentUserId: currentUserId
        ) { [weak self] nevera, error in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if let error = error {
                    self.state.error = error.localizedDescription
                    return
                }
                self.state.neveraNombre = nevera?.nombre ?? ""
                self.state.esPropietario = nevera?.esPropietario ?? false
                self.state.idPropietario = nevera?.idPropietario ?? ""
                self.state.modo = nevera.map { ModoNeveraUI($0.modo) } ?? .local
            }
        }
    }

    // MARK: - Nube + colaboración (acciones del propietario)

    /// "Guardar en mi cuenta" (LOCAL→SYNCED). Síncrona contra servidor — el
    /// timeout (15s) vive DENTRO de SubirANubeUseCase, que espera el ack del
    /// doc antes de voltear el modo y devuelve error si no llega. Aquí solo el
    /// spinner.
    func guardarEnMiCuenta() {
        guard !state.guardando else { return }  // guard anti doble-tap
        state.guardando = true
        state.errorCompartir = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.subirANubeUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId
                )
                self.aplicarResultadoCompartir(result)
            } catch {
                self.state.errorCompartir = error.localizedDescription
            }
            self.state.guardando = false
        }
    }

    /// "Dejar de compartir": vacía los colaboradores pero la nevera SIGUE en la
    /// nube (sigue SYNCED) y en los dispositivos del dueño. No pausa el sync.
    /// Síncrona contra servidor → spinner + timeout.
    func dejarDeCompartir() {
        guard !state.dejandoDeCompartir else { return }  // guard anti doble-tap
        state.dejandoDeCompartir = true
        state.errorCompartir = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let result = await Self.raceWithTimeout(seconds: Self.transitionTimeoutSeconds) {
                try? await self.dejarDeCompartirUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId
                )
            }
            if let result = result {
                self.aplicarResultadoCompartir(result)
            } else {
                self.state.errorCompartir = "Sin conexión con el servidor. "
                    + "Para dejar de compartir necesitas conexión; inténtalo de nuevo."
            }
            self.state.dejandoDeCompartir = false
        }
    }

    /// "Quitar de mi cuenta" (SYNCED→LOCAL): baja la nevera de la nube y
    /// conserva los datos locales. Síncrona contra servidor (revocación de
    /// acceso) → spinner + timeout; el use case reanuda el sync igualmente si
    /// el timeout cancela (finally NonCancellable).
    func quitarDeMiCuenta() {
        guard !state.quitando else { return }  // guard anti doble-tap
        state.quitando = true
        state.errorCompartir = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let result = await Self.raceWithTimeout(seconds: Self.transitionTimeoutSeconds) {
                try? await self.quitarDeNubeUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId
                )
            }
            if let result = result {
                self.aplicarResultadoCompartir(result)
            } else {
                self.state.errorCompartir = "Sin conexión con el servidor. "
                    + "Para quitar la nevera de tu cuenta necesitas conexión; inténtalo de nuevo."
            }
            self.state.quitando = false
        }
    }

    func limpiarErrorCompartir() {
        state.errorCompartir = nil
    }

    // MARK: - Borrar / salir / expulsar (gestión de miembros)

    /// Acción del diálogo de confirmación: el DUEÑO borra la nevera (casos
    /// 1-3, mismo use case — el aviso dinámico es de UI) o el COLABORADOR
    /// sale de ella (caso 4). Síncrona contra servidor en los casos con nube
    /// → spinner + timeout (los use cases reanudan el sync con NonCancellable
    /// si el timeout cancela). Con éxito se marca `neveraCerrada` y la vista
    /// vuelve a "Mis neveras".
    func borrarOSalir() {
        guard !state.borrandoOSaliendo else { return }  // guard anti doble-tap
        state.borrandoOSaliendo = true
        state.errorBorrado = nil
        let esPropietario = state.esPropietario
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let result = await Self.raceWithTimeout(seconds: Self.transitionTimeoutSeconds) {
                if esPropietario {
                    return try? await self.borrarNeveraUseCase.invoke(
                        neveraId: self.neveraId,
                        requesterId: self.currentUserId
                    )
                } else {
                    return try? await self.salirDeNeveraUseCase.invoke(
                        neveraId: self.neveraId,
                        requesterId: self.currentUserId
                    )
                }
            }
            if result is OperationResultSuccess<KotlinUnit> {
                self.state.neveraCerrada = true
            } else if let failure = result as? OperationResultError {
                self.state.errorBorrado = failure.message
            } else {
                let accion = esPropietario ? "borrar la nevera" : "salir de la nevera"
                self.state.errorBorrado = "Sin conexión con el servidor. "
                    + "Para \(accion) necesitas conexión; inténtalo de nuevo."
            }
            self.state.borrandoOSaliendo = false
        }
    }

    func limpiarErrorBorrado() {
        state.errorBorrado = nil
    }

    /// El DUEÑO expulsa a un colaborador concreto desde la hoja de miembros.
    /// Spinner por fila vía `expulsandoUid`; la hoja sigue abierta y se
    /// refresca con el conjunto resultante.
    func expulsarColaborador(_ colaboradorId: String) {
        guard state.expulsandoUid == nil else { return }  // una expulsión a la vez
        state.expulsandoUid = colaboradorId
        state.errorMiembros = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let result = await Self.raceWithTimeout(seconds: Self.transitionTimeoutSeconds) {
                try? await self.expulsarColaboradorUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId,
                    colaboradorId: colaboradorId
                )
            }
            if result is OperationResultSuccess<KotlinUnit> {
                // Re-lee miembros + tieneColaboradores (lista y avatares).
                self.loadNeveraName()
                self.loadMiembros()
            } else if let failure = result as? OperationResultError {
                self.state.errorMiembros = failure.message
            } else {
                self.state.errorMiembros = "Sin conexión con el servidor. "
                    + "Para expulsar necesitas conexión; inténtalo de nuevo."
            }
            self.state.expulsandoUid = nil
        }
    }

    func limpiarErrorMiembros() {
        state.errorMiembros = nil
    }

    private func aplicarResultadoCompartir(_ result: OperationResult<KotlinUnit>?) {
        if result is OperationResultSuccess<KotlinUnit> {
            // Re-lee nombre/modo/ownership (y miembros) tras la transición.
            loadNeveraName()
            loadMiembros()
        } else if let failure = result as? OperationResultError {
            state.errorCompartir = failure.message
        } else {
            state.errorCompartir = "Respuesta inesperada al compartir"
        }
    }

    /// Carrera op-vs-timeout, espejo del `withTimeoutOrNull` de Android:
    /// devuelve nil si el timeout gana (la operación queda cancelada).
    private static func raceWithTimeout<T: Sendable>(
        seconds: Double,
        _ op: @escaping @Sendable () async -> T?
    ) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask { await op() }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                return nil
            }
            let first = await group.next().flatMap { $0 }
            group.cancelAll()
            return first
        }
    }

    // `addProducto` was removed in favour of `AddProductoView` /
    // `AddProductoViewModel` (in `Screens/Productos/`), which owns the
    // creation flow end-to-end (validation, save, success signalling). The
    // VM here keeps the read+delete responsibilities for the products
    // already in this nevera; the `productoRepository` reference is still
    // needed by `deleteProducto(_:)` below.

    func deleteProducto(_ producto: Producto) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                try await self.productoRepository.deleteProducto(productoId: producto.id)
                // Flow reemits — no manual reload.
            } catch {
                self.state.error = error.localizedDescription
            }
        }
    }

    func clearError() {
        state.error = nil
    }
}
