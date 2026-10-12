import SwiftUI

/// Doomscroll Guard — iOS entry point.
///
/// HONEST SCOPE (see README): iOS cannot inspect or modify native apps.
/// This app gives you (1) a stripped in-app browser (WKWebView + injected JS)
/// for YouTube/Instagram without short-form surfaces, (2) optional whole-app
/// Screen Time shields for the native apps (entitlement-gated), and
/// (3) a Safari Content Blocker companion. The GuardViewModel degrades
/// gracefully when the FamilyControls entitlement is missing.
@main
struct DoomscrollGuardApp: App {
    @StateObject private var model = GuardViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
        }
    }
}
