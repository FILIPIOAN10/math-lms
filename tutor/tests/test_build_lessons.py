"""scripts/build_lessons.py: a chapter whose revision or second check fails is reported and never stored - its
review.json would describe another draft (health audit 2026-11-25 H1)."""

import importlib.util
import json
import pathlib
import sys

import pytest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "scripts" / "build_lessons.py"


@pytest.fixture
def build(tmp_path, monkeypatch):
    spec = importlib.util.spec_from_file_location("build_lessons_under_test", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    monkeypatch.setattr(module, "DATA", tmp_path)
    monkeypatch.setattr(module, "counts", lambda code: {"green": 3, "yellow": 1, "red": 1})
    return module


def test_a_failed_revision_is_reported_and_nothing_is_stored(build, monkeypatch):
    calls: list[str] = []

    def step(log, *args):
        calls.append(" ".join(args))
        return "--revise" not in args  # the model call of the revision fails

    monkeypatch.setattr(build, "step", step)
    monkeypatch.setattr(sys, "argv", ["build_lessons.py", "--chapters", "A11"])
    build.main()
    entry = json.loads((build.DATA / "summary.json").read_text(encoding="utf-8"))["A11"]

    assert entry["ok"] is False and entry["failed"] == "revise"
    assert not any(c.startswith("tutor.store_lesson") for c in calls)


def test_a_revised_draft_whose_check_fails_is_reported_and_nothing_is_stored(build, monkeypatch):
    calls: list[str] = []

    def step(log, *args):
        calls.append(" ".join(args))
        # the first check passes; the check of the revised draft fails (its review.json is the old draft's)
        return not (args[0] == "tutor.verify_lesson" and sum(c.startswith("tutor.verify_lesson") for c in calls) == 2)

    monkeypatch.setattr(build, "step", step)
    monkeypatch.setattr(sys, "argv", ["build_lessons.py", "--chapters", "A11"])
    build.main()
    entry = json.loads((build.DATA / "summary.json").read_text(encoding="utf-8"))["A11"]

    assert entry["ok"] is False and entry["failed"] == "verify"
    assert not any(c.startswith("tutor.store_lesson") for c in calls)


def test_a_chapter_built_without_failures_is_stored(build, monkeypatch):
    calls: list[str] = []

    def step(log, *args):
        calls.append(" ".join(args))
        return True

    monkeypatch.setattr(build, "step", step)
    monkeypatch.setattr(sys, "argv", ["build_lessons.py", "--chapters", "A11", "--rounds", "1"])
    build.main()
    entry = json.loads((build.DATA / "summary.json").read_text(encoding="utf-8"))["A11"]

    assert entry["ok"] is True and entry["revisions"] == 1 and entry["stored"] is True
    assert calls[-1] == "tutor.store_lesson --chapter A11"
