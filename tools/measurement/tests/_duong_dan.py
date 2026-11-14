"""Cho test import được gói do_luong khi chạy: python -m unittest discover tools/measurement/tests"""
import os
import sys

GOC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIXTURES = os.path.join(GOC, "tests", "fixtures")
if GOC not in sys.path:
    sys.path.insert(0, GOC)
