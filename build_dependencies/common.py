import hashlib
import os
import shutil
import subprocess
import sys
import tarfile
import time
from urllib.parse import unquote, urlsplit

from build_dependencies.values import LIB_DIR_PATH, ARCH_NAMES, DEFAULT_TOOLCHAIN, ANDROID_TOOLCHAIN_FILENAME, \
    get_toolchain_path, FILE_NAMES, Lib

alternative_cpp_path = ""

DOWNLOAD_RETRY_DELAYS = (1, 4, 9)
DOWNLOAD_TIMEOUT = (15, 60)
DOWNLOAD_CHUNK_SIZE = 64 * 1024
DOWNLOAD_USER_AGENT = "MJ-PDF-build"
RETRYABLE_HTTP_STATUS_CODES = {408, 425, 429, 500, 502, 503, 504}


class _DownloadError(Exception):
    pass


# ------------------------------------------------------------
def run_cmd(cmd):
    command = " ".join(cmd)
    log("Run: " + command)
    result = subprocess.call(command, shell=True)
    if result != 0:
        error(command + "  result=" + str(result))


def error(msg):
    log("Error !!! " + msg)
    os.sys.exit(1)


def log(msg):
    print("* " + msg)


def delete_if_exists(path):
    if os.path.exists(path):
        shutil.rmtree(path)


def delete_file_if_exists(path):
    if os.path.exists(path):
        os.remove(path)


def _download_sources(urls):
    if isinstance(urls, str):
        sources = (urls,)
    else:
        sources = tuple(dict.fromkeys(urls))

    if not sources or any(not isinstance(url, str) or not url for url in sources):
        error("At least one non-empty download URL is required.")
    return sources


def _fetch_from_sources(urls, description, consume):
    import requests

    sources = _download_sources(urls)
    failures = {}
    disabled_sources = set()
    attempts = len(DOWNLOAD_RETRY_DELAYS) + 1

    for attempt in range(attempts):
        for url in sources:
            if url in disabled_sources:
                continue

            log(f"Fetching {description} from {url} (attempt {attempt + 1}/{attempts}).")
            try:
                with requests.get(
                        url,
                        stream=True,
                        timeout=DOWNLOAD_TIMEOUT,
                        headers={"User-Agent": DOWNLOAD_USER_AGENT},
                ) as request:
                    request.raise_for_status()
                    return consume(request)
            except (requests.RequestException, OSError, _DownloadError) as exception:
                failures[url] = str(exception)
                log(f"Fetch failed: {exception}")

                if isinstance(exception, requests.HTTPError) and exception.response is not None:
                    status = exception.response.status_code
                    if 400 <= status < 500 and status not in RETRYABLE_HTTP_STATUS_CODES:
                        disabled_sources.add(url)

        if attempt == attempts - 1 or len(disabled_sources) == len(sources):
            break

        delay = DOWNLOAD_RETRY_DELAYS[attempt]
        log(f"All download sources failed; retrying in {delay} seconds.")
        time.sleep(delay)

    details = "\n".join(f"      {url}: {failures.get(url, 'not attempted')}" for url in sources)
    error(f"Could not fetch {description} from any configured source:\n{details}")


def fetch_text(urls, description):
    return _fetch_from_sources(urls, description, lambda request: request.text)


def download_file(urls, filename=None, show_done_message=False, sha256=None):
    sources = _download_sources(urls)
    if not filename:
        filename = os.path.basename(unquote(urlsplit(sources[0]).path))
        if not filename:
            error("Could not determine a filename from the download URL.")

    filename = os.fspath(filename)
    partial_filename = filename + ".part"
    delete_file_if_exists(partial_filename)

    def save(request):
        digest = hashlib.sha256() if sha256 else None
        try:
            with open(partial_filename, "wb") as file:
                for chunk in request.iter_content(chunk_size=DOWNLOAD_CHUNK_SIZE):
                    if not chunk:
                        continue
                    file.write(chunk)
                    if digest:
                        digest.update(chunk)

            if digest:
                actual = digest.hexdigest()
                if actual.lower() != sha256.lower():
                    raise _DownloadError(
                        f"SHA256 mismatch for {filename}\n"
                        f"      expected: {sha256}\n"
                        f"      actual:   {actual}"
                    )

            os.replace(partial_filename, filename)
        except BaseException:
            delete_file_if_exists(partial_filename)
            raise

        if show_done_message:
            log(f"Finished downloading {filename}")
        return filename

    return _fetch_from_sources(sources, filename, save)


def extract_tar_file(filename, path=".", show_done_message=False):
    log(f"Extracting {filename}")
    with tarfile.open(filename) as file:
        file.extractall(path)

    if show_done_message:
        log(f"Finished extracting {filename}")


def move_file(filename, target):
    os.makedirs(target, exist_ok=True)
    shutil.move(filename, f"{target}/{filename}")


def get_lib_path(arch, lib_filename, levels_up=2):
    return f"{'../' * levels_up}{LIB_DIR_PATH}/{ARCH_NAMES[arch]}/{lib_filename}"


def get_toolchain():
    path = os.path.join(DEFAULT_TOOLCHAIN, ANDROID_TOOLCHAIN_FILENAME)
    if os.path.exists(path):
        return path

    log(f"Searching for {ANDROID_TOOLCHAIN_FILENAME}")
    toolchain_path = get_toolchain_path(find_ndk_path())
    return os.path.join(toolchain_path, ANDROID_TOOLCHAIN_FILENAME)


def find_ndk_path():
    try:
        return os.environ["ANDROID_NDK"]
    except KeyError:
        error(f"ANDROID_NDK env variable is empty. Thus, can't locate {ANDROID_TOOLCHAIN_FILENAME}\n"
              f'Hint: Try running: find / -name "*android.toolchain.cmake*" 2>/dev/null\n'
              f'      Then run ANDROID_NDK=SOME_PATH/android-ndk-SOMETHING/android-ndk-SOME_VERSION python build_dependencies.py')
        exit(1)


def get_shared_cpp_libs_path():
    global alternative_cpp_path

    path = os.path.join(find_ndk_path(), "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib")
    if os.path.exists(path):
        return path

    if alternative_cpp_path != "":
        return alternative_cpp_path

    log(f"Couldn't find {FILE_NAMES[Lib.cpp_shared]} libs at {path}")
    log("Hint: you can try yo find the path  using 'find / -name libc++_shared.so 2>/dev/null'")

    if not sys.stdin.isatty():
        error(f"Couldn't find {FILE_NAMES[Lib.cpp_shared]} libs at {path} and no interactive terminal "
              f"is available to enter the path manually.")

    alternative_cpp_path = input("Enter the path manually: ")
    if os.path.exists(alternative_cpp_path):
        return alternative_cpp_path

    error(f"Couldn't find the path you entered: {alternative_cpp_path}")
    exit(1)
