package os.aios.core;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.WindowInsetsController;

/**
 * System-owned day/night palette following Material 3 colour roles from the Android dynamic
 * theme (same resources LineageOS Settings uses). Never cache across configuration changes.
 * Hex values are only fallbacks for builds without the system resources.
 */
public final class AiosTheme {
    private AiosTheme() {}
    public static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    private static int system(Context c, String role, int lightFallback, int darkFallback) {
        boolean dark = dark(c);
        int id = c.getResources().getIdentifier("system_" + role + (dark ? "_dark" : "_light"), "color", "android");
        return id == 0 ? (dark ? darkFallback : lightFallback) : c.getColor(id);
    }

    // Surfaces (page → nav bar → list row → fields), in order of elevation.
    /** Page background. */
    public static int background(Context c) {return system(c,"surface_container",0xffeef4f3,0xff0e1514);}
    /** Bottom navigation and sheets. */
    public static int surfaceLow(Context c) {return system(c,"surface_container_low",0xfff4f9f8,0xff161d1c);}
    /** Grouped list rows and cards. */
    public static int surface(Context c) {return dark(c)?system(c,"surface_container_high",0xffe6eceb,0xff1b2322):system(c,"surface",0xffffffff,0xff1b2322);}
    /** Text fields, chips, inactive track. */
    public static int surfaceHigh(Context c) {return dark(c)?system(c,"surface_container_highest",0xffdfe5e4,0xff262e2d):system(c,"surface_container_high",0xffe6eceb,0xff262e2d);}

    // Content colours.
    public static int text(Context c) {return system(c,"on_surface",0xff191c1d,0xffdde4e3);}
    public static int muted(Context c) {return system(c,"on_surface_variant",0xff3f4948,0xffbec9c8);}
    public static int outline(Context c) {return system(c,"outline_variant",0xffbec9c8,0xff3f4948);}
    public static int outlineStrong(Context c) {return system(c,"outline",0xff6f7978,0xff889392);}

    // Accent roles.
    public static int accent(Context c) {return system(c,"primary",0xff006a68,0xff80d5d1);}
    public static int onAccent(Context c) {return system(c,"on_primary",0xffffffff,0xff003736);}
    /** primary_container: user messages, FAB. */
    public static int selected(Context c) {return system(c,"primary_container",0xff9cf1ee,0xff00504f);}
    public static int onSelected(Context c) {return system(c,"on_primary_container",0xff002020,0xff9cf1ee);}
    /** secondary_container: selected chips, navigation indicator, tonal buttons. */
    public static int tonal(Context c) {return system(c,"secondary_container",0xffcce8e6,0xff324b4a);}
    public static int onTonal(Context c) {return system(c,"on_secondary_container",0xff051f1f,0xffcce8e6);}
    /** tertiary_container: suggestions and informational banners. */
    public static int tertiary(Context c) {return system(c,"tertiary_container",0xffd9e2ff,0xff2d3b5a);}
    public static int onTertiary(Context c) {return system(c,"on_tertiary_container",0xff0e1b36,0xffd9e2ff);}
    public static int error(Context c) {return system(c,"error",0xffba1a1a,0xffffb4ab);}
    public static int errorContainer(Context c) {return system(c,"error_container",0xffffdad6,0xff93000a);}
    public static int onErrorContainer(Context c) {return system(c,"on_error_container",0xff410002,0xffffdad6);}
    /** Paused / attention state. Android has no warning role; fixed amber tuned per mode. */
    public static int warn(Context c) {return dark(c)?0xfff3c26b:0xff8a5a00;}
    public static int ripple(Context c) {return (text(c)&0x00ffffff)|0x1f000000;}
    public static int scrim(Context c) {return dark(c)?0x80000000:0x52000000;}

    public static GradientDrawable shape(Context c,int color,float radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(radius*c.getResources().getDisplayMetrics().density);return d;
    }
    public static void window(Activity a) {
        // Callers may style the window before setContentView creates its decor.
        a.getWindow().getDecorView();
        int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        if(a.getWindow().getInsetsController()!=null)a.getWindow().getInsetsController().setSystemBarsAppearance(dark(a)?0:flags,flags);
        a.getWindow().setStatusBarColor(background(a));a.getWindow().setNavigationBarColor(background(a));
    }
}
