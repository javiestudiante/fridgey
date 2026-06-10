import Foundation
import Shared

/// SwiftUI ObservableObject mirroring the Android `UnirseViewModel`
/// (UC-03b): manual code entry + QR scan, with the accept call going
/// through the shared (idempotent) use case.
@MainActor
final class UnirseViewModel: ObservableObject {

    struct State {
        var codigo: String = ""
        /// Cámara abierta escaneando un QR.
        var escaneando: Bool = false
        /// Aceptación en curso (lecturas + batch contra el servidor).
        var validando: Bool = false
        /// Resultado de la última aceptación; nil mientras no se intenta.
        var resultado: ResultadoInvitacionUI? = nil
    }

    @Published var state = State()

    private let aceptarInvitacionUseCase = KoinIosKt.getAceptarInvitacionUseCase()

    func onCodigoChange(_ valor: String) {
        // El alfabeto del código no tiene minúsculas; normalizamos al teclear
        // (la normalización completa — guiones/espacios — la hace el use case).
        state.codigo = valor.uppercased()
        state.resultado = nil
    }

    func empezarEscaneo() {
        state.escaneando = true
        state.resultado = nil
    }

    func cancelarEscaneo() {
        state.escaneando = false
    }

    /// Primer QR leído por la cámara: cerrar el escáner y aceptar directamente.
    func onQrDetectado(_ rawValue: String, currentUserId: String) {
        guard !state.validando else { return }  // un frame tardío no debe re-entrar
        state.codigo = rawValue.uppercased()
        state.escaneando = false
        unirse(currentUserId: currentUserId)
    }

    /// Acepta la invitación con el código actual. Guard anti doble-tap aquí
    /// y aceptación idempotente en el use case compartido: re-intentos no
    /// duplican ni fallan.
    func unirse(currentUserId: String) {
        let codigo = state.codigo
        guard !codigo.isEmpty, !state.validando else { return }
        state.validando = true
        state.resultado = nil

        Task { @MainActor [weak self] in
            guard let self = self else { return }
            do {
                let resultado = try await self.aceptarInvitacionUseCase.invoke(
                    codigo: codigo,
                    usuarioId: currentUserId
                )
                self.state.resultado = ResultadoInvitacionUI(resultado)
            } catch {
                self.state.resultado = .error(error.localizedDescription)
            }
            self.state.validando = false
        }
    }
}
