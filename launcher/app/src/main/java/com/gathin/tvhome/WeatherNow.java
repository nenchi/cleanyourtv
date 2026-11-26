package com.gathin.tvhome;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local change: the latest weather reading from the Weather add-on's provider (config.xml weather_provider), for
 * the weather-tinted sky and the line under the date. The launcher itself still has no network access: the add-on
 * fetches, and only while Home asks (see the add-on's NowProvider). Queried off the UI thread on each return to
 * Home and once a minute, and again whenever the add-on stores a new reading.
 */
final class WeatherNow {
    private static final String TAG = "TvHome.Weather";

    /** One reading: the WMO code, day or night, and the line to show ("4° · Light rain"). */
    static final class Reading {
        final int code;
        final boolean day;
        final String line;

        Reading(int code, boolean day, String line) {
            this.code = code;
            this.day = day;
            this.line = line;
        }
    }

    interface Listener {
        /** On the UI thread; null when there is no fresh reading (no add-on, no city, offline for hours). */
        void onWeather(Reading reading);
    }

    private final Context ctx;
    private final Uri uri;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;

    /** Null when weather_provider is empty (the default): the feature is off. */
    static WeatherNow create(Context ctx, Listener listener) {
        String authority = ctx.getString(R.string.weather_provider).trim();
        return authority.isEmpty() ? null : new WeatherNow(ctx, authority, listener);
    }

    private WeatherNow(Context ctx, String authority, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.uri = Uri.parse("content://" + authority + "/now");
        this.listener = listener;
        this.observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                refresh();
            }
        };
    }

    /** Home is in front: watch for new readings and ask for the current one. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                Log.w(TAG, "cannot watch " + uri + ": " + e.getMessage());
            }
        }
        refresh();
    }

    void stop() {
        if (!watching) return;
        ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    /** Cheap: the provider answers from its own storage and fetches by itself when its reading is old. */
    void refresh() {
        io.execute(() -> {
            Reading reading = null;
            try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int code = c.getInt(c.getColumnIndexOrThrow("code"));
                    boolean day = c.getInt(c.getColumnIndexOrThrow("day")) == 1;
                    String temp = c.getString(c.getColumnIndexOrThrow("temp_text"));
                    String sky = c.getString(c.getColumnIndexOrThrow("sky"));
                    reading = new Reading(code, day, temp + "  ·  " + sky);
                }
            } catch (RuntimeException e) {   // add-on missing, permission not granted, provider died
                Log.w(TAG, "no reading from " + uri + ": " + e.getMessage());
            }
            Reading result = reading;
            main.post(() -> listener.onWeather(result));
        });
    }
}
