@echo off
REM ============================================================
REM  Bam dup file nay 1 LAN de tu tao shortcut "ME Launcher"
REM  ngoai Desktop, tro toi ME Launcher.exe nam CUNG thu muc.
REM  Dung PowerShell (khong qua Windows Script Host nhu ban .vbs)
REM  nen it bi mot so may chan hon.
REM ============================================================

if not exist "%~dp0ME Launcher.exe" (
    echo Khong tim thay "ME Launcher.exe" cung thu muc voi file nay.
    echo Kiem tra lai ban da giai nen DAY DU file .zip chua nhe.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('%USERPROFILE%\Desktop\ME Launcher.lnk'); $s.TargetPath = '%~dp0ME Launcher.exe'; $s.WorkingDirectory = '%~dp0'; $s.Description = 'ME Launcher - Launcher Minecraft tu viet'; $s.Save()"

if exist "%USERPROFILE%\Desktop\ME Launcher.lnk" (
    echo.
    echo Da tao xong shortcut "ME Launcher" ngoai Desktop!
) else (
    echo.
    echo Khong tu tao duoc shortcut. Hay lam thu cong:
    echo Chuot phai vao ME Launcher.exe roi chon Send to - Desktop.
)
pause
