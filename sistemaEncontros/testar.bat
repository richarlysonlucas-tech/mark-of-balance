@echo off
call compilar.bat
if errorlevel 1 exit /b 1
java -cp out testes.TesteServicoEncontro
