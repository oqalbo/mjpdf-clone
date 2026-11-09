from __future__ import annotations

import os
import re
import subprocess
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from .model import RendererLane


@dataclass(frozen=True)
class DeviceInfo:
    serial: str
    model: str
    android: str
    api: int | None
    abi: str
    size: str
    density: str
    memory_mb: int | None
    renderer: RendererLane
    renderer_name: str
    available: bool
    lease: str
    locale: str = "en-US"
    profile_size: str = "1080x2400"
    profile_density: str = "440"
    profile_locale: str = "en-US"

    @property
    def short_renderer(self) -> str:
        return "SW" if self.renderer is RendererLane.SOFTWARE else "HW"

    @property
    def pinned_profile(self) -> str | None:
        if self.api is None or self.locale != self.profile_locale:
            return None
        gpu = "swiftshader" if self.renderer is RendererLane.SOFTWARE else "hardware"
        return (f"api{self.api}-{self.profile_size}-{self.profile_density}dpi-"
                f"3button-{self.profile_locale}-{gpu}")


class DeviceDiscoveryError(RuntimeError):
    pass


def _run(argv: list[str], env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          env=env, check=False)


def _adb(adb: str, serial: str, *args: str) -> str:
    completed = _run([adb, "-s", serial, *args])
    return completed.stdout.strip().replace("\r", "") if completed.returncode == 0 else ""


def _properties(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2)
            for match in re.finditer(r"^\[([^]]+)\]: \[([^]]*)\]$", text, re.MULTILINE)}


def discover_devices(adb: str, lease_script: Path | None, agent: str,
                     selected_serials: Iterable[str] | None = None) -> tuple[DeviceInfo, ...]:
    listed = _run([adb, "devices"])
    if listed.returncode:
        raise DeviceDiscoveryError(f"could not list ADB devices: {listed.stdout.strip()}")
    serials = [line.split()[0] for line in listed.stdout.splitlines()
               if re.match(r"^emulator-\d+\s+device$", line.strip())]
    if selected_serials is not None:
        selected = set(selected_serials)
        serials = [serial for serial in serials if serial in selected]
    devices: list[DeviceInfo] = []
    env = os.environ.copy()
    env["EMU_AGENT"] = agent
    for serial in serials:
        if lease_script is None:
            lease_text = "free"
        else:
            lease_check = _run([str(lease_script), "check", serial], env=env)
            lease_text = lease_check.stdout.strip()
        available = lease_text in {"free", "mine"}
        props = _properties(_adb(adb, serial, "shell", "getprop"))
        surface = _adb(adb, serial, "shell", "dumpsys", "SurfaceFlinger")
        renderer_lines = [line.strip() for line in surface.splitlines()
                          if "GLES:" in line or "GL_RENDERER" in line]
        renderer_name = renderer_lines[0] if renderer_lines else "unreported"
        lane = (RendererLane.SOFTWARE if "swiftshader" in renderer_name.lower()
                else RendererLane.HARDWARE)
        size_output = _adb(adb, serial, "shell", "wm", "size")
        size_override = re.search(r"Override size:\s*(\d+x\d+)", size_output)
        size_physical = re.search(r"Physical size:\s*(\d+x\d+)", size_output)
        size_match = size_override or size_physical
        density_output = _adb(adb, serial, "shell", "wm", "density")
        density_override = re.search(r"Override density:\s*(\d+)", density_output)
        density_physical = re.search(r"Physical density:\s*(\d+)", density_output)
        density_match = density_override or density_physical
        memory_output = _adb(adb, serial, "shell", "cat", "/proc/meminfo")
        memory_match = re.search(r"^MemTotal:\s*(\d+)\s*kB", memory_output, re.MULTILINE)
        api_text = props.get("ro.build.version.sdk", "")
        devices.append(DeviceInfo(
            serial=serial,
            model=props.get("ro.product.model", "Unknown emulator"),
            android=props.get("ro.build.version.release", "-"),
            api=int(api_text) if api_text.isdigit() else None,
            abi=props.get("ro.product.cpu.abi", "-"),
            size=size_match.group(1) if size_match else "-",
            density=density_match.group(1) if density_match else "-",
            memory_mb=(int(memory_match.group(1)) // 1024) if memory_match else None,
            renderer=lane,
            renderer_name=renderer_name,
            available=available,
            lease=lease_text or "unavailable",
            locale=props.get("persist.sys.locale") or props.get("ro.product.locale", "-"),
            profile_size=env.get("PROFILE_SIZE", "1080x2400"),
            profile_density=env.get("PROFILE_DENSITY", "440"),
            profile_locale=env.get("PROFILE_LOCALE", "en-US"),
        ))
    return tuple(devices)


class LeaseManager:
    def __init__(
        self,
        script: Path | None,
        agent: str,
        ttl_seconds: int = 7200,
        task: str = "MJ PDF interactive test run",
    ):
        self.script = script
        self.agent = agent
        self.ttl_seconds = ttl_seconds
        self.task = task
        self.serials: tuple[str, ...] = ()
        self._claimed_here: tuple[str, ...] = ()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def _command(self, *args: str) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["EMU_AGENT"] = self.agent
        return _run([str(self.script), *args], env=env)

    def _ownership(self, serial: str) -> str:
        if self.script is None:
            return "mine" if serial in self.serials else "free"
        completed = self._command("check", serial)
        state = completed.stdout.strip()
        if completed.returncode == 0 and state == "mine":
            return "mine"
        if state == "free":
            return "free"
        raise DeviceDiscoveryError(
            state or f"could not determine lease ownership for {serial}"
        )

    def claim(self, serials: Iterable[str]) -> None:
        wanted = tuple(serials)
        if not wanted:
            raise DeviceDiscoveryError("select at least one emulator")
        if len(set(wanted)) != len(wanted):
            raise DeviceDiscoveryError("selected emulator serials must be unique")
        if self.serials:
            if wanted == self.serials:
                return
            raise DeviceDiscoveryError(
                "lease manager already owns a different emulator selection"
            )
        if self.script is None:
            self.serials = wanted
            return

        claimable = tuple(serial for serial in wanted if self._ownership(serial) == "free")
        if claimable:
            argv = ["claim"]
            for serial in claimable:
                argv.extend(["--serial", serial])
            argv.extend(["--ttl", str(self.ttl_seconds), "--task", self.task])
            completed = self._command(*argv)
            got = tuple(line.strip() for line in completed.stdout.splitlines()
                        if re.fullmatch(r"emulator-\d+", line.strip()))
            if completed.returncode:
                raise DeviceDiscoveryError(completed.stdout.strip() or
                                           "could not claim selected emulators")
            if set(got) != set(claimable):
                for serial in got:
                    self._command("release", serial)
                raise DeviceDiscoveryError(
                    "could not claim all free selected emulators atomically "
                    f"(requested {len(claimable)}, received {len(got)})"
                )

        self.serials = wanted
        self._claimed_here = claimable
        self._stop.clear()
        thread = threading.Thread(
            target=self._renew_loop,
            name="mjpdf-lease-renewer",
            daemon=True,
        )
        try:
            thread.start()
        except RuntimeError:
            for serial in self._claimed_here:
                self._command("release", serial)
            self.serials = ()
            self._claimed_here = ()
            raise
        self._thread = thread

    def _renew_loop(self) -> None:
        while not self._stop.wait(min(300, max(30, self.ttl_seconds // 3))):
            for serial in self.serials:
                self._command("renew", serial)

    def release(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=2)
            self._thread = None
        for serial in self._claimed_here:
            self._command("release", serial)
        self.serials = ()
        self._claimed_here = ()
