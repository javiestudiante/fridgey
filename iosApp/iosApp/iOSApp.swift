import SwiftUI
import Shared

@main
struct iOSApp: App {

    init() {
        KoinIosKt.doInitKoin()
        // Temporary verification (remove once UI work begins)
        let repo = KoinIosKt.getNeveraRepository()
        print("Koin wired: \(type(of: repo))")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
