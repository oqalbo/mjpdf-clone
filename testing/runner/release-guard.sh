#!/usr/bin/env bash
# The hooks absent in release gate.
#
# Every debug hook in core/debug and in the viewer library is
# guarded by BuildConfig.DEBUG or by a static debug flag inside the viewer
# library, and a release build must carry none of them. This checks that claim
# against the only artifact that settles it: the bytes that ship.
#
# It reads the dex, never mapping.txt. On a clean build mapping.txt lists hook
# names that are not in the APK at all, because R8 records inlined frame
# attribution and source file entries for code it removed. AnnotationTestHooks
# appears five times in mapping.txt and zero times in the dex, so a mapping.txt
# based gate is red on a correct build.
#
# A grep that finds nothing proves nothing on its own: a mistyped path, an empty
# file or a failed unzip all look identical to a clean result. So the run also
# requires a positive control, a string that must be there, and fails if it is
# missing.
#
# Usage:
#   ./release-guard.sh                 build the release APK, then check it
#   ./release-guard.sh --skip-build    check the APK already in outputs
#   ./release-guard.sh --apk <path>    check a specific APK
#
# Exits 0 when the dex is clean, 1 when a hook symbol is present or the control
# is missing, 2 on a usage or environment problem.

set -eu -o pipefail

GUARD_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$GUARD_DIR/../.." && pwd)

SKIP_BUILD=0
APK=""

# Every symbol that must not survive into a release build.
#
# The class names are DERIVED from core/debug rather than listed here, because a
# hand maintained list silently stops covering hooks added after it was written.
#
# Only symbols that are NOT a core/debug class name belong below: member names,
# the debug provider and its authority, and debug only field names.
DEBUG_HOOK_DIR="$REPO_ROOT/app/src/main/java/com/gitlab/mudlej/MjPdfReader/core/debug"

derived_hook_classes() {
    if [ ! -d "$DEBUG_HOOK_DIR" ]; then
        printf 'ERROR: %s does not exist, so no hook class names could be derived.\n' "$DEBUG_HOOK_DIR" >&2
        exit 1
    fi
    find "$DEBUG_HOOK_DIR" -maxdepth 1 -name '*.kt' -exec basename {} .kt \; | sort
}

HOOK_SYMBOLS="
IntroDebug
IntroPagerState
IntroPageSnapshot
TextModeDebug
TextModePageSnapshot
ShareQuoteDebug
ClosedCacheSnapshot
debugCacheStatsIfInitialized
debugLiveInstances
debugClosedCache
debugForgetClosedCache
hlBoundsCalls
groupCacheMisses
snapAligned
pageOffsetRatio
debugPageHighlights
debugFormField
StubSharerProvider
mjpdf.test.sharer
debugSaveFaultMode
debugState
DebugCacheStats
TileCacheStats
TextCacheStats
WeakRegistry
registerTextLoader
unregisterTextLoader
debugTextCacheStats
textCacheStats
debugReuseRecycles
debugPreviewReuseRecycles
debugForgetPreviewReuseRecycles
previewReuseRecycles
registerThumbnailCache
recordThumbnailCacheClosed
thumbnailStats
tileCacheStats
readCacheStats
debugCacheStats
debugCacheKeys
debugPartStats
debugPageStats
debugMemoryStats
debugPoolStats
debugBucketStats
debugPerBucketCap
debugMaxBytes
"

# The positive control. It sits beside the fault mode read in
# AnnotationSaveController, in main code with no debug guard, so it is in every
# build. If this is absent the grep is not reading the dex it thinks it is.
CONTROL_STRING="annotation-save-"

while [ $# -gt 0 ]; do
    case $1 in
        --skip-build) SKIP_BUILD=1; shift ;;
        --apk) APK=$2; shift 2 ;;
        -h | --help) sed -n '3,26p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) printf 'unknown option: %s\n' "$1" >&2; exit 2 ;;
    esac
done

say()  { printf '%s\n' "$*"; }
info() { printf '  %s\n' "$*"; }

if [ "$SKIP_BUILD" = 0 ]; then
    say "Building the release APK"
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :app:assembleRelease ||
        { printf 'ERROR: assembleRelease failed\n' >&2; exit 2; }
fi

# The ABI splits carry the same dex, so the universal APK is the one to read.
if [ -z "$APK" ]; then
    APK=$REPO_ROOT/app/build/outputs/apk/release/app-universal-release-unsigned.apk
    if [ ! -f "$APK" ]; then
        APK=$(find "$REPO_ROOT/app/build/outputs/apk/release" -name '*release*.apk' 2>/dev/null |
            sort | head -1)
    fi
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    printf 'ERROR: no release APK found. Run without --skip-build.\n' >&2
    exit 2
fi

say "Checking $(basename "$APK")"
info "built $(date -r "$APK" '+%Y-%m-%d %H:%M:%S')"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

unzip -o -q "$APK" 'classes*.dex' -d "$WORK" ||
    { printf 'ERROR: could not unpack the dex files from %s\n' "$APK" >&2; exit 2; }

DEX_COUNT=$(find "$WORK" -maxdepth 1 -name 'classes*.dex' | wc -l | tr -d ' ')
[ "$DEX_COUNT" -gt 0 ] ||
    { printf 'ERROR: %s holds no dex files\n' "$APK" >&2; exit 2; }

ALL_DEX=$WORK/all.dex
find "$WORK" -maxdepth 1 -name 'classes*.dex' | sort | xargs cat >"$ALL_DEX"
info "$DEX_COUNT dex files, $(wc -c <"$ALL_DEX" | tr -d ' ') bytes"

# Occurrences rather than matching lines, because a dex has no meaningful lines
# and one line could hold several hits. Finding nothing is the expected result
# here, so grep's exit status has to be absorbed before pipefail turns a clean
# symbol into an aborted run.
occurrences() {
    { grep -a -o -F -- "$1" "$ALL_DEX" 2>/dev/null || true; } | wc -l | tr -d ' '
}

say ""
say "Positive control"
control_hits=$(occurrences "$CONTROL_STRING")
if [ "$control_hits" -eq 0 ]; then
    printf '\nERROR: the control string %s is not in the dex.\n' "$CONTROL_STRING" >&2
    printf 'The grep is not reading what it should be, so a clean result here would mean nothing.\n' >&2
    exit 1
fi
info "$CONTROL_STRING found $control_hits times, the dex reads correctly"

say ""
say "Hook symbols, all of which must be absent"
FOUND=0
for symbol in $(derived_hook_classes) $HOOK_SYMBOLS; do
    hits=$(occurrences "$symbol")
    if [ "$hits" -eq 0 ]; then
        info "clean   $symbol"
    else
        info "PRESENT $symbol, $hits occurrences"
        FOUND=$((FOUND + 1))
    fi
done

say ""
if [ "$FOUND" -gt 0 ]; then
    printf 'ERROR: %d hook symbol(s) survived into the release build.\n' "$FOUND" >&2
    printf 'Each one is a debug only hook that a release build must not carry.\n' >&2
    exit 1
fi
say "Release build is clean, none of the $(printf '%s %s' "$(derived_hook_classes)" "$HOOK_SYMBOLS" | wc -w | tr -d ' ') hook symbols are present, $(derived_hook_classes | wc -l | tr -d ' ') of them derived from core/debug."
