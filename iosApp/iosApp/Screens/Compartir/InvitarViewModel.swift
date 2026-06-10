import Foundation
import Shared

/// SwiftUI ObservableObject mirroring the Android `InvitarViewModel`
/// (UC-03a): generates an invite code on appear, supports regeneration,
/// and surfaces errors with a retry.
@MainActor
final class InvitarViewModel: ObservableObject {

    /// Mirror of the Android sealed `InvitarUiState`.
    enum UiState {
        case generando
        case generada(codigo: String, expiraEnMillis: Int64)
        case error(String)
    }

    @Published private(set) var state: UiState = .generando

    private let generarInvitacionUseCase = KoinIosKt.getGenerarInvitacionUseCase()

    /// Anti double-tap guard, mirror of the Android flag.
    private var generando = false

    /// Generates (or re-generates) a code. Each call mints a new one.
    func generar(neveraId: String, currentUserId: String) {
        guard !generando else { return }
        generando = true
        state = .generando

        Task { @MainActor [weak self] in
            guard let self = self else { return }
            defer { self.generando = false }
            do {
                let result = try await self.generarInvitacionUseCase.invoke(
                    neveraId: neveraId,
                    requesterId: currentUserId
                )
                if let success = result as? OperationResultSuccess<InvitacionGenerada>,
                   let invitacion = success.data {
                    self.state = .generada(
                        codigo: invitacion.codigo,
                        expiraEnMillis: invitacion.expiraEnMillis
                    )
                } else if let failure = result as? OperationResultError {
                    self.state = .error(failure.message)
                } else {
                    self.state = .error("Respuesta inesperada al generar la invitación")
                }
            } catch {
                self.state = .error(error.localizedDescription)
            }
        }
    }
}
