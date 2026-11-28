<#
.SYNOPSIS
  Copies the encrypted math-lms backups from S3 to this PC: the copy outside the AWS account.

.DESCRIPTION
  The server writes an age-encrypted database dump and photo archive every night (deploy/backup.sh) and copies
  them to S3 - in the same AWS account as the server. If that account closes, both go at once. This script downloads
  every backup of the last -KeepDays days that is not on this PC yet, checks each file's size against S3, and prunes
  old local copies - never the newest -AlwaysKeep pairs, so the copies survive if S3 itself disappears.

  The files stay encrypted: only the age private key (kept by you, not on this PC's disk) can open them.
  Restore: deploy/restore-test.sh <db-...dump.age> <uploads-...tar.gz.age> with AGE_IDENTITY set (see that script).

  Run by hand:   powershell -ExecutionPolicy Bypass -File deploy\pull-backups.ps1
  Exit code 0 = up to date; 1 = a download failed; 2 = the newest backup in S3 is older than 2 days (the night job
  stopped). Each run appends to pull-backups.log in the destination folder.
#>
param(
    [string]$Bucket = 'math-lms-backups-074770434586',
    [string]$Region = 'eu-central-1',
    [string]$Destination = (Join-Path $env:USERPROFILE 'Backups\math-lms'),
    [int]$KeepDays = 14,
    [int]$AlwaysKeep = 3
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$log = Join-Path $Destination 'pull-backups.log'
$pattern = '^(db-(\d{8}-\d{6})\.dump\.age|uploads-(\d{8}-\d{6})\.tar\.gz\.age)$'

function Write-Log([string]$text) {
    $line = '[{0:yyyy-MM-dd HH:mm:ss}] {1}' -f (Get-Date), $text
    Add-Content -Path $log -Value $line -Encoding UTF8
    Write-Output $line
}

# "db-20261128-031501.dump.age" -> 2026-11-28 03:15:01 (the server's UTC clock)
function Get-Stamp([string]$name) {
    $m = [regex]::Match($name, '(\d{8}-\d{6})')
    [datetime]::ParseExact($m.Groups[1].Value, 'yyyyMMdd-HHmmss', [Globalization.CultureInfo]::InvariantCulture)
}

try {
    $json = & aws s3api list-objects-v2 --bucket $Bucket --prefix 'backups/' --region $Region --output json
    if ($LASTEXITCODE -ne 0) { throw "could not list s3://$Bucket/backups/ (aws exit $LASTEXITCODE)" }
    $remote = @((($json | Out-String) | ConvertFrom-Json).Contents |
        Where-Object { ($_.Key -replace '^backups/', '') -match $pattern } |
        ForEach-Object { [pscustomobject]@{ Name = ($_.Key -replace '^backups/', ''); Size = [long]$_.Size } })
    if ($remote.Count -eq 0) { throw "no backups in s3://$Bucket/backups/" }

    $utcNow = (Get-Date).ToUniversalTime()
    $wanted = $remote | Where-Object { (Get-Stamp $_.Name) -ge $utcNow.AddDays(-$KeepDays) }
    $downloaded = 0
    foreach ($object in $wanted) {
        $target = Join-Path $Destination $object.Name
        if ((Test-Path $target) -and (Get-Item $target).Length -eq $object.Size) { continue }
        $partial = "$target.partial"
        & aws s3 cp "s3://$Bucket/backups/$($object.Name)" $partial --region $Region --only-show-errors
        if ($LASTEXITCODE -ne 0) { throw "download of $($object.Name) failed (aws exit $LASTEXITCODE)" }
        $size = (Get-Item $partial).Length
        if ($size -ne $object.Size) {
            Remove-Item $partial -Force
            throw "download of $($object.Name) is incomplete ($size of $($object.Size) bytes)"
        }
        Move-Item -Force $partial $target
        $downloaded++
    }

    # Prune by age, but always keep the newest pairs: if S3 is gone, these are the only copies left.
    $local = @(Get-ChildItem -Path $Destination -File | Where-Object { $_.Name -match $pattern })
    foreach ($kind in 'db-', 'uploads-') {
        $files = @($local | Where-Object { $_.Name.StartsWith($kind) } | Sort-Object { Get-Stamp $_.Name } -Descending)
        $files | Select-Object -Skip $AlwaysKeep |
            Where-Object { (Get-Stamp $_.Name) -lt $utcNow.AddDays(-$KeepDays) } |
            ForEach-Object { Remove-Item $_.FullName -Force; Write-Log "removed old copy $($_.Name)" }
    }
    Get-ChildItem -Path $Destination -Filter '*.partial' -File | Remove-Item -Force

    $newest = $remote | Where-Object { $_.Name.StartsWith('db-') } | Sort-Object { Get-Stamp $_.Name } | Select-Object -Last 1
    $ageDays = ($utcNow - (Get-Stamp $newest.Name)).TotalDays
    $kept = @(Get-ChildItem -Path $Destination -File | Where-Object { $_.Name -match $pattern })
    $sizeMb = [math]::Round(($kept | Measure-Object Length -Sum).Sum / 1MB)
    Write-Log ("ok: {0} new file(s), {1} on this PC ({2} MB), newest backup {3}" -f $downloaded, $kept.Count, $sizeMb, $newest.Name)
    if ($ageDays -gt 2) {
        Write-Log ("WARNING: the newest backup in S3 is {0:N1} days old - is the server's nightly job running?" -f $ageDays)
        exit 2
    }
    exit 0
}
catch {
    Write-Log "FAILED: $($_.Exception.Message)"
    exit 1
}
