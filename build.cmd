@echo off
set target_sdvx=sdvx_helper
set target_ocr=ocr_reporter
set target_manager=manage_score
set pyin="D:\Program Files\Python312\Scripts\pyinstaller.exe"

%pyin% %target_sdvx%.pyw --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
%pyin% %target_ocr%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y
%pyin% %target_manager%.py --clean --noconsole --onefile --icon=icon.ico --add-data "icon.ico;./" --hidden-import=tkinter --hidden-import=tkinter.filedialog -y

REM xcopy /Y dist\*.exe to_bin\
xcopy /Y dist\*.exe %target_sdvx%\

REM del /F /Q %target_sdvx%\ocr_reporter.exe
REM del /F /Q %target_sdvx%\manage_score.exe
del /F /Q %target_sdvx%\out\rival*.pkl

REM xcopy /E /I /Y resources to_bin\
xcopy /E /I /Y resources %target_sdvx%\resources

del /F /Q out\*.xml
del /F /Q out\*.pkl

REM xcopy /E /I /Y out to_bin\
xcopy /E /I /Y out %target_sdvx%\out

copy version.txt %target_sdvx%\

if exist %target_sdvx%.zip del /F /Q %target_sdvx%.zip

powershell -Command "Compress-Archive -Path %target_sdvx%\* -DestinationPath %target_sdvx%.zip"

