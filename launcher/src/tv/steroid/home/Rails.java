package tv.steroid.home;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/**
 * Scrolling with breathing room: when focus moves, the focused card is brought into view with a
 * margin around it, so its zoom and outside focus ring are never cut by the screen edge.
 */
final class Rails {
    /** A horizontal row that spans the full screen width but rests at the page margin. */
    static class Rail extends HorizontalScrollView {
        final int margin;
        Rail(Context c, int marginPx) {
            super(c);
            margin = marginPx;
            setHorizontalScrollBarEnabled(false);
            setClipChildren(false);
            setClipToPadding(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setSmoothScrollingEnabled(true);
        }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
            Rect m = new Rect(r);
            m.inset(-margin, 0);
            return super.computeScrollDeltaToGetChildRectOnScreen(m);
        }
    }

    /** The home page itself: keeps a margin above and below the focused row. */
    static class Page extends ScrollView {
        final int margin;
        Page(Context c, int marginPx) {
            super(c);
            margin = marginPx;
            setVerticalScrollBarEnabled(false);
            setClipChildren(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setSmoothScrollingEnabled(true);
        }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
            Rect m = new Rect(r);
            m.inset(0, -margin);
            return super.computeScrollDeltaToGetChildRectOnScreen(m);
        }
    }
}
