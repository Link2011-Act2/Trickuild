# Trickuild

Kotlinで実装した独立したGradleプラグインです。プラグインIDは `dev.trickuild`。
Androidアプリと通常のJava/Kotlin JVMプロジェクトへ、ビルドメタデータのJavaクラスを生成します。
AndroidアプリやAndroid Libraryを作るプロジェクトではありません。

## Phase 1の機能

- `Major.Minor[.Patch][-channel][+build]` の解析。整数文字列として `2.1` と `2.10` を区別
- 任意のコードネーム（省略時はフォールバック）、実行時刻、Gitに依存しない内容ハッシュによるビルド番号
- 既定で無効の決定的な性格抽選
- Android公式Variant/Sources APIによるJavaソース生成。versionCodeは変更しない
- Configuration Cache、並列プロジェクト、複数variantに対応
- Maven Localへの実装JAR・Plugin Marker・依存モジュールの公開

## 開発と検証

JDK 21とGradle 9.3.1を使用します。同梱WrapperとDaemon設定を使用してください。
Coreと通常のプラグインテストにAndroid SDKは不要です。初回の依存取得にはネットワーク接続が必要です。

```powershell
.\gradlew.bat clean build
.\gradlew.bat :core:referenceCompatibilityTest '-Ptrickuild.referenceRoot=D:/apps'
.\gradlew.bat androidFunctionalTest '-Ptrickuild.androidSdk=C:/Users/you/AppData/Local/Android/Sdk'
```

macOS/Linuxでは `bash ./gradlew` に読み替えてください。
`build` はCore単体テスト、JVM TestKit機能テスト、`validatePlugins`、JAR生成を含みます。
`referenceCompatibilityTest` は4アプリを読み取る任意の検証で、書き込みは行いません。
`androidFunctionalTest` は独立consumerを一時ディレクトリへ複製し、debug APKとunsigned release APKを生成します。
API 36とBuild Tools 36をインストールしたSDKが必要です。SDKは `ANDROID_HOME` / `ANDROID_SDK_ROOT` でも指定できます。
本番用署名の検証はPhase 1の対象外です。

レポートは `build/reports/tests/test/`、`core/build/reports/tests/test/`、
`core/build/reports/tests/referenceCompatibilityTest/`、`build/reports/tests/androidFunctionalTest/` に生成されます。
Android consumerはAGP内蔵KotlinとJavaを併用し、生成Java定数のKotlin参照も検証します。
任意で `-Ptrickuild.testKitDir=/path/to/persistent/cache` を指定するとTestKitのキャッシュを継続利用できます。
追加のAGP/Gradle組み合わせは `-Ptrickuild.androidAgp=...` と
`-Ptrickuild.androidGradleHome=/path/to/unpacked/gradle` で検証できます。

## Kotlin DSLでの導入

開発中は、利用側の `settings.gradle.kts` にincluded buildを追加します。

```kotlin
pluginManagement {
    includeBuild("../Trickuild") // 実際の配置に合わせる
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
```

Androidアプリの `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") // バージョンは利用側で管理
    id("dev.trickuild")
}

android {
    namespace = "example.app"
    defaultConfig {
        versionCode = 42 // Trickuildは変更しない
        versionName = "2.2.0-IntDev_rev0"
    }
}

trickuild {
    codename.set("Epica")
    // 省略時はAndroidのvariantのversionNameを使用する。
    // 設定時はAPKのversionNameにも同じ値を適用する。
    versionName.set(providers.environmentVariable("VERSION_NAME").orElse("2.2.0-IntDev_rev0"))
    buildNumber {
        enabled.set(true)
        hashLength.set(5)
        timestampSource.set("build")
        timeZone.set("Asia/Tokyo")
    }
    roulette {
        enabled.set(true)
        namespace.set("GlyphBarty")
    }
}
```

`VERSION_NAME` 環境変数の利用は上記のようにProviderで明示的に設定します。
Trickuild自体は環境変数による暗黙の上書きを行いません。
フレーバーごとのversionNameを保持したい場合は `trickuild.versionName` を設定しないでください。

通常のJVMプロジェクトでは `java` / `application` / `org.jetbrains.kotlin.jvm` と併用し、
`trickuild.versionName` を指定します。生成ソースはJavaのmain source setへ自動接続されます。
SDKやAGPの適用は不要です。動作例は `consumers/jvm` と `consumers/android` にあります。

```powershell
.\gradlew.bat -p consumers/jvm run --configuration-cache
# SDKを環境変数かconsumers/android/local.propertiesに設定してから実行
.\gradlew.bat -p consumers/android :app:assembleDebug --configuration-cache
```

## Codenameの設定

`codename` が未設定・空文字・空白のみの場合、既定のフォールバック名 `Youngchun` を使用します。
`fallbackCodename` で自由に変更でき、空白以外を含む `codename` の明示指定を優先します。
トリッカル関連の命名を使う必要はありません。

```kotlin
trickuild {
    versionName.set("1.0.0")
    // codename未指定・空文字・空白のみの場合はfallbackCodenameを使用します。
    fallbackCodename.set("Unnamed") // この行も省略するとYoungchun
    // codename.set("Epica") // 指定するとfallbackCodenameより優先
}
```

この例の実効コードネームは `Unnamed` です。設定値そのものは書き換えず、
ビルド番号、生成クラスの `CODE_NAME`、診断タスク `trickuildInfo` に同じ実効値を使用します。
`fallbackCodename` に空文字・空白のみ（または制御文字）を明示すると、設定エラーになります。
Gradle Propertyの `set(null as String?)` は明示値を解除し、既定値があればそれを使用します。
値のないProviderを設定した場合も、未設定として安全にフォールバックします。

Personality Rouletteは独立したオプション機能で、既定では無効です。
コードネームやフォールバックを変えても、抽選の有効・無効や抽選結果は変わりません。

## 生成クラスとBuildConfigからの移行

Androidでは `${android.namespace}.TrickuildMetadata`、JVMでは
`dev.trickuild.generated.TrickuildMetadata` を生成します。
`metadataPackage` と `metadataClassName` で変更できます。

```kotlin
import example.app.TrickuildMetadata

val buildNumber = TrickuildMetadata.BUILD_NUMBER
val codename = TrickuildMetadata.CODE_NAME
// roulette.enabled=true の場合のみ生成
val seed = TrickuildMetadata.PERSONALITY_SEED
val number = TrickuildMetadata.PERSONALITY_NUMBER
```

`BUILD_NUMBER`、`CODE_NAME`、`PERSONALITY_SEED` は `public static final String`、
`PERSONALITY_NUMBER` は `public static final int` です。
`BUILD_NUMBER` はbuildNumber有効時、性格フィールドはRoulette有効時に生成します。
`CODE_NAME` は常に実効コードネームを生成し、ビルド番号やRouletteの無効時にもフォールバックを適用します。

既存コードの `BuildConfig.BUILD_NUMBER` 等を `TrickuildMetadata.BUILD_NUMBER` 等に変更してください。
`BuildConfig.VERSION_NAME` / `VERSION_CODE` 等はそのまま使用できます。
既存スクリプトの同名 `buildConfigField` と独自ビルド番号計算は、利用側で移行するときに除去してください。
既存4アプリへの変更はこのリポジトリでは行いません。

BuildConfigの設定フェーズの値へ実行時刻を混入させず、AGP内部タスクの書き換えも行わないため、
Phase 1ではBuildConfigの同名フィールドを自動追加しません。
生成タスクを公式 `variant.sources.java.addGeneratedSourceDirectory` で接続します。
実行時に読むAndroid側の追加ライブラリ依存はありません。

## ビルド番号と内容ハッシュ

```text
<Codename>-<Prefix><NumericVersion>-<yyMMdd>-<HHmm>-<Hash>
Epica-d2.2.0-261025-1920-a3f2b
```

- Codenameは明示指定を優先し、未設定・blankなら `fallbackCodename`（既定: `Youngchun`）。抽選から選択しない
- `IntDev` で始まるチャンネルは大文字小文字を問わず `d`、その他は `v`
- 数値バージョン部分だけを使用し、チャンネルと `+build` を除外
- 日時は実際にメタデータ生成タスクを実行した時刻。既定のタイムゾーンは設定時のシステムゾーン
- ハッシュはSHA-256の先頭5桁。`hashLength` は1〜64。性格抽選結果はビルド番号に含めない

既存4アプリの内容ハッシュ入力を維持しています。
対象は適用モジュールの `src` 以下の全ファイル（`**/build/**` を除外、テスト・リソースも含む）と、
存在するモジュールの `build.gradle.kts`、ルートの `build.gradle.kts`、`settings.gradle.kts`、
`gradle/libs.versions.toml` です。重複ファイルは一度だけ扱います。
Gradle FileTree標準のVCS管理ディレクトリ等の除外も適用します。
ルート相対パスを `/` 区切りで文字列昇順に並べ、各ファイルについて
`UTF-8の相対パス + NUL + ファイルの生バイト列 + NUL` をSHA-256へ渡します。
Git、コミット、mtimeは使用せず、未コミット変更・パス変更も反映します。
従来の3桁短縮から既定5桁へ変更しています。

`buildNumber.sourceFiles.from(...)` で対象を追加、`setFrom(...)` で置き換えられます。
対象はハッシュのルート（利用側rootProject）配下に置いてください。
Groovyビルドスクリプト等を使用する場合は明示的に追加してください。

### 実行時刻・Configuration Cache・増分ビルド

Sync、`help`、設定フェーズでは日時を確定せず、ファイルも生成しません。
生成クラスをコンパイルする成果物タスクから生成タスクへ依存関係を接続します。
最初の生成タスクの実行時にBuild Serviceが `Instant.now()` を一度だけ取得し、
同一Gradleビルドの複数variant・複数プロジェクトで共有します。
Configuration Cacheの再利用でもServiceは新しいインスタンスとなり、前回の時刻を持ち越しません。

**実際の実行時刻を優先するため、buildNumber有効時の生成タスクは常に実行し、
UP-TO-DATE判定・Build Cacheからの復元を使用しません。**
生成Javaには実行Instantのコメントも記録するため、ソース再コンパイルの負担があります。
秒以下の時刻が異なっても表示番号は分単位です。同じ分・同じ内容なら同じ番号になります。
その場合、同一バイトコードを入力とする後段タスクがUP-TO-DATEとなることはありますが、
APKに入る番号と生成クラスの番号は一致します。APKだけ削除した再ビルドでも生成処理は実行します。

buildNumberを無効にしたRoulette専用モードは日時を含まず、
入力不変なら生成タスク・JVMコンパイル・JAR生成をUP-TO-DATEにできます。
`--rerun-tasks` / `clean` では再生成しますが、抽選結果は変わりません。
検査用の `metadata.properties` は `build/trickuild/<生成タスク名>/` に出力します。
`buildInstant` はビルド番号有効時の正確なUTC時刻、`contentHash` は短縮内容ハッシュです。

## Version Parserと性格抽選

`2.2.0-IntDev_rev0`、`2.2.1-Beta2`、`3.0.0-RC1`、`4.3.0`、`2.10`、
`2.2.9+build.123` を扱います。パッチ省略を保持し、`2.10` を小数へ変換しません。
Coreの `ParsedVersion` は数値部分、チャンネル、完全なqualifier、チャンネル以降のsuffix、build metadataを分けます。
未知の英数字チャンネルは `OTHER` として保持し、prefixは `v` です。
空値、空接尾辞、余分な成分、負数、数値以外の成分、数値の先頭ゼロ、空白などは原因付きエラーにします。

Rouletteは既定で無効です。有効時のみnamespaceとversionNameを必要とし、
`<Namespace>:<Major>.<Minor>` をUTF-8でSHA-256に渡し、先頭バイトを符号なしとして
`(firstByte % 5) + 1` を求めます。パッチ・チャンネル・build metadataは結果に影響しません。

| 番号 | 英語 | 日本語 |
| --- | --- | --- |
| 1 | Innocent | 純粋 |
| 2 | Mad | 狂気 |
| 3 | Composed | 冷静 |
| 4 | Vivacious | 活発 |
| 5 | Depressed | 憂鬱 |

`GlyphBarty:2.2` → #4、`GlyphBarty:2.10` → #5、`GlyphBarty:4.3` → #5。
`buildNumber.enabled=false` ならコードネームを省略でき、
さらにRouletteも無効ならversionNameやnamespaceも不要です。
既存の `trickuild.enabled` と `trickuildInfo` も維持しています。

## モジュールと依存方向

```text
Gradleプラグイン（ルート: Plugin / DSL / Task / Build Service / Java生成）
  ├─ core（VersionParser / SourceContentHash / BuildNumberGenerator / PersonalityRoulette）
  └─ android-adapter（AGP公開APIへの接続のみ、AGPはcompileOnly）
consumers/jvm、consumers/android（独立検証用。ルートビルドのサブプロジェクトではない）
```

CoreはKotlin/JDKだけに依存し、Gradle API・AGP・Android SDKを参照しません。
AndroidアダプターはAGP 8.9.1の公開APIでコンパイルし、利用側のAGPを実行時に使用します。
AGPを自動適用せず、`com.android.application` があるときだけ連携クラスを読み込みます。
Phase 1の自動Android連携はアプリ用です。Android Library用のvariant接続は未実装です。

OSSライセンス、APK命名、Release署名検証は未実装です。
それらのプラットフォーム処理はGradle/Android側に追加し、Coreの純粋な計算APIを維持する方針です。

## Maven LocalとJitPack

```powershell
.\gradlew.bat publishToMavenLocal
.\gradlew.bat -p consumers/jvm run '-Ptrickuild.published=true' --configuration-cache
.\gradlew.bat -p consumers/android :app:assembleDebug '-Ptrickuild.published=true' --configuration-cache
```

既定の公開座標は次のとおりです。Source JARも公開します。

| 内容 | 座標 |
| --- | --- |
| 実装 | `dev.trickuild:trickuild-gradle-plugin:0.1.0-SNAPSHOT` |
| Core | `dev.trickuild:trickuild-core:0.1.0-SNAPSHOT` |
| Androidアダプター | `dev.trickuild:trickuild-android-adapter:0.1.0-SNAPSHOT` |
| Plugin Marker | `dev.trickuild:dev.trickuild.gradle.plugin:0.1.0-SNAPSHOT` |

利用側 `settings.gradle.kts` に `pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }`
を設定すれば、`plugins { id("dev.trickuild") version "0.1.0-SNAPSHOT" }` で導入できます。
公開済みJARから利用する場合はCore/アダプターも推移依存として解決され、included buildは不要です。

## JitPackからの導入

2026-10-27に、公開済みコミット `c6305ca29f2e8e15a50348f64d4bbe077065cbf0` をJitPackでビルドし、
POM・JARと、Maven Localやincluded buildを使用しない独立consumerからの利用を検証しました。
JitPackのバージョンには、この確認済みコミットSHAを指定します。

利用側の `settings.gradle.kts`:

```kotlin
pluginManagement {
    val trickuildVersion = "c6305ca29f2e8e15a50348f64d4bbe077065cbf0"
    repositories {
        google() // Androidを使わない場合は不要
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-gradle-plugin")
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-core")
                includeModule("com.github.Link2011-Act2.Trickuild", "trickuild-android-adapter")
            }
            metadataSources {
                mavenPom()
                ignoreGradleMetadataRedirection()
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.trickuild") {
                useModule("com.github.Link2011-Act2.Trickuild:trickuild-gradle-plugin:${requested.version ?: trickuildVersion}")
            }
        }
    }
    plugins { id("dev.trickuild") version trickuildVersion }
}
```

利用側の `build.gradle.kts` では `plugins { id("dev.trickuild") }` と指定し、
上記「Kotlin DSLでの導入」と同じ `trickuild { ... }` を設定します。
バージョンを利用側のpluginsブロックで指定する場合は
`id("dev.trickuild") version "c6305ca29f2e8e15a50348f64d4bbe077065cbf0"` とも書けます。
プラグインの依存は `pluginManagement.repositories` から解決されるため、
`dependencyResolutionManagement.repositories` への追加だけでは不十分です。

`V` を上記コミットSHAとした、実際にJitPackから取得できた座標は次のとおりです。

| 内容 | JitPack座標 |
| --- | --- |
| 実装 | `com.github.Link2011-Act2.Trickuild:trickuild-gradle-plugin:V` |
| Core | `com.github.Link2011-Act2.Trickuild:trickuild-core:V` |
| Androidアダプター | `com.github.Link2011-Act2.Trickuild:trickuild-android-adapter:V` |
| Plugin MarkerのPOM | `com.github.Link2011-Act2.Trickuild:dev.trickuild.gradle.plugin:V` |

実装POMは同じ `V` のCoreとAndroidアダプターに依存します。MarkerはPOMであり、実装モジュールを参照します。
JitPack上ではMarkerのグループも上記へ移されるため、Gradleが標準で探す
`dev.trickuild:dev.trickuild.gradle.plugin:V` は見つかりません。
上の `resolutionStrategy` はIDを実装モジュールへ対応付けます。
移動後のMarkerモジュールへ対応付ける方法も独立JVM consumerで確認しました。

今回のJitPack `.module` は各モジュールの `component` を集約座標
`com.github.Link2011-Act2:Trickuild:V` と記載し、`sourcesElements` のURLには通常のJAR名を記載しています。
実装・Core・アダプターのPOMと直接の `-sources.jar` はそれぞれ取得できました。
Gradle 9.3.1は通常のmetadata設定でもJVM consumerを実行できましたが、上の例はこの不整合を避け、
POMから推移依存を解決する設定です。POM優先はこの3モジュールだけに限定し、他ライブラリのmetadata解決を変更しません。Gradleの仕様は [metadata sources](https://docs.gradle.org/current/userguide/supported_metadata_formats.html) を参照してください。
同じJitPackリポジトリから他のライブラリも取得する場合は、この宣言の後に通常のmetadata設定のリポジトリ宣言を追加してください。
sources JARは `sources` classifierを指定して直接取得できますが、問題のある `.module` のsources variantを使うと通常のバイナリJARが選ばれます。

ソースの既定バージョン `0.1.0-SNAPSHOT` はMaven Local用です。`jitpack.yml` はJDK 21を選び、
JitPackから渡される `$VERSION` で公開バージョンを上書きします。タグ指定もJitPackの仕組み上は可能ですが、
この検証ではタグを作成せず、上記コミットSHAだけを実際に検証しています。
独立検証用プロジェクトと結果は [Phase 1.5検証記録](docs/phase-1.5-jitpack-verification.md) を参照してください。
Markerとmetadataの原因調査、および回避策の適用範囲は [Phase 1.6検証記録](docs/phase-1.6-publication-verification.md) に記載しています。

検証環境・結果・設計判断は [Phase 1検証記録](docs/phase-1-verification.md) にまとめています。

## 参考仕様

読み取り参照元: `Link2011-Act2/GlyphBarty`、`ShutUp`、`NITTC_Scheduler`、`BeastLocator`。
それらのソース・ビルド設定は変更しません。

- [AGP公開APIと生成ソース](https://developer.android.com/build/extend-agp)
- [Gradle Build Services](https://docs.gradle.org/current/userguide/build_services.html)
- [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
- [JitPackの複数モジュール・ビルド設定](https://docs.jitpack.io/building/)
