"""生产实例选择与后台构建流水线的回归测试。"""
import importlib.util
from pathlib import Path
import tempfile
import threading
import sys
import unittest
from unittest.mock import patch


LOGIC_PATH = Path(__file__).resolve().parents[1] / "scripts" / "mc_launcher_logic.py"
spec = importlib.util.spec_from_file_location("mc_launcher_logic", LOGIC_PATH)
logic = importlib.util.module_from_spec(spec)
spec.loader.exec_module(logic)


class InstanceTests(unittest.TestCase):
    def test_numeric_version_and_exact_minecraft_loader(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            versions = release / ".minecraft" / "versions"
            names = ["26.3-NeoForge_26.3.0.6-beta", "26.3-NeoForge_26.3.0.66-beta",
                     "26.30-NeoForge_26.30.0.100", "26.3-Forge_66.0.9"]
            for name in names:
                (versions / name).mkdir(parents=True)
            artifact = Path("autotorch-v1.9.0-mc26.3-neoforge.jar")
            selected = logic.targets([artifact], release, None)
            self.assertEqual(selected[0][3].name, names[1])
            self.assertEqual(logic.targets([artifact], release, "forge"), [])

    def test_stable_release_wins_over_same_version_beta(self):
        paths = [Path("26.3-NeoForge_26.3.0.66-beta"), Path("26.3-NeoForge_26.3.0.66")]
        self.assertEqual(logic.newest_instance(paths, "26.3", "neoforge"), paths[1])

    def test_unknown_or_duplicate_versions_remain_ambiguous(self):
        for names in [("26.3-NeoForge_custom", "26.3-NeoForge_26.3.0.66-beta"),
                      ("26.3-NeoForge_26.3.0.66-beta", "26.3-NeoForge 26.3.0.66-beta")]:
            with self.subTest(names=names), self.assertRaisesRegex(RuntimeError, "无法确定最新版本"):
                logic.newest_instance(list(map(Path, names)), "26.3", "neoforge")

    def test_missing_instance_reports_error(self):
        with tempfile.TemporaryDirectory() as directory:
            release = Path(directory)
            (release / ".minecraft" / "versions").mkdir(parents=True)
            with self.assertRaisesRegex(RuntimeError, "未安装 26.3 neoforge"):
                logic.targets([Path("autotorch-v1.9.0-mc26.3-neoforge.jar")], release, None)


class PipelineTests(unittest.TestCase):
    def test_launch_first_version_while_remaining_version_builds(self):
        first_launched = threading.Event()
        second_built = threading.Event()
        launches = []
        main_thread = threading.get_ident()

        def batches(root, task, cancel_event):
            self.assertNotEqual(threading.get_ident(), main_thread)
            if root == "second":
                if not first_launched.wait(5):
                    raise RuntimeError("首个版本未在剩余构建完成前启动")
                second_built.set()
            yield [root]

        def launch(target, release):
            launches.append(target)
            if target == "first":
                first_launched.set()
                self.assertTrue(second_built.wait(5), "游戏运行期间没有继续构建")

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install") as install, \
             patch.object(logic, "start_client", side_effect=launch):
            logic.release_clients(["first", "second"], "all", Path("release"))
        self.assertEqual(launches, ["first", "second"])
        self.assertEqual([call.args[0] for call in install.call_args_list], launches)

    def test_launch_failure_cancels_background_work(self):
        stopped = threading.Event()

        def batches(root, task, cancel_event):
            yield [root]
            if cancel_event.wait(5): stopped.set()

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install"), \
             patch.object(logic, "start_client", side_effect=RuntimeError("启动失败")):
            with self.assertRaisesRegex(RuntimeError, "启动失败"):
                logic.release_clients(["first"], "all", Path("release"))
        self.assertTrue(stopped.is_set())

    def test_background_build_failure_is_reported(self):
        with patch.object(logic, "artifact_batches", side_effect=RuntimeError("打包失败")):
            with self.assertRaisesRegex(RuntimeError, "打包失败"):
                logic.release_clients(["first"], "all", Path("release"))

    def test_keyboard_interrupt_cancels_background_work(self):
        stopped = threading.Event()

        def batches(root, task, cancel_event):
            yield [root]
            if cancel_event.wait(5): stopped.set()

        with patch.object(logic, "artifact_batches", side_effect=batches), \
             patch.object(logic, "targets", side_effect=lambda files, *args: files), \
             patch.object(logic, "install"), \
             patch.object(logic, "start_client", side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                logic.release_clients(["first"], "all", Path("release"))
        self.assertTrue(stopped.is_set())

    def test_all_branch_delivers_each_version_and_excludes_netease(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("mod_version=1.9.0\n", encoding="utf-8")
            built = []

            def release_build(build_root, version, cancel_event):
                built.append(version)
                artifact = root / f"autotorch-v1.9.0-mc{version}-forge.jar"
                artifact.write_bytes(b"jar")
                return [artifact]

            refs = "mc/26.3\nmc/26.2\nmc/netease\n"
            with patch.object(logic, "run") as run, \
                 patch.object(logic, "worktree", return_value=root), \
                 patch.object(logic, "release_build", side_effect=release_build):
                run.return_value.returncode = 0
                run.return_value.stdout = refs
                batches = logic.artifact_batches(root, "all_branch")
                first = next(batches)
                self.assertEqual(built, ["26.3"])
                self.assertTrue(first[0].is_file())
                self.assertEqual(first[0].parent, root / "build" / "v1.9.0")
                next(batches)
                self.assertEqual(built, ["26.3", "26.2"])
                with self.assertRaises(StopIteration): next(batches)


class ProcessTests(unittest.TestCase):
    def test_cancellation_stops_real_background_process(self):
        cancel_event = threading.Event()
        spawned = []
        real_popen = logic.subprocess.Popen
        timer = None

        def popen(*args, **kwargs):
            nonlocal timer
            process = real_popen(*args, **kwargs)
            spawned.append(process)
            if len(spawned) == 1:
                timer = threading.Timer(0.2, cancel_event.set)
                timer.start()
            return process

        try:
            with patch.object(logic.subprocess, "Popen", side_effect=popen):
                with self.assertRaisesRegex(RuntimeError, "已取消后台构建"):
                    logic.run([sys.executable, "-c", "import time; time.sleep(30)"],
                              capture=True, cancel_event=cancel_event)
            self.assertIsNotNone(spawned[0].poll(), "取消后后台子进程仍在运行")
        finally:
            if timer is not None: timer.cancel()
            for process in spawned:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
        self.assertTrue(cancel_event.is_set())
        self.assertIsNotNone(spawned[0].returncode)


if __name__ == "__main__":
    unittest.main()
