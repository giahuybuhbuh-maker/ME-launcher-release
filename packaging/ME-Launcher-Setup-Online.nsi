; ============================================================
;  ME Launcher - Setup (Phong cach Legacy Launcher)
;  Co trang License + Tao Ldoc_i5n + Dong bo day du JDK 25 runtime\bin
; ============================================================

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "TextFunc.nsh"

; Tu dong tao file LICENSE.txt chuan UTF-8 BOM ngay luc Compile
!system `powershell -NoProfile -Command "[System.IO.File]::WriteAllText('LICENSE.txt', [regex]::Unescape('GI\u1EA4Y PH\u00C9P S\u1EEC D\u1EE4NG PH\u1EA6N M\u1EC0M ME LAUNCHER\r\n======================================\r\n\r\nB\u1EA3n quy\u1EC1n (c) 2026 TonygamingTL / ME-Launcher.\r\n\r\n1. Ph\u1EA7n m\u1EC1m ME Launcher \u0111\u01B0\u1EE3c ph\u00E1t h\u00E0nh mi\u1EC5n ph\u00ED h\u1ED7 tr\u1EE3 c\u1ED9ng \u0111\u1ED3ng ng\u01B0\u1EDDi ch\u01A1i Minecraft.\r\n2. H\u1ED7 tr\u1EE3 t\u1EF1 \u0111\u1ED9ng t\u1ED1i \u01B0u h\u00F3a JVM (Aikar Flags) v\u00E0 Windows Graphics High Performance.\r\n3. H\u1ED7 tr\u1EE3 c\u00E0i \u0111\u1EB7t c\u00E1c Mod Loader (Fabric, Forge, NeoForge, OptiFine, Quilt).\r\n\r\nB\u1EB1ng vi\u1EC7c nh\u1EA5n \u0110\u1ED3ng \u00FD, b\u1EA1n ch\u1EA5p nh\u1EADn c\u00E0i \u0111\u1EB7t ME Launcher l\u00EAn m\u00E1y t\u00EDnh c\u1EE7a m\u00ECnh.'), [System.Text.Encoding]::UTF8)"`

Name "ME Launcher"
OutFile "ME-Launcher-Setup.exe"
InstallDir "$LOCALAPPDATA\ME Launcher"
InstallDirRegKey HKCU "Software\MELauncher" "InstallDir"
RequestExecutionLevel user
Unicode true

VIProductVersion "0.0.1.7"
VIAddVersionKey "ProductName" "ME Launcher"
VIAddVersionKey "CompanyName" "TonygamingTL / ME-Launcher"
VIAddVersionKey "FileDescription" "ME Launcher Installer"
VIAddVersionKey "FileVersion" "0.1.7.0"
VIAddVersionKey "LegalCopyright" "Copyright (c) 2026"

!define DOWNLOAD_URL "https://github.com/giahuybuhbuh-maker/ME-launcher-release/releases/download/v0.1.7/Me-Launcher-v0.1.7-alpha01.zip"
!define ADOPTIUM_JDK25_URL "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"

!define MUI_ABORTWARNING
!define MUI_ICON "..\src\main\resources\assets\ME_Launcher.ico"
!define MUI_UNICON "..\src\main\resources\assets\ME_Launcher.ico"

; Trang Chao mung & Trang Hoan tat
!define MUI_WELCOMEPAGE_TITLE "Chào mừng bạn đến với trình cài đặt ME Launcher"
!define MUI_WELCOMEPAGE_TEXT "Trình cài đặt sẽ thiết lập ME Launcher cùng cấu trúc Ldoc_i5n và bộ Runtime Java 25 đầy đủ lên máy tính của bạn, đồng thời tự động tối ưu hóa Windows Graphics High Performance cho Minecraft.$\r$\n$\r$\nNhấn 'Tiến' để bắt đầu."

!define MUI_FINISHPAGE_RUN "$INSTDIR\ME Launcher.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Khởi động ME Launcher ngay bây giờ"
!define MUI_FINISHPAGE_LINK "Truy cập trang chủ ME Launcher"
!define MUI_FINISHPAGE_LINK_LOCATION "https://giahuybuhbuh-maker.github.io/melauncher.app-website/"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Vietnamese"

Section "Cai dat ME Launcher" SecInstall
    AddSize 220000

    SetOutPath "$INSTDIR"
    SetDetailsPrint both

    ; 1. Nhung file icon ME_Launcher.ico va LICENSE.txt
    File "..\src\main\resources\assets\ME_Launcher.ico"
    File "LICENSE.txt"

    ; 2. Tai va giai nen ban phat hanh neu chua co san trong thu muc cai dat
    ${IfNot} ${FileExists} "$INSTDIR\ME Launcher.exe"
        DetailPrint "Đang tải ME Launcher từ GitHub Releases..."
        nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Unrestricted -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile(\"${DOWNLOAD_URL}\", \"$TEMP\me-launcher-download.zip\")"'
        Pop $0
        ${If} $0 != "0"
            MessageBox MB_ICONSTOP "Tải thất bại (mã lỗi $0). Kiểm tra kết nối mạng hoặc đường dẫn GitHub Releases rồi thử lại."
            Abort
        ${EndIf}

        DetailPrint "Đang giải nén dữ liệu ME Launcher..."
        nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Unrestricted -Command "Expand-Archive -Path \"$TEMP\me-launcher-download.zip\" -DestinationPath \"$INSTDIR\" -Force"'
        Pop $0
        ${If} $0 != "0"
            MessageBox MB_ICONSTOP "Giải nén thất bại (mã lỗi $0). Kiểm tra quyền ghi tại $INSTDIR."
            Abort
        ${EndIf}

        Delete "$TEMP\me-launcher-download.zip"
    ${EndIf}

    ; 3. Dong bo Ldoc_i5n, chep day du JDK 25 vao runtime\bin va cau hinh ME Launcher.cfg
    DetailPrint "Đang đồng bộ đầy đủ runtime\bin (Java 25) và cấu hình ME Launcher.exe..."
    FileOpen $4 "$TEMP\me-ldoc-setup.ps1" w
    FileWrite $4 'param([string]$$InstDir)$\r$\n'
    FileWrite $4 '$$ErrorActionPreference = "SilentlyContinue"$\r$\n'
    FileWrite $4 '$$d = [char]36$\r$\n'
    FileWrite $4 '$$exe = Get-ChildItem -Path $$InstDir -Filter "ME Launcher.exe" -Recurse | Select-Object -First 1$\r$\n'
    FileWrite $4 'if ($$exe -and ($$exe.DirectoryName -ne $$InstDir)) {$\r$\n'
    FileWrite $4 '    $$subDir = $$exe.DirectoryName$\r$\n'
    FileWrite $4 '    Copy-Item -Path "$$subDir\*" -Destination $$InstDir -Recurse -Force$\r$\n'
    FileWrite $4 '    Remove-Item -Path $$subDir -Recurse -Force$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 '$$ldoc = Join-Path $$InstDir "Ldoc_i5n"$\r$\n'
    FileWrite $4 'foreach ($$sub in @("classes", "resources", "tmp")) {$\r$\n'
    FileWrite $4 '    $$targetSub = Join-Path $$ldoc $$sub$\r$\n'
    FileWrite $4 '    if (!(Test-Path $$targetSub)) { New-Item -ItemType Directory -Path $$targetSub -Force | Out-Null }$\r$\n'
    FileWrite $4 '    $$oldBuildSub = Join-Path $$InstDir "build\$$sub"$\r$\n'
    FileWrite $4 '    if (Test-Path $$oldBuildSub) {$\r$\n'
    FileWrite $4 '        Copy-Item -Path "$$oldBuildSub\*" -Destination $$targetSub -Recurse -Force$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 '$$oldBuild = Join-Path $$InstDir "build"$\r$\n'
    FileWrite $4 'if (Test-Path $$oldBuild) { Remove-Item -Path $$oldBuild -Recurse -Force }$\r$\n'
    FileWrite $4 '$$oldLdocs = Join-Path $$InstDir "Ldocs-i5n"$\r$\n'
    FileWrite $4 'if (Test-Path $$oldLdocs) { Remove-Item -Path $$oldLdocs -Recurse -Force }$\r$\n'
    FileWrite $4 '$$appDir = Join-Path $$InstDir "app"$\r$\n'
    FileWrite $4 '$$classesMain = Join-Path $$ldoc "classes\java\main"$\r$\n'
    FileWrite $4 '$$resMain = Join-Path $$ldoc "resources\main"$\r$\n'
    FileWrite $4 'if ((Test-Path $$appDir) -and (Test-Path $$classesMain)) {$\r$\n'
    FileWrite $4 '    Add-Type -AssemblyName System.IO.Compression$\r$\n'
    FileWrite $4 '    Add-Type -AssemblyName System.IO.Compression.FileSystem$\r$\n'
    FileWrite $4 '    $$mainJar = Get-ChildItem -Path $$appDir -Filter "*.jar" | Where-Object { $$_.Name -notmatch "^(javafx-|gson-|error_prone)" } | Select-Object -First 1$\r$\n'
    FileWrite $4 '    if ($$mainJar) {$\r$\n'
    FileWrite $4 '        $$zip = [System.IO.Compression.ZipFile]::Open($$mainJar.FullName, [System.IO.Compression.ZipArchiveMode]::Update)$\r$\n'
    FileWrite $4 '        foreach ($$root in @($$classesMain, $$resMain)) {$\r$\n'
    FileWrite $4 '            if (Test-Path $$root) {$\r$\n'
    FileWrite $4 '                Get-ChildItem -Path $$root -Recurse -File | ForEach-Object {$\r$\n'
    FileWrite $4 '                    $$rel = $$_.FullName.Substring($$root.Length + 1).Replace("\", "/")$\r$\n'
    FileWrite $4 '                    $$old = $$zip.GetEntry($$rel)$\r$\n'
    FileWrite $4 '                    if ($$old) { $$old.Delete() }$\r$\n'
    FileWrite $4 '                    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($$zip, $$_.FullName, $$rel) | Out-Null$\r$\n'
    FileWrite $4 '                }$\r$\n'
    FileWrite $4 '            }$\r$\n'
    FileWrite $4 '        }$\r$\n'
    FileWrite $4 '        $$zip.Dispose()$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 '$$rtDir = Join-Path $$InstDir "runtime"$\r$\n'
    FileWrite $4 '$$rtBin = Join-Path $$rtDir "bin"$\r$\n'
    FileWrite $4 'if (!(Test-Path $$rtBin)) { New-Item -ItemType Directory -Path $$rtBin -Force | Out-Null }$\r$\n'
    FileWrite $4 '$$searchRoots = @("C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java", "C:\Program Files\Microsoft", "$$env:LOCALAPPDATA\Programs\Eclipse Adoptium", "$$env:USERPROFILE\.jdks", "$$env:USERPROFILE\.gradle\jdks")$\r$\n'
    FileWrite $4 '$$jdk25Bin = $$null$\r$\n'
    FileWrite $4 'foreach ($$root in $$searchRoots) {$\r$\n'
    FileWrite $4 '    if (Test-Path $$root) {$\r$\n'
    FileWrite $4 '        $$cand = Get-ChildItem -Path $$root -Filter "java.exe" -Recurse | Where-Object {$\r$\n'
    FileWrite $4 '            $$_.FullName -notlike "*\ME Launcher\*" -and ($$_.FullName -match "25" -or ((& $$_.FullName -version 2>&1 | Out-String) -match "version `"25"))$\r$\n'
    FileWrite $4 '        } | Select-Object -First 1$\r$\n'
    FileWrite $4 '        if ($$cand) { $$jdk25Bin = $$cand.DirectoryName; break }$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 'if ($$jdk25Bin) {$\r$\n'
    FileWrite $4 '    $$jdk25Home = Split-Path $$jdk25Bin -Parent$\r$\n'
    FileWrite $4 '    Copy-Item -Path "$$jdk25Bin\*" -Destination $$rtBin -Recurse -Force$\r$\n'
    FileWrite $4 '    foreach ($$dirName in @("lib", "conf")) {$\r$\n'
    FileWrite $4 '        $$srcSub = Join-Path $$jdk25Home $$dirName$\r$\n'
    FileWrite $4 '        $$dstSub = Join-Path $$rtDir $$dirName$\r$\n'
    FileWrite $4 '        if (Test-Path $$srcSub) {$\r$\n'
    FileWrite $4 '            if (!(Test-Path $$dstSub)) { New-Item -ItemType Directory -Path $$dstSub -Force | Out-Null }$\r$\n'
    FileWrite $4 '            Copy-Item -Path "$$srcSub\*" -Destination $$dstSub -Recurse -Force$\r$\n'
    FileWrite $4 '        }$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '} else {$\r$\n'
    FileWrite $4 '    $$rtJava = Join-Path $$rtBin "java.exe"$\r$\n'
    FileWrite $4 '    $$isJava25 = (Test-Path $$rtJava) -and (((& $$rtJava -version 2>&1 | Out-String) -match "version `"25"))$\r$\n'
    FileWrite $4 '    if (!$$isJava25) {$\r$\n'
    FileWrite $4 '        $$jdkZip = "$$env:TEMP\adoptium-jdk25.zip"$\r$\n'
    FileWrite $4 '        $$jdkExt = "$$env:TEMP\adoptium-jdk25-ext"$\r$\n'
    FileWrite $4 '        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12$\r$\n'
    FileWrite $4 '        (New-Object System.Net.WebClient).DownloadFile("${ADOPTIUM_JDK25_URL}", $$jdkZip)$\r$\n'
    FileWrite $4 '        if (Test-Path $$jdkZip) {$\r$\n'
    FileWrite $4 '            Expand-Archive -Path $$jdkZip -DestinationPath $$jdkExt -Force$\r$\n'
    FileWrite $4 '            $$dlJava = Get-ChildItem -Path $$jdkExt -Filter "java.exe" -Recurse | Select-Object -First 1$\r$\n'
    FileWrite $4 '            if ($$dlJava) {$\r$\n'
    FileWrite $4 '                $$dlHome = Split-Path $$dlJava.DirectoryName -Parent$\r$\n'
    FileWrite $4 '                Copy-Item -Path "$$dlHome\*" -Destination $$rtDir -Recurse -Force$\r$\n'
    FileWrite $4 '            }$\r$\n'
    FileWrite $4 '            Remove-Item $$jdkZip -Force$\r$\n'
    FileWrite $4 '            Remove-Item $$jdkExt -Recurse -Force$\r$\n'
    FileWrite $4 '        }$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 'if (Test-Path $$appDir) {$\r$\n'
    FileWrite $4 '    $$cfgPath = Join-Path $$appDir "ME Launcher.cfg"$\r$\n'
    FileWrite $4 '    $$jars = Get-ChildItem -Path $$appDir -Filter "*.jar" | Sort-Object { if ($$_.Name -match "^me-launcher") { 0 } else { 1 } }, Name$\r$\n'
    FileWrite $4 '    $$cfgLines = New-Object System.Collections.Generic.List[string]$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("[Application]")$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("app.mainclass=launcher.Main")$\r$\n'
    FileWrite $4 '    foreach ($$j in $$jars) {$\r$\n'
    FileWrite $4 '        $$cfgLines.Add("app.classpath=" + $$d + "APPDIR\" + $$j.Name)$\r$\n'
    FileWrite $4 '    }$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("")$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("[JavaOptions]")$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("java-options=-Djpackage.app-path=" + $$d + "APPDIR\..\ME Launcher.exe")$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("java-options=-Dfile.encoding=UTF-8")$\r$\n'
    FileWrite $4 '    $$cfgLines.Add("java-options=--enable-native-access=ALL-UNNAMED")$\r$\n'
    FileWrite $4 '    [System.IO.File]::WriteAllLines($$cfgPath, $$cfgLines, [System.Text.Encoding]::ASCII)$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileWrite $4 '$$regPath = "HKCU:\Software\Microsoft\DirectX\UserGpuPreferences"$\r$\n'
    FileWrite $4 'if (!(Test-Path $$regPath)) { New-Item -Path $$regPath -Force | Out-Null }$\r$\n'
    FileWrite $4 'Get-ChildItem -Path $$InstDir -Include "ME Launcher.exe","javaw.exe","java.exe" -Recurse | ForEach-Object {$\r$\n'
    FileWrite $4 '    Set-ItemProperty -Path $$regPath -Name $$_.FullName -Value "GpuPreference=2;" -Type String -Force$\r$\n'
    FileWrite $4 '}$\r$\n'
    FileClose $4

    nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Bypass -File "$TEMP\me-ldoc-setup.ps1" "$INSTDIR"'
    Pop $0
    Delete "$TEMP\me-ldoc-setup.ps1"

    ${IfNot} ${FileExists} "$INSTDIR\ME Launcher.exe"
        MessageBox MB_ICONSTOP "Không tìm thấy ME Launcher.exe sau khi giải nén.$\r$\nHãy kiểm tra lại file .zip trên GitHub Releases."
        Abort
    ${EndIf}

    StrCpy $R2 "$INSTDIR\ME Launcher.exe"
    ${If} ${FileExists} "$INSTDIR\ME_Launcher.ico"
        StrCpy $R2 "$INSTDIR\ME_Launcher.ico"
    ${EndIf}

    SetOutPath "$INSTDIR"
    CreateShortcut "$DESKTOP\ME Launcher.lnk" "$INSTDIR\ME Launcher.exe" "" "$R2" 0
    CreateDirectory "$SMPROGRAMS\ME Launcher"
    CreateShortcut "$SMPROGRAMS\ME Launcher\ME Launcher.lnk" "$INSTDIR\ME Launcher.exe" "" "$R2" 0
    CreateShortcut "$SMPROGRAMS\ME Launcher\Gỡ cài đặt ME Launcher.lnk" "$INSTDIR\Uninstall.exe" "" "$R2" 0

    WriteRegStr HKCU "Software\MELauncher" "InstallDir" "$INSTDIR"
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "DisplayName" "ME Launcher"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "UninstallString" "$INSTDIR\Uninstall.exe"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "DisplayIcon" "$R2"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "Publisher" "TonygamingTL / giahuybuhbuh-maker"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "DisplayVersion" "0.1.7"

    DetailPrint "Cài đặt hoàn tất!"
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\ME Launcher.lnk"
    RMDir /r "$SMPROGRAMS\ME Launcher"
    RMDir /r "$INSTDIR"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher"
    DeleteRegKey HKCU "Software\MELauncher"
SectionEnd