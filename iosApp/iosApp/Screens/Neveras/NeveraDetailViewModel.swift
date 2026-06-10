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
        var neveraNombre: String = ""
        var miembros: [Usuario] = []
        // --- compartir (Sprint B, paridad con Android) ---
        var esPropietario: Bool = false
        var modo: ModoNevera = .local
        /// Transición LOCAL→SHARED en curso.
        var compartiendo: Bool = false
        /// Transición SHARED→LOCAL en curso (espera ack del servidor → spinner).
        var dejandoDeCompartir: Bool = false
        var errorCompartir: String? = nil
    }

    @Published var state = State()

    private let neveraId: String
    private let currentUserId: String

    private let neveraRepository = KoinIosKt.getNeveraRepository()
    private let productoRepository = KoinIosKt.getProductoRepository()
    private let binder: ProductoListBinder = KoinIosKt.getProductoListBinder()
    private let shareNeveraUseCase = KoinIosKt.getShareNeveraUseCase()
    private let unshareNeveraUseCase = KoinIosKt.getUnshareNeveraUseCase()

    /// El unshare espera el ack del servidor; sin conexión cortamos aquí
    /// (espejo del `withTimeoutOrNull(15s)` del VM Android).
    private static let unshareTimeoutSeconds: Double = 15

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        self.currentUserId = currentUserId
    }

    func start() {
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

    /// Owner + collaborators for the detail header avatars + "N MIEMBROS".
    private func loadMiembros() {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.neveraRepository.getMiembros(neveraId: self.neveraId)
                self.state.miembros = (result as? [Usuario]) ?? []
            } catch {
                // best-effort; header just shows fewer avatars
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
                self.state.modo = nevera?.modo ?? .local
            }
        }
    }

    // MARK: - Compartir (LOCAL ↔ SHARED)

    /// Transición LOCAL→SHARED. El timeout (15s) vive DENTRO del use case
    /// común, que espera el ack del doc en el servidor antes de voltear el
    /// modo (enmienda del Hito 2 de Android).
    func hacerColaborativa() {
        guard !state.compartiendo else { return }  // guard anti doble-tap
        state.compartiendo = true
        state.errorCompartir = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.shareNeveraUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId
                )
                self.aplicarResultadoCompartir(result)
            } catch {
                self.state.errorCompartir = error.localizedDescription
            }
            self.state.compartiendo = false
        }
    }

    /// Transición SHARED→LOCAL. Estricta contra servidor: espera el ack (es
    /// una revocación de acceso), con spinner y timeout de 15s. Si el
    /// timeout cancela la corrutina, el use case común reanuda el sync
    /// igualmente (finally NonCancellable) y el listener reconcilia si el
    /// borrado encolado llegara a completarse después.
    func dejarDeCompartir() {
        guard !state.dejandoDeCompartir else { return }  // guard anti doble-tap
        state.dejandoDeCompartir = true
        state.errorCompartir = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            let result = await Self.raceWithTimeout(seconds: Self.unshareTimeoutSeconds) {
                try? await self.unshareNeveraUseCase.invoke(
                    neveraId: self.neveraId,
                    requesterId: self.currentUserId
                )
            }
            if let result = result {
                self.aplicarResultadoCompartir(result)
            } else {
                self.state.errorCompartir = "Sin conexión con el servidor. "
                    + "Dejar de compartir requiere conexión; inténtalo de nuevo."
            }
            self.state.dejandoDeCompartir = false
        }
    }

    func limpiarErrorCompartir() {
        state.errorCompartir = nil
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
