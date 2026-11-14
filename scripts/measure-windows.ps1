param(
    [Parameter(Mandatory = $true)][string]$Executable,
    [Parameter(Mandatory = $true)][string]$DataDir,
    [Parameter(Mandatory = $true)][string]$Output,
    [ValidateRange(5, 600)][int]$Seconds = 30
)

$ErrorActionPreference = 'Stop'
$exe = (Resolve-Path -LiteralPath $Executable).Path
$dataPath = [System.IO.Path]::GetFullPath($DataDir)
New-Item -ItemType Directory -Path $dataPath -Force | Out-Null

function Get-NetworkBytes {
    try {
        $adapters = @(Get-NetAdapterStatistics -ErrorAction Stop)
        return @{
            received = [long](($adapters | Measure-Object -Property ReceivedBytes -Sum).Sum)
            sent = [long](($adapters | Measure-Object -Property SentBytes -Sum).Sum)
        }
    } catch {
        return $null
    }
}

$networkBefore = Get-NetworkBytes
$startInfo = [System.Diagnostics.ProcessStartInfo]::new($exe)
$startInfo.WorkingDirectory = Split-Path -Parent $exe
[void]$startInfo.ArgumentList.Add('--gui')
[void]$startInfo.ArgumentList.Add('--data-dir')
[void]$startInfo.ArgumentList.Add($dataPath)
$process = [System.Diagnostics.Process]::Start($startInfo)
if ($null -eq $process) { throw 'Không khởi chạy được ứng dụng' }
$samples = [System.Collections.Generic.List[object]]::new()
$watch = [System.Diagnostics.Stopwatch]::StartNew()
try {
    for ($i = 0; $i -lt $Seconds; $i++) {
        Start-Sleep -Seconds 1
        $process.Refresh()
        if ($process.HasExited) { break }
        $samples.Add(@{
            elapsed_s = [math]::Round($watch.Elapsed.TotalSeconds, 3)
            working_set_bytes = [long]$process.WorkingSet64
            private_bytes = [long]$process.PrivateMemorySize64
            cpu_time_s = [math]::Round($process.TotalProcessorTime.TotalSeconds, 3)
        })
    }
} finally {
    $networkAfter = Get-NetworkBytes
    if (!$process.HasExited) {
        [void]$process.CloseMainWindow()
        if (!$process.WaitForExit(3000)) {
            $process.Kill()
            [void]$process.WaitForExit(3000)
        }
    }
}

$dataBytes = [long]((Get-ChildItem -LiteralPath $dataPath -Recurse -File -ErrorAction Stop |
    Measure-Object -Property Length -Sum).Sum)
$cpuStart = if ($samples.Count -gt 0) { $samples[0].cpu_time_s } else { 0 }
$cpuEnd = if ($samples.Count -gt 0) { $samples[$samples.Count - 1].cpu_time_s } else { 0 }
$elapsed = if ($samples.Count -gt 1) {
    $samples[$samples.Count - 1].elapsed_s - $samples[0].elapsed_s
} else { 0 }
$oneCoreCpuPercent = if ($elapsed -gt 0) {
    [math]::Round(100 * ($cpuEnd - $cpuStart) / $elapsed, 2)
} else { $null }
$result = @{
    executable = $exe
    executable_bytes = [long](Get-Item -LiteralPath $exe).Length
    data_dir = $dataPath
    data_bytes = $dataBytes
    windows_version = [System.Environment]::OSVersion.VersionString
    logical_processors = [System.Environment]::ProcessorCount
    sample_seconds = $Seconds
    actual_sample_count = $samples.Count
    process_exit_code = $process.ExitCode
    cpu_percent_one_core = $oneCoreCpuPercent
    network_all_adapters_received_bytes = if ($null -ne $networkBefore -and $null -ne $networkAfter) { $networkAfter.received - $networkBefore.received } else { $null }
    network_all_adapters_sent_bytes = if ($null -ne $networkBefore -and $null -ne $networkAfter) { $networkAfter.sent - $networkBefore.sent } else { $null }
    network_note = 'Lưu lượng của toàn máy nếu có quyền đọc bộ đếm; không quy được cho app riêng.'
    samples = $samples
}
$parent = Split-Path -Parent $Output
if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Output -Encoding utf8
Write-Output $Output
