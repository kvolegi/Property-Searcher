@echo off
title Property Searcher 3 Launcher
color 0B
echo ========================================================
echo       Starting Property Searcher 3
echo       Platforms: OLX.ua ^& DOM.RIA
echo ========================================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java Runtime Environment not found in PATH!
    echo Please install Java 17 or higher from https://adoptium.net/
    pause
    exit /b
)

echo Launching Property Searcher GUI...
start javaw -jar PropertySeacher-Universal.jar
exit
