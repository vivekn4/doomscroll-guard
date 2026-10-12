import SwiftUI
import WebKit

/// The stripped in-app browser. Each tab is a WKWebView loading the mobile
/// site with `shorts-stripper.js` injected at document start. No entitlement
/// needed — this is the layer that works on day one.
///
/// Navigations outside the tab's allowed hosts open in Safari (so the wrapper
/// can't become a general-purpose browser by accident).
struct WrapperView: View {
    let tabs: [WrapperTab]
    @State private var selected: WrapperTab?

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if !tabs.isEmpty {
                    Picker("Service", selection: Binding(
                        get: { selected ?? tabs[0] },
                        set: { selected = $0 }
                    )) {
                        ForEach(tabs) { tab in Text(tab.label).tag(tab as WrapperTab?) }
                    }
                    .pickerStyle(.segmented)
                    .padding()
                }
                if let tab = (selected ?? tabs.first) {
                    StrippedWebView(tab: tab)
                        .edgesIgnoringSafeArea(.bottom)
                } else {
                    Text("Enable YouTube or Instagram above to browse stripped.")
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .navigationTitle("Stripped Browser")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear { if selected == nil { selected = tabs.first } }
    }
}

struct StrippedWebView: UIViewRepresentable {
    let tab: WrapperTab

    func makeCoordinator() -> Coordinator { Coordinator(tab: tab) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default() // shares the user's Safari cookies
        // Inject the stripper at document start, main frame only.
        if let url = Bundle.main.url(forResource: "shorts-stripper", withExtension: "js"),
           let js = try? String(contentsOf: url) {
            let script = WKUserScript(source: js,
                                     injectionTime: .atDocumentStart,
                                     forMainFrameOnly: true)
            config.userContentController.addUserScript(script)
        }
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.load(URLRequest(url: tab.startURL))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let tab: WrapperTab
        init(tab: WrapperTab) { self.tab = tab }

        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let host = navigationAction.request.url?.host?.lowercased() else {
                decisionHandler(.cancel); return
            }
            let allowed = tab.allowedHosts
            if allowed.contains(where: { host == $0 || host.hasSuffix("." + $0) }) {
                decisionHandler(.allow)
            } else {
                // External link — hand to Safari, keep the wrapper fenced in.
                if let url = navigationAction.request.url {
                    UIApplication.shared.open(url)
                }
                decisionHandler(.cancel)
            }
        }
    }
}

extension WrapperTab {
    var allowedHosts: [String] {
        switch self {
        case .youtube:
            return ["m.youtube.com", "www.youtube.com", "youtube.com",
                    "youtu.be", "googlevideo.com", "ytimg.com"]
        case .instagram:
            return ["www.instagram.com", "instagram.com",
                    "cdninstagram.com", "fbcdn.net"]
        }
    }
}
