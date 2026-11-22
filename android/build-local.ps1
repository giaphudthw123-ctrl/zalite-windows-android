param(
    [string]$InternalUpdateRepo = ''
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME to an installed Android SDK.' }
if ($InternalUpdateRepo -and $InternalUpdateRepo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw 'InternalUpdateRepo must be owner/repo.'
}

$ndk = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'ndk') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $ndk) { throw 'Install an Android NDK first.' }
$llvm = Join-Path $ndk.FullName 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$targets = @(
    @{ Rust = 'aarch64-linux-android'; Abi = 'arm64-v8a'; Linker = 'aarch64-linux-android28-clang.cmd'; EnvName = 'CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER' },
    @{ Rust = 'x86_64-linux-android'; Abi = 'x86_64'; Linker = 'x86_64-linux-android28-clang.cmd'; EnvName = 'CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER' }
)

foreach ($target in $targets) {
    $linker = Join-Path $llvm $target.Linker
    if (-not (Test-Path -LiteralPath $linker)) { throw "Missing NDK linker: $linker" }
    [Environment]::SetEnvironmentVariable($target.EnvName, $linker, 'Process')
    # ring/TLS has C objects; Cargo's Rust linker setting alone does not configure cc-rs.
    $ccTarget = $target.Rust.Replace('-', '_')
    [Environment]::SetEnvironmentVariable("CC_$ccTarget", $linker, 'Process')
    [Environment]::SetEnvironmentVariable("AR_$ccTarget", (Join-Path $llvm 'llvm-ar.exe'), 'Process')
    & cargo build --manifest-path (Join-Path $projectDir 'native/Cargo.toml') --locked --release --target $target.Rust
    if ($LASTEXITCODE -ne 0) { throw "Rust build failed for $($target.Rust)" }
    $source = Join-Path $projectDir "native/target/$($target.Rust)/release/libctdozlite_android.so"
    $destination = Join-Path $projectDir "app/src/main/jniLibs/$($target.Abi)"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination (Join-Path $destination 'libctdozlite_android.so') -Force
}

Push-Location $projectDir
try {
    & gradle.bat --no-daemon "-PinternalUpdateRepo=$InternalUpdateRepo" :app:testForkDebugUnitTest :app:testInternalDebugUnitTest :app:assembleForkDebug :app:assembleInternalDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android Gradle build failed.' }
} finally {
    Pop-Location
}
