import AVFoundation
import SwiftUI
import Shared

/// Flujo "Unirse con código" (UC-03b), paridad con `UnirseScreen` (Android):
/// entrada manual del código o escaneo del QR de invitación. Todos los
/// estados del resultado se cubren con un `switch` exhaustivo sobre
/// [ResultadoInvitacionUI]; los dos finales felices ofrecen abrir la nevera.
struct UnirseView: View {

    let currentUserId: String

    @StateObject private var viewModel = UnirseViewModel()
    @Environment(\.dismiss) private var dismiss

    /// Wrapper Identifiable para `navigationDestination(item:)` — evita una
    /// conformance retroactiva global de String.
    private struct DestinoNevera: Identifiable, Hashable {
        let id: String
    }

    @State private var permisoDenegado = false
    @State private var destinoNevera: DestinoNevera?

    /// Sesión de cámara estable, propiedad de la vista (mismo patrón que
    /// `DateScannerView`: reconstruirla en cada cambio de estado deja la
    /// preview en negro).
    @State private var session = AVCaptureSession()
    @State private var analyzer = QrFrameAnalyzer()
    @State private var sessionConfigured = false

    private let permissionService: CameraPermissionService = DefaultCameraPermissionService()
    private let sessionQueue = DispatchQueue(label: "unirse.qr.session")

    var body: some View {
        VStack(spacing: 0) {
            header

            if viewModel.state.escaneando {
                escanerQr
            } else {
                entradaCodigo
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.fridgeyCream.ignoresSafeArea())
        .navigationBarHidden(true)
        .navigationDestination(item: $destinoNevera) { destino in
            NeveraDetailView(neveraId: destino.id, currentUserId: currentUserId)
        }
        .onChange(of: viewModel.state.escaneando) { _, escaneando in
            sincronizarCamara(activa: escaneando)
        }
        .onDisappear { sincronizarCamara(activa: false) }
        .onReceive(analyzer.results) { result in
            if let raw = result?.rawValue, !raw.isEmpty {
                viewModel.onQrDetectado(raw, currentUserId: currentUserId)
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            Button(action: {
                if viewModel.state.escaneando {
                    viewModel.cancelarEscaneo()
                } else {
                    dismiss()
                }
            }) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 18))
                    .foregroundStyle(Color.fridgeyInkSoft)
                    .frame(width: 36, height: 36)
                    .background(Color.fridgeyInk.opacity(0.04),
                                in: RoundedRectangle(cornerRadius: FridgeyRadius.backButton))
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                EyebrowLabel(text: "NEVERA COLABORATIVA")
                Text("Unirse con código")
                    .font(.custom("InstrumentSerif-Regular", size: 30))
                    .foregroundStyle(Color.fridgeyInk)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    // MARK: - Entrada manual

    private var entradaCodigo: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 16)
                Text("Introduce el código que te han compartido o escanea el QR de la invitación.")
                    .font(.custom("Inter-Regular", size: 14))
                    .foregroundStyle(Color.fridgeyInkMuted)
                    .multilineTextAlignment(.center)
                Spacer().frame(height: 20)

                TextField("p. ej. ABCD-EFGH", text: Binding(
                    get: { viewModel.state.codigo },
                    set: { viewModel.onCodigoChange($0) }
                ))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .disabled(viewModel.state.validando)
                .font(.custom("Inter-Regular", size: 16))
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(Color.fridgeySurfaceWhite,
                            in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.fridgeyHairline, lineWidth: 1))

                Spacer().frame(height: 14)

                // Guard anti doble-tap: deshabilitado mientras valida (más
                // el guard del VM y la idempotencia del use case común).
                Button(action: { viewModel.unirse(currentUserId: currentUserId) }) {
                    HStack(spacing: 10) {
                        if viewModel.state.validando {
                            ProgressView().tint(Color.fridgeySurfaceWhite)
                                .controlSize(.small)
                            Text("Uniéndote…")
                                .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                        } else {
                            Text("Unirse")
                                .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(
                        viewModel.state.codigo.isEmpty || viewModel.state.validando
                            ? Color.fridgeyMintDeep.opacity(0.4)
                            : Color.fridgeyMintDeep,
                        in: RoundedRectangle(cornerRadius: 16)
                    )
                    .foregroundStyle(Color.fridgeySurfaceWhite)
                }
                .buttonStyle(.plain)
                .disabled(viewModel.state.codigo.isEmpty || viewModel.state.validando)

                Spacer().frame(height: 10)

                Button(action: empezarEscaneoConPermiso) {
                    HStack(spacing: 8) {
                        Image(systemName: "qrcode.viewfinder").font(.system(size: 16))
                        Text("Escanear QR")
                            .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(Color.fridgeySurfaceWhite,
                                in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.fridgeyHairline, lineWidth: 1))
                    .foregroundStyle(Color.fridgeyInk)
                }
                .buttonStyle(.plain)
                .disabled(viewModel.state.validando)

                if permisoDenegado {
                    Spacer().frame(height: 12)
                    Text("Sin permiso de cámara no se puede escanear. Concédelo en Ajustes o teclea el código a mano.")
                        .font(.custom("Inter-Regular", size: 12))
                        .foregroundStyle(Color.fridgeyRust)
                        .multilineTextAlignment(.center)
                }

                if let resultado = viewModel.state.resultado {
                    Spacer().frame(height: 24)
                    resultadoCard(resultado)
                }

                Spacer().frame(height: 32)
            }
            .padding(.horizontal, 24)
        }
    }

    // MARK: - Resultado (switch EXHAUSTIVO sobre el enum puente)

    @ViewBuilder
    private func resultadoCard(_ resultado: ResultadoInvitacionUI) -> some View {
        switch resultado {
        case .aceptada(let neveraId, let nombre):
            exitoCard(
                titulo: "¡Te has unido a «\(nombre)»!",
                detalle: "Sus productos se están sincronizando en tu dispositivo.",
                neveraId: neveraId
            )

        case .yaEresMiembro(let neveraId, let nombre):
            exitoCard(
                titulo: "Ya formas parte de «\(nombre)»",
                detalle: "No hace falta volver a unirse — la nevera está enganchada a este dispositivo.",
                neveraId: neveraId
            )

        case .noEncontrada:
            avisoCard("El código no es válido. Revísalo e inténtalo de nuevo.")

        case .expirada:
            avisoCard("La invitación ha caducado (las invitaciones duran 24 horas). Pide al propietario que genere otra.")

        case .yaUsada:
            avisoCard("Esta invitación ya se ha utilizado: cada código vale para una sola persona. Pide otro al propietario.")

        case .neveraLlena:
            avisoCard("La nevera ya tiene 4 miembros, el máximo. No se pueden añadir más.")

        case .error(let mensaje):
            avisoCard("No se pudo completar la unión: \(mensaje)")
        }
    }

    private func exitoCard(titulo: String, detalle: String, neveraId: String) -> some View {
        VStack(spacing: 8) {
            Text(titulo)
                .font(.custom("Inter-Regular", size: 15).weight(.semibold))
                .foregroundStyle(Color.fridgeyInk)
                .multilineTextAlignment(.center)
            Text(detalle)
                .font(.custom("Inter-Regular", size: 13))
                .foregroundStyle(Color.fridgeyInkSoft)
                .multilineTextAlignment(.center)
            // Padding/background DENTRO del label: el área táctil debe ser
            // toda la cápsula, no solo el texto.
            Button(action: { destinoNevera = DestinoNevera(id: neveraId) }) {
                Text("Abrir nevera")
                    .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.fridgeyMintDeep, in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(Color.fridgeySurfaceWhite)
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(18)
        .background(Color.fridgeyMintTint, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18)
            .stroke(Color.fridgeyMintSoft, lineWidth: 1))
    }

    private func avisoCard(_ mensaje: String) -> some View {
        Text(mensaje)
            .font(.custom("Inter-Regular", size: 13))
            .foregroundStyle(Color.fridgeyRust)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(18)
            .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18)
                .stroke(Color.fridgeyHairline, lineWidth: 1))
    }

    // MARK: - Escáner QR (reutiliza CameraPreviewView + QrFrameAnalyzer)

    private var escanerQr: some View {
        VStack(spacing: 0) {
            CameraPreviewView(session: session)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text("Apunta al QR de la invitación")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInkMuted)

            Spacer().frame(height: 10)

            Button(action: { viewModel.cancelarEscaneo() }) {
                Text("Cancelar")
                    .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(Color.fridgeySurfaceWhite,
                                in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.fridgeyHairline, lineWidth: 1))
                    .foregroundStyle(Color.fridgeyInk)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 24)

            Spacer().frame(height: 20)
        }
    }

    private func empezarEscaneoConPermiso() {
        Task { @MainActor in
            let status = await permissionService.requestPermission()
            if status == .authorized {
                permisoDenegado = false
                viewModel.empezarEscaneo()
            } else {
                permisoDenegado = true
            }
        }
    }

    /// Arranca/para la sesión fuera del main thread (mismo criterio que
    /// `DateScannerView`: `startRunning` bloquea y iOS 17+ lo penaliza en main).
    private func sincronizarCamara(activa: Bool) {
        sessionQueue.async {
            if activa {
                if !sessionConfigured {
                    configurarSesion()
                }
                if !session.isRunning { session.startRunning() }
            } else {
                if session.isRunning { session.stopRunning() }
            }
        }
    }

    private func configurarSesion() {
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .high

        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera, for: .video, position: .back
        ), let input = try? AVCaptureDeviceInput(device: device),
            session.canAddInput(input) else { return }
        session.addInput(input)

        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(analyzer, queue: sessionQueue)
        if session.canAddOutput(videoOutput) {
            session.addOutput(videoOutput)
        }

        Task { @MainActor in sessionConfigured = true }
    }
}
