from __future__ import annotations

import json
import re
import shlex
from pathlib import Path
from typing import Callable

from .model import CommandSpec, PinRecord, RunnerConfig
from .process import CommandRunner

NAVBAR_THREEBUTTON = "com.android.internal.systemui.navbar.threebutton"
NAVBAR_GESTURAL = "com.android.internal.systemui.navbar.gestural"
CUTOUT_OVERLAYS = {
    "com.android.internal.display.cutout.emulation.corner",
    "com.android.internal.display.cutout.emulation.double",
    "com.android.internal.display.cutout.emulation.hole",
    "com.android.internal.display.cutout.emulation.tall",
    "com.android.internal.display.cutout.emulation.emu01",
    "com.android.internal.display.cutout.emulation.waterfall",
}
FOREGROUND_THIEVES = (
    "com.android.vending",
    "com.google.android.gms",
    "com.google.android.googlequicksearchbox",
)


def force_stop_packages(extra: tuple[str, ...] = ()) -> tuple[str, ...]:
    return tuple(dict.fromkeys((*FOREGROUND_THIEVES, *extra)))


class PinError(RuntimeError):
    pass


class PinManager:
    def __init__(self, config: RunnerConfig, commands: CommandRunner, paths_root: Path,
                 warn: Callable[[str], None], notice: Callable[[str], None]):
        self.config = config
        self.commands = commands
        self.root = paths_root
        self.warn = warn
        self.notice = notice
        self.records: list[PinRecord] = []
        self.api: int | None = None
        self.gpu_kind = "unknown"
        self.profile_name = "unknown"
        (self.root / "pins.log").touch()

    def _adb(self, *args: str) -> tuple[str, ...]:
        return (self.config.adb, "-s", self.config.serial, *args)

    def _run(self, name: str, *args: str, mutate: bool = True, check: bool = True) -> str:
        return self.commands.run(CommandSpec(self._adb(*args), f"pin-{name}", mutate=mutate),
                                 check=check).output.strip().replace("\r", "")

    def query(self, name: str, *args: str) -> str:
        return self._run(name, *args, mutate=False, check=False).strip()

    def _write_log(self) -> None:
        separator = "\x1f"
        with (self.root / "pins.log").open("w", encoding="utf-8") as fh:
            for record in self.records:
                restore = record.restore_shell or " ".join(record.restore_argv)
                fh.write(separator.join((record.name, record.prior, record.applied, restore)) + "\n")

    def _record(self, record: PinRecord) -> None:
        self.records.append(record)
        self._write_log()

    def _verify(self, name: str, got: str, want: str) -> None:
        if self.config.dry_run:
            return
        if got.strip() != want:
            raise PinError(f"pin {name} could not be applied: wanted {want!r}, device reports {got.strip()!r}")

    def _setting(self, namespace: str, key: str, want: str) -> None:
        prior = self.query(f"get-{key}", "shell", "settings", "get", namespace, key)
        restore = ("settings", "delete", namespace, key) if prior in ("", "null") else (
            "settings", "put", namespace, key, prior)
        self._record(PinRecord(f"{namespace}.{key}", prior, want, restore_argv=restore))
        self._run(f"set-{key}", "shell", "settings", "put", namespace, key, want)
        got = self.query(f"verify-{key}", "shell", "settings", "get", namespace, key)
        self._verify(f"{namespace}.{key}", got, want)

    def _api_pin(self) -> None:
        raw = self.query("api", "shell", "getprop", "ro.build.version.sdk")
        self._record(PinRecord("api_level", raw or "(unreadable)", raw or ">=33"))
        if self.config.dry_run:
            self.api = 34
            return
        try:
            self.api = int(raw)
        except ValueError as exc:
            raise PinError("could not read ro.build.version.sdk") from exc
        if self.api < 33:
            self.warn(f"API {self.api} is below 33; app-locale pin is unavailable")
        if self.api < 34:
            self.warn(f"API {self.api} is below the 34 the golden profile assumes")

    def _gpu_pin(self) -> None:
        output = self.query("gpu", "shell", "dumpsys", "SurfaceFlinger")
        match = re.search(r"^GLES: (.+)$", output, re.MULTILINE)
        renderer = match.group(1).strip() if match else ""
        if self.config.dry_run:
            self.gpu_kind = ("swiftshader" if self.config.renderer_lane.value == "software"
                             else "renderer-agnostic")
        else:
            self.gpu_kind = "swiftshader" if "swiftshader" in renderer.lower() else "hardware"
        self._record(PinRecord("gpu_renderer", renderer or "(unreadable)", renderer or "renderer-independent"))
        if not self.config.dry_run and self.config.renderer_lane.value == "software" and self.gpu_kind != "swiftshader":
            raise PinError(f"software renderer lane requires swiftshader, device reports {renderer!r}")

    def _navbar_pin(self) -> None:
        listing = self.query("navbar-list", "shell", "cmd", "overlay", "list", "android")
        if not self.config.dry_run and "internal.systemui.navbar" not in listing:
            self._record(PinRecord("navigation_mode", "(no navbar overlays before api 29)", NAVBAR_THREEBUTTON))
            return
        enabled = next((line.split()[-1] for line in listing.splitlines()
                        if line.startswith("[x]") and "navbar" in line), "")
        restore = ("cmd", "overlay", "enable-exclusive", "--category", enabled or NAVBAR_GESTURAL)
        self._record(PinRecord("navigation_mode", enabled, NAVBAR_THREEBUTTON, restore_argv=restore))
        self._run("navbar", "shell", "cmd", "overlay", "enable-exclusive", "--category", NAVBAR_THREEBUTTON)
        verify = self.query("navbar-verify", "shell", "cmd", "overlay", "list", "android")
        current = next((line.split()[-1] for line in verify.splitlines()
                        if line.startswith("[x]") and "navbar" in line), NAVBAR_THREEBUTTON if self.config.dry_run else "")
        self._verify("navigation_mode", current, NAVBAR_THREEBUTTON)

    @staticmethod
    def _wm_value(output: str, kind: str) -> tuple[str, str]:
        override = re.search(rf"^Override {kind}: (.+)$", output, re.MULTILINE)
        physical = re.search(rf"^Physical {kind}: (.+)$", output, re.MULTILINE)
        return ((override.group(1).strip() if override else ""),
                (physical.group(1).strip() if physical else ""))

    def _display_pin(self) -> None:
        for kind, want in (("size", self.config.profile_size), ("density", self.config.profile_density)):
            output = self.query(f"wm-{kind}", "shell", "wm", kind)
            override, _ = self._wm_value(output, kind)
            restore = ("wm", kind, override) if override else ("wm", kind, "reset")
            self._record(PinRecord(f"display_{kind}", override or "physical", want, restore_argv=restore))
            self._run(f"wm-set-{kind}", "shell", "wm", kind, want)
            verify = self.query(f"wm-verify-{kind}", "shell", "wm", kind)
            got_override, got_physical = self._wm_value(verify, kind)
            self._verify(f"display_{kind}", got_override or got_physical or (want if self.config.dry_run else ""), want)

    def _night_pin(self) -> None:
        output = self.query("night", "shell", "cmd", "uimode", "night")
        match = re.search(r"Night mode: (\S+)", output)
        prior = match.group(1) if match else "no"
        self._record(PinRecord("night_mode", prior, "no", restore_argv=("cmd", "uimode", "night", prior)))
        self._run("night-set", "shell", "cmd", "uimode", "night", "no")
        verify = self.query("night-verify", "shell", "cmd", "uimode", "night")
        got = re.search(r"Night mode: (\S+)", verify)
        self._verify("night_mode", got.group(1) if got else ("no" if self.config.dry_run else ""), "no")

    def _cutout_pin(self) -> None:
        listing = self.query("cutout-list", "shell", "cmd", "overlay", "list", "android")
        present = {line.split()[-1] for line in listing.splitlines() if "display.cutout.emulation" in line}
        enabled = [line.split()[-1] for line in listing.splitlines()
                   if line.startswith("[x]") and "display.cutout.emulation" in line]
        restore_shell = "; ".join(f"cmd overlay enable {name}" for name in enabled)
        self._record(PinRecord("display_cutout", " ".join(enabled) or "none", "none",
                               restore_shell=restore_shell or None))
        targets = CUTOUT_OVERLAYS if self.config.dry_run else CUTOUT_OVERLAYS & present
        for overlay in sorted(targets):
            self._run(f"cutout-{overlay.rsplit('.', 1)[-1]}", "shell", "cmd", "overlay", "disable", overlay)
        if not self.config.dry_run:
            verify = self.query("cutout-verify", "shell", "cmd", "overlay", "list", "android")
            still = [line for line in verify.splitlines()
                     if line.startswith("[x]") and "display.cutout.emulation" in line]
            if still:
                raise PinError(f"display cutout emulation is still on: {' '.join(still)}")

    def _locale_pin(self) -> None:
        locale = self.query("locale", "shell", "getprop", "persist.sys.locale") or self.query(
            "product-locale", "shell", "getprop", "ro.product.locale")
        self._record(PinRecord("device_locale", locale, locale))
        if not self.config.dry_run and locale != self.config.profile_locale:
            raise PinError(f"device locale is {locale!r}, profile wants {self.config.profile_locale!r}")
        if self.api is not None and self.api < 33:
            self._record(PinRecord("app_locale", "(unavailable before api 33)", "(skipped)"))
        else:
            prior = self.query("app-locale", "shell", "cmd", "locale", "get-app-locales", self.config.package)
            self._record(PinRecord("app_locale", prior, "(cleared)"))
            self._run("app-locale-clear", "shell", "cmd", "locale", "set-app-locales",
                      self.config.package, "--locales", "")

    def _timezone_pin(self) -> None:
        prior = self.query("timezone", "shell", "getprop", "persist.sys.timezone")
        target = PinRecord("timezone", prior, self.config.profile_timezone,
                           restore_argv=("cmd", "alarm", "set-timezone", prior or "UTC"))
        self._record(target)
        self._run("timezone-set", "shell", "cmd", "alarm", "set-timezone", self.config.profile_timezone)
        now = self.query("timezone-verify", "shell", "getprop", "persist.sys.timezone")
        if not self.config.dry_run and now != self.config.profile_timezone:
            self.warn(f"device refused timezone pin and stayed on {now!r}")
            self.records[-1] = PinRecord(
                "timezone", prior, f"{now} (refused {self.config.profile_timezone})")
            self._write_log()

    def _misc_pins(self) -> None:
        self._setting("global", "hide_error_dialogs", "1")
        self._setting("system", "font_scale", "1.0")
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self._setting("global", key, "0")
        self._setting("system", "accelerometer_rotation", "0")
        self._setting("system", "user_rotation", "0")
        self._setting("global", "always_finish_activities", "0")
        self._setting("secure", "immersive_mode_confirmations", "confirmed")
        prior_logcat = self.query("logcat-size", "shell", "logcat", "-g", "main").splitlines()
        self._record(PinRecord("logcat_buffer", prior_logcat[0] if prior_logcat else "", self.config.logcat_buffer))
        self._run("logcat-size-set", "shell", "logcat", "-G", self.config.logcat_buffer)
        prior_op = self.query("appop", "shell", "appops", "get", self.config.package,
                              "MANAGE_EXTERNAL_STORAGE").splitlines()
        appop = prior_op[0] if prior_op else ""
        if appop.startswith("Error") or "Unknown operation" in appop:
            appop = "(not an appop before api 30)"
        self._record(PinRecord("appops_manage_external_storage", appop, appop))
        stopped: list[str] = []
        for package in force_stop_packages(self.config.force_stop_packages):
            installed = self.query(f"thief-{package}", "shell", "pm", "list", "packages", package)
            if package in installed:
                self._run(f"stop-{package}", "shell", "am", "force-stop", package)
                stopped.append(package)
        self._record(PinRecord("foreground_thieves", " ".join(stopped) or "none", "stopped"))

    def apply(self) -> str:
        self._api_pin()
        self._gpu_pin()
        self._navbar_pin()
        self._display_pin()
        self._misc_pins()
        self._night_pin()
        self._cutout_pin()
        self._locale_pin()
        self._timezone_pin()
        api = self.api if self.api is not None else "<device>"
        self.profile_name = (f"api{api}-{self.config.profile_size}-{self.config.profile_density}dpi-"
                             f"3button-{self.config.profile_locale}-{self.gpu_kind}")
        manifest = {"profile": self.profile_name, "timeZone": self.config.profile_timezone,
                    "pins": [{"name": r.name, "prior": r.prior, "applied": r.applied,
                              "restore": (shlex.join(r.restore_argv) if r.restore_argv
                                          else r.restore_shell or ""),
                              "written": bool(r.restore_argv or r.restore_shell)
                                         or r.prior != r.applied}
                             for r in self.records]}
        (self.root / "pins.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        return self.profile_name

    def restore(self) -> None:
        if self.config.keep_pins:
            self.notice("keeping pins in place as requested")
            return
        for record in reversed(self.records):
            if not record.restore_argv and not record.restore_shell:
                continue
            args = ("shell", record.restore_shell) if record.restore_shell else ("shell", *record.restore_argv)
            try:
                completed = self.commands.run(
                    CommandSpec(self._adb(*args), f"pin-restore-{record.name}"), check=False)
                if completed.returncode:
                    self.warn(f"could not restore {record.name}")
            except Exception:
                self.warn(f"could not restore {record.name}")
