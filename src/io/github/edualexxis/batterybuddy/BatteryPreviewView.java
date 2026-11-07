package io.github.edualexxis.batterybuddy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

/**
 * Renders a live, larger-than-life preview of the icon using the exact same paint routine the
 * hook uses in SystemUI (BatteryPaint.drawCuteBattery / drawPlug / drawBolt), just called
 * directly from this app's own process. BatteryPaint has no Xposed dependency, unlike Hook,
 * which is why this references BatteryPaint and not Hook directly — see BatteryPaint's doc
 * comment. When charging, also shows the selected charging-icon style (Original's bolt here is
 * only ever a stand-in — the real status bar bolt is ColorOS's own asset, not readable from this
 * app's own process) next to the battery, same as how it actually sits in the status bar.
 */
public class BatteryPreviewView extends View {
  int fillColor = 0xFF8FBF8A;
  int outlineColor = 0xFF000000;
  float strokeRatio = 0.16f;
  BatteryPaint.Mood mood = BatteryPaint.Mood.HAPPY;
  boolean charging = false;
  String chargeIconStyle = "original";
  int chargeColor = 0xFF000000;
  boolean showPercent = false;
  boolean percentLeft = false;
  int percent = 50;
  final android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

  public BatteryPreviewView(Context ctx) { super(ctx); }

  public void update(int fillColor, int outlineColor, float strokeRatio, BatteryPaint.Mood mood,
                      boolean charging, String chargeIconStyle, int chargeColor,
                      boolean showPercent, boolean percentLeft, int percent) {
    this.fillColor = fillColor;
    this.outlineColor = outlineColor;
    this.strokeRatio = strokeRatio;
    this.mood = mood;
    this.charging = charging;
    this.chargeIconStyle = chargeIconStyle;
    this.chargeColor = chargeColor;
    this.showPercent = showPercent;
    this.percentLeft = percentLeft;
    this.percent = percent;
    invalidate();
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int w = MeasureSpec.getSize(widthSpec);
    float density = getResources().getDisplayMetrics().density;
    int h = Math.min((int) (w / 2.4f), (int) (150 * density));
    setMeasuredDimension(w, h);
  }

  @Override protected void onDraw(Canvas canvas) {
    float padY = getHeight() * 0.18f;
    float top = padY, bottom = getHeight() - padY;
    float fullH = bottom - top;

    float maxBatteryW = fullH * 2.0f;
    float chargeIconW = charging ? fullH * 0.55f : 0f; // portrait glyph, ~0.625 aspect of its own box
    float gap = charging ? fullH * 0.14f : 0f;

    String text = percent + "%";
    float textW = 0f, textGap = 0f;
    if (showPercent) {
      textPaint.setColor(outlineColor);
      textPaint.setTextSize(fullH * 0.6f);
      textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      textW = textPaint.measureText(text);
      textGap = fullH * 0.18f;
    }
    float extraW = showPercent ? textW + textGap : 0f;

    float totalW = Math.min(getWidth() - extraW, maxBatteryW) + gap + chargeIconW + extraW;
    float startX = (getWidth() - totalW) / 2f;
    float batteryW = Math.min(getWidth() - extraW - gap - chargeIconW, maxBatteryW);
    if (showPercent && percentLeft) startX += extraW;

    RectF batteryRect = new RectF(startX, top, startX + batteryW, bottom);
    BatteryPaint.drawCuteBattery(canvas, batteryRect, fillColor, outlineColor, mood, strokeRatio);

    if (showPercent) {
      float textX = percentLeft ? batteryRect.left - textGap - textW
          : batteryRect.right + gap + chargeIconW + textGap;
      float baseline = (top + bottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
      canvas.drawText(text, textX, baseline, textPaint);
    }

    if (charging) {
      RectF chargeRect = new RectF(batteryRect.right + gap, top, batteryRect.right + gap + chargeIconW, bottom);
      if ("cord".equals(chargeIconStyle)) {
        BatteryPaint.drawPlug(canvas, chargeRect, chargeColor);
      } else {
        BatteryPaint.drawBolt(canvas, chargeRect, chargeColor);
      }
    }
  }
}
