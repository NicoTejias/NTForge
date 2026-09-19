@echo off
cd /d "C:\dev\NTForge\forge-gui"

echo Generando classpath...
call mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q

if %ERRORLEVEL% NEQ 0 (
    ERROR: Falló al generar classpath
    exit /b 1
)

echo Iniciando servidor MMO en puerto 8089...
start "Forge MMO Server" java -cp "target/classes;classpath.txt" forge.mmo.server.MMOServer 8089

timeout /t 3 /nobreak > nul

echo Ejecutando cliente de prueba...
java -cp "target/classes;classpath.txt" forge.mmo.TestClient

echo Prueba completada.