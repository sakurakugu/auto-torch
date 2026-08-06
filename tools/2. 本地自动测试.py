#!/usr/bin/env python3
"""Auto Torch 本地自动测试入口。"""

from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPOSITORY_ROOT))

from tests.local_test import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
