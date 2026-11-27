@echo off
REM ═══════════════════════════════════════════════════════════════════════
REM  Doble clic aqui para saber si SICOT esta prendido en este equipo y
REM  con que direccion se entra.
REM  Solo mira: no prende ni apaga nada.
REM ═══════════════════════════════════════════════════════════════════════
chcp 65001 > nul
title SICOT - estado del servidor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\servidor\estado-sicot.ps1"
