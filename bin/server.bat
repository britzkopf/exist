@echo off

rem
rem In addition to the other parameter options for the standalone server 
rem pass -j or --jmx to enable JMX agent.  The port for it can be specified 
rem with optional port number e.g. -j1099 or --jmx=1099.
rem

set JMX_ENABLED=0
set JMX_PORT=1099
set JAVA_ARGS=

::remove any quotes from JAVA_HOME and EXIST_HOME env vars if present
for /f "delims=" %%G IN ("%JAVA_HOME%") DO SET "JAVA_HOME=%%~G"
for /f "delims=" %%G IN ("%EXIST_HOME%") DO SET "EXIST_HOME=%%~G"

set JAVA_RUN="java"

if not "%JAVA_HOME%" == "" (
    set JAVA_RUN="%JAVA_HOME%\bin\java"
    goto gotJavaHome
)

echo WARNING: JAVA_HOME not found in your environment.
echo.
echo Please, set the JAVA_HOME variable in your environment to match the
echo location of the Java Virtual Machine you want to use in case of run fail.
echo.

:gotJavaHome

if not "%EXIST_APP_HOME%" == "" goto gotExistAppHome
set EXIST_APP_HOME="%~dp0\.."
if exist "%EXIST_APP_HOME%\start.jar" goto gotExistAppHome
echo EXIST_APP_HOME not found. Please set your
echo EXIST_APP_HOME environment variable to the
echo installation directory of eXist-db.
goto :eof
:gotExistAppHome

if not "%JAVA_OPTIONS%" == "" goto doneJavaOptions
set MX=2048
set JAVA_OPTIONS="-Xms128m -Xmx%MX%m -Dfile.encoding=UTF-8"
:doneJavaOptions

if "%EXIST_HOME%" == "" goto doneExistHome
set OPTIONS="-Dexist.home=%EXIST_HOME%"
:doneExistHome

:: copy the command line args preserving equals chars etc. for thinks like -ouri=http://something
for /f "tokens=*" %%x IN ("%*") DO SET "CMD_LINE_ARGS=%%x"
set BATCH.D="%~dp0\batch.d"
call %BATCH.D%\get_opts.bat %CMD_LINE_ARGS%
call %BATCH.D%\check_jmx_status.bat

%JAVA_RUN% "%JAVA_OPTIONS%" "%OPTIONS%" -jar "%EXIST_APP_HOME%\start.jar" standalone %JAVA_ARGS%
:eof
