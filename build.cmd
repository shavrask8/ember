@echo off
rem Build Ember for Windows (WPF, .NET 10). Result: out\Ember.exe
setlocal
cd /d "%~dp0"
dotnet publish windows\Ember.csproj -c Release -o out -nologo || exit /b 1
echo OK: out\Ember.exe
