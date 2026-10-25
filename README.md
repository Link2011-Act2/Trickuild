# Trickuild

Kotlinで実装する独立したGradleプラグインです。AndroidアプリやAndroid Libraryではありません。
現段階は、設定DSL・診断タスク・テストを備えた最小の開発基盤です。

## 開発環境

- JDK 21（`JAVA_HOME`をJDKのインストール先に設定）
- Gradle 9.3.1（同梱のGradle Wrapperを使用）
- 初回はGradleおよびMaven Central / Gradle Plugin Portalの依存取得にネットワーク接続が必要です。
- Android SDK、Android Studio、`local.properties`は不要です。

```powershell
.\gradlew.bat clean build
```

macOS / Linuxでは `./gradlew clean build` を実行してください。
このコマンドでCoreの単体テスト、プラグインの単体テスト、Gradle TestKitの機能テスト、
プラグイン定義の検証（`validatePlugins`）とJAR生成を行います。

```powershell
.\gradlew.bat test :core:test
.\gradlew.bat validatePlugins
```

テストレポートは `build/reports/tests/test/index.html` と
`core/build/reports/tests/test/index.html` に生成されます。

## ローカルでの利用

利用側の `settings.gradle.kts` に、このフォルダをincluded buildとして登録します。
以下のパスは実際の配置に合わせて変更してください。既存4アプリへの組み込みは未実施です。

```kotlin
pluginManagement {
    includeBuild("../trickuild")
}
```

利用側の `build.gradle.kts`:

```kotlin
plugins {
    id("dev.trickuild")
}

trickuild {
    enabled.set(true)
}
```

プラグインIDは `dev.trickuild`、拡張DSL名は `trickuild` です。
`enabled` は既定値 `true` の `Property<Boolean>` です。
現段階では設定値の確認のみを行い、ビルドやAndroid設定の変更は行いません。

利用側で `./gradlew trickuildInfo`（Windowsでは `.\gradlew.bat trickuildInfo`）を実行すると
`Trickuild enabled: true` を表示します。`enabled.set(false)` を設定すると `false` を表示します。
診断タスク自体は無効化されません。Configuration Cacheにも対応しています。

## 構成と依存境界

```text
Trickuild/
  build.gradle.kts          # java-gradle-plugin / kotlin-dsl、プラグイン定義
  settings.gradle.kts
  gradlew / gradlew.bat
  gradle/wrapper/           # Wrapper JAR、バージョン・配布物SHA-256
  src/main/kotlin/          # Gradleアダプター: Plugin / Extension / Task
  src/test/kotlin/          # ProjectBuilder単体テスト、TestKit機能テスト
  core/
    build.gradle.kts       # Kotlin/JVMのみ
    src/main/kotlin/       # Gradle API・Android APIに依存しない設定モデル
    src/test/kotlin/       # Core単体テスト
```

依存方向は **Gradleプラグイン → Core** です。Coreの本体の依存はKotlin標準ライブラリのみで、
Gradle API・Android SDK・Android Gradle Plugin（AGP）は参照しません。
KotlinのコンパイラーはWrapperに対応する組み込みKotlinのバージョンに揃えています。

将来AGPとの連携が必要になった場合は、AGP APIを扱う専用のアダプターモジュールを追加し、
`pluginManager.withPlugin("com.android.application")` / `withPlugin("com.android.library")` を起点に
連携を有効にする方針です。CoreにAndroid固有の型やSDK探索処理を持ち込まず、
AGPのバージョン互換性はアダプター側で検証します。現段階ではAGP連携は未実装です。

動作検証の基準はGradle 9.3.1 / JDK 21です。他バージョンの互換性は今後検証します。
Plugin Portalへの公開設定は未導入で、外部公開は行いません。

参照対象は `beastlocator`、`glyphvisualizer`、`schedulernittc`、`shutup` の4アプリです。
この初期構築では各アプリのファイルを変更しません。

## 参考

- [Gradle Kotlin DSL](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
