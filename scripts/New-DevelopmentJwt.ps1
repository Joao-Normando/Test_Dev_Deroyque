param(
    [string]$Scope = "requests:read requests:write metrics:read",
    [ValidateRange(1, 24 * 60)]
    [int]$LifetimeMinutes = 60,
    [string]$Subject = "local-dev",
    [string]$Secret = $env:JWT_SECRET,
    [string]$Issuer = "https://identity.example.com",
    [string]$Audience = "activation-service"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Secret)) {
    $Secret = "local-development-secret-key-change-this-32bytes-min"
}

$utf8 = [System.Text.Encoding]::UTF8
$base64Url = {
    param([byte[]]$Bytes)
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$issuedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$headerJson = '{"alg":"HS256","typ":"JWT"}'
$payloadJson = @{
    iss   = $Issuer
    aud   = @($Audience)
    sub   = $Subject
    iat   = $issuedAt
    exp   = $issuedAt + ($LifetimeMinutes * 60)
    scope = $Scope
} | ConvertTo-Json -Compress

$encodedHeader = & $base64Url $utf8.GetBytes($headerJson)
$encodedPayload = & $base64Url $utf8.GetBytes($payloadJson)
$unsignedToken = "$encodedHeader.$encodedPayload"

$hmac = [System.Security.Cryptography.HMACSHA256]::new($utf8.GetBytes($Secret))
try {
    $signature = $hmac.ComputeHash($utf8.GetBytes($unsignedToken))
}
finally {
    $hmac.Dispose()
}

$encodedSignature = & $base64Url $signature
Write-Output "$unsignedToken.$encodedSignature"
