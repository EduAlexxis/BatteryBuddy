package io.github.edualexxis.batterybuddy;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Pure drawing code for the cute battery icon — no Xposed import or dependency anywhere in this
 * class. It's used both by Hook (inside SystemUI, via the hook) and by BatteryPreviewView
 * (inside this app's own normal process, for the in-app live preview).
 *
 * This split matters: Hook implements IXposedHookLoadPackage and imports de.robv.android.xposed
 * classes, which only exist inside a process Vector has actually injected into. Loading the Hook
 * class from this app's own (unhooked) process to reach a shared helper would force the VM to
 * resolve that whole class, including its Xposed supertype, and crash with
 * NoClassDefFoundError/ClassNotFoundException — confirmed the hard way the first time this was
 * tried. Keeping the drawing code here, with no Xposed references at all, avoids that.
 *
 * Shape matches the reference exactly: thick outline, a visible gap, then a flat (not
 * level-proportional) fill with the face, and a half-stadium nub with its own small gap off the
 * outline. No plug glyph — tried it, it's unreadable at real status-bar icon size (collides with
 * neighboring status bar icons), so charging is conveyed by mood (always-happy) only.
 */
public class BatteryPaint {
  public static final int LIGHT_OUTLINE = 0xFF000000;
  public static final int LIGHT_FILL = 0xFF8FBF8A;
  public static final int DARK_OUTLINE = 0xFFFFFFFF;
  public static final int DARK_FILL = 0xFF8FBF8A;

  public enum Mood { HAPPY, NEUTRAL, SAD, DEAD }

  // ColorOS hands us a taller bounds rect than the icon should visually fill — other status bar
  // icons (signal bars, carrier text, bolt) sit well short of their own slot's full height, but
  // our old code drew edge-to-edge, so the battery looked noticeably taller than its neighbors.
  // Insetting vertically here (centered, width untouched) makes it read as the same height as
  // everything else in the status bar.
  static final float VERTICAL_INSET_FRACTION = 0.06f; // per edge; ~12% of height removed total

  public static void drawCuteBattery(Canvas canvas, RectF rawFull, int fillColor, int outlineColor, Mood mood, float strokeRatio) {
    float vInset = rawFull.height() * VERTICAL_INSET_FRACTION;
    RectF full = new RectF(rawFull.left, rawFull.top + vInset, rawFull.right, rawFull.bottom - vInset);

    float strokeW = full.height() * strokeRatio;
    float gap = full.height() * 0.07f;
    float nubDiameter = full.height() * 0.5f;
    float nubGap = full.height() * 0.03f;

    // outer stroke-only body; reserve the right slice for the nub (plus its own small gap).
    RectF outer = new RectF(full.left + strokeW / 2f, full.top + strokeW / 2f,
        full.right - nubDiameter * 0.55f - nubGap - strokeW / 2f, full.bottom - strokeW / 2f);
    float outerRadius = outer.height() * 0.42f;

    Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    outline.setStyle(Paint.Style.STROKE);
    outline.setStrokeWidth(strokeW);
    outline.setColor(outlineColor);
    canvas.drawRoundRect(outer, outerRadius, outerRadius, outline);

    // inner fill, inset past the stroke AND a visible gap — not flush against the outline.
    float innerInset = strokeW / 2f + gap;
    RectF inner = new RectF(outer.left + innerInset, outer.top + innerInset,
        outer.right - innerInset, outer.bottom - innerInset);
    float innerRadius = inner.height() * 0.42f;

    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setStyle(Paint.Style.FILL);
    fill.setColor(fillColor);
    canvas.drawRoundRect(inner, innerRadius, innerRadius, fill);

    // terminal nub: half-stadium (flat where it meets the body, rounded on the outer side),
    // sitting just outside the outline with a small visible gap, not overlapping into it.
    RectF nub = new RectF(outer.right + strokeW / 2f + nubGap, full.top + (full.height() - nubDiameter) / 2f,
        outer.right + strokeW / 2f + nubGap + nubDiameter * 0.55f, full.top + (full.height() - nubDiameter) / 2f + nubDiameter);
    float nubR = nub.height() / 2f;
    Path nubPath = new Path();
    nubPath.addRoundRect(nub, new float[]{0, 0, nubR, nubR, nubR, nubR, 0, 0}, Path.Direction.CW);
    Paint nubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    nubPaint.setStyle(Paint.Style.FILL);
    nubPaint.setColor(outlineColor);
    canvas.drawPath(nubPath, nubPaint);

    // face, sized off the inner fill rect. Colored for contrast against the FILL it actually
    // sits on top of, not just copied from outlineColor — those can disagree (e.g. "System"
    // mode in light theme reporting a dark outline alongside a dark fill), which left the face
    // unreadable: a black face on a dark fill.
    int faceColor = bestOn(fillColor);
    Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    face.setColor(faceColor);
    face.setStyle(Paint.Style.FILL);

    Paint faceStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    faceStroke.setColor(faceColor);
    faceStroke.setStyle(Paint.Style.STROKE);
    faceStroke.setStrokeWidth(Math.max(1.5f, inner.height() * 0.13f));
    faceStroke.setStrokeCap(Paint.Cap.ROUND);

    float eyeR = inner.height() * 0.105f;
    float eyeY = inner.top + inner.height() * 0.4f;
    float eye1X = inner.left + inner.width() * 0.3f;
    float eye2X = inner.left + inner.width() * 0.62f;

    if (mood == Mood.DEAD) {
      drawX(canvas, eye1X, eyeY, eyeR, faceStroke);
      drawX(canvas, eye2X, eyeY, eyeR, faceStroke);
    } else {
      canvas.drawCircle(eye1X, eyeY, eyeR, face);
      canvas.drawCircle(eye2X, eyeY, eyeR, face);
    }

    float mouthY = inner.top + inner.height() * 0.62f;
    float mouthL = inner.left + inner.width() * 0.33f;
    float mouthR_ = inner.left + inner.width() * 0.59f;
    float mouthMidX = (mouthL + mouthR_) / 2f;
    float bow = inner.height() * 0.22f;

    Path mouth = new Path();
    switch (mood) {
      case HAPPY:
        mouth.moveTo(mouthL, mouthY);
        mouth.quadTo(mouthMidX, mouthY + bow, mouthR_, mouthY);
        break;
      case SAD:
        mouth.moveTo(mouthL, mouthY + bow * 0.6f);
        mouth.quadTo(mouthMidX, mouthY - bow * 0.6f, mouthR_, mouthY + bow * 0.6f);
        break;
      case NEUTRAL:
      case DEAD:
      default:
        mouth.moveTo(mouthL, mouthY);
        mouth.lineTo(mouthR_, mouthY);
        break;
    }
    canvas.drawPath(mouth, faceStroke);
  }

  static void drawX(Canvas canvas, float cx, float cy, float r, Paint p) {
    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, p);
    canvas.drawLine(cx - r, cy + r, cx + r, cy - r, p);
  }

  /** Picks whichever of black/white actually reads against this background color. */
  static int bestOn(int bgColor) {
    double luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255.0;
    return luminance > 0.55 ? 0xFF000000 : 0xFFFFFFFF;
  }

  /**
   * Traces the actual reference asset (BatteryBuddy's StatusBarCharger.png, inspected pixel by
   * pixel): two short prongs on top, a smooth wide "shoulder" flaring out right below them,
   * tapering into a thin stem/cord going down. Shared by Hook (the real status bar icon, inside
   * its custom Drawable's draw()) and BatteryPreviewView (the in-app preview, called directly),
   * so both places draw exactly the same shape — this is the one place that logic lives.
   */
  public static void drawPlug(Canvas canvas, RectF r, int color) {
    Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillPaint.setStyle(Paint.Style.FILL);
    fillPaint.setColor(color);
    drawPlug(canvas, r, fillPaint);
  }

  /** Same shape, but takes the Paint directly so a caller can keep a persistent, externally
   * mutable Paint (Hook does this — see its doc comment for why that matters for retinting). */
  public static void drawPlug(Canvas canvas, RectF r, Paint fillPaint) {
    if (r.width() <= 0 || r.height() <= 0) return;
    float marginX = r.width() * 0.06f;
    float marginY = r.height() * 0.03f;
    float gx = r.left + marginX;
    float gw = r.width() - 2 * marginX;
    float gy = r.top + marginY;
    float gh = r.height() - 2 * marginY;

    Path plug = new Path();
    plug.moveTo(gx + gw * 0.2f, gy);
    plug.lineTo(gx + gw * 0.2f, gy + gh * 0.25f);
    plug.quadTo(gx, gy + gh * 0.25f, gx, gy + gh * 0.292f);
    plug.quadTo(gx, gy + gh * 0.45f, gx + gw * 0.4f, gy + gh * 0.583f);
    plug.lineTo(gx + gw * 0.4f, gy + gh);
    plug.lineTo(gx + gw * 0.6f, gy + gh);
    plug.lineTo(gx + gw * 0.6f, gy + gh * 0.583f);
    plug.quadTo(gx + gw, gy + gh * 0.45f, gx + gw, gy + gh * 0.292f);
    plug.quadTo(gx + gw, gy + gh * 0.25f, gx + gw * 0.8f, gy + gh * 0.25f);
    plug.lineTo(gx + gw * 0.8f, gy);
    plug.lineTo(gx + gw * 0.6f, gy);
    plug.lineTo(gx + gw * 0.6f, gy + gh * 0.25f);
    plug.lineTo(gx + gw * 0.4f, gy + gh * 0.25f);
    plug.lineTo(gx + gw * 0.4f, gy);
    plug.close();

    canvas.drawPath(plug, fillPaint);
  }

  /** A simple lightning bolt, as a stand-in for ColorOS's own "Original" charging bolt in the
   * in-app preview — we can't read ColorOS's actual asset from this app's own process, only from
   * inside the hooked SystemUI (see Hook's doc comment), so the real status bar always shows the
   * real bolt for "Original"; this is only ever shown here, in the preview. */
  public static void drawBolt(Canvas canvas, RectF r, int color) {
    if (r.width() <= 0 || r.height() <= 0) return;
    Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillPaint.setStyle(Paint.Style.FILL);
    fillPaint.setColor(color);

    float w = r.width(), h = r.height();
    Path bolt = new Path();
    bolt.moveTo(r.left + w * 0.58f, r.top);
    bolt.lineTo(r.left + w * 0.18f, r.top + h * 0.58f);
    bolt.lineTo(r.left + w * 0.46f, r.top + h * 0.58f);
    bolt.lineTo(r.left + w * 0.42f, r.top + h);
    bolt.lineTo(r.left + w * 0.82f, r.top + h * 0.42f);
    bolt.lineTo(r.left + w * 0.54f, r.top + h * 0.42f);
    bolt.close();
    canvas.drawPath(bolt, fillPaint);
  }
}
