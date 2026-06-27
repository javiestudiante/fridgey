import UIKit
import UserNotifications
import FirebaseMessaging
import Shared

/// AppDelegate (conectado vía `@UIApplicationDelegateAdaptor`):
///  - Notis LOCALES de caducidad: fija el delegate de UNUserNotificationCenter y
///    enruta el TAP al [AppRouter] (sin cambios desde la Fase 1b).
///  - Push REMOTO (HITO 5): registro APNs→FCM con swizzling DESACTIVADO
///    (FirebaseAppDelegateProxyEnabled = NO en Info.plist), cableado explícito.
///
/// El token FCM se cruza al shared module (`onFcmTokenRecibido`), que escribe el
/// TokenDoc vía gitlive; NO se escribe Firestore desde Swift.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Push remoto: con swizzling OFF debemos enlazar APNs↔FCM a mano.
        // FirebaseApp.configure() ya corrió en iOSApp.init() (antes que este
        // callback), así que Messaging.messaging() es seguro aquí.
        Messaging.messaging().delegate = self
        // Pide el token APNs (no requiere permiso de alerta; el permiso solo
        // afecta a MOSTRAR, igual que en Android el token no depende de POST_NOTIFICATIONS).
        application.registerForRemoteNotifications()
        return true
    }

    // MARK: - APNs ↔ FCM (swizzling OFF)

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Sin swizzling, hay que entregar el APNs token a FCM manualmente; FCM
        // deriva de él su registration token y dispara didReceiveRegistrationToken.
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        NSLog("[PUSH] Fallo al registrar en APNs: %@", error.localizedDescription)
    }

    /// FCM entregó (o rotó) el registration token → lo cruzamos al shared module.
    /// `RegistroTokenPushIos` lo escribe si hay sesión, o lo guarda hasta el login.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        KoinIosKt.onFcmTokenRecibido(token: fcmToken)
    }

    // MARK: - Presentación / tap (UNUserNotificationCenter)

    /// Foreground: FILTRO de dispositivo compartido para los push de colaboración.
    /// Si el push trae `destinatarioUid` y NO coincide con la sesión actual (o no
    /// hay sesión) → no se presenta. Las notis LOCALES de caducidad NO llevan
    /// `destinatarioUid`, así que se muestran como siempre (banner + lista + sonido).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        if let destinatarioUid = userInfo["destinatarioUid"] as? String {
            let sesionUid = KoinIosKt.getAuthRepository().getCurrentUser()?.uid
            if sesionUid == nil || destinatarioUid != sesionUid {
                completionHandler([])
                return
            }
        }
        completionHandler([.banner, .list, .sound])
    }

    /// Tap en la notificación → guarda el neveraId pendiente en el router. Vale
    /// tanto para los avisos de caducidad locales como para los push de
    /// colaboración (su `data.neveraId` aparece en `userInfo`).
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
