import AVFoundation

/// Camera permission states the scanner UI cares about.
///
/// `denied` and `restricted` are surfaced as separate cases here because the
/// distinction is meaningful at the system level (parental controls vs.
/// explicit user denial), but the UI treats them identically: both lead to
/// the "open Settings" branch since neither can be re-prompted via
/// `requestPermission`.
enum CameraPermissionStatus {
    case notDetermined
    case authorized
    case denied
    case restricted
}

protocol CameraPermissionService {
    var currentStatus: CameraPermissionStatus { get }
    func requestPermission() async -> CameraPermissionStatus
}

final class DefaultCameraPermissionService: CameraPermissionService {

    var currentStatus: CameraPermissionStatus {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .notDetermined: return .notDetermined
        case .authorized:    return .authorized
        case .denied:        return .denied
        case .restricted:    return .restricted
        @unknown default:    return .denied
        }
    }

    /// Requests camera access from the user.
    ///
    /// **iOS-specific behaviour worth knowing about:** unlike Android, iOS
    /// does NOT expose a `shouldShowRequestPermissionRationale` equivalent.
    /// If the user has denied access at any point in the past
    /// (`authorizationStatus == .denied`), `AVCaptureDevice.requestAccess`
    /// silently resolves the continuation to `false` *without* presenting
    /// the system prompt. The only path forward is to nudge the user to
    /// `Settings.app`.
    ///
    /// In practice: only call this method when
    /// `currentStatus == .notDetermined`. For `.denied` / `.restricted`
    /// states the caller should show a "Open Settings" button instead.
    func requestPermission() async -> CameraPermissionStatus {
        let granted = await AVCaptureDevice.requestAccess(for: .video)
        return granted ? .authorized : .denied
    }
}
