package tv.steroid.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageView;

/**
 * Lucide icons (lucide.dev, ISC licence) as vector drawables in res/drawable/lu_*.xml.
 * The icon fills the view inside its padding, so set padding to control breathing room.
 */
class Icon extends ImageView {
    static final int MIC = 0, WIFI = 1, BLUETOOTH = 2, GEAR = 3, SOUND = 4, PREV = 5, PLAY = 6, PAUSE = 7,
        NEXT = 8, CLOSE = 9, PLUS = 10, INPUT = 11, ASSIST = 12,
        RAM = 13, STORAGE = 14, CPU = 15, TEMP = 16, NET = 17, RECENTS = 18, GRID = 19,
        USER = 20, IMAGE = 21, TIMER = 22, ZOOM = 23, UP = 24, DOWN = 25, EYE = 26, EYE_OFF = 27, SIZE = 28, APP = 29,
        CLAUDE = 30, CODEX = 31, GEMINI = 32, LAPTOP = 33, MONITOR = 34,
        SWAP = 35, MUSIC = 36, OPEN = 37,
        MUTE = 38, MOON = 39, WIFI_OFF = 40, BT_OFF = 41, COMPASS = 42, SHIELD = 43;
    static final int[] RES = {R.drawable.lu_mic, R.drawable.lu_wifi, R.drawable.lu_bluetooth, R.drawable.lu_settings,
        R.drawable.lu_volume_2, R.drawable.lu_skip_back, R.drawable.lu_play, R.drawable.lu_pause,
        R.drawable.lu_skip_forward, R.drawable.lu_x, R.drawable.lu_plus, R.drawable.lu_tv, R.drawable.lu_sparkles,
        R.drawable.lu_memory_stick, R.drawable.lu_hard_drive, R.drawable.lu_cpu, R.drawable.lu_thermometer,
        R.drawable.lu_arrow_down_up, R.drawable.lu_layers, R.drawable.lu_layout_grid,
        R.drawable.lu_user, R.drawable.lu_image, R.drawable.lu_timer, R.drawable.lu_zoom_in, R.drawable.lu_chevron_up,
        R.drawable.lu_chevron_down, R.drawable.lu_eye, R.drawable.lu_eye_off, R.drawable.lu_scaling, R.drawable.lu_app_window,
        R.drawable.lu_asterisk, R.drawable.lu_code_xml, R.drawable.lu_sparkle, R.drawable.lu_laptop, R.drawable.lu_monitor,
        R.drawable.lu_arrow_left_right, R.drawable.lu_music, R.drawable.lu_external_link, R.drawable.lu_volume_x, R.drawable.lu_moon, R.drawable.lu_wifi_off,
        R.drawable.lu_bluetooth_off, R.drawable.lu_compass, R.drawable.lu_shield_check};
    int type = -1, color = 0xE6FFFFFF;

    Icon(Context c, int type) {
        super(c);
        setScaleType(ScaleType.FIT_CENTER);
        set(type);
        setImageTintList(ColorStateList.valueOf(color));
    }

    void set(int t) { if (t != type) { type = t; setImageResource(RES[t]); } }
    void color(int c) { if (c != color) { color = c; setImageTintList(ColorStateList.valueOf(c)); } }
}
