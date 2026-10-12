import DeviceActivity
import ManagedSettings

/// Enforces the Guard on a schedule (the iOS equivalent of the Android
/// always-on service toggle). The schedule itself is a user choice surfaced
/// in a future settings screen; the plumbing is here from day one.
/// Target: DeviceActivityMonitorExtension (com.apple.developer.family-controls).
final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let store = ManagedSettingsStore(named: .doomscrollGuard)

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        // Schedule window opened (e.g. "Guard on 22:00–07:00"): re-apply shields
        // from the persisted selection, if the user authorized Screen Time.
        if let data = try? Data(contentsOf: Self.selectionURL()),
           let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data),
           !selection.applicationTokens.isEmpty {
            store.shield.applications = selection.applicationTokens
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name,
                                        activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        // Reserved for a future "N minutes of short-video apps per day" budget.
    }

    private static func selectionURL() -> URL {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: "group.com.vn.doomscrollguard")!
            .appendingPathComponent("selection.json")
    }
}

extension ManagedSettingsStore.Name {
    static let doomscrollGuard = Self("doomscrollguard")
}
