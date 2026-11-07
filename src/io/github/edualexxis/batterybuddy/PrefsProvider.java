package io.github.edualexxis.batterybuddy;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Exposes our settings to SystemUI's hook over Binder instead of a raw cross-app file read.
 * SELinux Enforcing blocks SystemUI from reading our shared_prefs file directly (DAC bits alone
 * aren't enough on this ROM — see Docs), so the Hook queries this provider instead. The provider
 * runs in our own process, where reading our own SharedPreferences is always allowed; only the
 * already-read values cross the process boundary, over the normal ContentResolver IPC path.
 */
public class PrefsProvider extends ContentProvider {
  public static final String AUTHORITY = "io.github.edualexxis.batterybuddy.prefs";
  public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/settings");

  @Override public boolean onCreate() { return true; }

  @Override public Cursor query(Uri uri, String[] projection, String selection,
                                 String[] selectionArgs, String sortOrder) {
    SharedPreferences prefs = getContext().getSharedPreferences(
        SettingsActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);

    String[] columns = {
        SettingsActivity.KEY_HAPPY_MIN, SettingsActivity.KEY_NEUTRAL_MIN, SettingsActivity.KEY_SAD_MIN,
        SettingsActivity.KEY_COLOR_MODE, SettingsActivity.KEY_CUSTOM_OUTLINE_COLOR,
        SettingsActivity.KEY_CUSTOM_FILL_COLOR, SettingsActivity.KEY_OUTLINE_THICKNESS,
        SettingsActivity.KEY_CHARGE_ICON_STYLE, SettingsActivity.KEY_CHARGE_COLOR_MODE,
        SettingsActivity.KEY_CHARGE_CUSTOM_COLOR, SettingsActivity.KEY_SHOW_PERCENT,
        SettingsActivity.KEY_PERCENT_SIDE
    };
    MatrixCursor cursor = new MatrixCursor(columns, 1);
    cursor.addRow(new Object[]{
        prefs.getInt(SettingsActivity.KEY_HAPPY_MIN, SettingsActivity.DEFAULT_HAPPY_MIN),
        prefs.getInt(SettingsActivity.KEY_NEUTRAL_MIN, SettingsActivity.DEFAULT_NEUTRAL_MIN),
        prefs.getInt(SettingsActivity.KEY_SAD_MIN, SettingsActivity.DEFAULT_SAD_MIN),
        prefs.getString(SettingsActivity.KEY_COLOR_MODE, SettingsActivity.DEFAULT_COLOR_MODE),
        prefs.getString(SettingsActivity.KEY_CUSTOM_OUTLINE_COLOR, SettingsActivity.DEFAULT_CUSTOM_OUTLINE_COLOR),
        prefs.getString(SettingsActivity.KEY_CUSTOM_FILL_COLOR, SettingsActivity.DEFAULT_CUSTOM_FILL_COLOR),
        prefs.getInt(SettingsActivity.KEY_OUTLINE_THICKNESS, SettingsActivity.DEFAULT_OUTLINE_THICKNESS),
        prefs.getString(SettingsActivity.KEY_CHARGE_ICON_STYLE, SettingsActivity.DEFAULT_CHARGE_ICON_STYLE),
        prefs.getString(SettingsActivity.KEY_CHARGE_COLOR_MODE, SettingsActivity.DEFAULT_CHARGE_COLOR_MODE),
        prefs.getString(SettingsActivity.KEY_CHARGE_CUSTOM_COLOR, SettingsActivity.DEFAULT_CHARGE_CUSTOM_COLOR),
        prefs.getBoolean(SettingsActivity.KEY_SHOW_PERCENT, SettingsActivity.DEFAULT_SHOW_PERCENT) ? 1 : 0,
        prefs.getString(SettingsActivity.KEY_PERCENT_SIDE, SettingsActivity.DEFAULT_PERCENT_SIDE)
    });
    return cursor;
  }

  @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.batterybuddy.settings"; }
  @Override public Uri insert(Uri uri, ContentValues values) { return null; }
  @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
  @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
