<#
.SYNOPSIS
  Keeps ONE copy of the newest encrypted math-lms backup on this PC: the copy outside the AWS account.

.DESCRIPTION
  The server writes an age-encrypted database dump and photo archive every night (deploy/backup.sh) and copies
  them to S3 - in the same AWS account as the server. If that account closes, both go at once. This script downloads
  the newest pair from S3 (database + photos, ~125 MB), checks each file's size against S3, and only THEN deletes the
  older copies on this PC - so there is always exactly one good backup here, and a failed download never leaves you
  with none (the old pair stays until a new one is complete).

  The files stay encrypted: only the age private key (kept by you, not on this PC's disk) can open them.
  Restore: deploy/restore-test.sh <db-...dump.age> <uploads-...tar.gz.age> with AGE_IDENTITY set (see that script).

  Run by hand:   powershell -ExecutionPolicy Bypass -File deploy\pull-backups.ps1
  Exit code 0 = up to date; 1 = the download failed (the previous copy is kept); 2 = the newest backup in S3 is older
  than 2 days (the server's night job stopped). Each run appends one line to pull-backups.log in the destination.
#>
param(
    [string]$Bucket = 'math-lms-backups-074770434586',
    [string]$Region = 'eu-central-1',
    [string]$Destination = (Join-Path $env:USERPROFILE 'Backups\math-lms')
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

# "db-20261128-031501.dump.age" -> "20261128-031501" (the server's UTC clock); sorts in time order as text
function Get-Stamp([string]$name) { [regex]::Match($name, '(\d{8}-\d{6})').Groups[1].Value }

try {
    $json = & aws s3api list-objects-v2 --bucket $Bucket --prefix 'backups/' --region $Region --output json
    if ($LASTEXITCODE -ne 0) { throw "could not list s3://$Bucket/backups/ (aws exit $LASTEXITCODE)" }
    $remote = @((($json | Out-String) | ConvertFrom-Json).Contents |
        Where-Object { ($_.Key -replace '^backups/', '') -match $pattern } |
        ForEach-Object { [pscustomobject]@{ Name = ($_.Key -replace '^backups/', ''); Size = [long]$_.Size } })

    # The newest night that has BOTH files (a database without its photos is only half a backup).
    $stamps = @($remote | ForEach-Object { Get-Stamp $_.Name } | Sort-Object -Unique -Descending)
    $stamp = $stamps | Where-Object {
        $s = $_
        ($remote | Where-Object { $_.Name -eq "db-$s.dump.age" }) -and ($remote | Where-Object { $_.Name -eq "uploads-$s.tar.gz.age" })
    } | Select-Object -First 1
    if (-not $stamp) { throw "no complete backup (database + photos) in s3://$Bucket/backups/" }
    $pair = @($remote | Where-Object { (Get-Stamp $_.Name) -eq $stamp })

    $downloaded = 0
    foreach ($object in $pair) {
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

    # The new pair is complete and checked: now, and only now, the older copies go.
    $removed = 0
    Get-ChildItem -Path $Destination -File | Where-Object { $_.Name -match $pattern -and (Get-Stamp $_.Name) -ne $stamp } |
        ForEach-Object { Remove-Item $_.FullName -Force; $removed++ }
    Get-ChildItem -Path $Destination -Filter '*.partial' -File | Remove-Item -Force

    $sizeMb = [math]::Round((($pair | Measure-Object Size -Sum).Sum) / 1MB)
    $made = [datetime]::ParseExact($stamp, 'yyyyMMdd-HHmmss', [Globalization.CultureInfo]::InvariantCulture)
    Write-Log ("ok: backup of {0:yyyy-MM-dd HH:mm} UTC on this PC ({1} MB; {2} file(s) downloaded, {3} older removed)" -f $made, $sizeMb, $downloaded, $removed)
    if (((Get-Date).ToUniversalTime() - $made).TotalDays -gt 2) {
        Write-Log ("WARNING: the newest backup in S3 is from {0:yyyy-MM-dd} - is the server's nightly job running?" -f $made)
        exit 2
    }
    exit 0
}
catch {
    Write-Log "FAILED (the previous copy is kept): $($_.Exception.Message)"
    exit 1
}
