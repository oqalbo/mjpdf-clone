# JVM unit tests

The JVM tests run on the development machine without an emulator. They pin the decision logic underneath the screens: layout and coordinate math in the viewer library, codecs and parsers, validation, state machines, and the text normalization in the PDFium bindings. Behavior on device stays the job of the instrumentation suite.

## Running

```sh
./gradlew :app:testDebugUnitTest
./gradlew :AndroidPdfViewer:android-pdf-viewer:testDebugUnitTest
./gradlew :PdfiumAndroid:testDebugUnitTest
```

A single class: add `--tests '*BackupCodecTest*'`. Results land in `<module>/build/test-results/testDebugUnitTest/`. The three modules together finish in a few seconds.

## What the tests may use

- JUnit 4 everywhere. Mockito 5 in the viewer library only. No Robolectric.
- The app module compiles against a stub `android.jar` with `returnDefaultValues = true`: framework methods return null, 0 or false. Code that needs the framework is reached through a seam instead: a pure function, an injected clock or resolver, or one of the fakes below.
- The viewer library module has no such flag, so framework calls throw. `RectF`, `PointF` and `Rect` constructors are no-ops there; build them with the no-argument constructor and assign the fields, or use `support.Rects`.
- `PdfiumCore` logs when the native library is missing, and `Log` throws on the JVM. In the viewer library the first touch of `PdfiumCore`, `PdfFile` or `CropMarginDetection` goes through `support.PdfiumTestSupport.ensureInitialized()` in a `@BeforeClass`.

## Shared fakes

App module, under `app/src/test/java/com/gitlab/mudlej/MjPdfReader/support/`:

- `InMemorySharedPreferences`: a full `SharedPreferences` with listeners. Typed getters throw `ClassCastException` on a type mismatch, like Android, so the recovery paths in `Preferences` run.
- `FakeFilesContext`: a `ContextWrapper` with real `filesDir` and `cacheDir` under a `TemporaryFolder`.
- `MetadataServiceFakes`: a recording metadata service and a binding context for the IPC client.

`app/src/test/java/android/net/Uri.java` shadows the stub `Uri` with a working implementation (parse, encode, decode, builder), so production code that builds or compares URIs runs unchanged.

Viewer library, under `AndroidPdfViewer/android-pdf-viewer/src/test/java/com/github/barteksc/pdfviewer/`:

- `support.PdfiumTestSupport`: the initialization rule above plus mocked `PdfiumCore` and `PdfDocument` factories.
- `support.Rects`: `rectF`, `rect`, `pointF` builders and a field by field `assertRectF`.
- `PdfFileTestSupport`: a fluent builder for a real `PdfFile` over mocked natives (page sizes, orientation, spacing, columns, crop, user pages).
- `GestureTestSupport`: a `DragPinchManager` with its detectors mocked.

## House rules

- A test pins a decision with the inputs that break it. No getters, no constants, no "does not throw".
- Deterministic: no wall clock unless injected, locale and time zone set and restored, no sleeps above 50 ms, no shared state without a reset.
- No reflection into privates. A private seam becomes `internal` (Kotlin) or package private (Java).
- No comments, no em dashes, full surface names, the GPLv3 header on every file.

## Pending rulings

A test that exposes a suspected bug asserts the correct behavior and carries `@Ignore("Pending ruling: <what the code does>, <what was expected>")`. It is skipped until the owner decides whether the code or the expectation changes. Nothing in production is changed for a pending ruling.
