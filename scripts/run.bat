@echo off
REM PHANTOM Mining Run Script for Windows

REM Set Java heap size for large datasets
set JAVA_OPTS=-Xmx4g -Xms2g

REM Run with all arguments passed through
java %JAVA_OPTS% -jar target\phantom-mining-1.0-SNAPSHOT-jar-with-dependencies.jar %*