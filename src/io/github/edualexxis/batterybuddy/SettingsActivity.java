package io.github.edualexxis.batterybuddy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Companion settings screen. Card-grouped sections, a 2x2 theme preset grid (System / Light /
 * Dark / Custom, with hex fields shown only for Custom), an outline-thickness slider, and a
 * banded range slider for the mood thresholds. No layout XML or external UI library — everything
 * built in code to keep the hand-rolled build pipeline simple.
 */
public class SettingsActivity extends Activity {
  public static final String PREFS_NAME = "batterybuddy_prefs";
  public static final String KEY_HAPPY_MIN = "happy_min";
  public static final String KEY_NEUTRAL_MIN = "neutral_min";
  public static final String KEY_SAD_MIN = "sad_min";
  public static final String KEY_COLOR_MODE = "color_mode"; // "system" | "light" | "dark" | "custom"
  public static final String KEY_CUSTOM_OUTLINE_COLOR = "custom_outline_color";
  public static final String KEY_CUSTOM_FILL_COLOR = "custom_fill_color";
  public static final String KEY_OUTLINE_THICKNESS = "outline_thickness"; // 0-100 slider
  public static final String KEY_CHARGE_ICON_STYLE = "charge_icon_style"; // "original" | "cord"
  public static final String KEY_CHARGE_COLOR_MODE = "charge_color_mode"; // "system" | "light" | "dark" | "custom"
  public static final String KEY_CHARGE_CUSTOM_COLOR = "charge_custom_color";

  public static final int DEFAULT_HAPPY_MIN = 75;
  public static final int DEFAULT_NEUTRAL_MIN = 50;
  public static final int DEFAULT_SAD_MIN = 10;
  public static final String DEFAULT_COLOR_MODE = "system";
  public static final String DEFAULT_CUSTOM_OUTLINE_COLOR = "#000000";
  public static final String DEFAULT_CUSTOM_FILL_COLOR = "#8FBF8A";
  public static final int DEFAULT_OUTLINE_THICKNESS = 42; // maps to the ratio the icon used before
  public static final String DEFAULT_CHARGE_ICON_STYLE = "original";
  public static final String DEFAULT_CHARGE_COLOR_MODE = "system";
  public static final String DEFAULT_CHARGE_CUSTOM_COLOR = "#000000";

  static final int ACCENT = Color.parseColor("#8FBF8A");

  MoodRangeSlider moodSlider;
  TextView happyLabel, neutralLabel, sadLabel;
  TextView segSystem, segLight, segDark, segCustom;
  LinearLayout customColorsBlock;
  TextView segChargeOriginal, segChargeCord;
  String chargeIconStyle;
  TextView segChargeSystem, segChargeLight, segChargeDark, segChargeCustom;
  String chargeColorMode;
  LinearLayout chargeCustomColorBlock;
  EditText chargeColorHex;
  View chargeColorSwatch;
  EditText outlineHex, fillHex;
  View outlineSwatch, fillSwatch;
  SeekBar thicknessSeek;
  TextView thicknessLabel;
  String colorMode;

  BatteryPreviewView previewView;
  TextView previewCaption;

  boolean night;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
        == Configuration.UI_MODE_NIGHT_YES;

    // The system nav bar was rendering as a plain black strip that didn't match the app's own
    // background, looking like it was cutting into the app rather than sitting below it.
    // Make both bars match our own background exactly, and reserve real padding for them so
    // content (e.g. the Save button) never ends up hidden underneath on gesture-nav devices.
    int screenBg = night ? 0xFF121212 : 0xFFF5F5F5;
    getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(screenBg));
    getWindow().setNavigationBarColor(screenBg);
    getWindow().setStatusBarColor(screenBg);

    // Matching the bar background color isn't enough on its own: the bar ICONS (clock, signal,
    // battery) default to white, meant for a dark bar. In light mode that left them white on
    // our light background — invisible. This is the actual fix: tell the system to flip the
    // bar icons to dark whenever our background is light, and back to light icons in dark mode.
    // Deferred to decorView.post(): calling getInsetsController() this early (before the window
    // is actually attached) throws a NullPointerException deep inside PhoneWindow — confirmed
    // the hard way, it crashed on launch — so this waits until the view is attached instead.
    final View decor = getWindow().getDecorView();
    decor.post(() -> {
      try {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
          android.view.WindowInsetsController insetsController = getWindow().getInsetsController();
          if (insetsController != null) {
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            insetsController.setSystemBarsAppearance(night ? 0 : mask, mask);
          }
        } else {
          int flags = decor.getSystemUiVisibility();
          int lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
          decor.setSystemUiVisibility(night ? (flags & ~lightBarFlags) : (flags | lightBarFlags));
        }
      } catch (Throwable ignored) {
        // worst case the bar icons stay the wrong color; never worth crashing the screen over.
      }
    });

    // the stock ActionBar (flat bar with left-aligned title) reads as dated legacy Android
    // chrome next to the rest of this screen's styling — drop it and use our own title instead.
    if (getActionBar() != null) getActionBar().hide();

    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    int happyMin = prefs.getInt(KEY_HAPPY_MIN, DEFAULT_HAPPY_MIN);
    int neutralMin = prefs.getInt(KEY_NEUTRAL_MIN, DEFAULT_NEUTRAL_MIN);
    int sadMin = prefs.getInt(KEY_SAD_MIN, DEFAULT_SAD_MIN);
    colorMode = prefs.getString(KEY_COLOR_MODE, DEFAULT_COLOR_MODE);
    String outlineColor = prefs.getString(KEY_CUSTOM_OUTLINE_COLOR, DEFAULT_CUSTOM_OUTLINE_COLOR);
    String fillColor = prefs.getString(KEY_CUSTOM_FILL_COLOR, DEFAULT_CUSTOM_FILL_COLOR);
    int thickness = prefs.getInt(KEY_OUTLINE_THICKNESS, DEFAULT_OUTLINE_THICKNESS);
    chargeIconStyle = prefs.getString(KEY_CHARGE_ICON_STYLE, DEFAULT_CHARGE_ICON_STYLE);
    chargeColorMode = prefs.getString(KEY_CHARGE_COLOR_MODE, DEFAULT_CHARGE_COLOR_MODE);
    String chargeCustomColor = prefs.getString(KEY_CHARGE_CUSTOM_COLOR, DEFAULT_CHARGE_CUSTOM_COLOR);

    ScrollView scroll = new ScrollView(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(20);
    root.setPadding(pad, dp(32), pad, dp(32));
    scroll.addView(root);

    // reserve real space for the nav bar at the bottom (and status bar at the top, just in
    // case) so the Save button is never hidden underneath it, on any nav bar style.
    scroll.setOnApplyWindowInsetsListener((v, insets) -> {
      int bottom = insets.getSystemWindowInsetBottom();
      int top = insets.getSystemWindowInsetTop();
      v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
      return insets;
    });

    TextView title = new TextView(this);
    title.setText("BatteryBuddy");
    title.setTextSize(30);
    title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
    root.addView(title);

    TextView subtitle = new TextView(this);
    subtitle.setText("Battery mood faces for the status bar");
    subtitle.setTextSize(14);
    subtitle.setAlpha(0.6f);
    subtitle.setPadding(0, dp(4), 0, dp(28));
    root.addView(subtitle);

    // --- Preview card ---
    LinearLayout previewCard = card();
    previewCard.addView(sectionTitle("Preview"));
    previewCard.addView(sectionSubtitle("Shows your current battery, live, with the settings below applied."));

    previewView = new BatteryPreviewView(this);
    LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    previewLp.topMargin = dp(16);
    previewView.setLayoutParams(previewLp);
    previewCard.addView(previewView);

    previewCaption = new TextView(this);
    previewCaption.setTextSize(13);
    previewCaption.setAlpha(0.6f);
    previewCaption.setGravity(Gravity.CENTER);
    previewCaption.setPadding(0, dp(10), 0, 0);
    previewCard.addView(previewCaption);

    root.addView(previewCard);

    // --- Theme card ---
    LinearLayout themeCard = card();
    themeCard.addView(sectionTitle("Theme"));
    themeCard.addView(sectionSubtitle("Pick how the outline and fill color are chosen."));

    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(2);
    grid.setRowCount(2);
    LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    gridLp.topMargin = dp(12);
    grid.setLayoutParams(gridLp);

    segSystem = segmentButton("System");
    segLight = segmentButton("Light");
    segDark = segmentButton("Dark");
    segCustom = segmentButton("Custom");
    addToGrid(grid, segSystem, 0, 0);
    addToGrid(grid, segLight, 0, 1);
    addToGrid(grid, segDark, 1, 0);
    addToGrid(grid, segCustom, 1, 1);
    themeCard.addView(grid);

    segSystem.setOnClickListener(v -> { colorMode = "system"; updateSegmented(); updatePreview(); });
    segLight.setOnClickListener(v -> { colorMode = "light"; updateSegmented(); updatePreview(); });
    segDark.setOnClickListener(v -> { colorMode = "dark"; updateSegmented(); updatePreview(); });
    segCustom.setOnClickListener(v -> { colorMode = "custom"; updateSegmented(); updatePreview(); });

    // custom color fields, shown only when Custom is selected
    customColorsBlock = new LinearLayout(this);
    customColorsBlock.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    ccLp.topMargin = dp(14);
    customColorsBlock.setLayoutParams(ccLp);

    LinearLayout outlineRow = new LinearLayout(this);
    outlineRow.setOrientation(LinearLayout.HORIZONTAL);
    outlineRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView outlineLbl = rowLabel("Outline");
    outlineHex = hexField(outlineColor);
    outlineSwatch = swatch();
    outlineRow.addView(outlineLbl);
    outlineRow.addView(outlineHex);
    outlineRow.addView(outlineSwatch);
    customColorsBlock.addView(outlineRow);
    applySwatch(outlineSwatch, outlineColor);
    outlineHex.addTextChangedListener(swatchUpdater(outlineSwatch));
    outlineHex.addTextChangedListener(previewUpdater());

    LinearLayout fillRow = new LinearLayout(this);
    fillRow.setOrientation(LinearLayout.HORIZONTAL);
    fillRow.setGravity(Gravity.CENTER_VERTICAL);
    fillRow.setPadding(0, dp(10), 0, 0);
    TextView fillLbl = rowLabel("Fill");
    fillHex = hexField(fillColor);
    fillSwatch = swatch();
    fillRow.addView(fillLbl);
    fillRow.addView(fillHex);
    fillRow.addView(fillSwatch);
    customColorsBlock.addView(fillRow);
    applySwatch(fillSwatch, fillColor);
    fillHex.addTextChangedListener(swatchUpdater(fillSwatch));
    fillHex.addTextChangedListener(previewUpdater());

    themeCard.addView(customColorsBlock);
    updateSegmented();

    // outline thickness, independent of color mode
    TextView thicknessTitle = new TextView(this);
    thicknessTitle.setText("Outline thickness");
    thicknessTitle.setTextSize(14);
    thicknessTitle.setPadding(0, dp(20), 0, dp(2));
    themeCard.addView(thicknessTitle);
    thicknessLabel = sectionSubtitle("");
    themeCard.addView(thicknessLabel);
    thicknessSeek = new SeekBar(this);
    thicknessSeek.setMax(100);
    thicknessSeek.setProgress(thickness);
    themeCard.addView(thicknessSeek);
    thicknessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar s, int p, boolean u) { updateThicknessLabel(); updatePreview(); }
      @Override public void onStartTrackingTouch(SeekBar s) {}
      @Override public void onStopTrackingTouch(SeekBar s) {}
    });
    updateThicknessLabel();

    root.addView(themeCard);

    // --- Charging icon card ---
    LinearLayout chargeCard = card();
    chargeCard.addView(sectionTitle("Charging icon"));
    chargeCard.addView(sectionSubtitle("ColorOS's own charging indicator next to the battery "
        + "icon — keep its default bolt, or replace it with a cord."));

    LinearLayout chargeSeg = new LinearLayout(this);
    chargeSeg.setOrientation(LinearLayout.HORIZONTAL);
    GradientDrawable chargeSegBg = new GradientDrawable();
    chargeSegBg.setCornerRadius(dp(14));
    chargeSegBg.setColor(overlayColor(0.06f));
    chargeSeg.setBackground(chargeSegBg);
    chargeSeg.setPadding(dp(4), dp(4), dp(4), dp(4));
    LinearLayout.LayoutParams chargeSegLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    chargeSegLp.topMargin = dp(12);
    chargeSeg.setLayoutParams(chargeSegLp);

    segChargeOriginal = segmentButton("Original");
    segChargeCord = segmentButton("Cord");
    chargeSeg.addView(segChargeOriginal, new LinearLayout.LayoutParams(0, dp(40), 1f));
    chargeSeg.addView(segChargeCord, new LinearLayout.LayoutParams(0, dp(40), 1f));
    chargeCard.addView(chargeSeg);

    segChargeOriginal.setOnClickListener(v -> { chargeIconStyle = "original"; updateChargeSegmented(); updatePreview(); });
    segChargeCord.setOnClickListener(v -> { chargeIconStyle = "cord"; updateChargeSegmented(); updatePreview(); });
    updateChargeSegmented();

    // charging icon's own color — independent of the battery's Theme card, so e.g. the battery
    // can stay System while the charge icon is forced to a fixed color, or vice versa. Applies
    // to "Original" too (tints ColorOS's own bolt) when not System, not just "Cord".
    TextView chargeColorTitle = new TextView(this);
    chargeColorTitle.setText("Color");
    chargeColorTitle.setTextSize(14);
    chargeColorTitle.setPadding(0, dp(20), 0, dp(2));
    chargeCard.addView(chargeColorTitle);
    chargeCard.addView(sectionSubtitle("Applies to either style above, including tinting the "
        + "Original bolt."));

    GridLayout chargeColorGrid = new GridLayout(this);
    chargeColorGrid.setColumnCount(2);
    chargeColorGrid.setRowCount(2);
    LinearLayout.LayoutParams chargeColorGridLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    chargeColorGridLp.topMargin = dp(12);
    chargeColorGrid.setLayoutParams(chargeColorGridLp);

    segChargeSystem = segmentButton("System");
    segChargeLight = segmentButton("Light");
    segChargeDark = segmentButton("Dark");
    segChargeCustom = segmentButton("Custom");
    addToGrid(chargeColorGrid, segChargeSystem, 0, 0);
    addToGrid(chargeColorGrid, segChargeLight, 0, 1);
    addToGrid(chargeColorGrid, segChargeDark, 1, 0);
    addToGrid(chargeColorGrid, segChargeCustom, 1, 1);
    chargeCard.addView(chargeColorGrid);

    segChargeSystem.setOnClickListener(v -> { chargeColorMode = "system"; updateChargeColorSegmented(); updatePreview(); });
    segChargeLight.setOnClickListener(v -> { chargeColorMode = "light"; updateChargeColorSegmented(); updatePreview(); });
    segChargeDark.setOnClickListener(v -> { chargeColorMode = "dark"; updateChargeColorSegmented(); updatePreview(); });
    segChargeCustom.setOnClickListener(v -> { chargeColorMode = "custom"; updateChargeColorSegmented(); updatePreview(); });

    chargeCustomColorBlock = new LinearLayout(this);
    chargeCustomColorBlock.setOrientation(LinearLayout.HORIZONTAL);
    chargeCustomColorBlock.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams ccLp2 = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    ccLp2.topMargin = dp(14);
    chargeCustomColorBlock.setLayoutParams(ccLp2);

    TextView chargeColorLbl = rowLabel("Color");
    chargeColorHex = hexField(chargeCustomColor);
    chargeColorSwatch = swatch();
    chargeCustomColorBlock.addView(chargeColorLbl);
    chargeCustomColorBlock.addView(chargeColorHex);
    chargeCustomColorBlock.addView(chargeColorSwatch);
    chargeCard.addView(chargeCustomColorBlock);
    applySwatch(chargeColorSwatch, chargeCustomColor);
    chargeColorHex.addTextChangedListener(swatchUpdater(chargeColorSwatch));
    chargeColorHex.addTextChangedListener(previewUpdater());

    updateChargeColorSegmented();

    root.addView(chargeCard);

    // --- Mood thresholds card ---
    LinearLayout moodCard = card();
    moodCard.addView(sectionTitle("Mood thresholds"));
    moodCard.addView(sectionSubtitle("Drag each handle. Below the sad threshold (but above 0%) "
        + "shows dead X eyes. While charging the face is always happy."));

    moodSlider = new MoodRangeSlider(this);
    moodSlider.setValues(happyMin, neutralMin, sadMin);
    LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    sliderLp.topMargin = dp(20);
    moodSlider.setLayoutParams(sliderLp);
    moodCard.addView(moodSlider);

    happyLabel = fieldLabel();
    neutralLabel = fieldLabel();
    sadLabel = fieldLabel();
    LinearLayout.LayoutParams labelTop = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    labelTop.topMargin = dp(14);
    happyLabel.setLayoutParams(labelTop);
    moodCard.addView(happyLabel);
    moodCard.addView(neutralLabel);
    moodCard.addView(sadLabel);

    moodSlider.setListener((happy, neutral, sad) -> { updateMoodLabels(happy, neutral, sad); updatePreview(); });
    updateMoodLabels(happyMin, neutralMin, sadMin);

    root.addView(moodCard);

    updatePreview();

    // --- disclaimer ---
    TextView disclaimer = new TextView(this);
    disclaimer.setText("Changes apply automatically within a few seconds, or immediately if "
        + "you restart System UI after saving.");
    disclaimer.setTextSize(12);
    disclaimer.setAlpha(0.55f);
    disclaimer.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams discLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    discLp.topMargin = dp(20);
    disclaimer.setLayoutParams(discLp);
    root.addView(disclaimer);

    // --- Save ---
    TextView save = new TextView(this);
    save.setText("Save");
    save.setTextSize(16);
    save.setTypeface(save.getTypeface(), android.graphics.Typeface.BOLD);
    save.setTextColor(bestOn(ACCENT));
    save.setGravity(Gravity.CENTER);
    GradientDrawable saveBg = new GradientDrawable();
    saveBg.setCornerRadius(dp(14));
    saveBg.setColor(ACCENT);
    save.setBackground(saveBg);
    LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
    saveLp.topMargin = dp(24);
    save.setLayoutParams(saveLp);
    save.setOnClickListener(v -> save());
    root.addView(save);

    setContentView(scroll);
  }

  // ---- styled building blocks ----

  LinearLayout card() {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(20));
    bg.setColor(overlayColor(0.045f));
    card.setBackground(bg);
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(14);
    card.setLayoutParams(lp);
    return card;
  }

  TextView sectionTitle(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextSize(17);
    t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
    return t;
  }

  TextView sectionSubtitle(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextSize(13);
    t.setAlpha(0.6f);
    t.setPadding(0, dp(4), 0, dp(4));
    return t;
  }

  TextView fieldLabel() {
    TextView t = new TextView(this);
    t.setTextSize(13);
    t.setAlpha(0.85f);
    return t;
  }

  TextView rowLabel(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setTextSize(14);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    t.setLayoutParams(lp);
    return t;
  }

  TextView segmentButton(String text) {
    TextView t = new TextView(this);
    t.setText(text);
    t.setGravity(Gravity.CENTER);
    t.setTextSize(14);
    t.setClickable(true);
    t.setFocusable(true);
    return t;
  }

  void addToGrid(GridLayout grid, View v, int row, int col) {
    GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
        GridLayout.spec(row, 1f), GridLayout.spec(col, 1f));
    lp.width = 0;
    lp.height = dp(44);
    lp.setMargins(dp(3), dp(3), dp(3), dp(3));
    grid.addView(v, lp);
  }

  void updateSegmented() {
    style(segSystem, "system".equals(colorMode));
    style(segLight, "light".equals(colorMode));
    style(segDark, "dark".equals(colorMode));
    style(segCustom, "custom".equals(colorMode));
    customColorsBlock.setVisibility("custom".equals(colorMode) ? View.VISIBLE : View.GONE);
  }

  void updateChargeSegmented() {
    style(segChargeOriginal, "original".equals(chargeIconStyle));
    style(segChargeCord, "cord".equals(chargeIconStyle));
  }

  void updateChargeColorSegmented() {
    style(segChargeSystem, "system".equals(chargeColorMode));
    style(segChargeLight, "light".equals(chargeColorMode));
    style(segChargeDark, "dark".equals(chargeColorMode));
    style(segChargeCustom, "custom".equals(chargeColorMode));
    chargeCustomColorBlock.setVisibility("custom".equals(chargeColorMode) ? View.VISIBLE : View.GONE);
  }

  /** Mirrors resolveColors' outline logic, but independent of the battery's own color mode. */
  int resolveChargeColor(String mode) {
    switch (mode) {
      case "light": return BatteryPaint.LIGHT_OUTLINE;
      case "dark": return BatteryPaint.DARK_OUTLINE;
      case "custom": return parseColorOr(chargeColorHex.getText().toString(), BatteryPaint.LIGHT_OUTLINE);
      default: return night ? BatteryPaint.DARK_OUTLINE : BatteryPaint.LIGHT_OUTLINE;
    }
  }

  void style(TextView seg, boolean selected) {
    if (selected) {
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(11));
      bg.setColor(ACCENT);
      seg.setBackground(bg);
      seg.setTextColor(bestOn(ACCENT));
      seg.setTypeface(seg.getTypeface(), android.graphics.Typeface.BOLD);
      seg.setAlpha(1f);
    } else {
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(11));
      bg.setColor(overlayColor(0.06f));
      seg.setBackground(bg);
      seg.setTextColor(currentTextColor());
      seg.setTypeface(seg.getTypeface(), android.graphics.Typeface.NORMAL);
      seg.setAlpha(0.75f);
    }
  }

  EditText hexField(String value) {
    EditText e = new EditText(this);
    e.setText(value);
    e.setHint("#RRGGBB");
    e.setTextSize(14);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMarginStart(dp(8));
    e.setLayoutParams(lp);
    return e;
  }

  View swatch() {
    View v = new View(this);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
    lp.setMarginStart(dp(8));
    v.setLayoutParams(lp);
    return v;
  }

  void applySwatch(View swatch, String hex) {
    int color;
    try { color = Color.parseColor(hex); } catch (Throwable t) { color = Color.GRAY; }
    GradientDrawable gd = new GradientDrawable();
    gd.setShape(GradientDrawable.OVAL);
    gd.setColor(color);
    gd.setStroke(dp(1), Color.DKGRAY);
    swatch.setBackground(gd);
  }

  TextWatcher swatchUpdater(View swatch) {
    return new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void afterTextChanged(Editable s) { applySwatch(swatch, s.toString()); }
    };
  }

  void updateThicknessLabel() {
    thicknessLabel.setText(thicknessSeek.getProgress() + "%");
  }

  TextWatcher previewUpdater() {
    return new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void afterTextChanged(Editable s) { updatePreview(); }
    };
  }

  /** Resolves {outlineColor, fillColor} for the currently selected (not-yet-saved) theme mode. */
  int[] resolveColors(String mode) {
    switch (mode) {
      case "light": return new int[]{BatteryPaint.LIGHT_OUTLINE, BatteryPaint.LIGHT_FILL};
      case "dark": return new int[]{BatteryPaint.DARK_OUTLINE, BatteryPaint.DARK_FILL};
      case "custom":
        int outline = parseColorOr(outlineHex.getText().toString(), BatteryPaint.LIGHT_OUTLINE);
        int fill = parseColorOr(fillHex.getText().toString(), BatteryPaint.LIGHT_FILL);
        return new int[]{outline, fill};
      default: // "system" — the real theme color only exists inside SystemUI; approximate it here
        return night ? new int[]{BatteryPaint.DARK_OUTLINE, BatteryPaint.DARK_FILL} : new int[]{BatteryPaint.LIGHT_OUTLINE, BatteryPaint.LIGHT_FILL};
    }
  }

  int parseColorOr(String hex, int fallback) {
    try { return Color.parseColor(hex); } catch (Throwable t) { return fallback; }
  }

  /** Reads the device's real, live battery state — no root needed, standard Android API. */
  int[] readRealBattery() {
    try {
      Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 50);
      int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
      int pct = Math.round(level * 100f / Math.max(1, scale));
      int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
      boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
          || status == BatteryManager.BATTERY_STATUS_FULL;
      return new int[]{pct, charging ? 1 : 0};
    } catch (Throwable t) {
      return new int[]{50, 0};
    }
  }

  void updatePreview() {
    if (previewView == null) return; // called before onCreate finishes building the view tree
    int[] battery = readRealBattery();
    int pct = battery[0];
    boolean charging = battery[1] != 0;

    int happy = moodSlider.getHappy(), neutral = moodSlider.getNeutral(), sad = moodSlider.getSad();
    BatteryPaint.Mood mood;
    if (charging) mood = BatteryPaint.Mood.HAPPY;
    else if (pct >= happy) mood = BatteryPaint.Mood.HAPPY;
    else if (pct >= neutral) mood = BatteryPaint.Mood.NEUTRAL;
    else if (pct >= sad) mood = BatteryPaint.Mood.SAD;
    else mood = BatteryPaint.Mood.DEAD;

    int[] colors = resolveColors(colorMode); // {outline, fill}
    float ratio = 0.06f + (thicknessSeek.getProgress() / 100f) * 0.24f;
    int chargeColor = resolveChargeColor(chargeColorMode);
    previewView.update(colors[1], colors[0], ratio, mood, charging, chargeIconStyle, chargeColor);

    String moodName = mood.name().charAt(0) + mood.name().substring(1).toLowerCase();
    previewCaption.setText(pct + "%" + (charging ? " · Charging" : "") + " · " + moodName);
  }

  void updateMoodLabels(int happy, int neutral, int sad) {
    happyLabel.setText("● Happy  " + happy + "–100%");
    neutralLabel.setText("● Neutral  " + neutral + "–" + (happy - 1) + "%");
    sadLabel.setText("● Sad  " + sad + "–" + (neutral - 1) + "%   ·   Dead  1–" + (sad - 1) + "%");
  }

  void save() {
    int happy = moodSlider.getHappy();
    int neutral = moodSlider.getNeutral();
    int sad = moodSlider.getSad();

    String outlineColor = normalizeOrFallback(outlineHex.getText().toString(), DEFAULT_CUSTOM_OUTLINE_COLOR);
    String fillColor = normalizeOrFallback(fillHex.getText().toString(), DEFAULT_CUSTOM_FILL_COLOR);
    String chargeCustomColor = normalizeOrFallback(chargeColorHex.getText().toString(), DEFAULT_CHARGE_CUSTOM_COLOR);

    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    prefs.edit()
        .putInt(KEY_HAPPY_MIN, happy)
        .putInt(KEY_NEUTRAL_MIN, neutral)
        .putInt(KEY_SAD_MIN, sad)
        .putString(KEY_COLOR_MODE, colorMode)
        .putString(KEY_CUSTOM_OUTLINE_COLOR, outlineColor)
        .putString(KEY_CUSTOM_FILL_COLOR, fillColor)
        .putInt(KEY_OUTLINE_THICKNESS, thicknessSeek.getProgress())
        .putString(KEY_CHARGE_ICON_STYLE, chargeIconStyle)
        .putString(KEY_CHARGE_COLOR_MODE, chargeColorMode)
        .putString(KEY_CHARGE_CUSTOM_COLOR, chargeCustomColor)
        .commit();

    new AlertDialog.Builder(this)
        .setTitle("Saved")
        .setMessage("Changes apply automatically within a few seconds, or you can restart "
            + "System UI now to see them immediately.")
        .setNegativeButton("Wait", (d, w) -> d.dismiss())
        .setPositiveButton("Restart System UI", (d, w) -> restartSystemUI())
        .show();
  }

  String normalizeOrFallback(String hex, String fallback) {
    try { Color.parseColor(hex); return hex; } catch (Throwable t) { return fallback; }
  }

  /** Needs root (su) — same KernelSU/Vector session the module already runs under. */
  void restartSystemUI() {
    new Thread(() -> {
      String resultMsg;
      try {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
        int code = p.waitFor();
        resultMsg = (code == 0) ? "System UI restarted." : "Couldn't restart (root denied?). Restart it manually.";
      } catch (Throwable t) {
        resultMsg = "Couldn't get root access. Restart System UI manually.";
      }
      final String msg = resultMsg;
      runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }).start();
  }

  // ---- color helpers: subtle theme-aware card tint without any UI library ----

  int overlayColor(float alpha) {
    int base = night ? Color.WHITE : Color.BLACK;
    int a = Math.round(255 * alpha);
    return Color.argb(a, Color.red(base), Color.green(base), Color.blue(base));
  }

  int currentTextColor() {
    return night ? Color.WHITE : Color.BLACK;
  }

  int bestOn(int bgColor) {
    double luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255;
    return luminance > 0.6 ? Color.BLACK : Color.WHITE;
  }

  int dp(int v) {
    return (int) (v * getResources().getDisplayMetrics().density);
  }
}
