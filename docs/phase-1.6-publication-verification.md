# Phase 1.6 配布検証記録

検証日: 2026-10-27（日本時間）。対象の公開済みHEADは `c6305ca29f2e8e15a50348f64d4bbe077065cbf0`、JDK 21、Gradle 9.3.1です。Phase 1.5の未コミット変更を維持して検証しています。ここでのREADMEと検証用consumerの変更はローカルのみで、JitPackに新たな公開物は作成していません。

## Markerの座標

- 同一コミット・同一バージョンを `publishToMavenLocal` で隔離したMavenリポジトリへ公開すると、Gradleが自動生成したMarkerは `dev.trickuild:dev.trickuild.gradle.plugin:V` になり、実装に依存します。対応付けなしの独立JVM consumerで実際に解決できました。
- [JitPackの複数モジュール仕様](https://docs.jitpack.io/building/#multi-module-projects)は個別モジュールのgroupを `com.github.User.Repo` とします。実際にJitPackのMarkerは `com.github.Link2011-Act2.Trickuild:dev.trickuild.gradle.plugin:V` で、標準検索先の `dev.trickuild` groupは404です。[Gradleの仕様](https://docs.gradle.org/current/userguide/plugins_intermediate.html#sec:plugin_markers)上、標準検索は `plugin.id:plugin.id.gradle.plugin:version` を使用します。
- 現在のJitPack配布方式では、Gradle側で正しく生成されたMarkerだけでは標準座標で配布されません。JitPackが公開する座標を変更できる設定は確認できていません。JitPackには[独自ドメイン設定](https://docs.jitpack.io/intro/#custom-domain-name)もありますが、DNSの管理・設定が必要で、今回の複数モジュールのMarkerに適用できるかは未検証です。
- `pluginManagement.resolutionStrategy` による実装への対応付けは検証済みです。他にはGradle Plugin Portal等の標準Marker座標を提供できる別のMavenリポジトリへの公開や、従来の `buildscript` / `apply` による適用が考えられます。新たな外部公開は行っていません。

## 3段階のmetadata比較

`V` は上記のコミットSHA全体、`G` は `com.github.Link2011-Act2.Trickuild` です。Gradleのpublicationは `-Pgroup=G -Pversion=V` を指定しました。JitPackが実行したコマンドは[公開ビルドログ](https://jitpack.io/com/github/Link2011-Act2/Trickuild/c6305ca29f2e8e15a50348f64d4bbe077065cbf0/build.log)で確認できます。

| モジュール | Maven Localの `.module` のcomponent | JitPackの `.module` のcomponent | sources variantのURL |
| --- | --- | --- | --- |
| `trickuild-gradle-plugin` | `G:trickuild-gradle-plugin:V` | `com.github.Link2011-Act2:Trickuild:V` | Localは `trickuild-gradle-plugin-V-sources.jar`、JitPackは `trickuild-gradle-plugin-V.jar` |
| `trickuild-core` | `G:trickuild-core:V` | `com.github.Link2011-Act2:Trickuild:V` | Localは `trickuild-core-V-sources.jar`、JitPackは `trickuild-core-V.jar` |
| `trickuild-android-adapter` | `G:trickuild-android-adapter:V` | `com.github.Link2011-Act2:Trickuild:V` | Localは `trickuild-android-adapter-V-sources.jar`、JitPackは `trickuild-android-adapter-V.jar` |

独立consumerが通常のGradle metadata設定で取得した3つの `.module` は、JitPackから直接取得したファイルとSHA-256が完全一致しました。さらにsources variantを指定してCoreを解決した実験では、通常の `trickuild-core-V.jar` が選択されました。これはソースではなくバイナリです。

3つの `-sources.jar` 自体はJitPackから直接取得でき、それぞれKotlinソースを含み、同一バージョンでローカル生成したsources JARおよびmetadata内に記録されたsources SHA-256と一致しました。しかし、JitPackのmetadataに記載されたsources URLが指す通常JARのSHA-256は異なります。POM方式で `sources` classifierを明示したCoreの取得も確認しました。IDEがソースを自動表示するかは別途未検証です。

生成元のmetadata、JitPackの応答、consumerキャッシュの一致関係から、不整合はGradle publicationの生成後、JitPackの公開経路で生じていると判断します。JitPack内部の変換処理は観測できないため、具体的な実装原因や公開設定だけでの修正可能性は断定できません。通常の `maven-publish` / `java-gradle-plugin` によるpublicationは変更せず、Coreにも配布専用コードは追加していません。

推奨するREADMEの設定は `mavenPom()` と `ignoreGradleMetadataRedirection()` を使用し、対象をプラグイン実装・Core・Android Adapterの3モジュールに限定しています。同じgroup内の別モジュールを通常設定の別リポジトリ宣言から解決する試験で、別モジュールのGradle metadataが使用されました。POM方式は実行時の依存解決を回避しますが、JitPack上のmetadata自体を修正するものではありません。

## 実際に取得した座標

JitPack上でPOMを取得したMarkerは `G:dev.trickuild.gradle.plugin:V`、POM・`.module`・通常JAR・sources JARを取得したモジュールは `G:trickuild-gradle-plugin:V`、`G:trickuild-core:V`、`G:trickuild-android-adapter:V` です。実装POMは同じ `V` のCoreとAndroid Adapterに依存します。各POMの座標と推移依存を検査しました。

各モジュールの公開URLは `https://jitpack.io/com/github/Link2011-Act2/Trickuild/<module>/<V>/<module>-<V>.<pom|module|jar>`、sourcesは同じ位置の `<module>-<V>-sources.jar` です。Markerは同じ規則の `.pom` です。集約POMは `com.github.Link2011-Act2:Trickuild:V` です。

比較用スクリプトは [Compare-Publication.ps1](../verification/jitpack/Compare-Publication.ps1)、実行条件は[独立consumerのREADME](../verification/jitpack/README.md)にあります。SHA版は実取得しましたが、将来のタグ版は未検証です。`jitpack.yml` の `$VERSION` はどちらを指定してもGradle publicationのバージョンに渡されます。

## 回帰検証

- Core単体テスト13件、Gradle側単体・JVM TestKitテスト9件はすべて成功しました。
- 既存4アプリのファイルは読み取りのみで内容ハッシュ互換テストを実行しました。
- 独立JVM consumerでは、隔離したMaven Localの標準MarkerとJitPackのREADME設定の両方で実行し、`GlyphBarty:2.2` / #4を確認しました。JitPack側は設定キャッシュ再利用も成功しました。
- Maven Localの標準Markerから解決する独立Android consumer（AGP 9.1.1）でもDebug APKを生成し、`GlyphBarty:2.2` / #4と `versionCode=42` を確認しました。
- JitPackの独立Android consumer（AGP 9.1.1）でDebug APKを生成し、`GlyphBarty:2.2` / #4、5桁ハッシュ、設定キャッシュ再利用、`versionCode=42` を確認しました。
- JitPackの独立Android consumer（AGP 9.4.1 / Gradle 9.6.0）でもDebug APKを生成しました。設定キャッシュを再利用した再ビルドで時刻が更新され、ハッシュ `d772a` が維持され、生成された番号がAPK内に含まれました。`GlyphBarty:2.2` / #4と `versionCode=42` も確認しました。
- 両Android consumerの対象ソースを別途SHA-256計算し、`8ba70b9c099d7ee4aa2723763cbd69cad11bcf5a13448aa2a4e584e57315889c` および `d772a19abc22453a4051529fc80e481469248d4a763a8cc2a8d982fce898e338` の先頭5桁と、それぞれのAPK内の番号が一致しました。
- 既存Android TestKitはAGP 9.1.1 / Gradle 9.3.1と、AGP 9.4.1 / Gradle 9.6.0の両方で成功しました。Debug・Releaseの同一時刻、設定キャッシュ再利用、APK削除やソース変更後の再生成、バージョン変更を検証しています。9.4.1は公開済みHEADと同一の隔離コピーで実行しました。
- AGP 9.1.1の初回TestKitではGoogle MavenのRelease用Lint依存3件のダウンロードがタイムアウトしました。既存Gradleキャッシュ内の対象JARのSHA-1がキャッシュのパスと一致することを確認し、そのキャッシュを使用した再実行は成功しました。
- ソースの時刻・内容ハッシュの詳細検証と、Roulette無効の場合を含む前回のJitPack公開物でのAPK検証は [Phase 1.5検証記録](phase-1.5-jitpack-verification.md) にあります。
