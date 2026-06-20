import UIKit
import UserNotifications

/// AppDelegate mínimo (conectado vía `@UIApplicationDelegateAdaptor`): fija el
/// delegate de notificaciones y enruta el TAP de un aviso de caducidad al
/// [AppRouter]. No gestiona push remoto (eso es Fase 2).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// Mostrar el aviso también con la app en primer plano (banner + sonido).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    /// Tap en la notificación → guarda el neveraId pendiente en el router para
    /// que `NeveraListView` navegue a la `NeveraDetailView` correspondiente.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let neveraId = response.notification.request.content.userInfo["neveraId"] as? String
        if let neveraId {
            Task { @MainActor in
                AppRouter.shared.neveraIdPendiente = neveraId
            }
        }
        completionHandler()
    }
}
