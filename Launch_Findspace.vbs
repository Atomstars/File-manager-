Option Explicit
Dim shell, fso, appDir, entry, result
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
appDir = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = appDir
entry = Chr(34) & fso.BuildPath(appDir, "Findspace.pyw") & Chr(34)
If fso.FileExists(fso.BuildPath(appDir, "Findspace.exe")) Then
    shell.Run Chr(34) & fso.BuildPath(appDir, "Findspace.exe") & Chr(34), 1, False
Else
    On Error Resume Next
    Err.Clear
    shell.Run "pyw -3 " & entry, 0, False
    If Err.Number <> 0 Then
        Err.Clear
        shell.Run "pythonw " & entry, 0, False
        If Err.Number <> 0 Then
            MsgBox "The source version needs Python 3.10 or later (with Tk). Install Python, or use a built Findspace.exe." & vbCrLf & vbCrLf & "No web server, AI service or terminal is needed for the desktop app.", vbInformation, "Findspace"
        End If
    End If
    On Error GoTo 0
End If
