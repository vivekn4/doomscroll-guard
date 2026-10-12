import SwiftUI
import Combine
import FamilyControls

/// Single source of truth for the switch UI. Everything the user flips here
/// maps to platform actions:
///  - master switch  -> wrapper tabs enabled/disabled + shields applied/removed
///  - per-app toggles -> which wrapper tabs exist / which apps get shielded
///
/// FamilyControls calls are isolated in FamilyControlsManager so the UI never
/// crashes when the entitlement is missing — it just reports .unavailable.
@MainActor
final class GuardViewModel: ObservableObject {
    @Published var masterOn: Bool {
        didSet { UserDefaults.standard.set(masterOn, forKey: "guard.master"); apply() }
    }
    @Published var youtubeOn: Bool {
        didSet { UserDefaults.standard.set(youtubeOn, forKey: "guard.youtube"); apply() }
    }
    @Published var instagramOn: Bool {
        didSet { UserDefaults.standard.set(instagramOn, forKey: "guard.instagram"); apply() }
    }
    @Published var tiktokShield: Bool {
        didSet { UserDefaults.standard.set(tiktokShield, forKey: "guard.tiktok"); apply() }
    }
    @Published var shieldState: ShieldAvailability = .unknown
    @Published var lastError: String?

    private let shields = FamilyControlsManager()

    init() {
        let d = UserDefaults.standard
        masterOn = d.object(forKey: "guard.master") as? Bool ?? false
        youtubeOn = d.object(forKey: "guard.youtube") as? Bool ?? true
        instagramOn = d.object(forKey: "guard.instagram") as? Bool ?? true
        tiktokShield = d.object(forKey: "guard.tiktok") as? Bool ?? true
        Task { await refreshShieldState() }
    }

    var enabledTabs: [WrapperTab] {
        guard masterOn else { return [] }
        var tabs: [WrapperTab] = []
        if youtubeOn { tabs.append(.youtube) }
        if instagramOn { tabs.append(.instagram) }
        return tabs
    }

    func refreshShieldState() async {
        shieldState = await shields.availability()
    }

    /// Apply toggle state to the shield layer. Never throws to the UI.
    func apply() {
        Task {
            do {
                if masterOn {
                    try await shields.applyShields(youtube: youtubeOn, instagram: instagramOn, tiktok: tiktokShield)
                } else {
                    try await shields.removeAllShields()
                }
                lastError = nil
            } catch {
                lastError = (error as? ShieldError)?.userMessage ?? error.localizedDescription
            }
            await refreshShieldState()
        }
    }

    func requestShieldAuthorization() async -> Bool {
        do {
            try await shields.requestAuthorization()
            await refreshShieldState()
            apply()
            return true
        } catch {
            lastError = (error as? ShieldError)?.userMessage ?? error.localizedDescription
            return false
        }
    }
}

enum WrapperTab: String, CaseIterable, Identifiable {
    case youtube, instagram
    var id: String { rawValue }
    var label: String { self == .youtube ? "YouTube" : "Instagram" }
    var startURL: URL {
        switch self {
        case .youtube: return URL(string: "https://m.youtube.com/")!
        case .instagram: return URL(string: "https://www.instagram.com/")!
        }
    }
}
