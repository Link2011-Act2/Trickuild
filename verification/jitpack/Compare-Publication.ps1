param(
    [Parameter(Mandatory = $true)][string]$LocalMaven,
    [Parameter(Mandatory = $true)][string]$ConsumerGradleHome,
    [string]$Version = 'c6305ca29f2e8e15a50348f64d4bbe077065cbf0'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$group = 'com.github.Link2011-Act2.Trickuild'
$modules = @('trickuild-gradle-plugin', 'trickuild-core', 'trickuild-android-adapter')
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('trickuild-publication-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null

$marker = 'dev.trickuild.gradle.plugin'
$localMarker = Join-Path $LocalMaven "dev/trickuild/$marker/$Version/$marker-$Version.pom"
$remoteMarker = Join-Path $scratch "$marker-$Version.pom"
Invoke-WebRequest -UseBasicParsing -Uri "https://jitpack.io/com/github/Link2011-Act2/Trickuild/$marker/$Version/$marker-$Version.pom" -OutFile $remoteMarker
[xml]$localMarkerPom = Get-Content -Raw -LiteralPath $localMarker
[xml]$remoteMarkerPom = Get-Content -Raw -LiteralPath $remoteMarker
if ($localMarkerPom.project.groupId -ne 'dev.trickuild' -or $remoteMarkerPom.project.groupId -ne $group) {
    throw 'Unexpected marker group'
}
foreach ($pom in @($localMarkerPom, $remoteMarkerPom)) {
    $dependency = $pom.project.dependencies.dependency
    if ($dependency.groupId -ne $group -or $dependency.artifactId -ne 'trickuild-gradle-plugin' -or $dependency.version -ne $Version) {
        throw 'Unexpected marker dependency'
    }
}
Write-Host "Marker: local dev.trickuild:$marker -> JitPack $group`:$marker"

foreach ($module in $modules) {
    $baseName = "$module-$Version"
    $localDir = Join-Path $LocalMaven "com/github/Link2011-Act2/Trickuild/$module/$Version"
    $remoteBase = "https://jitpack.io/com/github/Link2011-Act2/Trickuild/$module/$Version/$baseName"
    foreach ($suffix in @('.pom', '.module', '.jar', '-sources.jar')) {
        Invoke-WebRequest -UseBasicParsing -Uri "$remoteBase$suffix" -OutFile (Join-Path $scratch "$baseName$suffix")
    }
    $localFile = Join-Path $localDir "$baseName.module"
    $remoteFile = Join-Path $scratch "$baseName.module"
    $local = Get-Content -Raw -LiteralPath $localFile | ConvertFrom-Json
    $remote = Get-Content -Raw -LiteralPath $remoteFile | ConvertFrom-Json
    [xml]$remotePom = Get-Content -Raw -LiteralPath (Join-Path $scratch "$baseName.pom")
    if ($remotePom.project.groupId -ne $group -or $remotePom.project.artifactId -ne $module -or $remotePom.project.version -ne $Version) {
        throw "Unexpected remote POM coordinates: $module"
    }
    if ($module -eq 'trickuild-gradle-plugin') {
        foreach ($dependencyModule in @('trickuild-core', 'trickuild-android-adapter')) {
            $dependency = $remotePom.project.dependencies.dependency | Where-Object artifactId -eq $dependencyModule
            if ($dependency.groupId -ne $group -or $dependency.version -ne $Version) { throw "Unexpected dependency: $dependencyModule" }
        }
    }
    if ($local.component.group -ne $group -or $local.component.module -ne $module -or $local.component.version -ne $Version) {
        throw "Unexpected local component coordinates: $module"
    }
    $localSource = ($local.variants | Where-Object name -eq 'sourcesElements').files
    $remoteSource = ($remote.variants | Where-Object name -eq 'sourcesElements').files
    $expectedSource = "$baseName-sources.jar"
    if ($localSource.url -ne $expectedSource) { throw "Unexpected local sources URL: $module" }
    $localHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $localDir $expectedSource)).Hash.ToLowerInvariant()
    $remoteHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $scratch $expectedSource)).Hash.ToLowerInvariant()
    $binaryHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $scratch "$baseName.jar")).Hash.ToLowerInvariant()
    if ($localHash -ne $localSource.sha256 -or $localHash -ne $remoteHash -or $remoteHash -ne $remoteSource.sha256 -or $remoteHash -eq $binaryHash) {
        throw "Source JAR content or metadata checksum mismatch: $module"
    }
    $sourceZip = [IO.Compression.ZipFile]::OpenRead((Join-Path $scratch $expectedSource))
    try {
        if (!($sourceZip.Entries | Where-Object { $_.Name -like '*.kt' })) { throw "No Kotlin source in $expectedSource" }
    } finally { $sourceZip.Dispose() }
    $cacheRoot = Join-Path $ConsumerGradleHome "caches/modules-2/files-2.1/$group/$module/$Version"
    $cached = @(Get-ChildItem -LiteralPath $cacheRoot -Filter "$baseName.module" -File -Recurse)
    if ($cached.Count -ne 1) { throw "Expected one consumer-cached module file: $module" }
    $consumerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $cached[0].FullName).Hash
    $metadataHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $remoteFile).Hash
    if ($consumerHash -ne $metadataHash) { throw "Consumer metadata differs from JitPack: $module" }
    [pscustomobject]@{
        Module = $module
        LocalComponent = "$($local.component.group):$($local.component.module):$($local.component.version)"
        RemoteComponent = "$($remote.component.group):$($remote.component.module):$($remote.component.version)"
        LocalSourceUrl = $localSource.url
        RemoteSourceUrl = $remoteSource.url
        RemoteSourceSHA256 = $remoteHash
        RemoteBinarySHA256 = $binaryHash
        ConsumerMetadataMatches = $true
    }
}
Write-Host "Downloaded files: $scratch"
