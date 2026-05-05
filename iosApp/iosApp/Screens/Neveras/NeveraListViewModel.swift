import Foundation
import Shared

/// SwiftUI ObservableObject mirroring the Android `NeveraListViewModel`.
///
/// Subscribes to a Kotlin `Flow<List<Nevera>>` exposed via
/// [NeveraListBinder] (same pattern as `NeveraDetailViewModel` does with
/// `ProductoListBinder`), so the list re-renders automatically whenever a
/// fridge or its products change — no manual reload after create/delete.
///
/// `CreateNeveraUseCase` (for create) and `NeveraRepository` (for delete)
/// are still consumed as one-shot calls; the binder keeps the list in sync
/// after either of those completes.
@MainActor
final class NeveraListViewModel: ObservableObject {

    struct State {
        var isLoading: Bool = true
        var error: String? = nil
        var neveras: [Nevera] = []
    }

    @Published var state = State()

    private let currentUserId: String
    private let neveraRepository = KoinIosKt.getNeveraRepository()
    private let createNeveraUseCase = KoinIosKt.getCreateNeveraUseCase()
    private let binder: NeveraListBinder = KoinIosKt.getNeveraListBinder()

    init(currentUserId: String) {
        self.currentUserId = currentUserId
    }

    /// Starts (or restarts) the reactive subscription. Idempotent on the
    /// binder side — repeated calls cancel the previous job.
    func start() {
        binder.start(
            usuarioId: currentUserId,
            onValue: { [weak self] neveras in
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.state.isLoading = false
                    self.state.error = nil
                    self.state.neveras = (neveras as? [Nevera]) ?? []
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

    /// Cancels the active subscription but keeps the binder's scope alive,
    /// so a later `start()` (e.g. when navigating back from detail) can
    /// resubscribe. Hard cleanup happens in `deinit`.
    func stop() {
        binder.stop()
    }

    deinit {
        binder.dispose()
    }

    /// Mirror of `CreateNeveraUseCase`: validates name + 10-fridge cap and
    /// inserts. The Flow re-emits on success — no manual reload.
    func createNevera(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            state.error = "El nombre no puede estar vacío"
            return
        }
        state.error = nil

        createNeveraUseCase.invoke(
            nombre: trimmed,
            idPropietario: currentUserId
        ) { [weak self] result, error in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if let error = error {
                    self.state.error = error.localizedDescription
                    return
                }
                if result is OperationResultSuccess<NSString> {
                    // No-op: NeveraListBinder will re-emit with the new fridge.
                } else if let opError = result as? OperationResultError {
                    self.state.error = opError.message
                } else {
                    self.state.error = "Resultado inesperado al crear nevera"
                }
            }
        }
    }

    /// Deleting a fridge cascades to its products and collaborator rows
    /// (FK ON DELETE CASCADE). The Flow re-emits on success — no manual reload.
    func deleteNevera(_ nevera: Nevera) {
        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                try await self.neveraRepository.deleteNevera(neveraId: nevera.id)
            } catch {
                self.state.error = error.localizedDescription
            }
        }
    }

    func clearError() {
        state.error = nil
    }
}
