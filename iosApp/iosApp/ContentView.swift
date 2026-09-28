import SwiftUI
import Shared

/// Hosts the shared Compose Multiplatform UI (shared/src/commonMain) — the same screens as Android.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose draws edge to edge and handles safe areas and the keyboard itself.
        ComposeView()
            .ignoresSafeArea()
    }
}
