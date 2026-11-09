# MJ PDF Reader E2E runner

Runs the Android instrumentation suite under Android Test Orchestrator, pins the
emulator environment, seeds fixtures and checks shared storage for leftovers.

## Requirements

- A Linux host with Python 3.10 or newer, adb and the Android SDK build tools.
- One or more Android emulators. Only `emulator-NNNN` serials are discovered.
- Fixture PDFs generated with `python3 testing/fixtures/generate.py`.

## Usage

```
python3 testing/runner/run.py                          interactive setup for several emulators
python3 testing/runner/run.py --class FQCN[#method]    one class or method on --serial
python3 testing/runner/run.py --phase-scenario RES-54  a multi-process scenario from phases/
python3 testing/runner/run.py --help                   every option and exit status
```

Results are written to `testing/runner/runs/<run id>/`.

Unit tests for the runner itself:

```
cd testing/runner && python3 -m unittest discover -s tests
```

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `ANDROID_SERIAL` | `emulator-5554` | Default for `--serial` |
| `ADB` | `adb` | adb executable |
| `EMU_AGENT` | `mjpdf-cli-<pid>` | Lease name when `--lease-agent` is not given |
| `PROFILE_SIZE`, `PROFILE_DENSITY` | `1080x2400`, `440` | Display pins |
| `PROFILE_LOCALE`, `PROFILE_TIMEZONE` | `en-US`, `UTC` | Locale and time zone pins |
| `LOGCAT_BUFFER` | `64M` | Logcat buffer size during the run |

## Emulator leasing

Leasing lets several runners, or several people, share one pool of emulators
without two runs landing on the same device.

By default the runner leases through `../Scripts/android-emulator/lease-an-emulator.sh`
next to the repository. `--lease-script PATH` points it at another script and
`--lease-agent NAME` sets the name the leases are held under. If the script is
missing, the run stops with exit status 3.

`--no-lease` runs without leasing and prints a warning. Every listed emulator then
counts as free, so use it only when nobody else shares the emulators.

These options, and `--force-stop PACKAGE` for extra packages to force stop before
a run, also work with the interactive TUI: `python3 testing/runner/run.py --no-lease`.

The runner always takes a local lock per serial, so two runners on the same
host never share a device, with or without a lease script.

A lease script is called with `EMU_AGENT` in its environment and must support:

| Command | Output and exit status |
| --- | --- |
| `check <serial>` | `free`, or `mine` with exit 0 when this agent holds it; any other text means leased elsewhere |
| `claim --serial <s> [--serial <s> ...] --ttl <seconds> --task <text>` | each claimed serial on its own line, exit 0 |
| `renew <serial>` | extends a lease this agent holds |
| `release <serial>` | gives a lease back |

Leases are renewed in the background while a run is active and released when
it ends.
