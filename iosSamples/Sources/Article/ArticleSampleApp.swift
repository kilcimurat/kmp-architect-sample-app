import SwiftUI
import ArticleSample

@main
struct ArticleSampleApp: App {
    var body: some Scene {
        WindowGroup {
            SampleComposeRoot { ArticleSampleViewControllerKt.ArticleSampleViewController() }
                .ignoresSafeArea(.all)
        }
    }
}
