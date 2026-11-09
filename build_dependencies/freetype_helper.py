from packaging import version
from packaging.version import InvalidVersion

from build_dependencies.common import fetch_text

URL = 'https://download.savannah.gnu.org/releases/freetype/'
PREFIX = '<tr class="e"><td><a href="freetype-'
POSTFIX = '.tar.gz"'


def get_freetype_latest_version():
    versions = []
    lines = fetch_text(URL, "FreeType release index").splitlines()
    for line in lines:
        if line.startswith(PREFIX) and POSTFIX in line and "doc" not in line:
            end = line.index(POSTFIX)
            try:
                string = line[len(PREFIX):end]
                versions.append(version.parse(string))
            except InvalidVersion:
                pass

    return max(versions)

