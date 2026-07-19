param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\src\main\resources\autotorch.png')
)

Add-Type -AssemblyName System.Drawing

$size = 128
$bitmap = [System.Drawing.Bitmap]::new($size, $size)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

function Add-Rect {
    param([int]$X, [int]$Y, [int]$Width, [int]$Height, [string]$Color)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($Color))
    try {
        $graphics.FillRectangle($brush, $X, $Y, $Width, $Height)
    }
    finally {
        $brush.Dispose()
    }
}

function Add-Polygon {
    param([System.Drawing.Point[]]$Points, [string]$Color)
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($Color))
    try {
        $graphics.FillPolygon($brush, $Points)
    }
    finally {
        $brush.Dispose()
    }
}

try {
    Add-Rect 0 0 128 128 '#15191D'

    $stoneTiles = @(
        @(4, 4, 28, 24, '#343C42'), @(36, 4, 36, 24, '#2B3238'), @(76, 4, 48, 24, '#3B444A'),
        @(4, 32, 20, 28, '#2A3035'), @(28, 32, 44, 28, '#3A4248'), @(76, 32, 24, 28, '#293036'), @(104, 32, 20, 28, '#353D43'),
        @(4, 64, 36, 28, '#394147'), @(44, 64, 28, 28, '#293036'), @(76, 64, 48, 28, '#364047'),
        @(4, 96, 24, 28, '#282F34'), @(32, 96, 40, 28, '#343C42'), @(76, 96, 28, 28, '#293136'), @(108, 96, 16, 28, '#3A4248')
    )
    foreach ($tile in $stoneTiles) {
        Add-Rect $tile[0] $tile[1] $tile[2] $tile[3] $tile[4]
    }

    Add-Rect 8 8 8 36 '#36D06C'
    Add-Rect 8 8 36 8 '#36D06C'
    Add-Rect 84 8 36 8 '#36D06C'
    Add-Rect 112 8 8 36 '#36D06C'
    Add-Rect 8 84 8 36 '#36D06C'
    Add-Rect 8 112 36 8 '#36D06C'
    Add-Rect 84 112 36 8 '#36D06C'
    Add-Rect 112 84 8 36 '#36D06C'

    Add-Polygon @(
        [System.Drawing.Point]::new(64, 20),
        [System.Drawing.Point]::new(84, 44),
        [System.Drawing.Point]::new(76, 68),
        [System.Drawing.Point]::new(52, 68),
        [System.Drawing.Point]::new(44, 44)
    ) '#E94B32'
    Add-Polygon @(
        [System.Drawing.Point]::new(64, 28),
        [System.Drawing.Point]::new(76, 48),
        [System.Drawing.Point]::new(70, 64),
        [System.Drawing.Point]::new(54, 64),
        [System.Drawing.Point]::new(52, 48)
    ) '#FF9F1C'
    Add-Polygon @(
        [System.Drawing.Point]::new(64, 40),
        [System.Drawing.Point]::new(70, 52),
        [System.Drawing.Point]::new(66, 64),
        [System.Drawing.Point]::new(58, 60),
        [System.Drawing.Point]::new(58, 52)
    ) '#FFE66D'
    Add-Rect 52 64 24 12 '#774321'
    Add-Rect 56 76 16 40 '#8F5528'
    Add-Rect 60 76 8 40 '#C27A36'
    Add-Rect 52 64 24 4 '#E2A94F'

    $destination = [System.IO.Path]::GetFullPath($OutputPath)
    $directory = [System.IO.Path]::GetDirectoryName($destination)
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $bitmap.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output $destination
}
finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
