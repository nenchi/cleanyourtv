package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * The latest reading for the home screen, read-only: the WMO code, day or night, the temperature as the page shows
 * it, the words for the sky and the place. Readable only by apps signed with the same key (READ_NOW, signature).
 *
 * A query never waits for the network: it answers with what is stored, and when that is older than
 * {@link Forecast#REFRESH_MS} (and no try ran in the last {@link Forecast#RETRY_MS}) it starts one fetch on its own
 * thread and notifies {@link #URI} when the new reading is stored. So the weather is fetched only while something
 * on the TV asks for it - the Home screen when it is shown - and at most every 30 minutes.
 */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/now");
    private static final String PREFS = "now";
    private static final String[] COLUMNS = {"code", "day", "temp_text", "sky", "place", "updated_ms"};
    private static volatile boolean fetching;

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Stores a reading and tells readers; called by the page after each fetch and by this provider. */
    static void save(Context ctx, Forecast.Report r) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("code", r.code)
                .putBoolean("day", r.day)
                .putLong("temp", Double.doubleToRawLongBits(r.tempC))
                .putLong("updated", System.currentTimeMillis())
                .apply();
        ctx.getContentResolver().notifyChange(URI, null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context ctx = getContext();
        if (ctx == null) return null;
        MatrixCursor out = new MatrixCursor(COLUMNS, 1);
        SharedPreferences place = ctx.getSharedPreferences("place", Context.MODE_PRIVATE);
        if (!place.contains("name")) return out;   // no city chosen yet: nothing to fetch or show
        SharedPreferences now = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long updated = now.getLong("updated", 0);
        long nowMs = System.currentTimeMillis();
        if (nowMs - updated > Forecast.REFRESH_MS && nowMs - now.getLong("tried", 0) > Forecast.RETRY_MS) {
            now.edit().putLong("tried", nowMs).apply();
            fetchInBackground(ctx.getApplicationContext(), place);
        }
        // A reading older than three hours says nothing about now: leave the home screen plain instead.
        if (updated == 0 || nowMs - updated > 3 * 60 * 60_000L) return out;
        int code = now.getInt("code", 0);
        boolean day = now.getBoolean("day", true);
        boolean fahrenheit = ctx.getSharedPreferences("options", Context.MODE_PRIVATE).getBoolean("fahrenheit",
                "f".equalsIgnoreCase(ctx.getString(R.string.temperature_unit).trim()));
        double tempC = Double.longBitsToDouble(now.getLong("temp", 0));
        out.addRow(new Object[]{code, day ? 1 : 0, Forecast.degrees(tempC, fahrenheit) + "°",
                ctx.getString(Forecast.sky(code, day)), place.getString("name", ""), updated});
        return out;
    }

    private static void fetchInBackground(Context app, SharedPreferences place) {
        if (fetching) return;
        fetching = true;
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        new Thread(() -> {
            try {
                save(app, Forecast.fetch(lat, lon, Forecast.MIN_DAYS));
            } catch (Exception ignored) {
                // offline: the next query after RETRY_MS tries again
            } finally {
                fetching = false;
            }
        }, "weather-now").start();
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }
}
