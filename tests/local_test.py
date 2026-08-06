#!/usr/bin/env python3
"""在 Windows 本机启动开发客户端、执行冒烟测试并生成汇总报告。"""

from __future__ import annotations

import argparse
from collections import deque
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import threading
import time
import zlib


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
CHECKPOINTS = {
    "01-world": "01-游戏世界",
    "02-settings": "02-自动照明设置",
    "03-selection": "03-方块选区",
    "04-light-overlay": "04-光照强度显示",
}
LOADERS = ("fabric", "forge", "neoforge")
WINDOWS_EPOCH_SECONDS = 11_644_473_600
MAX_LOG_CHARACTERS = 2_000_000
REPORT_SCHEMA_VERSION = 3
REPORT_TEMPLATE = Path(__file__).with_name("local_test_report.html")


def parse_arguments(arguments: list[str] | None = None) -> argparse.Namespace | None:
    parser = argparse.ArgumentParser(description="自动启动 Minecraft 开发客户端并执行本地冒烟测试")
    parser.add_argument("--loader", choices=(*LOADERS, "all"), default="fabric",
                        help="要测试的加载器；all 表示依次测试全部加载器（默认：fabric）")
    parser.add_argument("--world", help="存档文件夹名；省略时使用当前 Minecraft 版本号")
    parser.add_argument("--java-path", type=Path, help="JDK 根目录")
    parser.add_argument("--timeout-seconds", type=int, default=240)
    actual_arguments = sys.argv[1:] if arguments is None else arguments
    if not actual_arguments:
        parser.print_help()
        return None
    return parser.parse_args(actual_arguments)


def select_java_home(configured: Path | None) -> Path | None:
    """返回可选的 Gradle 启动 JDK；项目 Toolchain 由 Gradle 自动下载。"""
    if configured is not None:
        candidate = configured.resolve()
        executable = candidate / "bin" / "java.exe"
        if not executable.is_file():
            raise RuntimeError(f"指定路径不是有效的 JDK：{candidate}")
        return candidate
    return None


def read_minecraft_version() -> str:
    properties = REPOSITORY_ROOT / "gradle.properties"
    for line in properties.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and key.strip() == "minecraft_version":
            version = value.strip()
            if version:
                return version
    raise RuntimeError(f"{properties} 中没有有效的 minecraft_version")


def read_branch_name() -> str:
    """读取当前 Git 分支原名。"""
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=REPOSITORY_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    branch_name = result.stdout.strip()
    if not branch_name:
        raise RuntimeError("当前处于 detached HEAD，无法确定本地测试输出所需的分支名")
    return branch_name


def read_branch_folder_name() -> str:
    """读取当前 Git 分支名，并转换为可用作单层目录的名称。"""
    return read_branch_name().replace("/", "／")


def world_folder_name(world_name: str) -> str:
    """按原版规则把世界显示名转换为默认存档目录名。"""
    return re.sub(r'[\\/."<>|:?*]', "_", world_name)


def select_world(loader: str, configured: str | None) -> tuple[str, str, bool]:
    saves = REPOSITORY_ROOT / loader / "run" / "saves"
    world_name = configured or read_minecraft_version()
    configured_path = saves / world_name
    world_folder = world_name if (configured_path / "level.dat").is_file() else world_folder_name(world_name)
    world_path = saves / world_folder
    level_data = world_path / "level.dat"
    if world_path.exists() and not level_data.is_file():
        raise RuntimeError(f"存档目录已存在但缺少 level.dat，无法自动创建：{world_path}")
    return world_name, world_folder, level_data.is_file()


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


class MemoryLog:
    """在内存中保存有限长度的进程输出。"""

    def __init__(self, maximum: int = MAX_LOG_CHARACTERS) -> None:
        self.maximum = maximum
        self.parts: deque[str] = deque()
        self.length = 0
        self.truncated = False
        self.lock = threading.Lock()

    def append(self, content: str) -> None:
        with self.lock:
            self.parts.append(content)
            self.length += len(content)
            while self.length > self.maximum and self.parts:
                removed = self.parts.popleft()
                self.length -= len(removed)
                self.truncated = True

    def text(self) -> str:
        with self.lock:
            prefix = "[较早的输出已截断]\n" if self.truncated else ""
            return prefix + "".join(self.parts)


def collect_stream(stream, target: MemoryLog) -> None:
    """持续排空子进程管道，避免 Gradle 因缓冲区写满而阻塞。"""
    try:
        while True:
            content = stream.read(4096)
            if not content:
                return
            target.append(content)
    finally:
        stream.close()


class LocalTestServer:
    """通过 localhost TCP 与游戏内测试运行器交换 JSON 行消息。"""

    def __init__(self) -> None:
        self.server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server.bind(("127.0.0.1", 0))
        self.server.listen(1)
        self.server.setblocking(False)
        self.port = self.server.getsockname()[1]
        self.token = secrets.token_urlsafe(24)
        self.connection: socket.socket | None = None
        self.buffer = b""
        self.authenticated = False

    def poll(self) -> list[dict]:
        messages: list[dict] = []
        if self.connection is None:
            try:
                self.connection, _ = self.server.accept()
                self.connection.setblocking(False)
            except BlockingIOError:
                return messages

        while self.connection is not None:
            try:
                content = self.connection.recv(65_536)
            except BlockingIOError:
                break
            if not content:
                self.connection.close()
                self.connection = None
                break
            self.buffer += content
            while b"\n" in self.buffer:
                raw, self.buffer = self.buffer.split(b"\n", 1)
                if not raw.strip():
                    continue
                message = json.loads(raw.decode("utf-8"))
                if not self.authenticated:
                    if message.get("type") != "hello" or message.get("token") != self.token:
                        raise RuntimeError("本地测试客户端身份校验失败")
                    self.authenticated = True
                    continue
                messages.append(message)
        return messages

    def send(self, message: dict) -> None:
        if self.connection is None or not self.authenticated:
            raise RuntimeError("本地测试客户端尚未连接")
        content = (json.dumps(message, ensure_ascii=False) + "\n").encode("utf-8")
        self.connection.settimeout(2)
        try:
            self.connection.sendall(content)
        finally:
            self.connection.setblocking(False)

    def close(self) -> None:
        if self.connection is not None:
            self.connection.close()
        self.server.close()


def stop_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    subprocess.run(
        ["taskkill", "/pid", str(process.pid), "/t", "/f"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
    )


def write_failure_log(path: Path, stdout: str, stderr: str) -> None:
    path.write_text(
        "===== 标准输出 =====\n" + stdout + "\n===== 标准错误 =====\n" + stderr,
        encoding="utf-8",
    )


def run_test(arguments: argparse.Namespace, loader: str, branch_name: str,
             branch_directory: Path) -> dict:
    started_at = datetime.now(timezone.utc)
    started_monotonic = time.monotonic()
    java_home = select_java_home(arguments.java_path)
    world_name, world_folder, world_exists = select_world(loader, arguments.world)
    result_directory = branch_directory / loader
    if result_directory.exists():
        shutil.rmtree(result_directory)
    result_directory.mkdir(parents=True)

    server = LocalTestServer()
    environment = os.environ.copy()
    if java_home is not None:
        environment["JAVA_HOME"] = str(java_home)
        environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    environment["AUTOTORCH_LOCAL_TEST_HOST"] = "127.0.0.1"
    environment["AUTOTORCH_LOCAL_TEST_PORT"] = str(server.port)
    environment["AUTOTORCH_LOCAL_TEST_TOKEN"] = server.token
    environment["AUTOTORCH_LOCAL_TEST_WORLD"] = world_name
    environment["AUTOTORCH_LOCAL_TEST_WORLD_FOLDER"] = world_folder
    environment["AUTOTORCH_LOCAL_TEST_CREATE_WORLD"] = "0" if world_exists else "1"

    command = [str(REPOSITORY_ROOT / "gradlew.bat"), f":{loader}:runClient"]
    if world_exists and loader != "neoforge":
        command.append(f'--args=--quickPlaySingleplayer "{world_folder}"')
    action = "加入" if world_exists else "创建"
    folder_detail = f"（目录：{world_folder}）" if world_folder != world_name else ""
    print(f"启动 {loader} 客户端，{action}测试存档：{world_name}{folder_detail}")

    stdout_log, stderr_log = MemoryLog(), MemoryLog()
    assertions: list[dict] = []
    captured: set[str] = set()
    pending_checkpoints: dict[str, str] = {}
    test_status: str | None = None
    driver_error: str | None = None
    launched_at = time.time()
    process: subprocess.Popen[str] | None = None
    threads: list[threading.Thread] = []

    try:
        process = subprocess.Popen(
            command, cwd=REPOSITORY_ROOT, env=environment,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", errors="replace",
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        assert process.stdout is not None and process.stderr is not None
        threads = [
            threading.Thread(target=collect_stream, args=(process.stdout, stdout_log), daemon=True),
            threading.Thread(target=collect_stream, args=(process.stderr, stderr_log), daemon=True),
        ]
        for thread in threads:
            thread.start()

        deadline = time.monotonic() + arguments.timeout_seconds
        while time.monotonic() < deadline:
            for message in server.poll():
                message_type = message.get("type")
                if message_type == "checkpoint_ready":
                    name = str(message.get("name", ""))
                    if name in CHECKPOINTS:
                        pending_checkpoints[name] = str(message.get("description", ""))
                elif message_type == "assertion":
                    assertions.append({
                        "status": str(message.get("status", "FAIL")),
                        "test": str(message.get("test", "未命名检查")),
                        "detail": str(message.get("detail", "")),
                    })
                elif message_type == "completed":
                    test_status = str(message.get("status", "FAIL"))

            for checkpoint in tuple(pending_checkpoints):
                window = find_minecraft_window(launched_at)
                if window is None:
                    break
                image_name = CHECKPOINTS[checkpoint]
                try:
                    save_window_screenshot(window, result_directory / f"{image_name}.png")
                except OSError:
                    # Forge 创建世界时可能短暂替换窗口句柄，下一轮重新查找即可。
                    continue
                server.send({"type": "screenshot_captured", "name": checkpoint})
                captured.add(checkpoint)
                pending_checkpoints.pop(checkpoint)
                print(f"已截图：{image_name}.png")

            if process.poll() is not None:
                break
            time.sleep(0.1)
        else:
            driver_error = f"本地测试超过 {arguments.timeout_seconds} 秒，已终止"
            stop_process_tree(process)

        process.wait()
    except Exception as exception:
        driver_error = str(exception)
        if process is not None:
            stop_process_tree(process)
            process.wait()
    finally:
        server.close()
        for thread in threads:
            thread.join(timeout=5)

    gradle_exit_code = process.returncode if process is not None else None
    if test_status is None:
        test_status = "FAIL"
        if driver_error is None:
            driver_error = "游戏进程结束前没有发送 completed 消息"
    missing_checkpoints = [checkpoint for checkpoint in CHECKPOINTS if checkpoint not in captured]
    status = "PASS" if test_status == "PASS" and gradle_exit_code == 0 and not missing_checkpoints else "FAIL"
    finished_at = datetime.now(timezone.utc)
    failure_log: str | None = None
    if status != "PASS":
        failure_log = f"{loader}/failure.log"
        write_failure_log(branch_directory / failure_log, stdout_log.text(), stderr_log.text())

    summary = {
        "branch": branch_name,
        "loader": loader,
        "world": world_name,
        "world_folder": world_folder,
        "status": status,
        "test_status": test_status,
        "gradle_exit_code": gradle_exit_code,
        "started_at": started_at.isoformat(),
        "finished_at": finished_at.isoformat(),
        "duration_seconds": round(time.monotonic() - started_monotonic, 3),
        "assertions": assertions,
        "checkpoints": {
            image_name: f"{loader}/{image_name}.png" if checkpoint in captured else None
            for checkpoint, image_name in CHECKPOINTS.items()
        },
        "failure_log": failure_log,
        "error": driver_error,
    }
    print(f"测试结果：{loader} {status}")
    return summary


def load_existing_tests(summary_path: Path, branch_name: str) -> dict[str, dict]:
    if not summary_path.is_file():
        return {}
    try:
        content = json.loads(summary_path.read_text(encoding="utf-8"))
        if content.get("branch") != branch_name or content.get("schema_version") != REPORT_SCHEMA_VERSION:
            return {}
        return {
            test["loader"]: test for test in content.get("tests", [])
            if test.get("loader") in LOADERS
        }
    except (OSError, ValueError, KeyError, TypeError):
        return {}


def render_html(report: dict) -> str:
    """把报告数据注入独立 HTML 模板。"""
    template = REPORT_TEMPLATE.read_text(encoding="utf-8")
    report_json = json.dumps(report, ensure_ascii=False)
    # 防止数据中的特殊内容提前结束 script 标签。
    report_json = (report_json.replace("<", "\\u003c")
                   .replace("\u2028", "\\u2028")
                   .replace("\u2029", "\\u2029"))
    return template.replace("__REPORT_JSON__", report_json)


def write_reports(branch_directory: Path, branch_name: str, tests: dict[str, dict]) -> dict:
    legacy_summary = branch_directory / "summary.csv"
    if legacy_summary.is_file():
        legacy_summary.unlink()
    legacy_names = {
        "completed", "gradle.stderr.log", "gradle.stdout.log", "results.tsv",
        "status.txt", "summary.json",
    }
    for loader in LOADERS:
        loader_directory = branch_directory / loader
        if not loader_directory.is_dir():
            continue
        for path in loader_directory.iterdir():
            if path.is_file() and (path.name in legacy_names or path.suffix in {".ready", ".captured"}):
                path.unlink()

    ordered_tests = [tests[loader] for loader in LOADERS if loader in tests]
    report = {
        "schema_version": REPORT_SCHEMA_VERSION,
        "branch": branch_name,
        "status": "PASS" if ordered_tests and all(test.get("status") == "PASS" for test in ordered_tests)
        else "FAIL",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "tests": ordered_tests,
    }
    (branch_directory / "summary.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    (branch_directory / "report.html").write_text(render_html(report), encoding="utf-8")
    return report


def main(arguments: list[str] | None = None) -> int:
    parsed = parse_arguments(arguments)
    if parsed is None:
        return 0
    if os.name != "nt":
        print("错误：本地游戏窗口测试目前仅支持 Windows", file=sys.stderr)
        return 1
    try:
        branch_name = read_branch_name()
        branch_directory = REPOSITORY_ROOT / "build" / "local-test" / read_branch_folder_name()
        branch_directory.mkdir(parents=True, exist_ok=True)
        tests = load_existing_tests(branch_directory / "summary.json", branch_name)
        loaders = LOADERS if parsed.loader == "all" else (parsed.loader,)
        for loader in loaders:
            tests[loader] = run_test(parsed, loader, branch_name, branch_directory)
        report = write_reports(branch_directory, branch_name, tests)
        print(f"汇总报告：{branch_directory / 'report.html'}")
        failed_loaders = [loader for loader in loaders if tests[loader]["status"] != "PASS"]
        if failed_loaders:
            raise RuntimeError(f"以下加载器测试未通过：{', '.join(failed_loaders)}")
        return 0
    except Exception as exception:
        print(f"错误：{exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
