@echo off
REM ═══════════════════════════════════════════════════════════════════════
REM  Doble clic aqui para apagar el servidor de SICOT.
REM  Los datos no se borran.
REM ═══════════════════════════════════════════════════════════════════════
chcp 65001 > nul
title SICOT - apagar el servidor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\servidor\apagar-sicot.ps1"
