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
    }

    @Published var state = State()

    private let currentUserId: String
    private let createNeveraUseCase = KoinIosKt.getCreateNeveraUseCase()
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

    /// Mirror of `CreateNeveraUseCase`. The Flow re-emits on success.
    func createNevera(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            state.error = "El nombre no puede estar vacío"
            return
        }
        state.error = nil
        createNeveraUseCase.invoke(nombre: trimmed, idPropietario: currentUserId) { [weak self] result, error in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if let error = error {
                    self.state.error = error.localizedDescription
                } else if let opError = result as? OperationResultError {
                    self.state.error = opError.message
                }
                // success → binder re-emits.
            }
        }
    }

    func clearError() {
        state.error = nil
    }
}
