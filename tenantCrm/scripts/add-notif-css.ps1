$templates = Join-Path $PSScriptRoot "..\src\main\resources\templates"
Get-ChildItem $templates -Filter *.html | ForEach-Object {
    $c = [IO.File]::ReadAllText($_.FullName)
    if ($c -notmatch 'auth\.js' -or $c -match 'notifications\.css') { return }
    $idx = $c.IndexOf('<link rel="stylesheet"')
    if ($idx -lt 0) { return }
    $end = $c.IndexOf('>', $idx) + 1
    $insert = $c.Substring(0, $end) + "`n`t<link rel=`"stylesheet`" th:href=`"@{/notifications.css}`">" + $c.Substring($end)
    [IO.File]::WriteAllText($_.FullName, $insert)
    Write-Output $_.Name
}
