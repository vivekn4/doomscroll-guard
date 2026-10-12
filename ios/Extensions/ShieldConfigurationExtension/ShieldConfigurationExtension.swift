import ManagedSettingsUI
import UIKit

/// Styles the system shield screen shown when a shielded app is opened.
/// Target: ShieldConfigurationExtension (com.apple.developer.family-controls).
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        ShieldConfiguration(
            icon: UIImage(systemName: "shield.fill"),
            title: ShieldConfiguration.Label(text: "Guarded by Doomscroll Guard", color: .label),
            subtitle: ShieldConfiguration.Label(
                text: "You turned short videos off. The feed will wait.",
                color: .secondaryLabel),
            primaryButtonLabel: ShieldConfiguration.Label(text: "OK", color: .white),
            primaryButtonBackgroundColor: .systemGreen,
            secondaryButtonLabel: ShieldConfiguration.Label(text: "Open Doomscroll Guard", color: .label)
        )
    }

    override func configuration(shielding application: Application,
                                in category: ActivityCategory) -> ShieldConfiguration {
        configuration(shielding: application)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        ShieldConfiguration(
            icon: UIImage(systemName: "shield.fill"),
            title: ShieldConfiguration.Label(text: "Guarded", color: .label),
            subtitle: ShieldConfiguration.Label(
                text: "This site is blocked while the Guard is on.",
                color: .secondaryLabel),
            primaryButtonLabel: ShieldConfiguration.Label(text: "OK", color: .white),
            primaryButtonBackgroundColor: .systemGreen
        )
    }

    override func configuration(shielding webDomain: WebDomain,
                                in category: ActivityCategory) -> ShieldConfiguration {
        configuration(shielding: webDomain)
    }
}
