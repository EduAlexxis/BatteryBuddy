package io.github.edualexxis.batterybuddy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.view.View;
import android.widget.ImageView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * BatteryBuddy-style cute status bar battery icon, SystemUI scope only.
 *
 * We hook the full draw(Canvas) of ColorOS's HorizontalBatteryContentDrawable and take over the
 * whole icon paint ourselves, via BatteryPaint.drawCuteBattery (kept in its own class with no
 * Xposed dependency — see BatteryPaint's own doc comment for why). Three mood bands
 * (happy/neutral/sad) plus a dead (X eyes) state below the sad band, always-happy while
 * charging (read from the real BatteryManager, not ColorOS's internal icon-id field). Colors
 * come from one of four presets (System/Light/Dark/Custom) chosen in the companion
 * SettingsActivity, read via PrefsProvider (a ContentProvider — SELinux Enforcing on this ROM
 * blocks a cross-app shared_prefs file read even with open DAC bits; see Docs).
 *
 * Logcat: adb logcat -s VectorLegacyBridge | grep ColorStatch
 */
public class Hook implements IXposedHookLoadPackage {
  static final String SYSUI = "com.android.systemui";
  static final String HORIZONTAL =
      "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable";

  @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
    if (!SYSUI.equals(p.packageName)) return;
    XposedBridge.log("ColorStatch: loaded in SystemUI, pid scope ok");

    try {
      Class<?> cls = p.classLoader.loadClass(HORIZONTAL);
      Method draw = findMethod(cls, "draw");
      if (draw == null) {
        XposedBridge.log("ColorStatch: draw(Canvas) not found, aborting");
        return;
      }
      XposedBridge.hookMethod(draw, new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam mh) {
          try {
            Drawable d = (Drawable) mh.thisObject;
            Canvas canvas = (Canvas) mh.args[0];
            Rect bounds = d.getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return; // not laid out yet

            int level = getInt(mh.thisObject, "batteryLevel", 50);
            int themeFill = getInt(mh.thisObject, "progressColor", 0xFF4CAF50);
            int themeOutline = getInt(mh.thisObject, "outlineColor", 0xFFFFFFFF);

            Object ctxObj = getField(mh.thisObject, "context");
            if (!(ctxObj instanceof Context)) ctxObj = getField(mh.thisObject, "mContext");
            Context ctx = (ctxObj instanceof Context) ? (Context) ctxObj : null;

            Prefs prefs = Prefs.read(ctx);
            String colorMode = prefs != null ? prefs.colorMode : SettingsActivity.DEFAULT_COLOR_MODE;
            int outlineColor, fillColor;
            switch (colorMode) {
              case "light": outlineColor = BatteryPaint.LIGHT_OUTLINE; fillColor = BatteryPaint.LIGHT_FILL; break;
              case "dark": outlineColor = BatteryPaint.DARK_OUTLINE; fillColor = BatteryPaint.DARK_FILL; break;
              case "custom":
                outlineColor = prefs != null ? parseColorOr(prefs.customOutlineColor, BatteryPaint.LIGHT_OUTLINE) : BatteryPaint.LIGHT_OUTLINE;
                fillColor = prefs != null ? parseColorOr(prefs.customFillColor, BatteryPaint.LIGHT_FILL) : BatteryPaint.LIGHT_FILL;
                break;
              default: outlineColor = themeOutline; fillColor = themeFill; break;
            }

            int happyMin = prefs != null ? prefs.happyMin : SettingsActivity.DEFAULT_HAPPY_MIN;
            int neutralMin = prefs != null ? prefs.neutralMin : SettingsActivity.DEFAULT_NEUTRAL_MIN;
            int sadMin = prefs != null ? prefs.sadMin : SettingsActivity.DEFAULT_SAD_MIN;
            int thicknessPct = prefs != null ? prefs.outlineThickness : SettingsActivity.DEFAULT_OUTLINE_THICKNESS;
            // maps the 0-100 slider to the stroke-to-height ratio the icon paint uses
            float strokeRatio = 0.06f + (thicknessPct / 100f) * 0.24f;

            Boolean charging = (ctx != null) ? isDeviceCharging(ctx) : null;
            if (charging == null) charging = getInt(mh.thisObject, "chargeIconId", 0) != 0; // fallback

            BatteryPaint.Mood mood;
            if (charging) mood = BatteryPaint.Mood.HAPPY;
            else if (level >= happyMin) mood = BatteryPaint.Mood.HAPPY;
            else if (level >= neutralMin) mood = BatteryPaint.Mood.NEUTRAL;
            else if (level >= sadMin) mood = BatteryPaint.Mood.SAD;
            else mood = BatteryPaint.Mood.DEAD; // 1..sadMin-1, and 0

            if (prefs != null && prefs.showPercent) {
              setPercentSetting(ctx, 2);
              applyPercent(d, prefs.percentLeft);
            } else if (prefs != null) {
              setPercentSetting(ctx, 0);
            }
            BatteryPaint.drawCuteBattery(canvas, new RectF(bounds), fillColor, outlineColor, mood, strokeRatio);
            mh.setResult(null); // we painted the whole icon; skip ColorOS's own draw entirely
          } catch (Throwable t) {
            XposedBridge.log(t);
            // leave original draw to run on failure, so the icon never disappears.
          }
        }
      });
      XposedBridge.log("ColorStatch: hooked draw(Canvas), cute battery active");
    } catch (Throwable t) {
      XposedBridge.log("ColorStatch: setup failed -> " + t);
    }

    hookChargeIcon();
  }

  /**
   * Moves ColorOS's own percentage TextView to the chosen side of the battery icon. Finds the
   * view hosting our drawable (its Callback), then looks for a sibling TextView whose resource id
   * contains "percent" at that level or a few levels up, and re-inserts it before/after the
   * anchor. Layout changes are posted rather than done inside draw(), and only when the order is
   * actually wrong, so this settles after one pass instead of re-running every frame.
   * Untested on-device: the id match is a best guess at ColorOS's naming.
   */
  /** ColorOS's own "battery percentage" setting (system table). On this device 2 = shown
   * outside the icon (percentage was visible beside the battery at 2) and 0 = hidden (read back
   * after hiding it in ColorOS's own settings).
   * Written from SystemUI, which runs as the system uid and may write system settings. */
  static void setPercentSetting(Context ctx, int value) {
    if (ctx == null) return;
    try {
      ContentResolver cr = ctx.getContentResolver();
      if (android.provider.Settings.System.getInt(cr, "display_power_percent", -1) != value) {
        android.provider.Settings.System.putInt(cr, "display_power_percent", value);
      }
    } catch (Throwable t) {
      XposedBridge.log(t);
    }
  }

  static void applyPercent(Drawable d, boolean left) {
    try {
      if (!(d.getCallback() instanceof View)) return;
      View anchor = (View) d.getCallback();
      for (int depth = 0; depth < 4 && anchor.getParent() instanceof android.view.ViewGroup; depth++) {
        final android.view.ViewGroup group = (android.view.ViewGroup) anchor.getParent();
        final View a = anchor;
        android.widget.TextView tv = null;
        for (int i = 0; i < group.getChildCount(); i++) {
          View c = group.getChildAt(i);
          if (!(c instanceof android.widget.TextView) || c.getId() == View.NO_ID) continue;
          try {
            if (c.getResources().getResourceEntryName(c.getId()).toLowerCase().contains("percent")) {
              tv = (android.widget.TextView) c;
              break;
            }
          } catch (Throwable ignored) {}
        }
        if (tv != null) {
          final android.widget.TextView text = tv;
          if (text.getVisibility() != View.VISIBLE) text.setVisibility(View.VISIBLE);
          // a little breathing room between the number and the battery icon
          int gapPx = Math.round(3 * text.getResources().getDisplayMetrics().density);
          int wantStart = left ? 0 : gapPx, wantEnd = left ? gapPx : 0;
          if (text.getPaddingStart() != wantStart || text.getPaddingEnd() != wantEnd) {
            text.setPaddingRelative(wantStart, text.getPaddingTop(), wantEnd, text.getPaddingBottom());
          }
          boolean isLeft = group.indexOfChild(text) < group.indexOfChild(a);
          if (isLeft != left) {
            text.post(() -> {
              group.removeView(text);
              int idx = group.indexOfChild(a);
              group.addView(text, left ? idx : idx + 1);
            });
          }
          return;
        }
        anchor = group;
      }
    } catch (Throwable t) {
      XposedBridge.log(t);
    }
  }

  /**
   * ColorOS draws its own charging bolt in a separate ImageView (id battery_charge_reverse),
   * independent of the battery icon's own drawable. We hook ImageView#setImageDrawable — a
   * method shared by every ImageView in the process, so this callback fires constantly — and
   * only act when the target view's id matches that one, leaving every other ImageView (and
   * calls that are just clearing the drawable to null) completely untouched. Swapping the
   * argument in beforeHookedMethod (rather than calling setImageDrawable again ourselves)
   * avoids any reentrancy concern.
   */
  void hookChargeIcon() {
    try {
      Method setImageDrawable = ImageView.class.getMethod("setImageDrawable", Drawable.class);
      XposedBridge.hookMethod(setImageDrawable, new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam mh) {
          try {
            View v = (View) mh.thisObject;
            if (v.getId() == View.NO_ID) return;
            String idName;
            try { idName = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable t) { return; }
            // battery_charge_reverse exists too (reverse-wireless-charging indicator, not the
            // one actually shown in the status bar) — battery_charge_icon is the real bolt.
            if (!"battery_charge_icon".equals(idName) && !"battery_charge_reverse".equals(idName)) return;

            Drawable incoming = (Drawable) mh.args[0];
            if (incoming == null) return; // being cleared, not currently showing charging state

            Context ctx = v.getContext();
            Prefs prefs = Prefs.read(ctx);
            String style = prefs != null ? prefs.chargeIconStyle : SettingsActivity.DEFAULT_CHARGE_ICON_STYLE;
            String chargeColorMode = prefs != null ? prefs.chargeColorMode : SettingsActivity.DEFAULT_CHARGE_COLOR_MODE;
            boolean honorSystemRetint = "system".equals(chargeColorMode);

            if ("cord".equals(style)) {
              int color = resolveChargeIconColor(ctx, prefs);
              mh.args[0] = buildCordDrawable(color, incoming.getIntrinsicWidth(), incoming.getIntrinsicHeight(), honorSystemRetint);
            } else {
              // "Original": keep ColorOS's own bolt shape, but still honor an explicit
              // Light/Dark/Custom color choice by tinting it directly — only "System" leaves it
              // fully untouched, since that's ColorOS's own theming already doing the right thing.
              if (!honorSystemRetint) {
                int color = resolveChargeIconColor(ctx, prefs);
                incoming.mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                mh.args[0] = incoming;
              }
            }
          } catch (Throwable t) {
            XposedBridge.log(t);
          }
        }
      });
      XposedBridge.log("ColorStatch: hooked charging icon swap");
    } catch (Throwable t) {
      XposedBridge.log("ColorStatch: charge icon hook setup failed -> " + t);
    }
  }

  /** The charging icon's own color setting — independent of the battery's Theme card, so e.g.
   * the battery can stay System while the charge icon is pinned to a fixed color, or vice versa
   * (see the companion app's "Charging icon" → "Color" controls). "System" mode uses a direct
   * night-mode Configuration check rather than tracking the battery hook's cached color: that
   * was tried first and is a race (this hook's call can fire before the battery's redraw after
   * a theme switch, reading a stale value — confirmed on-device: cord stayed white while
   * everything else correctly went dark). Configuration has no such race, it's globally
   * consistent the instant the switch happens, and matches reality (verified: `cmd uimode
   * night` correctly reported "no" exactly when the real icons were black). */
  static int resolveChargeIconColor(Context ctx, Prefs prefs) {
    String mode = prefs != null ? prefs.chargeColorMode : SettingsActivity.DEFAULT_CHARGE_COLOR_MODE;
    switch (mode) {
      case "light": return BatteryPaint.LIGHT_OUTLINE;
      case "dark": return BatteryPaint.DARK_OUTLINE;
      case "custom": return prefs != null ? parseColorOr(prefs.chargeCustomColor, BatteryPaint.LIGHT_OUTLINE) : BatteryPaint.LIGHT_OUTLINE;
      default:
        boolean night = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return night ? BatteryPaint.DARK_OUTLINE : BatteryPaint.LIGHT_OUTLINE;
    }
  }

  /**
   * Traces the actual reference asset (BatteryBuddy's StatusBarCharger.png, inspected pixel by
   * pixel): two short prongs on top, a smooth wide "shoulder" flaring out right below them,
   * tapering into a thin stem/cord going down. The glyph's own proportions (15 wide : 24 tall,
   * ignoring the source image's padding) come out to a 0.625 aspect ratio — almost exactly what
   * this icon slot already is (30x48 / 60x48, also 0.625) — so this maps the traced shape
   * directly onto the bounds with only a small margin, no other fitting logic needed.
   *
   * ColorOS re-tints its status bar icons for a theme change by calling setColorFilter() on the
   * SAME drawable instance already assigned — it doesn't call setImageDrawable() again. Our
   * first version's setColorFilter was a no-op, so every other status bar icon picked up the
   * new theme color immediately while ours sat stuck on whatever color it was built with —
   * confirmed as the actual cause (not a stale-cache timing issue) of "rest of the status bar
   * went dark, cord stayed white" after a light/dark switch. honorSystemRetint controls whether
   * we actually apply that retint: true only in "System" mode, where tracking ColorOS's theme is
   * the point; false for Light/Dark/Custom, where the user's explicit color choice must never
   * be silently overridden by ColorOS's own retinting.
   */
  static Drawable buildCordDrawable(final int color, final int intrinsicW, final int intrinsicH,
                                     final boolean honorSystemRetint) {
    return new Drawable() {
      final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      { paint.setStyle(android.graphics.Paint.Style.FILL); paint.setColor(color); }

      @Override public void draw(Canvas canvas) {
        Rect r = getBounds();
        BatteryPaint.drawPlug(canvas, new RectF(r), paint);
      }

      @Override public int getIntrinsicWidth() { return intrinsicW > 0 ? intrinsicW : 48; }
      @Override public int getIntrinsicHeight() { return intrinsicH > 0 ? intrinsicH : 48; }
      @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
      @Override public void setColorFilter(android.graphics.ColorFilter cf) {
        if (honorSystemRetint) { paint.setColorFilter(cf); invalidateSelf(); }
        // else: ignore — keep the user's explicit Light/Dark/Custom color, don't let ColorOS override it.
      }
      @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    };
  }

  /** Cached read of PrefsProvider: avoids a Binder round trip on every single redraw. */
  static class Prefs {
    int happyMin, neutralMin, sadMin, outlineThickness;
    String colorMode, customOutlineColor, customFillColor, chargeIconStyle;
    String chargeColorMode, chargeCustomColor;
    boolean showPercent, percentLeft;

    static Prefs cached;
    static long cachedAt;

    static Prefs read(Context ctx) {
      long now = System.currentTimeMillis();
      if (cached != null && now - cachedAt < 2000) return cached;
      if (ctx == null) return cached;
      try {
        ContentResolver cr = ctx.getContentResolver();
        try (Cursor c = cr.query(PrefsProvider.URI, null, null, null, null)) {
          if (c != null && c.moveToFirst()) {
            Prefs pr = new Prefs();
            pr.happyMin = c.getInt(c.getColumnIndexOrThrow(SettingsActivity.KEY_HAPPY_MIN));
            pr.neutralMin = c.getInt(c.getColumnIndexOrThrow(SettingsActivity.KEY_NEUTRAL_MIN));
            pr.sadMin = c.getInt(c.getColumnIndexOrThrow(SettingsActivity.KEY_SAD_MIN));
            pr.colorMode = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_COLOR_MODE));
            if (pr.colorMode == null) pr.colorMode = SettingsActivity.DEFAULT_COLOR_MODE;
            pr.customOutlineColor = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_CUSTOM_OUTLINE_COLOR));
            pr.customFillColor = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_CUSTOM_FILL_COLOR));
            pr.outlineThickness = c.getInt(c.getColumnIndexOrThrow(SettingsActivity.KEY_OUTLINE_THICKNESS));
            pr.chargeIconStyle = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_CHARGE_ICON_STYLE));
            if (pr.chargeIconStyle == null) pr.chargeIconStyle = SettingsActivity.DEFAULT_CHARGE_ICON_STYLE;
            pr.chargeColorMode = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_CHARGE_COLOR_MODE));
            if (pr.chargeColorMode == null) pr.chargeColorMode = SettingsActivity.DEFAULT_CHARGE_COLOR_MODE;
            pr.chargeCustomColor = c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_CHARGE_CUSTOM_COLOR));
            pr.showPercent = c.getInt(c.getColumnIndexOrThrow(SettingsActivity.KEY_SHOW_PERCENT)) != 0;
            pr.percentLeft = "left".equals(c.getString(c.getColumnIndexOrThrow(SettingsActivity.KEY_PERCENT_SIDE)));
            cached = pr;
            cachedAt = now;
          }
        }
      } catch (Throwable t) {
        XposedBridge.log("ColorStatch: prefs query failed -> " + t);
      }
      return cached;
    }
  }

  /** Ask Android's real BatteryManager, instead of relying on ColorOS's internal icon-id field. */
  static Boolean isDeviceCharging(Context ctx) {
    try {
      BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
      if (bm == null) return null;
      return bm.isCharging();
    } catch (Throwable t) {
      return null;
    }
  }

  static Method findMethod(Class<?> start, String name) {
    for (Class<?> c = start; c != null; c = c.getSuperclass()) {
      for (Method m : c.getDeclaredMethods()) {
        if (m.getName().equals(name)) { m.setAccessible(true); return m; }
      }
    }
    return null;
  }

  static Object getField(Object obj, String fieldName) {
    for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
      try {
        Field f = c.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
      } catch (Throwable ignored) {}
    }
    return null;
  }

  static int getInt(Object obj, String fieldName, int fallback) {
    Object v = getField(obj, fieldName);
    return (v instanceof Integer) ? (Integer) v : fallback;
  }

  static int parseColorOr(String hex, int fallback) {
    try { return Color.parseColor(hex); } catch (Throwable t) { return fallback; }
  }
}
