@echo off
chcp 65001 >nul
title NotificationFix - GitHub Push

echo ========================================
echo   NotificationFix - GitHub Upload
echo ========================================
echo.
echo GitHub e repo create kore URL niye ashen
echo.
echo 1. Browser e https://github.com khulun
echo 2. Login kore "+" icon > "New repository"
echo 3. Repository name: NotificationFix
echo 4. Create repository click kore
echo 5. Page er URL ta copy kore rakhen
echo.

REM Store the URL
set /p REPO_URL="GitHub Repository URL paste korun (ENTER diye): "

cd /d "%USERPROFILE%\Documents\New folder (3)\NotificationFix"

echo.
echo Setting up git...
git init
git add .
git commit -m "NotificationFix LSPosed module v1.0"

echo.
echo Adding remote...
git remote add origin %REPO_URL%
git branch -M main

echo.
echo ========================================
echo   Now pushing to GitHub...
echo   Next window e GitHub login ask korbe
echo   Apnar email/password dite hobe
echo   (Ei info ami dekhbo na!)
echo ========================================
echo.

git push -u origin main

echo.
echo ========================================
if %errorlevel%==0 (
    echo   SUCCESS! APK build hobe automatically!
    echo   GitHub > Actions tab e jayen
) else (
    echo   ERROR hoise. Try abar korun
)
echo ========================================
pause
