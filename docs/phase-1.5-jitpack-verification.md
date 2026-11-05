# Phase 1.5 JitPack検証記録

検証日: 2026-10-27（日本時間）。対象は、公開されたGitHubの `master` と一致するコミット `c6305ca29f2e8e15a50348f64d4bbe077065cbf0` です。JitPackのビルドログではJDK 21を使用し、`clean build publishToMavenLocal -Pgroup=com.github.Link2011-Act2.Trickuild -Pversion=$VERSION` が成功しました。Gradleは9.3.1です。

## 配布物

以下の `V` は上記のコミットSHA全体です。JitPackが返したPOMの座標とバージョンを実物で確認しました。Plugin MarkerはPOM、それ以外はPOM、module metadata、JARを取得しました。

| 種類 | 実際の座標 |
| --- | --- |
| Plugin Marker | `com.github.Link2011-Act2.Trickuild:dev.trickuild.gradle.plugin:V` |
| プラグイン実装 | `com.github.Link2011-Act2.Trickuild:trickuild-gradle-plugin:V` |
| Core | `com.github.Link2011-Act2.Trickuild:trickuild-core:V` |
| Android Adapter | `com.github.Link2011-Act2.Trickuild:trickuild-android-adapter:V` |

実装POMからCoreとAndroid Adapterへの依存は同じ `V` に解決され、それぞれのPOMはKotlin標準ライブラリ2.2.21に依存します。実装JARにはプラグインIDと実装クラスの対応付けが含まれます。取得したJARのSHA-256は次のとおりです。

| JAR | SHA-256 |
| --- | --- |
| プラグイン実装 | `012eff3ae1385d92f83a53b4abef5e1270c1ead37ca1c2ed415e826c0518ac24` |
| Core | `0390b30a3027b2991ca93521ad7ff3a946093e4bfa53698fd132c9756e6d4bfd` |
| Android Adapter | `31de3675350d33d702021e595e8eba61f9e0a271cf01e5a0d6e7706c3c392bbe` |

通常のプラグインIDからのMarker検索は `dev.trickuild` グループを探しますが、そのJitPack上のURLは404でした。上記の移動先Markerまたは実装モジュールへの `resolutionStrategy` での対応付けは、どちらもGradleで動作を確認しています。

JitPackが返した各 `.module` ではcomponent座標が個別モジュールではなく集約座標 `com.github.Link2011-Act2:Trickuild:V` になり、sources variantのURLも通常のJARを指していました。個別の `-sources.jar` 自体は取得できます。Gradle 9.3.1の通常のmetadata解決でもJVM consumerは動作しましたが、READMEの例ではPOMを使い、metadataへのリダイレクトを無視します。これは公開済みコミットに対して検証した回避策であり、metadataの不整合自体は残っています。

通常のローカル公開の既定バージョンは `0.1.0-SNAPSHOT` です。今回のJitPackビルドは `-Pversion=$VERSION` によりコミットSHAをバージョンに使用しました。タグによる公開は作成・検証していません。

## 独立consumer

`verification/jitpack` のconsumerをリポジトリ外の一時ディレクトリへコピーし、Maven Local、included build、隣接プロジェクトを使わず検証しました。独立したGradleユーザーホームからJitPackのPOMとJARを取得しています。Gradleディストリビューションは既存のWrapper用コピーを使用しました。READMEの設定例そのものを別のJVM consumerへコピーして実行する検証も成功しています。

- JVMのTooling APIによるIDEモデル取得は成功し、同期時にはメタデータを生成しません。
- JVM実行で `GlyphBarty:2.2` は `PERSONALITY_NUMBER=4` となり、Roulette無効時はnamespaceなしで動作し、性格のフィールドを生成しません。
- 実行時刻は設定キャッシュ再利用後も更新されました。同じソースの内容ハッシュは一定で、独立したSHA-256計算の先頭5桁と一致しました。
- AndroidのAGP 9.1.1公開v2モデルとIDEモデルを取得し、同期エラーは0件でした。モデル取得時にいくつかの依存設定が設定フェーズで解決された旨の警告は出ています。
- Android Debug APKはRoulette無効・有効の両方で生成できました。有効時はJavaから `PERSONALITY_SEED` と `PERSONALITY_NUMBER` を直接参照してコンパイルし、`GlyphBarty:2.2` と `4` を確認しました。無効時はnamespaceを指定せず性格フィールドを生成しません。
- Androidの設定キャッシュ再利用時、`Epica-d2.2.0-261027-1858-36646` から `Epica-d2.2.0-261027-1859-36646` に更新されました。ハッシュ `36646` は対象ファイルについて別途計算したSHA-256 `36646575abfa394fdecc8f0a0a55285ca84f178eb9c7204b215d0b05be81ac41` の先頭5桁と一致します。更新後の番号がAPK内のDEXにも含まれることを確認しました。
- APKの情報をAndroid SDKの `aapt2 dump badging` で検査し、既存の `versionCode=42` および `versionName=2.2.0-IntDev_rev0` が維持されていることを確認しました。

AndroidのKotlinコンパイラ依存はネットワーク取得の待機が長かったため、既存Gradleキャッシュの2.2.10を使用しました。ファイルのSHA-1はMaven Centralの公開チェックサムと一致しています。Trickuild自体はJitPackから取得しました。

GitHubへのコミット、Push、タグ作成は行っていません。READMEと検証用consumerはローカル変更であり、公開済みコミットの内容は変更されていません。
