' ============================================================
' Bam dup file nay 1 LAN la tu tao shortcut "ME Launcher"
' ngoai man hinh Desktop, tro thang toi ME Launcher.exe
' nam CUNG thu muc voi file nay. Khong can cai them gi ca.
' ============================================================

Set oWS = WScript.CreateObject("WScript.Shell")
strDesktop = oWS.SpecialFolders("Desktop")

' Lay duong dan thu muc chua chinh file .vbs nay (de tro dung
' toi ME Launcher.exe du nguoi dung giai nen ra bat ky dau)
strCurrentDir = Left(WScript.ScriptFullName, InStrRev(WScript.ScriptFullName, "\"))
strExePath = strCurrentDir & "ME Launcher.exe"

If Not CreateObject("Scripting.FileSystemObject").FileExists(strExePath) Then
    MsgBox "Khong tim thay ME Launcher.exe cung thu muc voi file nay." & vbCrLf & _
           "Kiem tra lai ban da giai nen DAY DU file .zip chua nhe.", vbExclamation, "ME Launcher"
    WScript.Quit
End If

Set oLink = oWS.CreateShortcut(strDesktop & "\ME Launcher.lnk")
oLink.TargetPath = strExePath
oLink.WorkingDirectory = strCurrentDir
oLink.Description = "ME Launcher - Launcher Minecraft tu viet"
oLink.Save

MsgBox "Da tao xong shortcut ""ME Launcher"" ngoai Desktop!" & vbCrLf & _
       "Tu gio ban co the mo game ngay tu Desktop, khong can vao lai thu muc nay nua.", _
       vbInformation, "ME Launcher"
