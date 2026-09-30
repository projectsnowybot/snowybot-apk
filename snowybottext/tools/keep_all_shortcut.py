#!/usr/bin/env python3
"""Repeat Ctrl+Shift+Enter to the focused window once per second.

X11: uses pyautogui (install with ``python3 -m pip install pyautogui``).
Wayland: pyautogui cannot generally send global input. This script supports
``ydotool`` when its daemon/permissions are configured; see the setup notes in
README/help output. Wayland compositors may block synthetic input regardless.

This sends input to whichever window currently has focus. Stop with Ctrl+C.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time


def detect_session() -> str:
    """Return the desktop session type, preferring the explicit environment."""
    session = os.environ.get("XDG_SESSION_TYPE", "").strip().lower()
    if session in {"x11", "wayland"}:
        return session
    if os.environ.get("WAYLAND_DISPLAY"):
        return "wayland"
    if os.environ.get("DISPLAY"):
        return "x11"
    return "unknown"


def build_sender(session: str):
    if session == "x11":
        try:
            import pyautogui
        except ImportError as exc:
            raise RuntimeError(
                "X11 backend requires pyautogui. Install it with:\n"
                "  python3 -m pip install pyautogui"
            ) from exc
        return lambda: pyautogui.hotkey("ctrl", "shift", "enter"), "pyautogui (X11)"

    if session == "wayland":
        executable = shutil.which("ydotool")
        if executable is None:
            raise RuntimeError(
                "Wayland detected, but ydotool is not installed. Global synthetic "
                "input is normally blocked unless an authorized compositor, portal, "
                "or input-injection tool is configured. Install and configure ydotool "
                "as described by your distribution, then ensure its daemon is running "
                "and this user has access to /dev/uinput. This is not unrestricted "
                "Wayland support."
            )
        # Linux input key codes: left Ctrl=29, left Shift=42, Enter=28.
        # Hold modifiers, tap Enter, then release modifiers in reverse order.
        command = [executable, "key", "29:1", "42:1", "28:1", "28:0", "42:0", "29:0"]

        def send_ydotool() -> None:
            subprocess.run(command, check=True)

        return send_ydotool, "ydotool (Wayland; compositor/system setup required)"

    raise RuntimeError(
        "Could not determine the graphical session (XDG_SESSION_TYPE, "
        "WAYLAND_DISPLAY, and DISPLAY are unset). Run this from an X11 or "
        "Wayland graphical session."
    )


def main() -> int:
    session = detect_session()
    try:
        send_shortcut, backend = build_sender(session)
    except RuntimeError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    print(f"Session: {session}; backend: {backend}")
    print("WARNING: Ctrl+Shift+Enter is sent to whichever window has focus.")
    print("Focus the intended window now. Press Ctrl+C to stop.")
    for remaining in range(5, 0, -1):
        print(f"Starting in {remaining} second(s)...", flush=True)
        time.sleep(1)

    print("Sending Ctrl+Shift+Enter once per second. Press Ctrl+C to stop.")
    try:
        while True:
            send_shortcut()
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nStopped.")
    except (OSError, subprocess.CalledProcessError) as exc:
        print(f"Input backend failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
