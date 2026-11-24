package tv.steroid.home;

import android.service.dreams.DreamService;

/**
 * Optional screensaver: the same photo slideshow as the home screen, fully offline.
 * Replaces Google's Ambient mode, which downloads images in the background.
 * Enabled from the Mac with: settings put secure screensaver_components tv.steroid.home/.PhotoDream
 */
public class PhotoDream extends DreamService {
    Ambient a;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        Ui.init(this);
        a = new Ambient(this);
        setContentView(a);
    }

    @Override public void onDreamingStarted() { super.onDreamingStarted(); a.reload(); a.run(true); }
    @Override public void onDreamingStopped() { a.run(false); super.onDreamingStopped(); }
}
