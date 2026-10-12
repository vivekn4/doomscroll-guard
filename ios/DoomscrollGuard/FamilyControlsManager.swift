import Foundation
import FamilyControls
import ManagedSettings

/// All FamilyControls interaction lives here, behind availability checks.
/// Without the `com.apple.developer.family-controls` entitlement, authorization
/// fails — this manager reports .unavailable with a human explanation instead
/// of crashing or hanging. The wrapper (no entitlement needed) keeps working.
enum ShieldAvailability: Equatable {
    case unknown
    case ready
    case denied
    case unavailable(reason: String)
}

enum ShieldError: Error {
    case entitlementMissing
    case authorizationFailed(String)
    case storeFailed(String)

    var userMessage: String {
        switch self {
        case .entitlementMissing:
            return "App-lock isn't available in this build: Apple's FamilyControls approval hasn't landed yet. The stripped browser still blocks Shorts & Reels."
        case .authorizationFailed(let d):
            return "Screen Time authorization failed: \(d)"
        case .storeFailed(let d):
            return "Couldn't update app locks: \(d)"
        }
    }
}

@MainActor
final class FamilyControlsManager {
    private let center = AuthorizationCenter.shared
    private let store = ManagedSettingsStore(named: .doomscrollGuard)
    private var selection = FamilyActivitySelection()

    func availability() async -> ShieldAvailability {
        switch center.authorizationStatus {
        case .approved: return .ready
        case .denied: return .denied
        case .notDetermined: return .unknown
        @unknown default: return .unknown
        }
    }

    func requestAuthorization() async throws {
        do {
            try await center.requestAuthorization(for: .individual)
        } catch {
            // The classic missing-entitlement failure surfaces here as an XPC error.
            let msg = (error as NSError)
            if msg.domain == NSCocoaErrorDomain && msg.code == 4099 {
                throw ShieldError.entitlementMissing
            }
            throw ShieldError.authorizationFailed(error.localizedDescription)
        }
    }

    /// The user picks apps in Apple's own FamilyActivityPicker (tokens are opaque —
    /// we never learn which app is which; that is the point). We persist the
    /// selection and shield/unshield per the master switch.
    func setSelection(_ selection: FamilyActivitySelection) {
        self.selection = selection
        persist(selection)
    }

    func applyShields(youtube: Bool, instagram: Bool, tiktok: Bool) async throws {
        guard center.authorizationStatus == .approved else {
            throw ShieldError.authorizationFailed("not authorized")
        }
        // NOTE: tokens are opaque. A production build stores the user's
        // FamilyActivitySelection (via @AppStorage / file) and shields
        // selection.applicationTokens as a whole — per-app granularity
        // beyond the user's own picker choice is intentionally impossible.
        // The youtube/instagram/tiktok flags here drive UI copy and which
        // wrapper tabs exist; the shield itself covers the user's selection.
        store.shield.applications = selection.applicationTokens.isEmpty
            ? nil : selection.applicationTokens
        _ = (youtube, instagram, tiktok) // documented above; kept for API clarity
    }

    func removeAllShields() async throws {
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
    }

    // MARK: - persistence of the opaque selection
    //
    // FamilyActivitySelection is persisted with JSONEncoder (the pattern used
    // across Screen Time tutorials). If the SDK in use doesn't expose Codable
    // conformance here, persist via NSKeyedArchiver instead — the underlying
    // tokens are NSSecureCoding. Either way the bytes live in the shared App
    // Group so the DeviceActivityMonitor extension can read them.
    private func persist(_ selection: FamilyActivitySelection) {
        if let data = try? JSONEncoder().encode(selection),
           let url = Self.groupURL()?.appendingPathComponent("selection.json") {
            try? data.write(to: url)
        }
    }

    static func groupURL() -> URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.vn.doomscrollguard")
    }
}

extension ManagedSettingsStore.Name {
    static let doomscrollGuard = Self("doomscrollguard")
}
