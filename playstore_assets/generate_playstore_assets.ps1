Add-Type -AssemblyName System.Drawing

function Draw-RoundedRectangle {
    param(
        [System.Drawing.Graphics]$g,
        [System.Drawing.Brush]$brush,
        [System.Drawing.Pen]$pen,
        [float]$x, [float]$y, [float]$width, [float]$height, [float]$radius
    )
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $radius * 2.0
    $path.AddArc($x, $y, $diameter, $diameter, 180.0, 90.0)
    $path.AddArc(($x + $width - $diameter), $y, $diameter, $diameter, 270.0, 90.0)
    $path.AddArc(($x + $width - $diameter), ($y + $height - $diameter), $diameter, $diameter, 0.0, 90.0)
    $path.AddArc($x, ($y + $height - $diameter), $diameter, $diameter, 90.0, 90.0)
    $path.CloseFigure()

    if ($brush -ne $null) { $g.FillPath($brush, $path) }
    if ($pen -ne $null) { $g.DrawPath($pen, $path) }
    $path.Dispose()
}

# ==========================================
# 1. FEATURE GRAPHIC (1024 x 500)
# ==========================================
$fg = New-Object System.Drawing.Bitmap 1024, 500
$g = [System.Drawing.Graphics]::FromImage($fg)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point 0, 0),
    (New-Object System.Drawing.Point 1024, 500),
    [System.Drawing.Color]::FromArgb(255, 12, 10, 9),
    [System.Drawing.Color]::FromArgb(255, 24, 24, 27)
)
$g.FillRectangle($bgBrush, 0, 0, 1024, 500)
$bgBrush.Dispose()

$glowBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(35, 245, 158, 11))
$g.FillEllipse($glowBrush, 650, -50, 450, 450)
$glowBrush.Dispose()

$glowBrush2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(25, 99, 102, 241))
$g.FillEllipse($glowBrush2, -100, 200, 400, 400)
$glowBrush2.Dispose()

$iconPath = "playstore_assets\app_icon_512x512.png"
if (Test-Path $iconPath) {
    $iconImg = [System.Drawing.Image]::FromFile($iconPath)
    $g.DrawImage($iconImg, 720, 100, 240, 240)
    $iconImg.Dispose()
}

$fontFamily = "Segoe UI"
$fontBrand = New-Object System.Drawing.Font($fontFamily, 44, [System.Drawing.FontStyle]::Bold)
$fontTagline = New-Object System.Drawing.Font($fontFamily, 16, [System.Drawing.FontStyle]::Bold)
$fontDesc = New-Object System.Drawing.Font($fontFamily, 13, [System.Drawing.FontStyle]::Regular)
$fontBadge = New-Object System.Drawing.Font($fontFamily, 11, [System.Drawing.FontStyle]::Bold)

$amberBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 158, 11))
$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 244, 244, 245))
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 161, 161, 170))

$g.DrawString("gobeats", $fontBrand, $whiteBrush, 70, 75)
$g.DrawString("HIGH-FIDELITY OFFLINE MEDIA ENGINE", $fontTagline, $amberBrush, 75, 145)

$descText = "Bit-Perfect 32-bit Float Audio * Offline AI Dual Subtitles`nHardware Auto EQ Routing * 60 FPS Visualizer * Local Wi-Fi Sync"
$g.DrawString($descText, $fontDesc, $mutedBrush, 75, 180)

$badgeBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 39, 39, 42))
$badgePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 63, 63, 70), 1)

$badges = @("100% OFFLINE", "NO ADS", "BIT-PERFECT", "AI SUBTITLES", "STUDIO EQ")
$bx = 75
foreach ($b in $badges) {
    $size = $g.MeasureString($b, $fontBadge)
    Draw-RoundedRectangle $g $badgeBg $badgePen $bx 275 ($size.Width + 24) 34 8
    $g.DrawString($b, $fontBadge, $whiteBrush, ($bx + 12), 283)
    $bx += ($size.Width + 34)
}

$accentPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, 245, 158, 11), 3)
$waveY = 440
$heights = @(10, 18, 35, 20, 50, 80, 45, 95, 60, 40, 70, 90, 110, 65, 30, 85, 100, 55, 25, 15, 5, 20, 40, 75, 120, 80, 45, 20, 10)
$wx = 75
foreach ($h in $heights) {
    $g.DrawLine($accentPen, $wx, ($waveY - ($h * 0.4)), $wx, ($waveY + ($h * 0.4)))
    $wx += 12
}

$g.Dispose()
$fg.Save("playstore_assets\feature_graphic_1024x500.png", [System.Drawing.Imaging.ImageFormat]::Png)
$fg.Dispose()
Write-Host "Generated feature_graphic_1024x500.png"

# ==========================================
# 2. SCREENSHOT BUILDER HELPER (1080 x 1920)
# ==========================================
function Make-Screenshot {
    param(
        [string]$outFile,
        [string]$badge,
        [string]$title,
        [string]$sub,
        [string]$colorHex,
        [int]$shotIndex
    )

    $bmp = New-Object System.Drawing.Bitmap 1080, 1920
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $accentColor = [System.Drawing.ColorTranslator]::FromHtml($colorHex)

    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point 0, 0),
        (New-Object System.Drawing.Point 1080, 1920),
        [System.Drawing.Color]::FromArgb(255, 10, 10, 12),
        [System.Drawing.Color]::FromArgb(255, 24, 24, 30)
    )
    $g.FillRectangle($bg, 0, 0, 1080, 1920)
    $bg.Dispose()

    $glow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, $accentColor))
    $g.FillEllipse($glow, 240, -100, 600, 600)
    $glow.Dispose()

    # Pill
    $pillFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    $pillBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, $accentColor))
    $pillPen = New-Object System.Drawing.Pen($accentColor, 2)
    $pillBrush = New-Object System.Drawing.SolidBrush($accentColor)
    $pillSize = $g.MeasureString($badge, $pillFont)
    $pillX = (1080.0 - ($pillSize.Width + 40.0)) / 2.0
    Draw-RoundedRectangle $g $pillBg $pillPen $pillX 80 ($pillSize.Width + 40) 50 16
    $g.DrawString($badge, $pillFont, $pillBrush, ($pillX + 20), 90)

    # Title & Subtitle
    $titleFont = New-Object System.Drawing.Font("Segoe UI", 36, [System.Drawing.FontStyle]::Bold)
    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString($title, $titleFont, $whiteBrush, (New-Object System.Drawing.RectangleF 60, 150, 960, 120), $sf)

    $subFont = New-Object System.Drawing.Font("Segoe UI", 20, [System.Drawing.FontStyle]::Regular)
    $mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 161, 161, 170))
    $g.DrawString($sub, $subFont, $mutedBrush, (New-Object System.Drawing.RectangleF 80, 275, 920, 80), $sf)

    # Phone Frame
    $fx = 90.0
    $fy = 380.0
    $fw = 900.0
    $fh = 1540.0
    $frameBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 18, 18, 22))
    $frameBorder = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 45, 45, 55), 4)
    Draw-RoundedRectangle $g $frameBg $frameBorder $fx $fy $fw $fh 48

    # Dynamic Island
    $notchBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 28, 28, 35))
    Draw-RoundedRectangle $g $notchBrush $null 460 405 160 28 14

    if ($shotIndex -eq 1) {
        # Screen 1: Now Playing
        $artX = $fx + 100.0
        $artY = $fy + 100.0
        $artW = $fw - 200.0
        $artH = $artW
        $artBg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            (New-Object System.Drawing.Point $artX, $artY),
            (New-Object System.Drawing.Point ($artX + $artW), ($artY + $artH)),
            [System.Drawing.Color]::FromArgb(255, 45, 25, 75),
            [System.Drawing.Color]::FromArgb(255, 245, 158, 11)
        )
        Draw-RoundedRectangle $g $artBg $null $artX $artY $artW $artH 36
        $artBg.Dispose()

        $discBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 15, 15, 20))
        $discBorder = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 245, 158, 11), 3)
        $g.FillEllipse($discBrush, ($artX + 175), ($artY + 175), 150, 150)
        $g.DrawEllipse($discBorder, ($artX + 175), ($artY + 175), 150, 150)

        $tF = New-Object System.Drawing.Font("Segoe UI", 28, [System.Drawing.FontStyle]::Bold)
        $aF = New-Object System.Drawing.Font("Segoe UI", 18, [System.Drawing.FontStyle]::Regular)
        $g.DrawString("Midnight Horizon", $tF, $whiteBrush, ($fx + 100), ($artY + $artH + 40))
        $g.DrawString("Audiophile Studio Master - 24-bit / 96 kHz FLAC", $aF, $mutedBrush, ($fx + 100), ($artY + $artH + 90))

        # 60 FPS Visualizer
        $vPen = New-Object System.Drawing.Pen($accentColor, 5)
        $vx = $fx + 100
        $vy = $artY + $artH + 180
        $vHeights = @(20, 45, 90, 140, 80, 160, 210, 110, 70, 180, 230, 150, 95, 175, 220, 130, 85, 190, 105, 45, 25)
        foreach ($vh in $vHeights) {
            $g.DrawLine($vPen, $vx, ($vy - ($vh * 0.4)), $vx, ($vy + ($vh * 0.4)))
            $vx += 24
        }
        $vPen.Dispose()

        $scrubBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 45, 45, 55))
        $scrubFg = New-Object System.Drawing.SolidBrush($accentColor)
        Draw-RoundedRectangle $g $scrubBg $null ($fx + 100) ($vy + 120) ($fw - 200) 10 5
        Draw-RoundedRectangle $g $scrubFg $null ($fx + 100) ($vy + 120) 320 10 5
    }
    elseif ($shotIndex -eq 2) {
        # Screen 2: AI Subtitles
        $vX = $fx + 60.0
        $vY = $fy + 100.0
        $vW = $fw - 120.0
        $vH = 480.0
        $vBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 12, 16, 28))
        $vBorder = New-Object System.Drawing.Pen($accentColor, 2)
        Draw-RoundedRectangle $g $vBg $vBorder $vX $vY $vW $vH 24

        $subF1 = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
        $subF2 = New-Object System.Drawing.Font("Segoe UI", 20, [System.Drawing.FontStyle]::Bold)
        $cyanBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 224, 242, 254))
        $amberBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 158, 11))
        $pillB = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 10, 10, 15))

        Draw-RoundedRectangle $g $pillB $null ($vX + 60) ($vY + 310) ($vW - 120) 42 12
        $g.DrawString("Secondary: 'Works 100% offline on your device'", $subF1, $cyanBrush, ($vX + 75), ($vY + 318))

        Draw-RoundedRectangle $g $pillB $null ($vX + 40) ($vY + 370) ($vW - 80) 50 14
        $g.DrawString("Primary: 'Hindi & Regional AI Speech Transcription'", $subF2, $amberBrush, ($vX + 60), ($vY + 378))

        $bF = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
        $bBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 25, 30, 45))
        $bPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 56, 189, 248), 1)

        $langs = @("Hindi (Regional Focus)", "Tamil (On-Device AI)", "Telugu (Native Engine)", "Marathi (Offline Pack)", "English (Mandatory Fallback)")
        $ly = $vY + 540
        foreach ($l in $langs) {
            Draw-RoundedRectangle $g $bBg $bPen ($fx + 80) $ly ($fw - 160) 58 14
            $g.DrawString("[OK]  $l Active", $bF, $whiteBrush, ($fx + 110), ($ly + 16))
            $ly += 74
        }
    }
    elseif ($shotIndex -eq 3) {
        # Screen 3: Device Profiles
        $cardY = $fy + 100.0
        $cardW = $fw - 120.0
        $cardX = $fx + 60.0

        $tF = New-Object System.Drawing.Font("Segoe UI", 20, [System.Drawing.FontStyle]::Bold)
        $dF = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Regular)
        $activeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))

        $cards = @(
            @("TWS Earbuds Profile", "Auto EQ: Deep Bass Punch (+5 dB)", "Bluetooth Sync: +45 ms AV Lip-Sync"),
            @("Car Audio Profile", "Auto EQ: Warm Analog Acoustics", "Bluetooth Sync: +120 ms AV Delay"),
            @("Studio Headphones", "Auto EQ: Studio Reference (Flat)", "Latency: Sub-4ms Low-Jitter Direct"),
            @("Built-in Phone Speaker", "Auto EQ: Vocal Clarity Boost", "Mode: 0 ms Direct Native HAL")
        )

        foreach ($c in $cards) {
            $cBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 22, 27, 24))
            $cPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 40, 60, 45), 2)
            Draw-RoundedRectangle $g $cBg $cPen $cardX $cardY $cardW 135 20
            
            $g.DrawString($c[0], $tF, $whiteBrush, ($cardX + 24), ($cardY + 20))
            $g.DrawString($c[1], $dF, $activeBrush, ($cardX + 24), ($cardY + 58))
            $g.DrawString($c[2], $dF, $mutedBrush, ($cardX + 24), ($cardY + 88))
            $cardY += 165
        }
    }
    elseif ($shotIndex -eq 4) {
        # Screen 4: Dialogue Boost & Skip Intro
        $cardX = $fx + 60.0
        $cardW = $fw - 120.0
        $cardY = $fy + 100.0

        $cBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 30, 20, 28))
        $cBorder = New-Object System.Drawing.Pen($accentColor, 2)
        Draw-RoundedRectangle $g $cBg $cBorder $cardX $cardY $cardW 320 24

        $tF = New-Object System.Drawing.Font("Segoe UI", 22, [System.Drawing.FontStyle]::Bold)
        $dF = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Regular)

        $g.DrawString("Dialogue Boost DSP Filter", $tF, $whiteBrush, ($cardX + 30), ($cardY + 30))
        $g.DrawString("+4 dB / +7 dB Vocal Clarity (300 Hz - 3.4 kHz)`n-6 dB Low-End Rumble Attenuation (60 Hz)`nIsolates actor voices from explosive sound effects", $dF, $mutedBrush, ($cardX + 30), ($cardY + 85))

        $cardY2 = $cardY + 370
        Draw-RoundedRectangle $g $cBg $cBorder $cardX $cardY2 $cardW 280 24

        $g.DrawString("100% Offline Skip Intro", $tF, $whiteBrush, ($cardX + 30), ($cardY2 + 30))
        $g.DrawString("Analyzes embedded chapters and opening energy`nZero internet required`nOne-tap responsive skip button", $dF, $mutedBrush, ($cardX + 30), ($cardY2 + 85))

        $pillBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 158, 11))
        $black = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::Black)
        $pillFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
        Draw-RoundedRectangle $g $pillBg $null ($cardX + 160) ($cardY2 + 185) 300 55 18
        $g.DrawString(">>  Skip Intro  (0:85)", $pillFont, $black, ($cardX + 190), ($cardY2 + 198))
    }
    elseif ($shotIndex -eq 5) {
        # Screen 5: PC Sync
        $cardX = $fx + 60.0
        $cardW = $fw - 120.0
        $cardY = $fy + 100.0

        $cBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 20, 22, 38))
        $cBorder = New-Object System.Drawing.Pen($accentColor, 2)
        Draw-RoundedRectangle $g $cBg $cBorder $cardX $cardY $cardW 550 24

        $tF = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
        $dF = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Regular)
        $pinFont = New-Object System.Drawing.Font("Segoe UI", 42, [System.Drawing.FontStyle]::Bold)
        $indigo = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 129, 140, 248))

        $g.DrawString("Instant Local PC Pairing", $tF, $whiteBrush, ($cardX + 30), ($cardY + 30))
        $g.DrawString("Open desktop browser and enter the 6-digit PIN:`nhttp://192.168.1.15:8080", $dF, $mutedBrush, ($cardX + 30), ($cardY + 80))

        $pinBoxBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 30, 35, 60))
        Draw-RoundedRectangle $g $pinBoxBg $null ($cardX + 60) ($cardY + 160) ($cardW - 120) 100 20
        $g.DrawString("8  4  2  9  1  7", $pinFont, $indigo, ($cardX + 130), ($cardY + 180))

        $g.DrawString("Gigabit Local Wi-Fi Speed`nDrag-and-drop full albums, FLACs and MP4s`nZero cloud upload - 100% Private LAN", $dF, $whiteBrush, ($cardX + 30), ($cardY + 310))
    }

    $g.Dispose()
    $bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Generated $outFile"
}

Make-Screenshot "playstore_assets\screenshot_1_now_playing.png" "AUDIOPHILE SOUND" "Dynamic Mesh Art & 60 FPS Visualizer" "Bit-perfect 32-bit Float decoding with responsive real-time acoustics" "#F59E0B" 1
Make-Screenshot "playstore_assets\screenshot_2_ai_subtitles.png" "AI VIDEO INTELLIGENCE" "Offline AI Subtitles & Dual Stacking" "On-device speech recognition for Hindi, Tamil, Telugu, Marathi & English" "#38BDF8" 2
Make-Screenshot "playstore_assets\screenshot_3_device_profiles.png" "HARDWARE-AWARE AUDIO" "Auto Device Profiles & Lip-Sync" "Detects Earbuds, Headphones and Car Audio with persistent Bluetooth delay" "#10B981" 3
Make-Screenshot "playstore_assets\screenshot_4_dialogue_boost.png" "CLEAN CINEMATIC SOUND" "Dialogue Boost & Offline Skip Intro" "Isolate actor speech over explosive sound effects with one-tap intro skip" "#EC4899" 4
Make-Screenshot "playstore_assets\screenshot_5_pc_sync.png" "ZERO-CLOUD ECOSYSTEM" "Local Wi-Fi PC Sync & PIN Pairing" "Wireless gigabit music and video transfers directly from desktop browser" "#6366F1" 5
