import SwiftUI
import UIKit

/// Shared by all three sample hosts.
///
/// It takes a factory closure rather than importing a feature framework, which is what lets one
/// file serve every sample: each target supplies its own Kotlin entry point, and nothing here knows
/// which feature is running.
struct SampleComposeRoot: UIViewControllerRepresentable {

    let makeViewController: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController {
        makeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
