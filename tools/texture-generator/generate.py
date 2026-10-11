#!/usr/bin/env python3
"""Convenience launcher: python generate.py [options]  (same as python -m mycelial_textures)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mycelial_textures.__main__ import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
