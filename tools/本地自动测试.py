#!/usr/bin/env python3
"""在 Windows 本机启动开发客户端、执行冒烟测试并截取 Minecraft 窗口。"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import zlib


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
CHECKPOINTS = ("01-world", "02-settings", "03-selection", "04-light-overlay")
WINDOWS_EPOCH_SECONDS = 11_644_473_600


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="自动启动 Minecraft 开发客户端并执行本地冒烟测试")
    parser.add_argument("--loader", choices=("fabric", "forge", "neoforge"), default="fabric")
    parser.add_argument("--world", help="存档文件夹名；省略时使用最近修改的存档")
    parser.add_argument("--java-path", type=Path, help="JDK 根目录")
    parser.add_argument("--timeout-seconds", type=int, default=240)
    return parser.parse_args()


def select_java_home(configured: Path | None) -> Path | None:
    """返回可选的 Gradle 启动 JDK；项目 Toolchain 由 Gradle 自动下载。"""
    if configured is not None:
        candidate = configured.resolve()
        executable = candidate / "bin" / "java.exe"
        if not executable.is_file():
            raise RuntimeError(f"指定路径不是有效的 JDK：{candidate}")
        return candidate
    return None


def select_world(loader: str, configured: str | None) -> str:
    saves = REPOSITORY_ROOT / loader / "run" / "saves"
    if not saves.is_dir():
        raise RuntimeError(f"没有找到 {loader} 的存档目录：{saves}")
    if configured:
        if not (saves / configured / "level.dat").is_file():
            raise RuntimeError(f"指定的存档文件夹不存在：{saves / configured}")
        return configured

    candidates = [path for path in saves.iterdir() if path.is_dir() and (path / "level.dat").is_file()]
    if not candidates:
        raise RuntimeError(f"{saves} 中没有可用存档，请先创建世界或使用 --world 指定")
    return max(candidates, key=lambda path: path.stat().st_mtime).name


class Rect(ctypes.Structure):
    _fields_ = (("left", ctypes.c_long), ("top", ctypes.c_long),
                ("right", ctypes.c_long), ("bottom", ctypes.c_long))


class FileTime(ctypes.Structure):
    _fields_ = (("low", wintypes.DWORD), ("high", wintypes.DWORD))


class BitmapInfoHeader(ctypes.Structure):
    _fields_ = (
        ("size", wintypes.DWORD), ("width", ctypes.c_long), ("height", ctypes.c_long),
        ("planes", wintypes.WORD), ("bit_count", wintypes.WORD),
        ("compression", wintypes.DWORD), ("size_image", wintypes.DWORD),
        ("x_pixels_per_meter", ctypes.c_long), ("y_pixels_per_meter", ctypes.c_long),
        ("colors_used", wintypes.DWORD), ("colors_important", wintypes.DWORD),
    )


class RgbQuad(ctypes.Structure):
    _fields_ = (("blue", ctypes.c_ubyte), ("green", ctypes.c_ubyte),
                ("red", ctypes.c_ubyte), ("reserved", ctypes.c_ubyte))


class BitmapInfo(ctypes.Structure):
    _fields_ = (("header", BitmapInfoHeader), ("colors", RgbQuad * 1))


user32 = ctypes.WinDLL("user32", use_last_error=True)
gdi32 = ctypes.WinDLL("gdi32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

EnumWindowsCallback = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
user32.EnumWindows.argtypes = (EnumWindowsCallback, wintypes.LPARAM)
user32.IsWindowVisible.argtypes = (wintypes.HWND,)
user32.GetWindowTextW.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
user32.GetWindowThreadProcessId.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.DWORD))
user32.GetWindowRect.argtypes = (wintypes.HWND, ctypes.POINTER(Rect))
user32.ShowWindowAsync.argtypes = (wintypes.HWND, ctypes.c_int)
user32.SetForegroundWindow.argtypes = (wintypes.HWND,)
user32.GetDC.argtypes = (wintypes.HWND,)
user32.GetDC.restype = wintypes.HDC
user32.ReleaseDC.argtypes = (wintypes.HWND, wintypes.HDC)
user32.SetWindowPos.argtypes = (
    wintypes.HWND, wintypes.HWND, ctypes.c_int, ctypes.c_int,
    ctypes.c_int, ctypes.c_int, wintypes.UINT,
)
kernel32.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
kernel32.OpenProcess.restype = wintypes.HANDLE
kernel32.QueryFullProcessImageNameW.argtypes = (
    wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD),
)
kernel32.GetProcessTimes.argtypes = (
    wintypes.HANDLE, ctypes.POINTER(FileTime), ctypes.POINTER(FileTime),
    ctypes.POINTER(FileTime), ctypes.POINTER(FileTime),
)
kernel32.CloseHandle.argtypes = (wintypes.HANDLE,)
gdi32.CreateCompatibleDC.argtypes = (wintypes.HDC,)
gdi32.CreateCompatibleDC.restype = wintypes.HDC
gdi32.CreateCompatibleBitmap.argtypes = (wintypes.HDC, ctypes.c_int, ctypes.c_int)
gdi32.CreateCompatibleBitmap.restype = wintypes.HBITMAP
gdi32.SelectObject.argtypes = (wintypes.HDC, wintypes.HGDIOBJ)
gdi32.SelectObject.restype = wintypes.HGDIOBJ
gdi32.BitBlt.argtypes = (
    wintypes.HDC, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
    wintypes.HDC, ctypes.c_int, ctypes.c_int, wintypes.DWORD,
)
gdi32.GetDIBits.argtypes = (
    wintypes.HDC, wintypes.HBITMAP, wintypes.UINT, wintypes.UINT,
    wintypes.LPVOID, ctypes.POINTER(BitmapInfo), wintypes.UINT,
)
gdi32.DeleteObject.argtypes = (wintypes.HGDIOBJ,)
gdi32.DeleteDC.argtypes = (wintypes.HDC,)


def process_details(process_id: int) -> tuple[str, float] | None:
    process = kernel32.OpenProcess(0x1000, False, process_id)
    if not process:
        return None
    try:
        path_buffer = ctypes.create_unicode_buffer(32_768)
        path_size = wintypes.DWORD(len(path_buffer))
        if not kernel32.QueryFullProcessImageNameW(process, 0, path_buffer, ctypes.byref(path_size)):
            return None
        creation, exit_time, kernel_time, user_time = FileTime(), FileTime(), FileTime(), FileTime()
        if not kernel32.GetProcessTimes(
                process, ctypes.byref(creation), ctypes.byref(exit_time),
                ctypes.byref(kernel_time), ctypes.byref(user_time)):
            return None
        file_time = (creation.high << 32) | creation.low
        started_at = file_time / 10_000_000 - WINDOWS_EPOCH_SECONDS
        return Path(path_buffer.value).name.lower(), started_at
    finally:
        kernel32.CloseHandle(process)


def find_minecraft_window(launched_at: float) -> int | None:
    found: list[int] = []

    @EnumWindowsCallback
    def callback(handle: int, parameter: int) -> bool:
        if not user32.IsWindowVisible(handle):
            return True
        title = ctypes.create_unicode_buffer(512)
        user32.GetWindowTextW(handle, title, len(title))
        if "minecraft" not in title.value.lower():
            return True
        process_id = wintypes.DWORD()
        user32.GetWindowThreadProcessId(handle, ctypes.byref(process_id))
        details = process_details(process_id.value)
        if details and details[0] in ("java.exe", "javaw.exe") and details[1] >= launched_at - 5:
            found.append(handle)
            return False
        return True

    user32.EnumWindows(callback, 0)
    return found[0] if found else None


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def write_png(path: Path, width: int, height: int, bgra: bytes) -> None:
    rgb = bytearray(width * height * 3)
    rgb[0::3] = bgra[2::4]
    rgb[1::3] = bgra[1::4]
    rgb[2::3] = bgra[0::4]
    stride = width * 3
    scanlines = b"".join(b"\x00" + rgb[offset:offset + stride]
                         for offset in range(0, len(rgb), stride))
    content = b"\x89PNG\r\n\x1a\n"
    content += png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    content += png_chunk(b"IDAT", zlib.compress(scanlines, 6))
    content += png_chunk(b"IEND", b"")
    path.write_bytes(content)


def save_window_screenshot(handle: int, path: Path) -> None:
    topmost = wintypes.HWND(-1)
    not_topmost = wintypes.HWND(-2)
    flags = 0x0043  # SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW
    user32.ShowWindowAsync(handle, 9)
    user32.SetForegroundWindow(handle)
    user32.SetWindowPos(handle, topmost, 0, 0, 0, 0, flags)
    time.sleep(0.7)

    rect = Rect()
    if not user32.GetWindowRect(handle, ctypes.byref(rect)):
        raise ctypes.WinError(ctypes.get_last_error())
    width, height = rect.right - rect.left, rect.bottom - rect.top
    if width <= 0 or height <= 0:
        raise RuntimeError("Minecraft 窗口尺寸无效")

    screen_dc = user32.GetDC(None)
    memory_dc = gdi32.CreateCompatibleDC(screen_dc)
    bitmap = gdi32.CreateCompatibleBitmap(screen_dc, width, height)
    previous = gdi32.SelectObject(memory_dc, bitmap)
    try:
        if not gdi32.BitBlt(memory_dc, 0, 0, width, height, screen_dc,
                            rect.left, rect.top, 0x40CC0020):
            raise ctypes.WinError(ctypes.get_last_error())
        info = BitmapInfo()
        info.header = BitmapInfoHeader(
            ctypes.sizeof(BitmapInfoHeader), width, -height, 1, 32, 0,
            width * height * 4, 0, 0, 0, 0,
        )
        pixels = ctypes.create_string_buffer(width * height * 4)
        if not gdi32.GetDIBits(memory_dc, bitmap, 0, height, pixels, ctypes.byref(info), 0):
            raise ctypes.WinError(ctypes.get_last_error())
        write_png(path, width, height, pixels.raw)
    finally:
        user32.SetWindowPos(handle, not_topmost, 0, 0, 0, 0, flags)
        gdi32.SelectObject(memory_dc, previous)
        gdi32.DeleteObject(bitmap)
        gdi32.DeleteDC(memory_dc)
        user32.ReleaseDC(None, screen_dc)


def stop_process_tree(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    subprocess.run(
        ["taskkill", "/pid", str(process.pid), "/t", "/f"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
    )


def run_test(arguments: argparse.Namespace) -> Path:
    java_home = select_java_home(arguments.java_path)
    world = select_world(arguments.loader, arguments.world)
    result_directory = REPOSITORY_ROOT / "build" / "local-test" / arguments.loader
    if result_directory.exists():
        shutil.rmtree(result_directory)
    result_directory.mkdir(parents=True)

    environment = os.environ.copy()
    if java_home is not None:
        environment["JAVA_HOME"] = str(java_home)
        environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    environment["AUTOTORCH_LOCAL_TEST_DIR"] = str(result_directory)

    gradle = REPOSITORY_ROOT / "gradlew.bat"
    command = [str(gradle), f":{arguments.loader}:runClient",
               f'--args=--quickPlaySingleplayer "{world}"']
    stdout_path = result_directory / "gradle.stdout.log"
    stderr_path = result_directory / "gradle.stderr.log"
    print(f"启动 {arguments.loader} 客户端，测试存档：{world}")
    launched_at = time.time()
    with stdout_path.open("w", encoding="utf-8") as stdout_file, \
            stderr_path.open("w", encoding="utf-8") as stderr_file:
        process = subprocess.Popen(
            command, cwd=REPOSITORY_ROOT, env=environment,
            stdout=stdout_file, stderr=stderr_file,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        captured: set[str] = set()
        deadline = time.monotonic() + arguments.timeout_seconds
        try:
            while process.poll() is None and time.monotonic() < deadline:
                for checkpoint in CHECKPOINTS:
                    if checkpoint in captured or not (result_directory / f"{checkpoint}.ready").exists():
                        continue
                    window = find_minecraft_window(launched_at)
                    if window is None:
                        continue
                    save_window_screenshot(window, result_directory / f"{checkpoint}.png")
                    (result_directory / f"{checkpoint}.captured").write_text("OK\n", encoding="utf-8")
                    captured.add(checkpoint)
                    print(f"已截图：{checkpoint}.png")
                time.sleep(0.25)
            if process.poll() is None:
                stop_process_tree(process)
                raise TimeoutError(f"本地测试超过 {arguments.timeout_seconds} 秒，已终止")
            process.wait()
        finally:
            stop_process_tree(process)

    status_path = result_directory / "status.txt"
    if not status_path.is_file():
        raise RuntimeError(f"游戏没有生成测试结果，请检查 {stdout_path} 和 {stderr_path}")
    status = status_path.read_text(encoding="utf-8").strip()
    print(f"测试结果：{status}")
    print(f"报告与截图：{result_directory}")
    if status != "PASS":
        raise RuntimeError(status)
    return result_directory


def main() -> int:
    if os.name != "nt":
        print("错误：本地游戏窗口测试目前仅支持 Windows", file=sys.stderr)
        return 1
    try:
        run_test(parse_arguments())
        return 0
    except Exception as exception:
        print(f"错误：{exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
