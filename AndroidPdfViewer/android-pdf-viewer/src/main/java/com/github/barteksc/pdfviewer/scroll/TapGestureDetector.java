package com.github.barteksc.pdfviewer.scroll;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Distinguishes a tap from a drag using Android's system touch slop. */
final class TapGestureDetector {

    private final float touchSlopSquared;
    private float downRawX;
    private float downRawY;
    private boolean tapCandidate;

    TapGestureDetector(Context context) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        touchSlopSquared = touchSlop * touchSlop;
    }

    boolean isTap(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                tapCandidate = true;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (tapCandidate && movedBeyondTouchSlop(event)) {
                    tapCandidate = false;
                }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                tapCandidate = false;
                return false;
            case MotionEvent.ACTION_UP:
                boolean wasTap = tapCandidate && !movedBeyondTouchSlop(event);
                tapCandidate = false;
                return wasTap;
            default:
                return false;
        }
    }

    private boolean movedBeyondTouchSlop(MotionEvent event) {
        float deltaX = event.getRawX() - downRawX;
        float deltaY = event.getRawY() - downRawY;
        return deltaX * deltaX + deltaY * deltaY > touchSlopSquared;
    }
}
