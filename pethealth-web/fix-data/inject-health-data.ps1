# 健康记录演示数据注入（近 30 天，多类型，符合物种特征）
# 用法: powershell -File inject-health-data.ps1
$ErrorActionPreference = 'Stop'
$base = Get-Date '2026-08-06'
$end  = Get-Date '2026-09-05'

function Push-Records {
    param($petId, $ownerId, $type, $unit, $n, $startOffsetDays, $stepDays, $v0, $v1, $prec, $noiseScale)
    for ($i = 0; $i -lt $n; $i++) {
        $d = $base.AddDays($startOffsetDays + $i * $stepDays)
        if ($d -gt $end) { continue }
        $frac = if ($n -gt 1) { $i / [double]($n - 1) } else { 0 }
        $v = $v0 + ($v1 - $v0) * $frac + (Get-Random -Minimum (-$noiseScale) -Maximum $noiseScale)
        $v = [math]::Round($v, $prec)
        $rec = @{
            petId      = $petId
            ownerId    = $ownerId
            recordType = $type
            value      = @{ value = $v; unit = $unit }
            recordedAt = $d.ToString('yyyy-MM-ddTHH:mm:ss')
            notes      = $null
        } | ConvertTo-Json -Depth 4 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($rec)
        Invoke-RestMethod -Uri 'http://localhost:8080/api/health-records' -Method Post `
            -ContentType 'application/json; charset=utf-8' -Body $bytes | Out-Null
        Write-Host "OK  $type  $v $unit  $($d.ToString('MM-dd'))"
    }
}

# ---------- 小橘（猫 · 2 岁 · 4.6->5.0kg 缓升；补充 8-31 前的体重/体温/心率/饮食/运动）----------
$xiaoju  = '6a84140ba01d86426fe96e26'
$ownerDemo = '6a84140aa01d86426fe96e1e'
Push-Records $xiaoju $ownerDemo '体重' 'kg'     6 4 4 4.6 4.8 1 0.12
Push-Records $xiaoju $ownerDemo '体温' '℃'     5 3 5 38.4 38.8 1 0.15
Push-Records $xiaoju $ownerDemo '心率' '次/分' 6 4 4 155 180 0 8
Push-Records $xiaoju $ownerDemo '饮食' 'g'     7 0 4 135 160 0 6
Push-Records $xiaoju $ownerDemo '运动' '分钟'  4 5 7 12 24 0 4

# ---------- 豆豆（柯基犬 · 1 岁 4 个月 · 10.2->11.0kg 成长期）----------
$doudou = '6a84140ba01d86426fe96e27'
$ownerAdmin = '6a84140aa01d86426fe96e1f'
Push-Records $doudou $ownerAdmin '体重' 'kg'     9 0 3 10.2 11.0 1 0.15
Push-Records $doudou $ownerAdmin '体温' '℃'     7 1 4 38.0 38.6 1 0.15
Push-Records $doudou $ownerAdmin '心率' '次/分' 7 2 4 96 118 0 8
Push-Records $doudou $ownerAdmin '饮食' 'g'     7 0 4 180 220 0 10
Push-Records $doudou $ownerAdmin '运动' '分钟'  6 2 5 35 60 0 6
Push-Records $doudou $ownerAdmin '排便' '次'    5 3 6 1 2 0 0.5

# ---------- 绿豆（鹦鹉 · 约 7 个月 · 60->67g 幼鸟生长，单位 kg）----------
$lvdou  = '6a9a7edc6c58a94073dcf761'
Push-Records $lvdou $ownerDemo '体重' 'kg'    10 0 3 0.060 0.067 2 0.002
Push-Records $lvdou $ownerDemo '体温' '℃'     7 1 4 40.8 41.5 1 0.2
Push-Records $lvdou $ownerDemo '心率' '次/分' 6 3 5 330 390 0 15
Push-Records $lvdou $ownerDemo '饮食' 'g'     6 2 5 22 28 0 1.5
Push-Records $lvdou $ownerDemo '运动' '分钟'  5 4 6 30 40 0 4

Write-Host '== 注入完成 =='
