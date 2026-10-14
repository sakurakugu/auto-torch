"""Auto Torch Minecraft 构建/启动工具（PCL-CE CLI 版本）。"""
from __future__ import annotations
import json, os, queue, re, shutil, signal, subprocess, sys, threading, time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
WORKTREES_ROOT = PROJECT_ROOT.parent / "worktrees"

def run(cmd: list[str], cwd: Path | None = None, capture=False, cancel_event=None):
    """运行子进程，并在 Ctrl+C 时终止整个进程树。"""
    if cancel_event is not None and cancel_event.is_set():
        raise RuntimeError("已取消后台构建。")
    creationflags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    p = subprocess.Popen(
        cmd, cwd=str(cwd) if cwd else None, text=True, encoding="utf-8", errors="replace",
        stdout=subprocess.PIPE if capture else None, stderr=subprocess.PIPE if capture else None,
        creationflags=creationflags,
    )
    try:
        while True:
            try:
                stdout, stderr = p.communicate(timeout=0.5 if cancel_event is not None else None)
                break
            except subprocess.TimeoutExpired:
                if cancel_event.is_set():
                    raise KeyboardInterrupt
    except KeyboardInterrupt:
        if os.name == "nt":
            # Python 3.14 不再保证 subprocess 暴露 CTRL_BREAK_EVENT，
            # 该常量的正式归属是 signal 模块。
            break_signal = getattr(subprocess, "CTRL_BREAK_EVENT", None)
            if break_signal is None:
                break_signal = getattr(signal, "CTRL_BREAK_EVENT", None)
            try:
                if break_signal is not None:
                    p.send_signal(break_signal)
            except (OSError, ValueError):
                # 进程可能已在 Ctrl+C 到达前退出，继续执行 taskkill 清理。
                pass
            try:
                p.wait(timeout=3)
            except subprocess.TimeoutExpired:
                pass
            # 即使包装器已退出，也清理其可能遗留的 Gradle/Minecraft 子进程。
            subprocess.run(["taskkill", "/PID", str(p.pid), "/T", "/F"], capture_output=True, check=False)
        else:
            p.terminate()
            try:
                p.wait(timeout=3)
            except subprocess.TimeoutExpired:
                p.kill()
        if cancel_event is not None and cancel_event.is_set():
            raise RuntimeError("已取消后台构建。")
        raise
    return subprocess.CompletedProcess(cmd, p.returncode, stdout, stderr)

def version_key(v: str):
    return tuple((list(map(int, v.split("."))) + [0] * 4)[:4])

def prop(root: Path, name: str) -> str:
    p = root / "gradle.properties"
    if p.is_file():
        for line in p.read_text(encoding="utf-8", errors="replace").splitlines():
            m = re.match(rf"^{re.escape(name)}=(.*)$", line)
            if m: return m.group(1).strip()
    raise RuntimeError(f"未在 {p} 中找到属性 {name}。")

def branches():
    r = run(["git", "-C", str(PROJECT_ROOT), "for-each-ref", "--format=%(refname:short)", "refs/heads/mc/"], capture=True)
    if r.returncode: raise RuntimeError("无法读取 Minecraft 版本分支。")
    out = []
    for b in r.stdout.splitlines():
        m = re.fullmatch(r"mc/(\d+(?:\.\d+){1,3})", b.strip())
        if m: out.append((b.strip(), m.group(1)))
    return sorted(out, key=lambda x: version_key(x[1]), reverse=True)

def worktree(branch: str, version: str) -> Path:
    root = WORKTREES_ROOT / version
    if (root / ".git").exists(): return root
    if root.exists() and any(root.iterdir()): raise RuntimeError(f"版本工作树目录已存在但不是 Git 工作树：{root}")
    WORKTREES_ROOT.mkdir(parents=True, exist_ok=True)
    run(["git", "-C", str(PROJECT_ROOT), "worktree", "prune"])
    print(f"创建 {version} 工作树：{root}")
    if run(["git", "-C", str(PROJECT_ROOT), "worktree", "add", str(root), branch]).returncode:
        raise RuntimeError(f"无法创建 {version} 工作树。")
    return root

def java_env(path):
    # Mavenizer 会单独调用旧版 javac，需要通过环境变量统一使用 UTF-8。
    java_options = os.environ.get("JAVA_TOOL_OPTIONS", "").strip()
    utf8_option = "-Dfile.encoding=UTF-8"
    if java_options.split()[-1:] != [utf8_option]:
        os.environ["JAVA_TOOL_OPTIONS"] = f"{java_options} {utf8_option}".strip()
    if not path:
        print("Gradle 将通过 Toolchain 自动检测或下载所需 Java。")
        return
    home = Path(path).expanduser().resolve()
    exe = home / "bin" / ("java.exe" if os.name == "nt" else "java")
    if not exe.is_file(): raise RuntimeError(f"指定的 Java 路径无效：'{path}'。未找到 bin/java.exe。")
    os.environ["JAVA_HOME"] = str(home); os.environ["PATH"] = str(home / "bin") + os.pathsep + os.environ.get("PATH", "")

def gradle(root, *args, cancel_event=None):
    return run([str(root / "gradlew.bat"), *args], cwd=root, cancel_event=cancel_event).returncode

def build_all_branches():
    """构建所有 Minecraft 分支，并将产物汇总到主分支的专用目录。"""
    vs = branches()
    if not vs: raise RuntimeError("未找到 mc/<版本号> 形式的本地分支。")
    built = []
    names = set()
    for branch, version in vs:
        root = worktree(branch, version)
        if gradle(root, "clean", "build"):
            raise RuntimeError(f"[{version}] 构建失败。")
        mod = prop(root, "mod_version")
        source_dir = root / "build" / f"v{mod}"
        files = list(source_dir.glob(f"autotorch-v{mod}-mc{version}-*.jar")) if source_dir.is_dir() else []
        if not files:
            raise RuntimeError(f"[{version}] 未在 {source_dir} 中找到构建产物。")
        for file in files:
            if file.name in names:
                raise RuntimeError(f"汇总产物名称冲突：{file.name}。")
            names.add(file.name)
            built.append(file)

    mod = prop(PROJECT_ROOT, "mod_version")
    archive = PROJECT_ROOT / "build" / f"v{mod}-all_branch"
    archive.mkdir(parents=True, exist_ok=True)
    for old in archive.glob("autotorch-*.jar"):
        old.unlink()
    for file in built:
        shutil.copy2(file, archive / file.name)
    print(f"已将 {len(built)} 个产物汇总到：{archive}")
    return archive

def pcl_cli(release, *args):
    """运行 PCL-CE CLI 命令并解析 PCL_CLI_RESULT 结果块（config 命令输出的是缩进的多行 JSON）。"""
    r = run([str(release / "PCL2_Release.exe"), *args], cwd=release, capture=True)
    output, result = (r.stdout or "") + (r.stderr or ""), None
    marker = "PCL_CLI_RESULT="
    if (i := output.rfind(marker)) >= 0:
        try: result = json.JSONDecoder().raw_decode(output[i + len(marker):])[0]
        except ValueError: pass
    return result, output

def skip_profile_auth(release):
    """首次运行时通过 PCL-CE CLI 开启 LaunchSkipProfileAuthRequirement，跳过创建档案的正版账号要求；用户已显式设置过则不覆盖。"""
    key = "LaunchSkipProfileAuthRequirement"
    result, output = pcl_cli(release, "config", "--get", key)
    if not result or result.get("status") != "ok": raise RuntimeError(f"无法读取 PCL-CE 配置项 {key}：{(result or {}).get('message') or output.strip() or '无 CLI 输出'}")
    if not (result.get("data") or {}).get("is_default"): return
    result, output = pcl_cli(release, "config", "--set", f"{key}=true")
    if not result or result.get("status") != "ok": raise RuntimeError(f"无法写入 PCL-CE 配置项 {key}：{(result or {}).get('message') or output.strip() or '无 CLI 输出'}")
    print("首次运行：已通过 PCL-CE CLI 开启 LaunchSkipProfileAuthRequirement（跳过档案正版账号要求）。")

def set_language(p: Path, version: str):
    match = re.match(r"^1\.(\d+)", version)
    lang = "zh_CN" if match and int(match.group(1)) <= 10 else "zh_cn"
    p.parent.mkdir(parents=True, exist_ok=True)
    lines = p.read_text(encoding="utf-8", errors="replace").splitlines() if p.is_file() else []
    out=[]; done=False
    for line in lines:
        if line.startswith("lang:"):
            if not done: out.append(f"lang:{lang}"); done=True
        else: out.append(line)
    if not done: out.append(f"lang:{lang}")
    p.write_text("\n".join(out) + "\n", encoding="utf-8")

def language(root, loader):
    set_language(root / loader / "run" / "options.txt", prop(root, "minecraft_version"))

def loader_client(root, loader, version):
    if not (root / loader / "build.gradle").is_file(): print(f"[{version}] 未找到 {loader}，跳过。"); return
    print(f"[{version}] 启动 {loader}；关闭游戏后继续。"); language(root, loader)
    code = gradle(root, f":{loader}:runClient")
    if code: print(f"警告：[{version}] {loader} 已退出，退出码：{code}。", file=sys.stderr)

def release_build(root, version, cancel_event=None):
    print(f"\n========== 打包 Minecraft {version} =========="); run(["git", "-C", str(root), "config", "core.hooksPath", ".githooks"])
    code=gradle(root, "clean", "build", cancel_event=cancel_event)
    if code: raise RuntimeError(f"[{version}] 打包失败，退出码：{code}。")
    ar=root / "build" / f"v{prop(root,'mod_version')}"
    # 打包目录可能包含其他 Minecraft 版本的历史产物，只返回本次版本。
    files=list(ar.glob(f"autotorch-v{prop(root,'mod_version')}-mc{version}-*.jar")) if ar.is_dir() else []
    if not files: raise RuntimeError(f"[{version}] 未在 {ar} 中找到打包产物。")
    return files

def artifact_batches(root, task, cancel_event=None):
    """每完成一个 Minecraft 版本就交付产物，供前台立即部署和启动。"""
    mod=prop(root,"mod_version"); archive=root / "build" / f"v{mod}"
    if task != "all_branch":
        yield release_build(root, prop(root,"minecraft_version"), cancel_event)
        return
    vs=branches()
    if not vs: raise RuntimeError("未找到 mc/<版本号> 形式的本地分支。")
    archive.mkdir(parents=True, exist_ok=True)
    for old in archive.glob(f"autotorch-v{mod}-mc*.jar"): old.unlink()
    for b,v in vs:
        if cancel_event is not None and cancel_event.is_set(): return
        files = release_build(worktree(b,v), v, cancel_event)
        copied = []
        for f in files:
            dest = archive / f.name
            shutil.copy2(f, dest)
            copied.append(dest)
        yield copied

def artifacts(root, task):
    return [f for batch in artifact_batches(root, task) for f in batch]

def newest_instance(found, version, loader):
    """存在多个实例时按加载器版本的数值选择最新版本，避免字典序误判。"""
    if len(found) == 1: return found[0]
    pattern = re.compile(rf"{re.escape(version)}-{re.escape(loader)}[_ ](\d+(?:\.\d+)+)(?:-([\w.]+))?", re.I)
    ranked = []
    for instance in found:
        match = pattern.fullmatch(instance.name)
        if not match: break
        # 同一数值版本优先正式版；未知命名或同版本重复实例仍报错。
        key = (tuple(map(int, match.group(1).split('.'))), not bool(match.group(2)))
        ranked.append((key, instance))
    else:
        best = max(key for key, _ in ranked)
        latest = [instance for key, instance in ranked if key == best]
        if len(latest) == 1:
            print(f"[{version} {loader}] 检测到多个生产测试实例，选择最新版本：{latest[0].name}")
            return latest[0]
    raise RuntimeError(f"{version} {loader} 匹配到多个生产测试实例，无法确定最新版本：{'、'.join(p.name for p in found)}")

def targets(files, release, loader_filter):
    vr=release / ".minecraft" / "versions"
    if not vr.is_dir(): raise RuntimeError(f"生产测试端缺少版本目录：{vr}")
    pat=re.compile(r"^autotorch-v[^-]+-mc(?P<v>\d+(?:\.\d+){1,3})-(?P<l>forge|fabric|neoforge)\.jar$", re.I); out=[]
    for f in files:
        m=pat.match(f.name)
        if not m: print(f"警告：无法识别产物名称，跳过：{f.name}", file=sys.stderr); continue
        v,l=m.group("v"),m.group("l").lower()
        if loader_filter and l != loader_filter: continue
        found=[p for p in vr.iterdir() if p.is_dir() and re.fullmatch(rf"{re.escape(v)}-{re.escape(l)}(?:[_ ].*)?",p.name,re.I)]
        if len(found)==0: raise RuntimeError(f"未安装 {v} {l} 的生产测试实例；release 模式不会下载实例。")
        out.append((v,l,f,newest_instance(found,v,l)))
    order={"forge":0,"fabric":1,"neoforge":2}; return sorted(out,key=lambda x:(version_key(x[0]),-order[x[1]]),reverse=True)

def release_clients(build_roots, task, release, loader_filter=None):
    """后台串行构建各版本，前台按交付顺序逐个启动并等待游戏关闭。"""
    pending = queue.Queue()
    cancel_event = threading.Event()

    def build():
        try:
            for root in build_roots:
                for files in artifact_batches(root, task, cancel_event):
                    if cancel_event.is_set(): return
                    pending.put((files, None))
        except Exception as exc:
            pending.put((None, exc))
        finally:
            pending.put((None, None))

    worker = threading.Thread(target=build, name="release-build")
    print("后台逐版本打包；每个版本完成后立即启动测试，关闭游戏后启动下一个。")
    worker.start()
    launched = False
    try:
        while True:
            try: files, error = pending.get(timeout=0.5)
            except queue.Empty: continue
            if error is not None: raise error
            if files is None: break
            selected = targets(files, release, loader_filter)
            for target in selected: install(target)
            for target in selected:
                start_client(target, release)
                launched = True
        if not launched: raise RuntimeError("没有可部署的 release 产物。")
    finally:
        # 主线程收到 Ctrl+C 或启动失败时，终止正在构建的 Gradle 进程树。
        cancel_event.set()
        worker.join()

def install(item):
    _,_,artifact,root=item; mods=root / "mods"; mods.mkdir(parents=True,exist_ok=True)
    for old in mods.glob("autotorch-*.jar"): old.unlink()
    shutil.copy2(artifact, mods / artifact.name); print(f"已部署 [{root.name}] {artifact.name}")

def pcl_processes(root):
    if os.name != "nt": return []
    esc=str(root).replace("'","''"); ps=f"$r=(Resolve-Path -LiteralPath '{esc}').Path.TrimEnd('\\')+'\\'; Get-CimInstance Win32_Process | ? {{$_.ExecutablePath -and $_.ExecutablePath.StartsWith($r,[StringComparison]::OrdinalIgnoreCase) -and ([IO.Path]::GetFileName($_.ExecutablePath) -eq 'PCL2_Release.exe')}} | select -Expand ProcessId | ConvertTo-Json -Compress"
    r=run(["powershell","-NoProfile","-NonInteractive","-Command",ps],capture=True)
    try:
        v=json.loads(r.stdout); return [int(v)] if isinstance(v,int) else [int(x) for x in v]
    except (ValueError,TypeError): return []

def minecraft_process(instance):
    """查找命令行中包含指定实例目录的 Minecraft Java 进程。"""
    if os.name != "nt": return None
    esc=str(instance).replace("'", "''")
    ps=("Get-CimInstance Win32_Process | ? {($_.Name -in @('java.exe','javaw.exe')) "
        f"-and $_.CommandLine -and $_.CommandLine.IndexOf('{esc}',[StringComparison]::OrdinalIgnoreCase) -ge 0}} "
        "| select -First 1 -Expand ProcessId")
    r=subprocess.run(["powershell","-NoProfile","-NonInteractive","-Command",ps],capture_output=True,text=True,
                     encoding="utf-8",errors="replace",check=False)
    try: return int((r.stdout or "").strip().splitlines()[-1])
    except (ValueError,IndexError): return None

def process_running(pid):
    """在 Windows 上直接查询进程是否仍存在，避免启动额外的等待进程。"""
    if os.name != "nt":
        try: os.kill(pid, 0); return True
        except OSError: return False
    import ctypes
    handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, int(pid))
    if not handle: return False
    code = ctypes.c_ulong()
    ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
    ctypes.windll.kernel32.CloseHandle(handle)
    return code.value == 259

def start_client(item, release):
    v,l,_,instance=item; exe=release / "PCL2_Release.exe"
    if not exe.is_file(): raise RuntimeError(f"未找到 PCL-CE CLI 启动器：{exe}")
    set_language(instance / "options.txt", v)
    print(f"\n[{instance.name}] 启动生产客户端；关闭游戏后继续。")
    # launch 必须是第一个用户参数，PCL-CE 的命令行解析器据此识别子命令。
    cmd=[str(exe),"launch","--instance",str(instance),"--folder",str(release/".minecraft")]
    try:
        # PCL CLI 会一直保持运行直到游戏结束，不能用 communicate() 阻塞等待它。
        flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        if os.name == "nt": flags |= getattr(subprocess, "CREATE_NO_WINDOW", 0)
        launcher = subprocess.Popen(cmd, cwd=str(release), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=flags)
        deadline = time.monotonic() + 180
        pid = None
        while time.monotonic() < deadline:
            pid = minecraft_process(instance)
            if pid: break
            time.sleep(0.5)
        if not pid: raise RuntimeError(f"[{instance.name}] 等待 Minecraft 进程启动超时。")
        print(f"[{instance.name}] Minecraft 已启动。")
        try:
            while process_running(pid):
                time.sleep(0.5)
        except KeyboardInterrupt:
            subprocess.run(["taskkill", "/PID", str(int(pid)), "/T", "/F"], capture_output=True, check=False)
            raise
    finally:
        # CLI 完成后 PCL 通常会自行退出，残留进程则在这里清理。
        for pid in pcl_processes(release):
            subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True, check=False)
