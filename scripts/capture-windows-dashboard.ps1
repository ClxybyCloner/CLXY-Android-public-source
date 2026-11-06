# Hosted-CI-only capture of the unchanged portable Windows application.
#Requires -Version 7
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:GITHUB_ACTIONS -ne 'true') { throw 'This helper is restricted to GitHub Actions.' }

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class ClxyWin32 {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int command);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint MapVirtualKey(uint code, uint mapType);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll", SetLastError=true)] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int height, uint flags);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT point);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll", SetLastError=true)] private static extern uint SendInput(uint count, INPUT[] inputs, int size);
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int Dx, Dy; public uint MouseData, Flags, Time; public IntPtr ExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort Vk, Scan; public uint Flags, Time; public IntPtr ExtraInfo; }
    [StructLayout(LayoutKind.Explicit, Size=40)] public struct INPUT {
        [FieldOffset(0)] public uint Type;
        [FieldOffset(8)] public MOUSEINPUT Mouse;
        [FieldOffset(8)] public KEYBDINPUT Keyboard;
    }
    public static void ClickClient(IntPtr h, int x, int y) {
        POINT point = new POINT { X=x, Y=y };
        if (!ClientToScreen(h, ref point)) throw new InvalidOperationException("ClientToScreen failed.");
        if (!SetCursorPos(point.X, point.Y)) throw new InvalidOperationException("SetCursorPos failed.");
        INPUT[] inputs = new INPUT[2];
        inputs[0].Type=0; inputs[0].Mouse.Flags=0x0002;
        inputs[1].Type=0; inputs[1].Mouse.Flags=0x0004;
        if (SendInput(2, inputs, Marshal.SizeOf(typeof(INPUT))) != 2) throw new InvalidOperationException("SendInput mouse click failed.");
    }
    private static void SendScan(ushort scan, bool up) {
        INPUT[] inputs = new INPUT[1];
        inputs[0].Type=1;
        inputs[0].Keyboard.Scan=scan;
        inputs[0].Keyboard.Flags=0x0008u | (up ? 0x0002u : 0u);
        if (SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT))) != 1) throw new InvalidOperationException("SendInput physical key failed.");
        System.Threading.Thread.Sleep(80);
    }
    public static void CtrlKey(IntPtr h, ushort virtualKey) {
        SetForegroundWindow(h);
        ClickClient(h,600,50); // Noninteractive About header; focus the Flutter child.
        System.Threading.Thread.Sleep(150);
        if (GetForegroundWindow() != h) throw new InvalidOperationException("The app is not foreground for the physical shortcut.");
        ushort controlScan=(ushort)MapVirtualKey(0xA2,0); // VK_LCONTROL.
        ushort keyScan=(ushort)MapVirtualKey(virtualKey,0);
        if (controlScan == 0 || keyScan == 0) throw new InvalidOperationException("Physical key scan mapping failed.");
        SendScan(controlScan,false);
        try { SendScan(keyScan,false); }
        finally { SendScan(keyScan,true); SendScan(controlScan,true); }
    }
    [DllImport("user32.dll", EntryPoint="GetWindowLongPtrW")] private static extern IntPtr GetWindowLongPtr(IntPtr h, int index);
    [DllImport("user32.dll", EntryPoint="SetWindowLongPtrW", SetLastError=true)] private static extern IntPtr SetWindowLongPtr(IntPtr h, int index, IntPtr value);
    public static void MakeBorderless(IntPtr h) {
        long style=GetWindowLongPtr(h,-16).ToInt64();
        style=(style & ~(0x00C00000L | 0x00040000L | 0x00010000L | 0x00020000L | 0x00080000L)) | 0x80000000L;
        SetWindowLongPtr(h,-16,new IntPtr(style));
    }
}
'@
[ClxyWin32]::SetProcessDPIAware() | Out-Null
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'out'
$fixture = Join-Path $env:RUNNER_TEMP 'clxy-dashboard-fixture'
$artifactDir = Join-Path $env:RUNNER_TEMP 'clxy-dashboard-artifact'
$appProcess = $null
$coreProcess = $null
$log = [System.Collections.Generic.List[string]]::new()
$viewLog = [System.Collections.Generic.List[string]]::new()
function Note([string]$Message) { $log.Add($Message); Write-Host $Message }
function Note-View([string]$Message) { $viewLog.Add($Message); Write-Host "View: $Message" }
function Wait-Until([scriptblock]$Test, [int]$Seconds, [string]$Failure) {
    $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
    do { if (& $Test) { return }; Start-Sleep -Milliseconds 250 } while ([DateTime]::UtcNow -lt $deadline)
    throw $Failure
}
function Stop-Safely($Process) {
    if ($null -eq $Process) { return }
    try { if (-not $Process.HasExited) { Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue } } catch { }
}
function Reset-CaptureWindow {
    if (-not [ClxyWin32]::SetWindowPos($hwnd,[IntPtr](-1),0,0,0,0,0x0455)) { throw 'Could not reset the capture window to the origin.' }
    [ClxyWin32]::SetForegroundWindow($hwnd) | Out-Null
    Start-Sleep -Milliseconds 500
}
function Capture-View([string]$Name) {
    $path=Join-Path $outDir $Name
    & python (Join-Path $PSScriptRoot 'capture-windows-compositor.py') --hwnd ($hwnd.ToInt64()) --output $path
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $path)) { throw "Native capture did not produce the requested verified image: $Name" }
    Note "Captured verified ${needW}x${needH} actual Windows pixels from four native viewport captures, stitched without scaling: $path"
    Note-View "Requested and captured $Name via four native viewport tiles."
}
try {
    # Preserve the populated artifact directory; remove only our own fixture.
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $fixture,$artifactDir,$outDir | Out-Null
    $dataDir=Join-Path $fixture 'data'
    New-Item -ItemType Directory -Force $dataDir | Out-Null
    @{
        language='en'; theme='dark'; close='quit'; welcome_done=$true
        show_public_ip=$false; autostart=$false; auto_connect=$false
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $dataDir 'app-settings.json') -Encoding utf8
    $zip=Get-ChildItem -LiteralPath $artifactDir -Filter 'CLXY-Windows-x64.zip' -File -Recurse | Select-Object -First 1
    if ($null -eq $zip) { throw 'Portable artifact was not downloaded.' }
    $portable=Join-Path $fixture 'portable'
    Expand-Archive -LiteralPath $zip.FullName -DestinationPath $portable -Force
    $appExe=Get-ChildItem -LiteralPath $portable -Filter 'clxy.exe' -File -Recurse | Select-Object -First 1
    if ($null -eq $appExe) { throw 'Portable clxy.exe was not found.' }
    Note "Portable app: $($appExe.FullName)"
    $dart=(Get-Command dart -ErrorAction Stop).Source
    $fakeCoreExe=Join-Path $fixture 'fake-core.exe'
    & $dart compile exe (Join-Path $root 'app/tool/fake_core.dart') -o $fakeCoreExe
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the isolated fake core.' }
    $coreProcess=Start-Process -FilePath $fakeCoreExe -ArgumentList @($dataDir,'19090') -WorkingDirectory $fixture -PassThru -WindowStyle Hidden
    Wait-Until {
        if ($coreProcess.HasExited) { return $false }
        try {
            $client=[Net.Sockets.TcpClient]::new()
            try { $ready=$client.ConnectAsync('127.0.0.1',19090).Wait(500) -and $client.Connected; return $ready } finally { $client.Dispose() }
        } catch { return $false }
    } 30 'The isolated fake core did not become ready.'
    Note 'Fake core ready on loopback port 19090.'
    $appProcess=Start-Process -FilePath $appExe.FullName -ArgumentList @("--data-dir=$dataDir") -WorkingDirectory $appExe.DirectoryName -PassThru
    Wait-Until { $appProcess.Refresh(); return (-not $appProcess.HasExited -and $appProcess.MainWindowHandle -ne [IntPtr]::Zero) } 45 'The app did not create its main window.'
    $appProcess.Refresh()
    $hwnd=$appProcess.MainWindowHandle
    $needW=1363; $needH=1106
    Note "Physical desktop: $([ClxyWin32]::GetSystemMetrics(0))x$([ClxyWin32]::GetSystemMetrics(1))."
    # Change only native non-client chrome for this temporary capture. The app
    # binary/content is unchanged. WS_POPUP plus SWP_NOSENDCHANGING avoids the
    # hosted desktop's maximum tracking-size clamp on an overlapped window.
    [ClxyWin32]::ShowWindow($hwnd,9) | Out-Null
    [ClxyWin32]::MakeBorderless($hwnd)
    [ClxyWin32]::SetForegroundWindow($hwnd) | Out-Null
    if (-not [ClxyWin32]::SetWindowPos($hwnd,[IntPtr](-1),0,0,$needW,$needH,0x0460)) { throw 'Could not resize the temporary capture window.' }
    # Let the two bounded direct-IP providers and finite route animation finish.
    # No origin country or IP is injected into the production application.
    Start-Sleep -Seconds 35
    $rect=New-Object ClxyWin32+RECT
    [ClxyWin32]::GetClientRect($hwnd,[ref]$rect) | Out-Null
    $w=$rect.Right-$rect.Left; $h=$rect.Bottom-$rect.Top
    if ($w -ne $needW -or $h -ne $needH) { throw "Actual client area ${w}x${h}, expected ${needW}x${needH}." }
    Note "Actual Windows client area: ${w}x${h}."
    & python -m pip install --disable-pip-version-check Pillow
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the CI-only native capture dependency.' }
    Capture-View 'windows-dashboard-content.png'
    Reset-CaptureWindow
    Note-View 'Issued native click at sidebar Accounts position (client x=120, y=315).'
    [ClxyWin32]::ClickClient($hwnd,120,315)
    Start-Sleep -Seconds 2
    Capture-View 'windows-accounts-content.png'
    Reset-CaptureWindow
    Note-View 'Issued native click at sidebar About position (client x=120, y=535).'
    [ClxyWin32]::ClickClient($hwnd,120,535)
    Start-Sleep -Seconds 2
    Capture-View 'windows-about-content.png'
    Reset-CaptureWindow
    Note-View 'Issued native Ctrl+= twice for zoom-in verification.'
    [ClxyWin32]::CtrlKey($hwnd,0xBB)
    Start-Sleep -Milliseconds 250
    [ClxyWin32]::CtrlKey($hwnd,0xBB)
    Start-Sleep -Seconds 2
    Capture-View 'windows-about-zoom-in-content.png'
    Reset-CaptureWindow
    Note-View 'Issued native Ctrl+- twice to request zoom reset.'
    [ClxyWin32]::CtrlKey($hwnd,0xBD)
    Start-Sleep -Milliseconds 250
    [ClxyWin32]::CtrlKey($hwnd,0xBD)
    Start-Sleep -Seconds 2
    Capture-View 'windows-about-zoom-reset-content.png'
    # Measure real layout pixels; a no-op key sender must not pass the test.
    $zoomGeometryCheck = @'
from pathlib import Path
import sys
from PIL import Image
root = Path(sys.argv[1])
def sidebar_width(name):
    with Image.open(root / name) as image:
        y = image.height - 20
        background = image.getpixel((image.width - 10, y))
        for x in range(image.width // 2):
            if all(image.getpixel((x + d, y)) == background for d in range(4)):
                return x
    raise RuntimeError('Cannot find the actual sidebar edge')
normal = sidebar_width('windows-about-content.png')
zoomed = sidebar_width('windows-about-zoom-in-content.png')
reset = sidebar_width('windows-about-zoom-reset-content.png')
if not (150 < normal < 400 and 1.18 <= zoomed / normal <= 1.22 and abs(reset - normal) <= 1):
    raise RuntimeError(f'Native zoom geometry failed: {normal} -> {zoomed} -> {reset} px')
print(f'Native Ctrl+ and Ctrl- verified in actual Windows pixels: {normal} -> {zoomed} -> {reset} px')
'@
    $zoomGeometryCheck | & python - $outDir
    if ($LASTEXITCODE -ne 0) { throw 'The native keyboard zoom did not pass actual layout geometry checks.' }
    Note-View 'Physical scan-code Ctrl+ and Ctrl- passed the actual sidebar geometry check.'
} catch {
    Note "Capture failed: $($_.Exception.Message)"
    throw
} finally {
    Stop-Safely $appProcess
    Stop-Safely $coreProcess
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force $outDir | Out-Null }
    $log | Set-Content -LiteralPath (Join-Path $outDir 'windows-dashboard-preview.log') -Encoding utf8
    $viewLog | Set-Content -LiteralPath (Join-Path $outDir 'windows-dashboard-views.log') -Encoding utf8
    Remove-Item -LiteralPath $fixture,$artifactDir -Recurse -Force -ErrorAction SilentlyContinue
}
