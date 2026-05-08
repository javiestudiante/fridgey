import Foundation
import Shared

/// Mirror of Android's `ScanMode` enum (in `AddProductoViewModel.kt`).
/// Drives the toggle UI in `AddProductoView`.
enum ScanMode: Hashable {
    case scan
    case manual
}

/// MVVM-S ViewModel for the AddProducto form.
///
/// Behavioural parity with the Kotlin `AddProductoViewModel`:
///  - `isFormPristine` is a **one-way gate**: starts `true`, flips to `false`
///    on the first non-default field change, and never flips back even if the
///    user clears every field. Drives the visibility of the
///    Escanear / A mano toggle in the UI.
///  - `onScannedDateReceived(_:)` always marks the form dirty and switches
///    `scanMode` back to `.manual` so the user can fine-tune the rest of the
///    fields after the scanner returns a date.
///  - Save logic mirrors Android: trim name, validate non-empty, validate
///    date >= today, call `productoRepository.insertProducto(producto:)`.
@MainActor
final class AddProductoViewModel: ObservableObject {

    struct State {
        var name: String = ""
        var categoria: Categoria = .otros
        var fechaCaducidad: Kotlinx_datetimeLocalDate
        var isLoading: Bool = false
        var error: String? = nil
        var success: Bool = false
        var scanMode: ScanMode = .manual
        /// One-way pristine flag — see class doc.
        var isFormPristine: Bool = true
    }

    @Published private(set) var state: State

    /// Captured at construction so `onFechaSelected` can compare against the
    /// exact same value that seeded the state (avoids the midnight-rollover
    /// edge case where two `defaultExpiry()` calls could disagree).
    private let initialExpiry: Kotlinx_datetimeLocalDate

    private let productoRepository = KoinIosKt.getProductoRepository()

    init() {
        let today = Date()
        let inSevenDays = Calendar.current.date(byAdding: .day, value: 7, to: today) ?? today
        let expiry = Kotlinx_datetimeLocalDate.from(date: inSevenDays)
        self.initialExpiry = expiry
        self.state = State(fechaCaducidad: expiry)
    }

    // MARK: - Field setters (each guards `isFormPristine` against its default)

    func onNameChanged(_ name: String) {
        state.name = name
        state.error = nil
        // Stay pristine only if (was pristine) AND (new value still equals default).
        state.isFormPristine = state.isFormPristine && name.isEmpty
    }

    func onCategoriaSelected(_ categoria: Categoria) {
        state.categoria = categoria
        state.isFormPristine = state.isFormPristine && (categoria == .otros)
    }

    func onFechaSelected(_ fecha: Kotlinx_datetimeLocalDate) {
        state.fechaCaducidad = fecha
        state.error = nil
        state.isFormPristine = state.isFormPristine && Self.datesEqual(fecha, initialExpiry)
    }

    // MARK: - Scan-mode toggle plumbing

    /// Updates the toggle selection. Does NOT touch `isFormPristine` —
    /// toggling between Scan/Manual without entering data leaves the form
    /// pristine, which is what keeps the toggle visible.
    func setScanMode(_ mode: ScanMode) {
        state.scanMode = mode
    }

    /// Called when the scanner returns a date. Always marks the form dirty
    /// (regardless of whether the date happens to equal `initialExpiry`) and
    /// switches the active mode back to Manual so the user can adjust the
    /// rest of the fields.
    func onScannedDateReceived(_ date: Kotlinx_datetimeLocalDate) {
        state.fechaCaducidad = date
        state.error = nil
        state.isFormPristine = false
        state.scanMode = .manual
    }

    // MARK: - Save

    func onSavePressed(neveraId: String) {
        let trimmed = state.name.trimmingCharacters(in: .whitespaces)
        let todayDate = Date()
        let todayStart = Calendar.current.startOfDay(for: todayDate)

        guard !trimmed.isEmpty else {
            state.error = "El nombre es obligatorio"
            return
        }
        let pickedStart = state.fechaCaducidad.asSwiftDate
        guard pickedStart >= todayStart else {
            state.error = "La fecha debe ser hoy o futura"
            return
        }

        state.isLoading = true
        let snapshot = state
        let categoria = snapshot.categoria
        let fechaCaducidad = snapshot.fechaCaducidad

        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let producto = Producto(
                    id: UUID().uuidString,
                    idNevera: neveraId,
                    codigoBarras: nil,
                    nombre: trimmed,
                    categoria: categoria,
                    fechaCaducidad: fechaCaducidad,
                    fechaRegistro: Kotlinx_datetimeLocalDate.from(date: todayDate),
                    imagenUrl: nil
                )
                try await self.productoRepository.insertProducto(producto: producto)
                self.state.isLoading = false
                self.state.success = true
            } catch {
                self.state.isLoading = false
                self.state.error = error.localizedDescription
            }
        }
    }

    func clearError() {
        state.error = nil
    }

    // MARK: - Helpers

    private static func datesEqual(
        _ a: Kotlinx_datetimeLocalDate, _ b: Kotlinx_datetimeLocalDate
    ) -> Bool {
        a.year == b.year && a.monthNumber == b.monthNumber && a.dayOfMonth == b.dayOfMonth
    }
}
