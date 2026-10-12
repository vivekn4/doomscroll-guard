import SwiftUI

/// Main screen: the one big switch, per-app rows in plain language,
/// and an honest explainer of what iOS can and can't do.
struct ContentView: View {
    @EnvironmentObject var model: GuardViewModel
    @State private var showWrapper = false
    @State private var showShieldSetup = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // The giant switch.
                    VStack(spacing: 8) {
                        Text("🛡️ Doomscroll Guard")
                            .font(.largeTitle).bold()
                        Text(model.masterOn
                             ? "Guard is ON — Shorts & Reels blocked"
                             : "Guard is OFF — everything allowed")
                            .foregroundColor(.secondary)
                        Button(action: { model.masterOn.toggle() }) {
                            Text(model.masterOn ? "✓ GUARD ON" : "○ GUARD OFF")
                                .font(.title2).bold()
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 28)
                                .background(model.masterOn ? Color.green : Color(.systemGray5))
                                .foregroundColor(model.masterOn ? .black : .primary)
                                .cornerRadius(24)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8)
                    }
                    .padding(.top, 12)

                    // Plain-language per-app rows.
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Block short videos in:").bold()
                        ToggleRow(title: "YouTube Shorts",
                                  sub: "Stripped in-app browser — no Shorts tab, no shelves",
                                  isOn: $model.youtubeOn)
                        ToggleRow(title: "Instagram Reels",
                                  sub: "Stripped in-app browser — no Reels tab",
                                  isOn: $model.instagramOn)
                        ToggleRow(title: "TikTok",
                                  sub: "Locks the TikTok app itself — it's all short video",
                                  isOn: $model.tiktokShield)
                    }

                    // Wrapper entry.
                    if !model.enabledTabs.isEmpty {
                        Button("Open the stripped browser →") { showWrapper = true }
                            .buttonStyle(.borderedProminent)
                    }

                    // Shield setup (entitlement-gated, degrades gracefully).
                    shieldSection

                    // Honest explainer.
                    VStack(alignment: .leading, spacing: 8) {
                        Text("What this app can and can't do on iPhone").bold()
                        Text("• ✅ Removes Shorts/Reels inside its own built-in browser.")
                        Text("• ✅ Can lock the native YouTube / Instagram / TikTok apps entirely (needs Apple's approval — see below).")
                        Text("• ❌ Cannot remove the Reels tab from inside the Instagram app or the Shorts tab from inside the YouTube app. Apple gives no app that power.")
                        Text("• Nothing leaves your device. ✦ VN ✦")
                    }
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(16)
                }
                .padding()
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showWrapper) {
                WrapperView(tabs: model.enabledTabs)
            }
            .sheet(isPresented: $showShieldSetup) {
                ShieldSetupView(onDone: { showShieldSetup = false })
                    .environmentObject(model)
            }
        }
    }

    @ViewBuilder
    private var shieldSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Native-app lock (Screen Time)").bold()
            switch model.shieldState {
            case .unknown:
                Text("Checking…").font(.footnote).foregroundColor(.secondary)
            case .unavailable(let reason):
                Text(reason).font(.footnote).foregroundColor(.secondary)
            case .denied:
                Text("Screen Time permission was denied. The stripped browser above still works.")
                    .font(.footnote).foregroundColor(.secondary)
            case .ready:
                Button("Set up app lock") { showShieldSetup = true }
                    .buttonStyle(.bordered)
            }
            if let err = model.lastError {
                Text(err).font(.footnote).foregroundColor(.red)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(16)
    }
}

struct ToggleRow: View {
    let title: String
    let sub: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading) {
                Text(title)
                Text(sub).font(.caption).foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}

struct ShieldSetupView: View {
    @EnvironmentObject var model: GuardViewModel
    let onDone: () -> Void
    @State private var working = false

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                Text("🔒 Lock the native apps")
                    .font(.title2).bold()
                Text("This uses Apple's Screen Time to grey out the YouTube, Instagram and TikTok apps while the Guard is on — so the stripped browser becomes the way in.\n\nYou'll pick the apps in Apple's own picker on the next screen. Nothing is sent anywhere; the choice stays on your phone.")
                    .font(.body).foregroundColor(.secondary)
                Button(working ? "Waiting for Apple…" : "Authorize Screen Time") {
                    working = true
                    Task {
                        _ = await model.requestShieldAuthorization()
                        working = false
                        onDone()
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(working)
                Spacer()
            }
            .padding()
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
