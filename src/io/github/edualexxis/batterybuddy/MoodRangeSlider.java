package io.github.edualexxis.batterybuddy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * A single banded range bar with 3 draggable handles (sad|neutral|happy thresholds), replacing
 * three separate 0-100 sliders. Shows the four mood zones as colored segments so the bands are
 * visible at a glance, and each handle is confined between its neighbors so it can't cross them.
 */
public class MoodRangeSlider extends View {
  public interface Listener { void onChanged(int happy, int neutral, int sad); }

  static final int COLOR_HAPPY = Color.parseColor("#8FBF8A");
  static final int COLOR_NEUTRAL = Color.parseColor("#F2C94C");
  static final int COLOR_SAD = Color.parseColor("#F2994A");
  static final int COLOR_DEAD = Color.parseColor("#D9534F");

  int happy = 75, neutral = 50, sad = 10;
  Listener listener;

  final float density;
  final float trackHeight, handleRadius, sidePad, touchSlopPx;
  int activeHandle = -1; // 0 = sad, 1 = neutral, 2 = happy

  final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint handleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path clipPath = new Path();

  public MoodRangeSlider(Context ctx) {
    super(ctx);
    density = ctx.getResources().getDisplayMetrics().density;
    trackHeight = 14 * density;
    handleRadius = 15 * density;
    sidePad = handleRadius;
    touchSlopPx = 28 * density;
    labelPaint.setTextSize(12 * density);
    labelPaint.setTextAlign(Paint.Align.CENTER);
    handleFill.setColor(Color.WHITE);
    handleBorder.setStyle(Paint.Style.STROKE);
    handleBorder.setStrokeWidth(3.5f * density);
    setClickable(true);
  }

  public void setValues(int happy, int neutral, int sad) {
    this.happy = happy; this.neutral = neutral; this.sad = sad;
    invalidate();
  }

  public void setListener(Listener l) { this.listener = l; }
  public int getHappy() { return happy; }
  public int getNeutral() { return neutral; }
  public int getSad() { return sad; }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    int w = MeasureSpec.getSize(widthSpec);
    int h = (int) (64 * density);
    setMeasuredDimension(w, h);
  }

  float usableWidth() { return Math.max(1f, getWidth() - 2 * sidePad); }
  float xFor(int value) { return sidePad + usableWidth() * (value / 100f); }
  int valueForX(float x) {
    float v = (x - sidePad) / usableWidth() * 100f;
    return Math.max(0, Math.min(100, Math.round(v)));
  }

  @Override protected void onDraw(Canvas canvas) {
    float cy = getHeight() * 0.42f;
    float top = cy - trackHeight / 2f, bottom = cy + trackHeight / 2f;
    float left = sidePad, right = getWidth() - sidePad;

    canvas.save();
    clipPath.reset();
    clipPath.addRoundRect(new RectF(left, top, right, bottom), trackHeight / 2f, trackHeight / 2f, Path.Direction.CW);
    canvas.clipPath(clipPath);
    drawSegment(canvas, top, bottom, 0, sad, COLOR_DEAD);
    drawSegment(canvas, top, bottom, sad, neutral, COLOR_SAD);
    drawSegment(canvas, top, bottom, neutral, happy, COLOR_NEUTRAL);
    drawSegment(canvas, top, bottom, happy, 100, COLOR_HAPPY);
    canvas.restore();

    drawHandle(canvas, sad, cy, COLOR_SAD);
    drawHandle(canvas, neutral, cy, COLOR_NEUTRAL);
    drawHandle(canvas, happy, cy, COLOR_HAPPY);
  }

  void drawSegment(Canvas canvas, float top, float bottom, int from, int to, int color) {
    trackPaint.setColor(color);
    canvas.drawRect(xFor(from), top, xFor(to), bottom, trackPaint);
  }

  void drawHandle(Canvas canvas, int value, float cy, int color) {
    float x = xFor(value);
    canvas.drawCircle(x, cy, handleRadius, handleFill);
    handleBorder.setColor(color);
    canvas.drawCircle(x, cy, handleRadius - handleBorder.getStrokeWidth() / 2f, handleBorder);
    labelPaint.setColor(color);
    canvas.drawText(value + "%", x, cy + handleRadius + labelPaint.getTextSize() + 4 * density, labelPaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        activeHandle = nearestHandle(event.getX());
        getParent().requestDisallowInterceptTouchEvent(true);
        if (activeHandle != -1) { moveActive(event.getX()); return true; }
        return false;
      case MotionEvent.ACTION_MOVE:
        if (activeHandle != -1) { moveActive(event.getX()); return true; }
        return false;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        activeHandle = -1;
        getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    return super.onTouchEvent(event);
  }

  int nearestHandle(float x) {
    float dSad = Math.abs(x - xFor(sad));
    float dNeutral = Math.abs(x - xFor(neutral));
    float dHappy = Math.abs(x - xFor(happy));
    float min = Math.min(dSad, Math.min(dNeutral, dHappy));
    if (min > touchSlopPx) return -1;
    if (min == dSad) return 0;
    if (min == dNeutral) return 1;
    return 2;
  }

  void moveActive(float x) {
    int v = valueForX(x);
    switch (activeHandle) {
      case 0: sad = clamp(v, 1, neutral - 1); break;
      case 1: neutral = clamp(v, sad + 1, happy - 1); break;
      case 2: happy = clamp(v, neutral + 1, 100); break;
    }
    invalidate();
    if (listener != null) listener.onChanged(happy, neutral, sad);
  }

  static int clamp(int v, int lo, int hi) {
    if (lo > hi) return lo; // bands collapsed to minimum gap; keep handle pinned
    return Math.max(lo, Math.min(hi, v));
  }
}
