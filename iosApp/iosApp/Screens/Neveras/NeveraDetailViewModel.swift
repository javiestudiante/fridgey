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
    }

    @Published var state = State()

    private let neveraId: String
    private let currentUserId: String

    private let neveraRepository = KoinIosKt.getNeveraRepository()
    private let productoRepository = KoinIosKt.getProductoRepository()
    private let binder: ProductoListBinder = KoinIosKt.getProductoListBinder()

    init(neveraId: String, currentUserId: String) {
        self.neveraId = neveraId
        self.currentUserId = currentUserId
    }

    func start() {
        loadNeveraName()
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
            }
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
