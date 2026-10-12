import ManagedSettingsUI

/// Handles taps on the shield screen's buttons.
/// Target: ShieldActionExtension (com.apple.developer.family-controls).
///
/// ShieldActionResponse cases: .none / .close / .defer (plain enum, no payloads).
/// NOTE: `.openParentalControlsApp` exists only on iOS 26.5+; with a lower
/// deployment target the extension cannot open the host app (UIApplication is
/// forbidden in this sandbox), so the secondary button just dismisses.
final class ShieldActionExtension: ShieldActionDelegate {
    override func handle(action: ShieldAction, for application: Application,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        switch action {
        case .primaryButtonPressed:
            completionHandler(.close)
        case .secondaryButtonPressed:
            completionHandler(.close) // iOS 26.5+: could return .openParentalControlsApp
        @unknown default:
            completionHandler(.close)
        }
    }

    override func handle(action: ShieldAction, for webDomain: WebDomain,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(.close)
    }

    override func handle(action: ShieldAction, for category: ActivityCategory,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(.close)
    }
}
