import SwiftUI
import BookmarksSample

@main
struct BookmarksSampleApp: App {
    var body: some Scene {
        WindowGroup {
            SampleComposeRoot { BookmarksSampleViewControllerKt.BookmarksSampleViewController() }
                .ignoresSafeArea(.all)
        }
    }
}
