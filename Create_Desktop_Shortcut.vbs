Option Explicit
Dim shell, fso, appDir, target, linkPath, shortcut
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
appDir = fso.GetParentFolderName(WScript.ScriptFullName)
linkPath = fso.BuildPath(shell.SpecialFolders("Desktop"), "Findspace.lnk")
If fso.FileExists(linkPath) Then
    If MsgBox("Replace the existing Findspace desktop shortcut?", vbYesNo + vbQuestion, "Findspace") <> vbYes Then WScript.Quit
End If
Set shortcut = shell.CreateShortcut(linkPath)
If fso.FileExists(fso.BuildPath(appDir, "Findspace.exe")) Then
    shortcut.TargetPath = fso.BuildPath(appDir, "Findspace.exe")
Else
    shortcut.TargetPath = shell.ExpandEnvironmentStrings("%SystemRoot%\System32\wscript.exe")
    shortcut.Arguments = Chr(34) & fso.BuildPath(appDir, "Launch_Findspace.vbs") & Chr(34)
End If
shortcut.WorkingDirectory = appDir
shortcut.Description = "Findspace - click the desktop orb to reveal folders and file tools"
shortcut.IconLocation = fso.BuildPath(appDir, "findspace.ico")
shortcut.Save
MsgBox "Findspace is on your desktop. Double-click the shortcut, then click the floating orb to expand it." & vbCrLf & vbCrLf & "Keep this extracted folder in its current location. You can drag the orb wherever you want.", vbInformation, "Findspace"
