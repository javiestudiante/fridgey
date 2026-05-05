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

    func addProducto(name: String, categoria: Categoria, fechaCaducidad: Date) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            state.error = "El nombre es obligatorio"
            return
        }
        let today = Calendar.current.startOfDay(for: Date())
        let pickedStart = Calendar.current.startOfDay(for: fechaCaducidad)
        guard pickedStart >= today else {
            state.error = "La fecha debe ser hoy o futura"
            return
        }

        let producto = Producto(
            id: UUID().uuidString,
            idNevera: neveraId,
            codigoBarras: nil,
            nombre: trimmed,
            categoria: categoria,
            fechaCaducidad: Kotlinx_datetimeLocalDate.from(date: fechaCaducidad),
            fechaRegistro: Kotlinx_datetimeLocalDate.from(date: Date()),
            imagenUrl: nil
        )

        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                try await self.productoRepository.insertProducto(producto: producto)
                // The Flow will reemit with the new product; no manual reload.
            } catch {
                self.state.error = error.localizedDescription
            }
        }
    }

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
