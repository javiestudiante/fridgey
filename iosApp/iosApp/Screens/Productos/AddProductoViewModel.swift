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
        var cantidad: Double = 1.0
        var unidad: UnidadMedida = .unidades
        var diasAvisoAntes: Int = 3
        /// Pre-filled by the scanner's barcode / Open Food Facts phase; saved
        /// with the product. Nil when added by hand (no barcode scanned).
        var codigoBarras: String? = nil
        var imagenUrl: String? = nil
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

    /// Changing the category also re-applies that category's default unit.
    /// Mirrors the Kotlin contract: any manual unit override is wiped on
    /// the next category switch — we do NOT remember per-user overrides
    /// across categories.
    func onCategoriaSelected(_ categoria: Categoria) {
        state.categoria = categoria
        state.unidad = categoria.unidadDefault
        state.isFormPristine = state.isFormPristine && (categoria == .otros)
    }

    /// Re-anchors the expiry date. Also clamps `diasAvisoAntes` to the new
    /// `daysUntil(fecha)` upper bound — see `clampAvisoAntes`. The pristine
    /// flag still depends only on whether `fecha` equals `initialExpiry`.
    func onFechaSelected(_ fecha: Kotlinx_datetimeLocalDate) {
        state.fechaCaducidad = fecha
        state.diasAvisoAntes = Self.clampAvisoAntes(state.diasAvisoAntes, fecha: fecha)
        state.error = nil
        state.isFormPristine = state.isFormPristine && Self.datesEqual(fecha, initialExpiry)
    }

    func onCantidadChanged(_ cantidad: Double) {
        guard cantidad > 0.0 else { return }
        state.cantidad = cantidad
        state.error = nil
        state.isFormPristine = state.isFormPristine && cantidad == 1.0
    }

    /// Manual override of the unit (after the auto-default from category).
    func onUnidadChanged(_ unidad: UnidadMedida) {
        state.unidad = unidad
        state.error = nil
        state.isFormPristine = state.isFormPristine && unidad == state.categoria.unidadDefault
    }

    func onDiasAvisoAntesChanged(_ dias: Int) {
        state.diasAvisoAntes = dias
        state.error = nil
        state.isFormPristine = state.isFormPristine && dias == 3
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
        state.diasAvisoAntes = Self.clampAvisoAntes(state.diasAvisoAntes, fecha: date)
        state.error = nil
        state.isFormPristine = false
        state.scanMode = .manual
    }

    /// Applies the Open Food Facts autofill resolved by the scanner's CÓDIGO
    /// phase. Sets fields directly (NOT via `onCategoriaSelected`, which would
    /// override `unidad` with the category default) and marks the form dirty.
    /// A blank name (lookup miss) leaves the current name untouched.
    func onScannedProductReceived(_ autoFill: ProductAutoFill) {
        if !autoFill.nombre.isEmpty {
            state.name = autoFill.nombre
        }
        state.categoria = autoFill.categoria
        state.cantidad = autoFill.cantidad
        state.unidad = autoFill.unidad
        state.codigoBarras = autoFill.codigoBarras
        state.imagenUrl = autoFill.imagenUrl
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
        guard state.cantidad > 0.0 else {
            state.error = "La cantidad debe ser mayor que cero"
            return
        }
        guard state.diasAvisoAntes >= 0 else {
            state.error = "Los días de aviso deben ser cero o positivos"
            return
        }

        state.isLoading = true
        let snapshot = state
        let categoria = snapshot.categoria
        let fechaCaducidad = snapshot.fechaCaducidad
        // Kotlin Double <-> Swift Double map directly via K/N, no cast.
        let cantidad = snapshot.cantidad
        let unidad = snapshot.unidad
        // `diasAvisoAntes` is still Kotlin Int → Swift Int32 at the boundary.
        let diasAvisoAntes = Int32(snapshot.diasAvisoAntes)

        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let producto = Producto(
                    id: UUID().uuidString,
                    idNevera: neveraId,
                    codigoBarras: snapshot.codigoBarras,
                    nombre: trimmed,
                    categoria: categoria,
                    fechaCaducidad: fechaCaducidad,
                    fechaRegistro: Kotlinx_datetimeLocalDate.from(date: todayDate),
                    imagenUrl: snapshot.imagenUrl,
                    cantidad: cantidad,
                    unidad: unidad,
                    diasAvisoAntes: diasAvisoAntes,
                    creadoPor: nil
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

    /// Clamps `currentDias` so it never exceeds the number of days
    /// between today and `fecha`. If the date is today or earlier,
    /// the clamp floor is 0 ("warn me the same day"). Mirrors the
    /// Kotlin `clampAvisoAntes` helper exactly.
    static func clampAvisoAntes(_ currentDias: Int, fecha: Kotlinx_datetimeLocalDate) -> Int {
        let todayStart = Calendar.current.startOfDay(for: Date())
        let fechaStart = Calendar.current.startOfDay(for: fecha.asSwiftDate)
        let diff = Calendar.current.dateComponents([.day], from: todayStart, to: fechaStart).day ?? 0
        let maxDias = max(0, diff)
        return min(currentDias, maxDias)
    }
}
