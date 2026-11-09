@echo off
REM ═══════════════════════════════════════════════════════════════════════
REM  Doble clic aqui para prender el servidor de SICOT.
REM
REM  chcp 65001 pone la consola en UTF-8: sin esto, las tildes y los
REM  recuadros del script se ven como simbolos raros.
REM  ExecutionPolicy Bypass es solo para esta ejecucion; no cambia la
REM  configuracion del equipo.
REM ═══════════════════════════════════════════════════════════════════════
chcp 65001 > nul
title SICOT - prender el servidor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\servidor\prender-sicot.ps1"
