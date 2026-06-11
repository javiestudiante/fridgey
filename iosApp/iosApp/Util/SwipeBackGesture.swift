import SwiftUI
import UIKit

/// Reactiva el gesto nativo de "atrás" (swipe desde el borde izquierdo) en
/// pantallas que ocultan la barra de navegación del sistema.
///
/// UIKit desactiva el `interactivePopGestureRecognizer` cuando la barra está
/// oculta (`.navigationBarHidden(true)`) o hay back button custom, porque su
/// delegate por defecto depende del back button del sistema. Este helper se
/// cuelga de la jerarquía UIKit que `NavigationStack` genera por debajo,
/// sustituye ese delegate mientras la pantalla está visible y lo restaura al
/// salir, de modo que el resto de pantallas conservan el comportamiento
/// estándar.
///
/// El gesto solo se permite con más de un view controller en la pila y sin
/// transición en curso, así que las pantallas raíz (sin botón back) no se ven
/// afectadas ni puede dejarse la navegación en estado inconsistente.
private final class SwipeBackController: UIViewController, UIGestureRecognizerDelegate {

    private weak var popGesture: UIGestureRecognizer?
    private weak var originalDelegate: UIGestureRecognizerDelegate?

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard let gesture = navigationController?.interactivePopGestureRecognizer,
              gesture.delegate !== self else { return }
        popGesture = gesture
        originalDelegate = gesture.delegate
        gesture.delegate = self
        gesture.isEnabled = true
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if let gesture = popGesture, gesture.delegate === self {
            gesture.delegate = originalDelegate
        }
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let nav = navigationController else { return false }
        return nav.viewControllers.count > 1 && nav.transitionCoordinator == nil
    }
}

private struct SwipeBackEnabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { SwipeBackController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

extension View {
    /// Aplica a pantallas pusheadas con barra de navegación oculta para que el
    /// swipe-back nativo conviva con la flecha "atrás" custom.
    func enableSwipeBack() -> some View {
        background(SwipeBackEnabler().frame(width: 0, height: 0))
    }
}
