@echo off
setlocal

set BASE_DIR=%~dp0
set WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
set WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties

if not "%JAVA_HOME%" == "" (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

if not exist "%WRAPPER_JAR%" (
  for /f "tokens=1,* delims==" %%A in (%WRAPPER_PROPS%) do (
    if "%%A" == "wrapperUrl" set WRAPPER_URL=%%B
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
)

"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
