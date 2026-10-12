import Foundation

/// Safari Content Blocker extension handler.
/// Serves blockerList.json (generated from shared/rules/rules.json — see the
/// header comment there; regenerate when rules change).
/// The user enables it in Settings → Safari → Extensions → Doomscroll Guard.
final class ContentBlockerRequestHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        let attachment = NSItemProvider(
            contentsOf: Bundle.main.url(forResource: "blockerList", withExtension: "json"))
        let item = NSExtensionItem()
        item.attachments = attachment.map { [$0] }
        context.completeRequest(returningItems: [item], completionHandler: nil)
    }
}
