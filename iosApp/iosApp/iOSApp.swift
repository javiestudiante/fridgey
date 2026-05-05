import SwiftUI
import FirebaseCore
import Shared

@main
struct iOSApp: App {

    init() {
        // Order matters:
        //   1. Firebase MUST be configured before any Firebase API call.
        //   2. Koin then resolves AuthRepository, which already references
        //      Firebase.auth at construction time.
        //   3. The bridges must be installed before LoginView is shown,
        //      because LoginView resolves the use cases that go through
        //      GoogleSignInHelper / AppleSignInHelper.
        FirebaseApp.configure()
        KoinIosKt.doInitKoin()
        GoogleSignInBridge.register()
        AppleSignInBridge.register()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
