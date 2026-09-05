"""Auto Torch 启动工具命令入口；具体实现位于 tools/scripts。"""
from __future__ import annotations
import argparse, importlib.util, re, sys
from pathlib import Path

DEFAULT_RELEASE_ROOT = Path(r"D:\elric\Code\Repos\Minecraft\test-mc")
LOADERS = ("forge", "fabric", "neoforge")
VERSION_RE = re.compile(r"^\d+(?:\.\d+){1,3}$")
VERSION_SELECTOR_RE = re.compile(r"^(\d+(?:\.\d+){1,3})(?:-(\d+(?:\.\d+){1,3}))?$")

_logic_path = Path(__file__).parent / "scripts" / "mc_launcher_logic.py"
_spec = importlib.util.spec_from_file_location("auto_torch_launcher_logic", _logic_path)
if _spec is None or _spec.loader is None:
    raise RuntimeError(f"无法加载脚本逻辑：{_logic_path}")
logic = importlib.util.module_from_spec(_spec)
sys.modules[_spec.name] = logic
_spec.loader.exec_module(logic)

def parse_args(argv):
    p = argparse.ArgumentParser(add_help=False, usage="1.一键启动mc脚本.py [目标] [动作] [选项]")
    p.add_argument("--all", dest="task", action="store_const", const="all")
    p.add_argument("--all_branch", dest="task", action="store_const", const="all_branch")
    for loader in LOADERS:
        p.add_argument(f"--{loader}", dest="task", action="store_const", const=loader)
    p.add_argument("--build", action="store_true")
    p.add_argument("--debug", dest="mode", action="store_const", const="debug")
    p.add_argument("--release", dest="mode", action="store_const", const="release")
    p.add_argument("--path", dest="release_path")
    p.add_argument("--java-path", dest="java_path")
    p.add_argument("--mc_version")
    p.add_argument("--branch")
    p.add_argument("--help", action="store_true")
    args, unknown = p.parse_known_args(argv)
    if unknown:
        p.error(f"不支持的参数 '{unknown[0]}'。请使用 --help 查看用法。")
    args.task = args.task or "all"
    args.mode = args.mode or "debug"
    if args.mc_version:
        m = VERSION_SELECTOR_RE.fullmatch(args.mc_version)
        if not m: p.error(f"Minecraft 版本号无效：'{args.mc_version}'。示例：1.7.10 或 1.16.5-1.8.9。")
    if args.branch and (not args.branch.strip() or args.branch.startswith("-")):
        p.error(f"Git 分支名无效：'{args.branch}'。")
    return args

def show_help():
    print("""用法：
  1.一键启动mc脚本.py [目标] [动作] [选项]

目标：--all（默认）、--forge、--fabric、--neoforge、--all_branch
动作：--debug（默认）、--build、--release
选项：--path <路径>、--java-path <路径>、--mc_version <版本号或范围>、--branch <分支名>、--help
版本范围使用闭区间，例如：--mc_version 1.16.5-1.8.9。
release 默认使用 D:\\elric\\Code\\Repos\\Minecraft\\test-mc\\PCL2_Release.exe。
""")

def main(argv=None):
    raw = list(sys.argv[1:] if argv is None else argv)
    args = parse_args(raw)
    if not raw or args.help:
        show_help(); return 0
    if args.mc_version and args.branch:
        raise RuntimeError("--mc_version 不能与 --branch 同时使用。")
    if args.mc_version and args.task == "all_branch":
        raise RuntimeError("--mc_version 不能与 --all_branch 同时使用。")
    root = logic.PROJECT_ROOT
    roots = None
    if args.branch:
        if args.task == "all_branch": raise RuntimeError("--branch 不能与 --all_branch 同时使用。")
        if logic.run(["git", "-C", str(root), "show-ref", "--verify", "--quiet", f"refs/heads/{args.branch}"]).returncode:
            raise RuntimeError(f"未找到 Git 分支：{args.branch}。")
        if args.branch == "main": root = logic.PROJECT_ROOT
        elif re.fullmatch(r"mc/\d+(?:\.\d+){1,3}", args.branch): root = logic.worktree(args.branch, args.branch[3:])
        else: raise RuntimeError("暂不支持此分支的工作树路径：请使用 main 或 mc/<版本号>。")
    elif args.mc_version:
        m = VERSION_SELECTOR_RE.fullmatch(args.mc_version)
        start, end = m.group(1), m.group(2) or m.group(1)
        lo, hi = sorted((logic.version_key(start), logic.version_key(end)))
        selected = [x for x in logic.branches() if lo <= logic.version_key(x[1]) <= hi]
        if not selected: raise RuntimeError(f"未找到 Minecraft 版本分支：mc/{args.mc_version}。")
        roots = [logic.worktree(*x) for x in selected]
        root = roots[0]
    logic.java_env(args.java_path)
    if args.mode == "release":
        release = Path(args.release_path).expanduser().resolve() if args.release_path else DEFAULT_RELEASE_ROOT
        if not (release / "PCL2_Release.exe").is_file(): raise RuntimeError(f"生产测试端路径无效：{release}。未找到 PCL2_Release.exe。")
        logic.skip_profile_auth(release)
        build_roots = roots or [root]
        files = [f for r in build_roots for f in logic.artifacts(r, args.task)]
        selected = logic.targets(files, release, args.task if args.task in LOADERS else None)
        if not selected: raise RuntimeError("没有可部署的 release 产物。")
        for target in selected: logic.install(target)
        for target in selected: logic.start_client(target, release)
        return 0
    if args.build:
        if roots and len(roots) > 1: raise RuntimeError("--build 暂不支持版本范围，请指定单个 --mc_version。")
        if args.task == "all_branch":
            for branch, version in logic.branches():
                if logic.gradle(logic.worktree(branch, version), "clean", "build"): raise RuntimeError(f"[{version}] 构建失败。")
            return 0
        return logic.gradle(root, "clean", "build" if args.task == "all" else f":{args.task}:build")
    if args.task == "all":
        for launch_root in roots or [root]:
            for loader in LOADERS: logic.loader_client(launch_root, loader, logic.prop(launch_root, "minecraft_version"))
    elif args.task == "all_branch":
        for branch, version in logic.branches():
            for loader in LOADERS: logic.loader_client(logic.worktree(branch, version), loader, version)
    else:
        for launch_root in roots or [root]:
            logic.language(launch_root, args.task)
            code = logic.gradle(launch_root, f":{args.task}:runClient")
            if code: print(f"警告：[{logic.prop(launch_root, 'minecraft_version')}] {args.task} 已退出，退出码：{code}。", file=sys.stderr)
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n已取消启动。", file=sys.stderr)
        raise SystemExit(130)
    except (RuntimeError, OSError) as exc: print(f"错误：{exc}", file=sys.stderr); raise SystemExit(1)
