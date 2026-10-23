# meshcore-kotlin

Kotlin code for talking to a [MeshCore](https://docs.meshcore.io/) companion radio, shared by the [Colorado Mesh](https://coloradomesh.org/) Android apps:

- [MeshCore TAK Bridge](https://github.com/Colorado-Mesh/meshcore-tak-bridge), which presents the mesh to ATAK on a phone.
- [WearMesh](https://github.com/Colorado-Mesh/wearmesh), MeshCore messaging on a Wear OS watch with no phone.

## Modules

| Module | Package | What it holds |
| --- | --- | --- |
| `protocol` | `org.coloradomesh.meshcore.protocol` | The companion protocol in pure Kotlin, with no Android dependencies: the frame codec (`Companion.kt`), the command queue that runs a session with a radio (`CompanionClient.kt`), telemetry (`CayenneLpp.kt`), battery estimates (`RadioBattery.kt`), `@[Name]` replies (`ChannelReplies.kt`) and MECP emergency messages (`Mecp.kt`). Unit tests run on the JVM. |
| `ble` | `org.coloradomesh.meshcore.ble` | The Bluetooth LE side, as an Android library: `GattLink` (one connection, every step queued and time-limited, retries, PIN pairing), `RadioScanner`, and `RadioSession`, which joins a link to a protocol client. |

The code only reads from a radio, sends text, and asks tracked nodes for their position. Nothing in it changes a radio's settings, channels, contacts, paths or keys.

## Using it from an app

Each app includes this repository as a git submodule at its root and builds it from source:

```bash
git submodule add https://github.com/Colorado-Mesh/meshcore-kotlin.git meshcore-kotlin
```

In the app's `settings.gradle.kts`:

```kotlin
includeBuild("meshcore-kotlin")
```

And in the module that uses it:

```kotlin
dependencies {
    implementation("org.coloradomesh.meshcore:ble")       // brings in protocol too
    // or, for the protocol alone:
    implementation("org.coloradomesh.meshcore:protocol")
}
```

Gradle builds the modules here as part of the app's build, so a change in the submodule shows up in the app straight away. Keep this build's Android Gradle Plugin and Kotlin versions (`gradle/libs.versions.toml`) the same as the apps'.

To move an app to a newer version of this code: `git -C meshcore-kotlin pull origin main`, then commit the submodule change in the app. After cloning an app, run `git submodule update --init`, or clone with `--recurse-submodules`.

## Build and test

```bash
./gradlew :protocol:test :ble:assembleDebug
```

Needs JDK 17 and the Android SDK (compile SDK 34).

## Changes

A change here reaches both apps, so test it in both before merging: point each app's submodule at the branch, build, and try it on a device when Bluetooth or protocol behaviour changes. Anything learned about a radio or a phone or watch goes in the code comments, where the next person will see it.

## History

The code began in MeshCore TAK Bridge (`protocol/` and `app/.../ble/`, up to commit `33baf18`) and moved here in October 2026, when WearMesh needed it too. One change came with the move: `CompanionClient` takes the name the app gives the radio (`appName`).

## License

GPL-3.0, the same as both apps. See [LICENSE](LICENSE).
