#!/usr/bin/env python3
"""CI-only native Windows viewport capture for a 1363x1106 app on a small monitor.

Four real desktop captures are stitched without scaling, painting, or text changes.
Seams are in dashboard gutters so live cards are never split across moments.
Dependency: Pillow. Does not run outside the hosted Windows CI environment.
"""
from __future__ import annotations
import argparse
import ctypes
import ctypes.wintypes as wt
import os
import sys
import threading
import time
from pathlib import Path

SIZE = (1363, 1106)


def verify_pixels(image, label, *, require_detail=True):
    rgb = image.convert('RGB')
    total = rgb.width * rgb.height
    black = sum(1 for p in rgb.getdata() if p == (0, 0, 0))
    if black / total > 0.02:
        raise RuntimeError(f'{label}: {black / total:.2%} unrendered black pixels')
    if require_detail and len(set(rgb.resize((64, 64)).getdata())) < 32:
        raise RuntimeError(f'{label}: insufficient rendered detail')
    return rgb


def main():
    if os.name != 'nt' or os.environ.get('GITHUB_ACTIONS') != 'true':
        raise RuntimeError('Native capture is restricted to hosted Windows CI')
    parser = argparse.ArgumentParser()
    parser.add_argument('--hwnd', required=True, type=int)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    from PIL import Image, ImageGrab
    user = ctypes.WinDLL('user32', use_last_error=True)
    try:
        user.SetProcessDpiAwarenessContext.argtypes = [ctypes.c_void_p]
        user.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    except AttributeError:
        user.SetProcessDPIAware()

    class RECT(ctypes.Structure):
        _fields_ = [('left', wt.LONG), ('top', wt.LONG), ('right', wt.LONG), ('bottom', wt.LONG)]
    class POINT(ctypes.Structure):
        _fields_ = [('x', wt.LONG), ('y', wt.LONG)]
    hwnd = wt.HWND(args.hwnd)
    user.GetClientRect.argtypes = [wt.HWND, ctypes.POINTER(RECT)]
    user.GetClientRect.restype = wt.BOOL
    user.ClientToScreen.argtypes = [wt.HWND, ctypes.POINTER(POINT)]
    user.ClientToScreen.restype = wt.BOOL
    user.SetWindowPos.argtypes = [wt.HWND, wt.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, wt.UINT]
    user.SetWindowPos.restype = wt.BOOL
    user.SetCursorPos.argtypes = [ctypes.c_int, ctypes.c_int]
    user.SetCursorPos.restype = wt.BOOL
    user.GetSystemMetrics.argtypes = [ctypes.c_int]
    user.GetSystemMetrics.restype = ctypes.c_int
    user.IsWindow.argtypes = [wt.HWND]
    user.IsWindow.restype = wt.BOOL
    if not user.IsWindow(hwnd):
        raise RuntimeError('The supplied native app window no longer exists')
    rect = RECT()
    if not user.GetClientRect(hwnd, ctypes.byref(rect)):
        raise RuntimeError('Cannot read actual client geometry')
    if (rect.right - rect.left, rect.bottom - rect.top) != SIZE:
        raise RuntimeError('Actual app client is not 1363x1106')
    sw, sh = user.GetSystemMetrics(0), user.GetSystemMetrics(1)
    if sw < 992 or sh < 740:
        raise RuntimeError(f'Monitor {sw}x{sh} is too small for the selected card-safe viewport seams')
    dx, dy = max(0, SIZE[0] - sw), max(0, SIZE[1] - sh)
    plans = [
        ('upper-left', 0, 0, (0, 0, 992, 740)),
        ('upper-right', -dx, 0, (992, 0, 1363, 660)),
        ('lower-left', 0, -dy, (0, 740, 992, 1106)),
        ('lower-right', -dx, -dy, (992, 660, 1363, 1106)),
    ]
    done = threading.Event()
    def watchdog():
        if not done.wait(60):
            print('Native capture watchdog expired', file=sys.stderr, flush=True)
            os._exit(2)
    threading.Thread(target=watchdog, daemon=True).start()
    output = Image.new('RGB', SIZE)
    try:
        # Keep the pointer in a non-interactive gutter in all four window positions.
        user.SetCursorPos(0, 0)
        for name, x, y, box in plans:
            # NOSIZE | NOZORDER | NOACTIVATE | SHOWWINDOW | NOSENDCHANGING.
            if not user.SetWindowPos(hwnd, None, x, y, 0, 0, 0x0455):
                raise RuntimeError(f'{name}: SetWindowPos failed')
            time.sleep(2)
            origin = POINT(0, 0)
            if not user.ClientToScreen(hwnd, ctypes.byref(origin)):
                raise RuntimeError(f'{name}: ClientToScreen failed')
            physical = (origin.x + box[0], origin.y + box[1], origin.x + box[2], origin.y + box[3])
            if not (0 <= physical[0] < physical[2] <= sw and 0 <= physical[1] < physical[3] <= sh):
                raise RuntimeError(f'{name}: requested pixels are not all visible: {physical}')
            desktop = ImageGrab.grab(bbox=(0, 0, sw, sh), include_layered_windows=True)
            # Accounts/About can have legitimate flat background in a tile.
            # Reject black clipping per tile; require detail in the whole image.
            tile = verify_pixels(desktop.crop(physical), name, require_detail=False)
            output.paste(tile, box[:2])
            print(f'Captured actual Windows viewport {name}: client {box}, desktop {physical}', flush=True)
        verify_pixels(output, 'complete image')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_suffix('.tmp.png')
        output.save(temporary, format='PNG')
        with Image.open(temporary) as final:
            if final.size != SIZE:
                raise RuntimeError('Final PNG dimensions changed')
            verify_pixels(final, 'saved final image')
        os.replace(temporary, args.output)
        print('Verified 1363x1106 tiled native Windows preview; no rescaling or synthetic pixels.', flush=True)
    finally:
        user.SetWindowPos(hwnd, None, 0, 0, 0, 0, 0x0455)
        done.set()
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'Native Windows capture failed: {error}', file=sys.stderr)
        sys.exit(1)
