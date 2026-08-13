import SwiftUI
import UIKit
import AppShared

/// The whole iOS host: a window, a Compose view controller, and one capability the shared code
/// cannot provide for itself.
@main
struct KmpArchitectApp: App {

    private let shareBridge = SystemShareBridge()

    var body: some Scene {
        WindowGroup {
            ComposeRoot(shareBridge: shareBridge)
                .ignoresSafeArea(.all)
        }
    }
}

/// Hosts the shared Compose surface. Swift never assembles Kotlin objects — it calls one factory.
struct ComposeRoot: UIViewControllerRepresentable {

    let shareBridge: IosShareBridge

    func makeUIViewController(context: Context) -> UIViewController {
        AppViewControllerKt.AppViewController(shareBridge: shareBridge)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Presents `UIActivityViewController`, which needs a live view controller — exactly the kind of
/// capability that belongs to the host rather than to shared code.
///
/// The protocol is intentionally callback-based: Kotlin/Native cannot have Swift implement a
/// `suspend` function, so the Kotlin side adapts this completion handler back into the suspending
/// `Sharer` port the domain layer uses.
final class SystemShareBridge: NSObject, IosShareBridge {

    func share(title: String, url: String, onResult: @escaping (KotlinBoolean) -> Void) {
        guard let presenter = Self.topViewController() else {
            onResult(KotlinBoolean(value: false))
            return
        }

        var items: [Any] = [title]
        if let link = URL(string: url) {
            items.append(link)
        }

        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, _ in
            onResult(KotlinBoolean(value: completed))
        }

        // iPad requires an anchor for the popover presentation.
        if let popover = controller.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(
                x: presenter.view.bounds.midX,
                y: presenter.view.bounds.midY,
                width: 0,
                height: 0
            )
            popover.permittedArrowDirections = []
        }

        presenter.present(controller, animated: true)
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }

        var controller = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }
}
