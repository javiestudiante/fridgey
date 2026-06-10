import SwiftUI
import Shared

/// Pantalla de invitación (UC-03a), paridad con `InvitarScreen` (Android):
/// genera el código al entrar, muestra el QR (240pt) + código "ABCD-EFGH",
/// permite compartirlo y regenerarlo; el error ofrece reintentar.
struct InvitarView: View {

    let neveraId: String
    let currentUserId: String

    @StateObject private var viewModel = InvitarViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            header

            switch viewModel.state {
            case .generando:
                ProgressView().tint(Color.fridgeyMintDeep)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

            case .generada(let codigo, _):
                codigoGenerado(codigo: codigo)

            case .error(let mensaje):
                errorContent(mensaje: mensaje)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.fridgeyCream.ignoresSafeArea())
        .navigationBarHidden(true)
        .task { viewModel.generar(neveraId: neveraId, currentUserId: currentUserId) }
    }

    // MARK: - Header (mirror del header de InvitarScreen)

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            Button(action: { dismiss() }) {
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
                Text("Invitar")
                    .font(.custom("InstrumentSerif-Regular", size: 30))
                    .foregroundStyle(Color.fridgeyInk)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    // MARK: - Código generado (QR + código + acciones)

    private func codigoGenerado(codigo: String) -> some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 24)
            QrCard(codigo: codigo)
            Spacer().frame(height: 20)
            Text(formatearCodigo(codigo))
                .font(.custom("Inter-Regular", size: 28).weight(.semibold))
                .tracking(4)
                .foregroundStyle(Color.fridgeyInk)
            Spacer().frame(height: 6)
            Text("Comparte el QR o el código. Caduca en 24 horas y es de un solo uso.")
                .font(.custom("Inter-Regular", size: 13))
                .foregroundStyle(Color.fridgeyInkMuted)
                .multilineTextAlignment(.center)
            Spacer().frame(height: 24)

            ShareLink(item: "Únete a mi nevera en Fridgey con el código \(formatearCodigo(codigo)) (válido 24 horas)") {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 16))
                    Text("Compartir código")
                        .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(Color.fridgeyMintDeep,
                            in: RoundedRectangle(cornerRadius: 16))
                .foregroundStyle(Color.fridgeySurfaceWhite)
            }

            Spacer().frame(height: 10)

            Button {
                viewModel.generar(neveraId: neveraId, currentUserId: currentUserId)
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.clockwise").font(.system(size: 16))
                    Text("Generar otro código")
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

            Spacer()
        }
        .padding(.horizontal, 32)
    }

    private func errorContent(mensaje: String) -> some View {
        VStack(spacing: 16) {
            Text(mensaje)
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyRust)
                .multilineTextAlignment(.center)
            // Padding/background DENTRO del label: el área táctil debe ser
            // toda la cápsula, no solo el texto.
            Button(action: {
                viewModel.generar(neveraId: neveraId, currentUserId: currentUserId)
            }) {
                Text("Reintentar")
                    .font(.custom("Inter-Regular", size: 14).weight(.semibold))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.fridgeyMintDeep, in: RoundedRectangle(cornerRadius: 16))
                    .foregroundStyle(Color.fridgeySurfaceWhite)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Tarjeta del QR: renderiza fuera del hilo principal (Core Image es
/// CPU-bound) y muestra un spinner hasta que el bitmap está listo.
private struct QrCard: View {
    let codigo: String

    @State private var qrImage: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.fridgeySurfaceWhite)
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.fridgeyHairline, lineWidth: 1)

            if let image = qrImage {
                Image(uiImage: image)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 240, height: 240)
                    .accessibilityLabel("Código QR de invitación")
            } else {
                ProgressView().tint(Color.fridgeyMintDeep)
            }
        }
        .frame(width: 264, height: 264)
        .task(id: codigo) {
            // El QR codifica el código TAL CUAL (sin guion): es lo que el
            // flujo de escaneo entrega a AceptarInvitacionUseCase.
            let code = codigo
            qrImage = await Task.detached(priority: .userInitiated) {
                QrCodeImage.generate(from: code, sidePx: 720)  // 240pt @3x
            }.value
        }
    }
}

/// "ABCDEFGH" → "ABCD-EFGH" solo para mostrar; al aceptar se normaliza.
private func formatearCodigo(_ codigo: String) -> String {
    guard codigo.count == 8 else { return codigo }
    return "\(codigo.prefix(4))-\(codigo.suffix(4))"
}
