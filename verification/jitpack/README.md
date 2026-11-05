# 独立consumerの再検証

このディレクトリには、公開済みJitPack成果物を使うJVMとAndroidのconsumerがあります。`mavenLocal()`、`includeBuild()`、隣接プロジェクトへの依存はありません。検証対象は既定でコミット `c6305ca29f2e8e15a50348f64d4bbe077065cbf0` です。`-PtrickuildVersion=<version>` で変更できますが、他のバージョンは別途検証してください。

Windows PowerShellでの例です。Trickuildルートで実行し、JDK 21とAndroid SDKをあらかじめ設定してください。Wrapperは実行ツールとしてコピーし、プラグインはJitPackから解決します。

```powershell
$sourceRoot = (Get-Location).Path
$consumerRoot = Join-Path $env:TEMP ("trickuild-consumers-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $consumerRoot | Out-Null
Copy-Item "$sourceRoot/verification/jitpack/jvm" "$consumerRoot/jvm" -Recurse
Copy-Item "$sourceRoot/verification/jitpack/android" "$consumerRoot/android" -Recurse
foreach ($consumer in @("jvm", "android")) {
    Copy-Item "$sourceRoot/gradlew.bat" "$consumerRoot/$consumer/"
    Copy-Item "$sourceRoot/gradle" "$consumerRoot/$consumer/gradle" -Recurse
}
$env:GRADLE_USER_HOME = "$consumerRoot/gradle-home"
Set-Location "$consumerRoot/jvm"
.\gradlew.bat run '-Proulette=true' '-ProuletteNamespace=GlyphBarty' --configuration-cache
.\gradlew.bat run --configuration-cache
Set-Location "$consumerRoot/android"
.\gradlew.bat :app:assembleDebug --configuration-cache
.\gradlew.bat :app:assembleDebug '-Proulette=true' '-ProuletteNamespace=GlyphBarty' --configuration-cache
.\gradlew.bat :app:assembleDebug '-Proulette=true' '-ProuletteNamespace=GlyphBarty' --configuration-cache
```

有効時は `GlyphBarty:2.2` と番号 `4` を確認します。Androidの生成値は `app/build/trickuild/generateDebugTrickuildMetadata/metadata.properties`、APKは `app/build/outputs/apk/debug/app-debug.apk` にあります。再実行時、設定キャッシュは再利用され、ソースが同じなら内容ハッシュは変わりません。ビルド番号の時刻は分単位です。

詳細な検証結果とJitPack metadataの制約は [Phase 1.5検証記録](../../docs/phase-1.5-jitpack-verification.md) を参照してください。

同一バージョンを隔離したMaven Localに公開し、通常のmetadata設定でJitPackから各 `.module` を取得したGradleユーザーホームがある場合、3段階の比較を次のように再実行できます。公開済みコミットと異なるソースの出版物を比較すると、sources JARの一致検査は失敗します。

```powershell
.\verification\jitpack\Compare-Publication.ps1 -LocalMaven <maven-repository-path> -ConsumerGradleHome <gradle-user-home>
```

比較内容と結果は [Phase 1.6検証記録](../../docs/phase-1.6-publication-verification.md) を参照してください。
