# Run once from PowerShell: powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1
# Does not send credentials anywhere; saves BOT_TOKEN to local ignored .env.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$template = Join-Path $root '.env.example'
$target = Join-Path $root '.env'
if (-not (Test-Path $target)) { Copy-Item $template $target }

function Set-DotEnvValue([string]$Key, [string]$Value) {
    # Escape controls that would corrupt dotenv entries. Expected Telegram token is alphanumeric.
    if ($Value -match '[\r\n]') { throw "Invalid newline in $Key" }
    $lines = @(Get-Content -Path $target -Encoding UTF8)
    $written = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^' + [regex]::Escape($Key) + '=')) {
            $lines[$i] = "$Key=$Value"
            $written = $true
        }
    }
    if (-not $written) { $lines += "$Key=$Value" }
    [System.IO.File]::WriteAllLines($target, $lines, [System.Text.UTF8Encoding]::new($false))
}

Write-Host "Настройка Telegram-бота. Данные сохраняются только в .env (он исключен из Git)."
$secret = Read-Host 'Вставьте BOT_TOKEN от BotFather (ввод скрыт)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($token -notmatch '^\d+:[A-Za-z0-9_-]{20,}$') { throw 'BOT_TOKEN не похож на токен Telegram. Проверьте значение у @BotFather.' }
    Set-DotEnvValue 'BOT_TOKEN' $token
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $token = $null
}

$username = (Read-Host 'Username бота без @ (необязательно)').Trim().TrimStart('@')
if ($username) { Set-DotEnvValue 'BOT_USERNAME' $username }
$admin = (Read-Host 'Ваш Telegram chat_id (необязательно)').Trim()
if ($admin) {
    if ($admin -notmatch '^-?\d+$') { throw 'ADMIN_CHAT_ID должен состоять из цифр.' }
    Set-DotEnvValue 'ADMIN_CHAT_ID' $admin
}
$name = (Read-Host 'Название студии (Enter — оставить прежнее)').Trim()
if ($name) { Set-DotEnvValue 'SALON_NAME' $name }
$url = (Read-Host 'HTTPS URL Mini App (Enter — настроить позже)').Trim()
if ($url) {
    if ($url -notmatch '^https://[^\s]+$') { throw 'Укажите полный https:// адрес.' }
    Set-DotEnvValue 'APP_BASE_URL' $url.TrimEnd('/')
}
Write-Host 'Готово! Секрет сохранен в .env. Для запуска: docker compose up --build -d'
Write-Host 'Для Mini App внутри Telegram понадобится публичный HTTPS-адрес.'
