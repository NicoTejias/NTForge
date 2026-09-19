# run-mmo-test.ps1
$ErrorActionPreference = "Stop"

cd C:\dev\NTForge\forge-gui

Write-Host "Generando classpath..." -ForegroundColor Green
& "C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd" dependency:build-classpath -Dmdep.outputFile=classpath.txt -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Falló al generar classpath" -ForegroundColor Red
    exit 1
}

Write-Host "Iniciando servidor MMO en puerto 8089..." -ForegroundColor Green
$serverProcess = Start-Process -FilePath "java" -ArgumentList "-cp", "target/classes;classpath.txt", "forge.mmo.server.MMOServer", "8089" -PassThru -WindowStyle Hidden

Start-Sleep -Seconds 3

Write-Host "Ejecutando cliente de prueba..." -ForegroundColor Green
$classpathContent = Get-Content classpath.txt
$classpath = "target/classes;" + $classpathContent

$testResult = java -cp $classpath forge.mmo.TestClient

Write-Host "Prueba completada." -ForegroundColor Green
$serverProcess.Stop()