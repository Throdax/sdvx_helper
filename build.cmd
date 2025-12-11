@echo off
SETLOCAL EnableDelayedExpansion

set target_sdvx=sdvx_helper
set target_ocr=ocr_reporter
set target_manager=manage_score
set target_updater=update
set target_sync=play_log_sync
set pyin="%userprofile%\AppData\Local\Programs\Python\Python314\Scripts\pyinstaller.exe"
REM set pyin="%userprofile%\AppData\Roaming\Python\Python313\Scripts\pyinstaller.exe"

set SDVX=Not Build
set OCR=Not Build
set MANAGER=Not Build
set UPDATER=Not Build
set SYNC=Not Build

if "%1" == "sdvx" set SDVX=Build
if "%1" == "helper" set SDVX=Build
if "%2" == "sdvx" set SDVX=Build
if "%2" == "helper" set SDVX=Build
if "%3" == "sdvx" set SDVX=Build
if "%3" == "helper" set SDVX=Build
if "%4" == "sdvx" set SDVX=Build
if "%4" == "helper" set SDVX=Build


if "%1" == "ocr" set OCR=Build
if "%2" == "ocr" set OCR=Build
if "%3" == "ocr" set OCR=Build
if "%4" == "ocr" set OCR=Build

if "%1" == "manager" set MANAGER=Build
if "%2" == "manager" set MANAGER=Build
if "%3" == "manager" set MANAGER=Build
if "%4" == "manager" set MANAGER=Build

if "%1" == "updater" set UPDATER=Build
if "%2" == "updater" set UPDATER=Build
if "%3" == "updater" set UPDATER=Build
if "%4" == "updater" set UPDATER=Build

if "%1" == "sync" set SYNC=Build
if "%2" == "sync" set SYNC=Build
if "%3" == "sync" set SYNC=Build
if "%4" == "sync" set SYNC=Build

if "%1" == "*" (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
 	set UPDATER=Build
 	set SYNC=Build
)
if "%1" == "all" (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
 	set UPDATER=Build
 	set SYNC=Build
)
if [%1] == [] (
	set SDVX=Build
 	set OCR=Build
 	set MANAGER=Build
 	set UPDATER=Build
 	set SYNC=Build
)

echo SDVX: %SDVX%
echo OCR: %OCR%
echo MANAGER: %MANAGER%
echo UPDATER: %UPDATER%
echo SYNC: %SYNC%

del /F /Q dist\*.exe

if "%SDVX%" == "Build" %pyin% %target_sdvx%.pyw --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%OCR%" == "Build" %pyin% %target_ocr%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --add-data "NotoSansJP-Regular.ttf;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%MANAGER%" == "Build" %pyin% %target_manager%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%UPDATER%" == "Build" %pyin% %target_updater%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
if "%SYNC%" == "Build" %pyin% %target_sync%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y

if exist %target_sdvx% powershell Remove-Item -Path %target_sdvx% -Recurse -Force

REM binaries
xcopy /Y dist\*.exe %target_sdvx%\
xcopy /Y .\version.properties %target_sdvx%\

REM resources folder
xcopy /E /I /Y resources %target_sdvx%\resources
del /F /Q %target_sdvx%\resources\*.bk
del /F /Q %target_sdvx%\resources\*.size8

REM out folder
del /F /Q out\*.xml
del /F /Q out\*.pkl
xcopy /E /I /Y out %target_sdvx%\out
del /F /Q %target_sdvx%\out\rival*.pkl

REM Zip everything
if exist %target_sdvx%_en_all.zip del /F /Q %target_sdvx%_en_all.zip

if "%SDVX%" == "Build" (
    if "%OCR%" == "Build" (
        if "%MANAGER%" == "Build" (
            if "%UPDATER%" == "Build" (
                if "%SYNC%" == "Build" (
                    powershell -Command "Compress-Archive -Path %target_sdvx%\* -DestinationPath %target_sdvx%_en_all.zip"
                )
            )
        )
    )
)

