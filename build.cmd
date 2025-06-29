@echo off
SETLOCAL EnableDelayedExpansion

set target_sdvx=sdvx_helper
set target_ocr=ocr_reporter
set target_manager=manage_score
set pyin="%userprofile%\AppData\Roaming\Python\Python313\Scripts\pyinstaller.exe"

set SDVX=Not Build
set OCR=Not Build
set MANAGER=Not Build

if "%1" == "sdvx" set SDVX=Build
if "%1" == "helper" set SDVX=Build
if "%2" == "sdvx" set SDVX=Build
if "%2" == "helper" set SDVX=Build
if "%3" == "sdvx" set SDVX=Build
if "%3" == "helper" set SDVX=Build

if "%1" == "ocr" set OCR=Build
if "%2" == "ocr" set OCR=Build
if "%3" == "ocr" set OCR=Build

if "%1" == "manager" set MANAGER=Build
if "%2" == "manager" set MANAGER=Build
if "%3" == "manager" set MANAGER=Build

if "%1" == "*" (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
)
if "%1" == "all" (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
)
if [%1] == [] (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
)

echo SDVX: %SDVX%
echo OCR: %OCR%
echo MANAGER: %MANAGER%

del /F /Q dist\*.exe

if "%SDVX%" == "Build" %pyin% %target_sdvx%.pyw --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%OCR%" == "Build" %pyin% %target_ocr%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%MANAGER%" == "Build" %pyin% %target_manager%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y

xcopy /Y dist\sdvx_helper.exe %target_sdvx%\

del /F /Q %target_sdvx%\out\rival*.pkl

xcopy /E /I /Y resources %target_sdvx%\resources

del /F /Q out\*.xml
del /F /Q out\*.pkl

xcopy /E /I /Y out %target_sdvx%\out

copy version.txt %target_sdvx%\

if exist %target_sdvx%.zip del /F /Q %target_sdvx%.zip

powershell -Command "Compress-Archive -Path %target_sdvx%\* -DestinationPath %target_sdvx%.zip"

