@echo off
if exist out rmdir /S /Q out
mkdir out
javac -encoding UTF-8 -d out src\modelo\*.java src\servico\*.java src\controle\*.java src\Principal.java src\testes\*.java
