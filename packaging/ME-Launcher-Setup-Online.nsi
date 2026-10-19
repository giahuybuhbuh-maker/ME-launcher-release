; ============================================================
;  ME Launcher - Setup ONLINE (nho gon, ~1-3MB)
;
;  Khac voi ban .zip day du (~150-300MB co san Java + JavaFX):
;  file setup nay CHI la 1 "vo tai ve" - luc chay moi tu tai ban
;  that tu GitHub Releases ve may nguoi dung, roi giai nen + tao
;  shortcut. Can co mang luc CAI, khong can luc build script nay.
;
;  ------------------------------------------------------------
;  CACH BIEN DICH (lam 1 lan tren may ban):
;  1. Cai NSIS (mien phi): https://nsis.sourceforge.io/Download
;  2. Chuot phai vao file nay -> "Compile NSIS Script"
;     -> ra file ME-Launcher-Setup.exe, chi vai MB
;  (Khong can cai them plugin gi ca - dung PowerShell co san trong Windows
;  de tai va giai nen, xu ly HTTPS/chuyen huong tot hon plugin NSISdl)
;  ------------------------------------------------------------
; ============================================================

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "TextFunc.nsh"

Name "ME Launcher"
OutFile "ME-Launcher-Setup.exe"
InstallDir "$LOCALAPPDATA\ME Launcher"
InstallDirRegKey HKCU "Software\MELauncher" "InstallDir"
RequestExecutionLevel user
Unicode true

VIProductVersion "0.0.1.2"
VIAddVersionKey "ProductName" "ME-Launcher"
VIAddVersionKey "CompanyName" "TonygamingTL / ME-Launcher-Codex"
VIAddVersionKey "FileDescription" "ME-Launcher-Setup"
VIAddVersionKey "FileVersion" "1.0.0.0"
VIAddVersionKey "LegalCopyright" "Copyright © 2026"

!define DOWNLOAD_URL "https://github.com/giahuybuhbuh-maker/ME-launcher-release/releases/download/v0.1.2/ME-Launcher-release.v0.1.2.zip"

!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Cai dat ME Launcher" SecInstall
    ; Khai báo tay dung luong can - script nay KHONG nhung file truc tiep
    ; (toan bo tai luc cai dat), nen NSIS khong tu tinh duoc "Space required"
    ; tu lenh File nhu binh thuong. 150MB = 153600 KB, sua so nay neu ban
    ; kiem tra dung luong that cua ban build khac di.
    AddSize 153600

    SetOutPath "$INSTDIR"
    SetDetailsPrint both

    DetailPrint "Dang tai ME Launcher tu GitHub (can ket noi mang)..."
    nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Unrestricted -Command "(New-Object System.Net.WebClient).DownloadFile(\"${DOWNLOAD_URL}\", \"$TEMP\me-launcher-download.zip\")"'
    Pop $0
    ${If} $0 != "0"
        MessageBox MB_ICONSTOP "Tai that bai (ma loi $0). Kiem tra lai ket noi mang roi thu lai."
        Abort
    ${EndIf}

    DetailPrint "Dang giai nen (dung PowerShell co san trong Windows)..."
    nsExec::ExecToLog 'powershell -NoProfile -ExecutionPolicy Unrestricted -Command "Expand-Archive -Path \"$TEMP\me-launcher-download.zip\" -DestinationPath \"$INSTDIR\" -Force"'
    Pop $0
    ${If} $0 != "0"
        MessageBox MB_ICONSTOP "Giai nen that bai (ma loi $0). Kiem tra $INSTDIR co du quyen ghi khong."
        Abort
    ${EndIf}

    Delete "$TEMP\me-launcher-download.zip"

    ; TU TIM file ME Launcher.exe o bat ky dau ben trong $INSTDIR, KHONG
    ; gia dinh 1 duong dan cu the - vi ten thu muc con ben trong file zip
    ; hay doi theo tung phien ban (vd "ME-Launcher-release v0.1.2\..."),
    ; da sai 2 lan roi vi doan cung duong dan.
    DetailPrint "Dang tim ME Launcher.exe..."
    nsExec::ExecToStack 'powershell -NoProfile -Command "(Get-ChildItem -Path \"$INSTDIR\" -Filter \"ME Launcher.exe\" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName"'
    Pop $0
    Pop $R0
    ${TrimNewLines} "$R0" $R0

    ${If} $R0 == ""
        MessageBox MB_ICONSTOP "Giai nen xong nhung khong tim thay ME Launcher.exe o dau ca.$\r$\nKiem tra lai file .zip da upload len GitHub co dung cau truc khong."
        Abort
    ${EndIf}

    DetailPrint "Tim thay: $R0"

    CreateShortcut "$DESKTOP\ME Launcher.lnk" "$R0" "" "$R0" 0
    CreateDirectory "$SMPROGRAMS\ME Launcher"
    CreateShortcut "$SMPROGRAMS\ME Launcher\ME Launcher.lnk" "$R0"
    CreateShortcut "$SMPROGRAMS\ME Launcher\Go cai dat ME Launcher.lnk" "$INSTDIR\Uninstall.exe"

    WriteRegStr HKCU "Software\MELauncher" "InstallDir" "$INSTDIR"
    WriteUninstaller "$INSTDIR\Uninstall.exe"

    ; Dang ky trong "Add or Remove Programs" cua Windows cho dung chuan
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "DisplayName" "ME Launcher"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "UninstallString" "$INSTDIR\Uninstall.exe"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher" "Publisher" "giahuybuhbuh-maker"

    DetailPrint "Cai dat xong!"
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\ME Launcher.lnk"
    RMDir /r "$SMPROGRAMS\ME Launcher"
    RMDir /r "$INSTDIR"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\MELauncher"
    DeleteRegKey HKCU "Software\MELauncher"
SectionEnd
