"""Generator registry and shared output layout.

A generator is a function decorated with @fixture_group. It receives the output
root and writes files under it. Groups are named so a single family can be
regenerated without rebuilding everything.
"""

import os

_GROUPS = {}


def fixture_group(name, description="", slow=False):
    def deco(fn):
        _GROUPS[name] = {"fn": fn, "description": description or fn.__doc__ or "",
                         "slow": slow}
        return fn
    return deco


def groups():
    return dict(sorted(_GROUPS.items()))


def out_path(root, *parts):
    p = os.path.join(root, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


# Where each fixture family lands on the device. The runner pushes these trees,
# and the shared storage contract in the test plan requires them to be known.
DEVICE_TARGETS = {
    "": "/sdcard/Download/",
    "scan": "/sdcard/scan/",
    "backups": "/sdcard/Documents/backup-fixtures/",
    "library": "/sdcard/TestBooks/",
    "perf": "/sdcard/Documents/perf-library/",
    "assets": "/sdcard/Download/",
}
