# Generate ic_launcher.png + ic_launcher_round.png for all mipmap densities from one PNG.
#
# Usage (repo root):
#   .\scripts\generate-launcher-icons.ps1 -Source "D:\path\to\icon.png"
#   .\scripts\generate-launcher-icons.ps1 -Source ".\icon-source.png"
#
# Recommended source: square PNG, at least 1024x1024, important content in center 66% (safe zone).

param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [string]$ResRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) "app\src\main\res")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$sourcePath = (Resolve-Path $Source).Path
$ext = [System.IO.Path]::GetExtension($sourcePath).ToLowerInvariant()
if ($ext -notin ".png", ".jpg", ".jpeg", ".webp") {
    throw "Source must be PNG/JPG/WEBP: $sourcePath"
}

# Launcher icon bitmap sizes (px) per density — Android legacy mipmap spec
$densitySizes = [ordered]@{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

function New-SquareBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $bmp.SetResolution(96, 96)
    return $bmp
}

function Draw-ScaledImage {
    param(
        [System.Drawing.Graphics]$G,
        [System.Drawing.Image]$Src,
        [int]$Size,
        [bool]$CircularClip
    )
    $G.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $G.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $G.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $G.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $G.Clear([System.Drawing.Color]::Transparent)

    if ($CircularClip) {
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddEllipse(0, 0, $Size, $Size)
        $G.SetClip($path)
    }

    # Uniform scale, center crop to square
    $srcW = $Src.Width
    $srcH = $Src.Height
    $crop = [Math]::Min($srcW, $srcH)
    $sx = ($srcW - $crop) / 2
    $sy = ($srcH - $crop) / 2
    $G.DrawImage($Src, (New-Object System.Drawing.Rectangle 0, 0, $Size, $Size),
        (New-Object System.Drawing.Rectangle $sx, $sy, $crop, $crop),
        [System.Drawing.GraphicsUnit]::Pixel)
}

$srcImage = [System.Drawing.Image]::FromFile($sourcePath)
try {
    foreach ($entry in $densitySizes.GetEnumerator()) {
        $folder = Join-Path $ResRoot $entry.Key
        $size = $entry.Value
        if (-not (Test-Path $folder)) {
            New-Item -ItemType Directory -Path $folder -Force | Out-Null
        }

        $squarePath = Join-Path $folder "ic_launcher.png"
        $roundPath = Join-Path $folder "ic_launcher_round.png"

        $square = New-SquareBitmap $size
        $g1 = [System.Drawing.Graphics]::FromImage($square)
        try { Draw-ScaledImage -G $g1 -Src $srcImage -Size $size -CircularClip $false }
        finally { $g1.Dispose() }
        $square.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
        $square.Dispose()

        $round = New-SquareBitmap $size
        $g2 = [System.Drawing.Graphics]::FromImage($round)
        try { Draw-ScaledImage -G $g2 -Src $srcImage -Size $size -CircularClip $true }
        finally { $g2.Dispose() }
        $round.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $round.Dispose()

        Write-Host "  $($entry.Key) -> ${size}x${size}" -ForegroundColor Green
    }
}
finally {
    $srcImage.Dispose()
}

Write-Host ""
Write-Host "Done. Icons written under: $ResRoot" -ForegroundColor Cyan
Write-Host "Rebuild: java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug"
