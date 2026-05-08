import SwiftUI
import AVFoundation

/// SwiftUI wrapper around an `AVCaptureVideoPreviewLayer`. Receives an
/// already-configured (or about-to-be-started) `AVCaptureSession` and binds
/// it to the preview layer.
///
/// The session is **owned by the parent view**, not by this representable.
/// Lifecycle (configure / start / stop) is the parent's responsibility:
/// this struct only renders whatever the supplied session is producing.
/// That keeps the same session reference stable across recompositions and
/// avoids the recurring "preview goes black" pitfall caused by the wrapper
/// rebuilding its own session on every state change.
struct CameraPreviewView: UIViewRepresentable {

    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.videoPreviewLayer.session = session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        // Session reference is stable; nothing to update on recompose. If
        // the parent ever needs to swap sessions (it doesn't today), this
        // is where `uiView.videoPreviewLayer.session = session` would go.
    }

    /// `UIView` whose backing `CALayer` is an `AVCaptureVideoPreviewLayer`.
    /// Using `+ layerClass` is the standard idiom for camera previews — it
    /// avoids manually adding a sublayer and keeping its frame in sync with
    /// the view's bounds (the layer IS the view's layer, so it auto-resizes).
    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer {
            // Force-cast is safe given `layerClass` above; the runtime
            // guarantees the backing layer is exactly this type.
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}
