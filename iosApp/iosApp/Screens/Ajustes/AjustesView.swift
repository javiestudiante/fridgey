import SwiftUI
import UIKit
import Shared

/// Pantalla de Ajustes (Fase 1b iOS). Hogar del toggle "Avisos de caducidad"
/// (default ON). Se llega desde el icono de ajustes de la lista de neveras.
struct AjustesView: View {

    @StateObject private var vm = AjustesViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showConfirmEliminar = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                filaToggle
                if vm.permisoDenegado {
                    bannerPermiso
                }
                seccionPreferencias
                seccionCuenta
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
        // Confirmación fuerte (irreversibilidad).
        .alert("¿Eliminar tu cuenta?", isPresented: $showConfirmEliminar) {
            Button("Eliminar", role: .destructive) {
                Task { await vm.onEliminarCuentaConfirmado() }
            }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Esta acción es permanente y no se puede deshacer. Se eliminarán tu " +
                 "cuenta, tus neveras en solitario y sus productos. Saldrás de las " +
                 "neveras compartidas de otras personas.")
        }
        // Bloqueo: neveras compartidas a resolver primero.
        .alert("Tienes neveras compartidas", isPresented: Binding(
            get: { vm.neverasBloqueadas != nil },
            set: { if !$0 { vm.dismissBloqueo() } }
        )) {
            Button("Entendido", role: .cancel) { vm.dismissBloqueo() }
        } message: {
            Text(mensajeBloqueo)
        }
        // Error legible del borrado.
        .alert("No se pudo eliminar la cuenta", isPresented: Binding(
            get: { vm.errorEliminarCuenta != nil },
            set: { if !$0 { vm.dismissErrorEliminar() } }
        )) {
            Button("OK", role: .cancel) { vm.dismissErrorEliminar() }
        } message: {
            Text(vm.errorEliminarCuenta ?? "")
        }
    }

    /// Sección "Cuenta": acción destructiva de borrado de cuenta (RGPD).
    private var seccionCuenta: some View {
        VStack(alignment: .leading, spacing: 10) {
            EyebrowLabel(text: "CUENTA")
            Button(action: { showConfirmEliminar = true }) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Eliminar cuenta")
                            .font(.custom("Inter-Regular", size: 16).weight(.semibold))
                            .foregroundStyle(Color.fridgeyRust)
                        Text("Borra tu cuenta y tus datos de forma permanente.")
                            .font(.custom("Inter-Regular", size: 13))
                            .italic()
                            .foregroundStyle(Color.fridgeyInkMuted)
                    }
                    Spacer(minLength: 8)
                    if vm.eliminandoCuenta {
                        ProgressView().tint(Color.fridgeyRust)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.fridgeyHairline, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(vm.eliminandoCuenta)
        }
        .padding(.top, 4)
    }

    /// Mensaje del diálogo de bloqueo: lista las neveras compartidas a resolver.
    private var mensajeBloqueo: String {
        let nombres = (vm.neverasBloqueadas ?? [])
            .map { "• \($0.nombre.isEmpty ? "Nevera" : $0.nombre)" }
            .joined(separator: "\n")
        return "Para eliminar tu cuenta, primero elimina estas neveras compartidas:\n\n\(nombres)"
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

    /// Sección "Preferencias": cómo abre el botón "+" de una nevera el alta de
    /// producto. Reutiliza el mismo SegmentedToggle que el Escanear/A mano de
    /// AddProducto. Índice 0 = Manual, índice 1 = Escanear.
    private var seccionPreferencias: some View {
        VStack(alignment: .leading, spacing: 10) {
            EyebrowLabel(text: "PREFERENCIAS")
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Al añadir productos")
                        .font(.custom("Inter-Regular", size: 16).weight(.medium))
                        .foregroundStyle(Color.fridgeyInk)
                    Text("Qué abre el botón + de una nevera por defecto.")
                        .font(.custom("Inter-Regular", size: 13))
                        .italic()
                        .foregroundStyle(Color.fridgeyInkMuted)
                }
                SegmentedToggle(
                    options: [
                        SegmentOption(label: "Manual"),
                        SegmentOption(label: "Escanear"),
                    ],
                    selectedIndex: vm.modoAnadir == .manual ? 0 : 1,
                    onSelect: { idx in
                        Task { await vm.onModoAnadirSeleccionado(idx == 0 ? .manual : .escanear) }
                    }
                )
            }
            .padding(16)
            .background(Color.fridgeySurfaceWhite, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.fridgeyHairline, lineWidth: 1))
        }
        .padding(.top, 10)
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
