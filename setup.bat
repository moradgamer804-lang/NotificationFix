# NotificationFix - One Click Setup
# PowerShell e run kore sob hoye jabe!

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   NotificationFix - Auto Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Git install check
if (!(Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "Git install korben:" -ForegroundColor Yellow
    Write-Host "https://git-scm.com/download/win" -ForegroundColor Green
    Write-Host "Tarpor abar ei script run korun" -ForegroundColor Yellow
    Start-Process "https://git-scm.com/download/win"
    pause
    exit
}

Write-Host "Git ache! Continue korchi..." -ForegroundColor Green

# Navigate to project
Set-Location "C:\Users\Admin\Documents\New folder (3)\NotificationFix"

# Git init
Write-Host ""
Write-Host "1/5 - Git initialize korchi..." -ForegroundColor Yellow
git init

# Git config (if not set)
Write-Host ""
Write-Host "2/5 - Git config set korchi..." -ForegroundColor Yellow
$read = Read-Host "GitHub Username likhun"
$remail = Read-Host "GitHub Email likhun"
git config --global user.name $read
git config --global user.email $remail

# Add files
Write-Host ""
Write-Host "3/5 - Files add korchi..." -ForegroundColor Yellow
git add .
git commit -m "NotificationFix LSPosed module - SMS flood protection"

# Remote add
Write-Host ""
Write-Host "4/5 - GitHub remote add korchi..." -ForegroundColor Yellow
Write-Host ""
Write-Host "IMPORTANT: GitHub e ekta repo create kore or URL copy kore likhun" -ForegroundColor Red
Write-Host "GitHub e jayen > '+' > 'New repository' > Name: NotificationFix > Create" -ForegroundColor Yellow
Write-Host ""
$repoUrl = Read-Host "GitHub Repository URL paste kore likhun (example: https://github.com/username/NotificationFix.git)"
git remote add origin $repoUrl

# Push
Write-Host ""
Write-Host "5/5 - GitHub e push korchi..." -ForegroundColor Yellow
git branch -M main
git push -u origin main

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   SOB HOYE GELA!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "APK download korar jonno:" -ForegroundColor Yellow
Write-Host "1. GitHub e repo khulun" -ForegroundColor White
Write-Host "2. 'Actions' tab e click korun" -ForegroundColor White
Write-Host "3. Build shesh hole 'NotificationFix-debug' download korun" -ForegroundColor White
Write-Host ""
pause
