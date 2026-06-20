import SwiftUI
import UIKit

/// Pantalla de Ajustes (Fase 1b iOS). Hogar del toggle "Avisos de caducidad"
/// (default ON). Se llega desde el icono de ajustes de la lista de neveras.
struct AjustesView: View {

    @StateObject private var vm = AjustesViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                filaToggle
                if vm.permisoDenegado {
                    bannerPermiso
                }
            }
            .padding(16)
        }
        .background(Color.fridgeyCream.ignoresSafeArea())
        .navigationTitle("Ajustes")
        .navigationBarTitleDisplayMode(.inline)
        .task { await vm.cargar() }
        .onChange(of: scenePhase) { _, fase in
            if fase == .active { Task { await vm.refrescarPermiso() } }
        }
    }

    private var filaToggle: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text("Avisos de caducidad")
                    .font(.custom("Inter-Regular", size: 16).weight(.medium))
                    .foregroundStyle(Color.fridgeyInk)
                Text("Te avisamos en este dispositivo cuando un producto está a punto de caducar.")
                    .font(.custom("Inter-Regular", size: 13))
                    .italic()
                    .foregroundStyle(Color.fridgeyInkMuted)
            }
            Spacer(minLength: 8)
            Toggle("Avisos de caducidad", isOn: Binding(
                get: { vm.avisosCaducidad },
                set: { nuevo in Task { await vm.onToggle(nuevo) } }
            ))
            .labelsHidden()
            .tint(Color.fridgeyMintDeep)
        }
        .padding(16)
        .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.fridgeyHairline, lineWidth: 1))
    }

    private var bannerPermiso: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Activa las notificaciones en los ajustes del sistema para recibir avisos.")
                .font(.custom("Inter-Regular", size: 14))
                .foregroundStyle(Color.fridgeyInk)
            Button("Abrir ajustes del sistema") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(.custom("Inter-Regular", size: 14).weight(.medium))
            .foregroundStyle(Color.fridgeyRust)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.fridgeyRustSoft, in: RoundedRectangle(cornerRadius: 14))
    }
}
