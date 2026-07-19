echo off
set JAVA_HOME=D:\Dev\env\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
D:\Dev\env\Java\maven\apache-maven-3.9.14\bin\mvn.cmd compile -pl hospital-common -f D:\Dev\workspace\project\GP\hospital-appointment-system\pom.xml > D:\Dev\workspace\project\GP\hospital-appointment-system\mvn_compile.log 2>&1
echo DONE > D:\Dev\workspace\project\GP\hospital-appointment-system\mvn_done.txt
