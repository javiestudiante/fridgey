import Foundation

/// Router observable para navegación dirigida desde FUERA de la jerarquía de
/// vistas — en concreto, el tap de una notificación de caducidad. Singleton para
/// que el `AppDelegate` (que no vive en el árbol SwiftUI) pueda escribir el
/// destino pendiente y la UI (inyectada como `EnvironmentObject`) reaccione.
@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()
    private init() {}

    /// neveraId a abrir tras tocar una notificación (`nil` = ninguno). La vista
    /// lo consume una sola vez y lo vuelve a poner a `nil`.
    @Published var neveraIdPendiente: String?
}
