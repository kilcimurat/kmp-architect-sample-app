import SwiftUI
import FeedSample

/// The entire iOS host for this sample: one call into the feature framework.
///
/// The framework name (FeedSample) is derived from the Gradle path by the kmp.framework convention
/// plugin, which is what keeps four modules named `shared` from producing four frameworks with the
/// same name.
@main
struct FeedSampleApp: App {
    var body: some Scene {
        WindowGroup {
            SampleComposeRoot { FeedSampleViewControllerKt.FeedSampleViewController() }
                .ignoresSafeArea(.all)
        }
    }
}
