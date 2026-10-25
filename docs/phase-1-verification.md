# Phase 1 検証記録

検証日: 2026-10-25（日本時間）。Windows / JDK 21。
既存の `dev.trickuild`、Kotlin DSL、Gradle 9.3.1 Wrapper、Coreモジュール、診断タスクを維持して拡張しました。
Daemon設定は、着手時のJDK 25指定を要件のJDK 21へ揃えています。

## 実装と設計判断

- CoreはVersion Parser、内容SHA-256、Build Number、Personality Rouletteを担当する純粋なKotlin/JDKモジュールです。実行時依存はKotlin標準ライブラリとそのannotationsのみです。
- Android連携は `android-adapter` へ分離。AGP 8.9.1の公開APIでコンパイルし、利用側のAGPで実行します。AGPの自動適用や内部タスクの書き換えは行いません。
- Androidには公式Sources APIで生成Javaクラス `TrickuildMetadata` を接続します。BuildConfigの追加フィールドへの実行時刻注入を避け、既存参照からの置換をREADMEに示しました。
- `versionCode` は変更しません。明示したversionNameはAndroid出力へ適用し、省略時は既存のvariantの値を使用します。`VERSION_NAME` 環境変数の利用はProviderで明示します。
- 時刻は生成タスク実行時にBuild Serviceで一度だけ取得します。同一Gradleビルドの複数variant・並列プロジェクトで共有し、Configuration Cacheへ時刻を保存しません。
- 実行時刻を優先するため、buildNumber有効時の生成タスクは毎回実行し、Build Cacheによる値の復元を無効にしました。生成Javaの実行時刻コメントにより再コンパイル負担があります。番号は分単位なので、同じ分・同じソースでは同じ番号になります。後段がUP-TO-DATEになっても、成果物と生成番号は一致します。
- buildNumber無効のRoulette専用モードは時刻を含まず、生成・コンパイル・JARをUP-TO-DATEにできます。Roulette無効時にnamespace等は必須にしません。
- ハッシュ対象の選択はGradle側のFileTreeに任せ、Coreは渡されたファイルの相対パスと生バイト列を処理します。VCSの標準除外を独自のファイル走査で再実装しません。

## テスト結果

| 検証 | 結果 |
| --- | --- |
| `clean build`、最終 `build`、`validatePlugins` | 成功 |
| Core単体テスト | 13件、失敗0 |
| Gradle側単体・JVM TestKitテスト | 9件、失敗0 |
| 実際の4アプリの内容ハッシュ比較 | 1テスト内で4アプリすべて一致 |
| Android TestKit / AGP 9.1.1 / Gradle 9.3.1 | 成功 |
| Android TestKit / AGP 9.4.1 / Gradle 9.6.0 | 成功 |
| `publishToMavenLocal` | 実装・Core・アダプター・Plugin Markerすべて成功 |
| Maven LocalからJVM consumerを実行 | 成功。Rouletteも有効にしてseed `GlyphBarty:2.2` / #4を確認 |
| Maven LocalからAndroid consumerをビルド | Kotlin/Java混在APKの生成成功 |
| included buildからJVM consumerを実行 | 成功 |
| `VERSION_NAME=2.10.1-Beta2` による実APKビルド | 成功。APKメタデータと `BuildConfig.VERSION_NAME` が一致 |

独立Android consumerはAPI 36 / Build Tools 36、AGP内蔵Kotlinを使用しました。
Gradle 9.6.0は互換性検証用consumerだけで使用し、Trickuild本体のWrapperは9.3.1です。
実アプリ自身のビルドは行っていません。

Android TestKitは、同じテストを上記2環境で実行し、以下を確認しています。

- `help` / 設定処理ではメタデータ未生成
- debug APKとunsigned release APKの生成、および両者の同一時刻・同一番号
- APK内DEXに生成番号が存在すること
- versionCode 42の維持、5桁ハッシュ、既知の性格抽選
- Configuration Cacheの再利用、新しいビルド実行Instant、ソース不変時のハッシュ不変
- APK削除後の再生成、ソース変更時のハッシュ変化とAPK内の値の整合性
- `2.10.1-Beta2` への上書きでprefix `v`、seed `GlyphBarty:2.10` / #5

Core/JVMテストは、不正バージョンの原因付き診断、2.1と2.10の区別、固定時刻・タイムゾーン、
異なる分のビルド番号、UTF-8パス・CRLFの生バイト列・NUL区切り、ハッシュ長、
mtimeに依存しないハッシュ、未コミット相当の編集、Git管理情報の除外、無効時の任意設定、
並列プロジェクトの時刻一致、Roulette専用モードのUP-TO-DATEを検証しています。

TestKitの注入クラスローダーではAGPも同じ注入クラスパスへ渡しています。
通常のMaven Local導入では、利用側のpluginsブロックで両プラグインを解決する経路を別途検証しました。
参照アプリの実FileTree一覧は検証用ファイル経由でテストへ渡し、Windowsのコマンド長上限を回避しています。

## 既存4アプリとの内容ハッシュ比較

参照元のGradle FileTreeを読み取り、既存スクリプトどおりのダイジェスト処理とCoreを比較しました。
パスはルート相対・`/`区切り・文字列昇順で、各ファイルを `UTF-8パス + NUL + 生バイト列 + NUL` として入力します。
以下は検証時点のスナップショットです。

| プロジェクト | 対象ファイル数 | 先頭5桁 |
| --- | ---: | --- |
| GlyphBarty (`glyphvisualizer`) | 194 | `84f30` |
| ShutUp | 146 | `e4fd5` |
| NITTC_Scheduler (`schedulernittc`) | 240 | `b8800` |
| BeastLocator | 133 | `5a582` |

完全なSHA-256と比較結果は `core/build/test-results/referenceCompatibilityTest/` に記録されます。
新しいファイルはTrickuild本体または独立consumerにのみ追加し、既存4アプリは読み取りのみです。

## 公開物と成果物

既定バージョン `0.1.0-SNAPSHOT` をMaven Localへ公開しました。
Marker `dev.trickuild:dev.trickuild.gradle.plugin` が実装 `dev.trickuild:trickuild-gradle-plugin` を参照し、
実装から `trickuild-core` / `trickuild-android-adapter` への推移依存も解決できています。
アダプターの公開POMにはAGP依存を含めず、CoreにもAGP/Gradle API依存を含めません。

実際に生成した独立consumer APK:
`consumers/android/app/build/outputs/apk/debug/app-debug.apk`。
SDKのaapt2でもversionCode 42を確認しました。

テストレポート、APK、ローカル検証用XMLはbuild配下の生成物で、Git管理対象外です。
再実行コマンド・導入DSL・BuildConfigからの移行例は [README](../README.md) を参照してください。

## 残る検証・対象外

- JitPackの実公開は未検証です。`jitpack.yml` と導入例は用意しましたが、GitHubへのPush後に座標・依存解決を検証する必要があります。
- 実際の4アプリへの組み込み、各アプリ固有の全ビルド構成、上記以外のAGP/Gradle組み合わせは未検証です。
- Android Libraryへの自動接続、OSSライセンス生成、APK命名、Release署名検証は今回実装していません。
- Release機能テストのAPKは署名なしです。配布用署名を検証した結果ではありません。

コミット・Pushは行っていません。
