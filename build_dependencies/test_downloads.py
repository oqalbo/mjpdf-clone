import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import requests

from build_dependencies.common import download_file
from build_dependencies.pdfium_source import run


class FakeResponse:
    def __init__(self, content=b"", status_code=200):
        self.content = content
        self.status_code = status_code

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(
                f"HTTP {self.status_code}",
                response=self,
            )

    def iter_content(self, chunk_size):
        for offset in range(0, len(self.content), chunk_size):
            yield self.content[offset:offset + chunk_size]


class DownloadFileTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.target = Path(self.temp_dir.name) / "dependency.tar.xz"
        self.payload = b"verified dependency archive"
        self.sha256 = hashlib.sha256(self.payload).hexdigest()

    def tearDown(self):
        self.temp_dir.cleanup()

    @patch("build_dependencies.common.time.sleep")
    @patch("requests.get")
    def test_falls_back_to_next_source_without_waiting(self, get, sleep):
        get.side_effect = [
            FakeResponse(status_code=502),
            FakeResponse(self.payload),
        ]

        result = download_file(
            ("https://primary.invalid/archive", "https://mirror.invalid/archive"),
            self.target,
            sha256=self.sha256,
        )

        self.assertEqual(str(self.target), result)
        self.assertEqual(self.payload, self.target.read_bytes())
        self.assertEqual(2, get.call_count)
        sleep.assert_not_called()

    @patch("build_dependencies.common.time.sleep")
    @patch("requests.get")
    def test_retries_transient_network_failure(self, get, sleep):
        get.side_effect = [
            requests.ConnectionError("connection reset"),
            FakeResponse(self.payload),
        ]

        download_file("https://primary.invalid/archive", self.target, sha256=self.sha256)

        self.assertEqual(self.payload, self.target.read_bytes())
        self.assertEqual(2, get.call_count)
        sleep.assert_called_once_with(1)

    @patch("build_dependencies.common.time.sleep")
    @patch("requests.get")
    def test_does_not_retry_permanent_http_error(self, get, sleep):
        get.return_value = FakeResponse(status_code=404)

        with self.assertRaises(SystemExit):
            download_file("https://primary.invalid/missing", self.target, sha256=self.sha256)

        self.assertEqual(1, get.call_count)
        sleep.assert_not_called()
        self.assertFalse(self.target.exists())

    @patch("build_dependencies.common.time.sleep")
    @patch("requests.get")
    def test_checksum_failure_never_replaces_existing_file(self, get, sleep):
        self.target.write_bytes(b"existing archive")
        get.return_value = FakeResponse(b"corrupt archive")

        with self.assertRaises(SystemExit):
            download_file("https://primary.invalid/archive", self.target, sha256=self.sha256)

        self.assertEqual(b"existing archive", self.target.read_bytes())
        self.assertFalse(Path(str(self.target) + ".part").exists())
        self.assertEqual(4, get.call_count)
        self.assertEqual([unittest.mock.call(1), unittest.mock.call(4), unittest.mock.call(9)], sleep.call_args_list)


class NetworkCommandTest(unittest.TestCase):
    @patch("build_dependencies.pdfium_source.time.sleep")
    @patch("build_dependencies.pdfium_source.subprocess.run")
    def test_retries_network_command(self, subprocess_run, sleep):
        subprocess_run.side_effect = [
            SimpleNamespace(returncode=1),
            SimpleNamespace(returncode=0),
        ]

        run(["gclient", "sync"], ".", {}, retry_delays=(2,))

        self.assertEqual(2, subprocess_run.call_count)
        sleep.assert_called_once_with(2)

    @patch("build_dependencies.pdfium_source.time.sleep")
    @patch("build_dependencies.pdfium_source.subprocess.run")
    def test_retries_timed_out_network_command(self, subprocess_run, sleep):
        subprocess_run.side_effect = [
            subprocess.TimeoutExpired(["gclient", "sync"], 30),
            SimpleNamespace(returncode=0),
        ]

        run(["gclient", "sync"], ".", {}, retry_delays=(3,), timeout=30)

        self.assertEqual(2, subprocess_run.call_count)
        sleep.assert_called_once_with(3)


if __name__ == "__main__":
    unittest.main()
