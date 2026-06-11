import Foundation
import Shared

/// SwiftUI ObservableObject mirroring the Android `NeveraListViewModel`.
///
/// Subscribes to the reactive home feed: per-fridge resúmenes
/// (`NeveraListBinder` → `Flow<List<NeveraResumen>>`) AND the cross-fridge
/// "caducan hoy" summary (`ExpiringTodayBinder`). `CreateNeveraUseCase` is a
/// one-shot; the binders re-emit on success.
@MainActor
final class NeveraListViewModel: ObservableObject {

    struct State {
        var isLoading: Bool = true
        var error: String? = nil
        var neveras: [NeveraResumen] = []
        var expiringToday: ExpiringTodaySummary? = nil
        /// Aviso NO bloqueante: la nevera se creó en LOCAL pero la subida a la
        /// nube falló. La creación no se revierte.
        var uploadWarning: String? = nil
    }

    @Published var state = State()

    private let currentUserId: String
    private let createNeveraUseCase = KoinIosKt.getCreateNeveraUseCase()
    private let subirANubeUseCase = KoinIosKt.getSubirANubeUseCase()
    private let binder: NeveraListBinder = KoinIosKt.getNeveraListBinder()
    private let expiringBinder: ExpiringTodayBinder = KoinIosKt.getExpiringTodayBinder()

    init(currentUserId: String) {
        self.currentUserId = currentUserId
    }

    func start() {
        binder.start(
            usuarioId: currentUserId,
            onValue: { [weak self] neveras in
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.state.isLoading = false
                    self.state.error = nil
                    self.state.neveras = (neveras as? [NeveraResumen]) ?? []
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
        expiringBinder.start(
            usuarioId: currentUserId,
            onValue: { [weak self] summary in
                Task { @MainActor [weak self] in
                    self?.state.expiringToday = summary
                }
            },
            onError: { _ in /* banner is best-effort; ignore its errors */ }
        )
    }

    func stop() {
        binder.stop()
        expiringBinder.stop()
    }

    deinit {
        binder.dispose()
        expiringBinder.dispose()
    }

    /// Crea una nevera (siempre LOCAL) y, si [guardarEnCuenta] está activo,
    /// encadena la subida a la nube (crear LOCAL → SubirANube). El Flow re-emite
    /// al crear. Un fallo de subida NO revierte la creación: se informa con un
    /// aviso no bloqueante y la nevera queda en LOCAL.
    func createNevera(name: String, guardarEnCuenta: Bool) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            state.error = "El nombre no puede estar vacío"
            return
        }
        state.error = nil
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let result = try await self.createNeveraUseCase.invoke(
                    nombre: trimmed, idPropietario: self.currentUserId
                )
                if let opError = result as? OperationResultError {
                    self.state.error = opError.message
                    return
                }
                // CreateNeveraUseCase devuelve el ID en Success<String>.
                guard guardarEnCuenta,
                      let success = result as? OperationResultSuccess<NSString>,
                      let neveraId = success.data as String? else {
                    // Toggle apagado (o sin ID): nevera LOCAL, el binder la refleja.
                    return
                }
                await self.subirNuevaNevera(neveraId: neveraId)
            } catch {
                self.state.error = error.localizedDescription
            }
        }
    }

    /// Sube a la nube una nevera recién creada. SubirANube ya trae su timeout +
    /// compensación (borra el doc remoto huérfano si falla). Un fallo NO revierte
    /// la creación: la nevera queda LOCAL y se informa con un aviso no bloqueante.
    private func subirNuevaNevera(neveraId: String) async {
        let warning = "Tu nevera se ha creado, pero no se pudo guardar en tu cuenta " +
            "ahora. Podrás guardarla más tarde desde sus opciones."
        do {
            let upload = try await subirANubeUseCase.invoke(
                neveraId: neveraId, requesterId: currentUserId
            )
            if upload is OperationResultError {
                state.uploadWarning = warning
            }
            // success → la nevera queda SYNCED; el binder/sync lo reflejan.
        } catch {
            state.uploadWarning = warning
        }
    }

    func clearError() {
        state.error = nil
    }

    func clearUploadWarning() {
        state.uploadWarning = nil
    }
}
