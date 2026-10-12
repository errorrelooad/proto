package com.errorrelooad.wavehome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;

/** A lightweight wallpaper renderer for media selected inside Wave Home. */
public class HomeWallpaperService extends WallpaperService {
    static final String PREFS = "wave_home_settings";
    static final String ACTION_REFRESH = "com.errorrelooad.wavehome.REFRESH_WALLPAPER";

    @Override
    public Engine onCreateEngine() {
        return new MediaEngine();
    }

    private final class MediaEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Uri mediaUri;
        private Bitmap bitmap;
        private Movie movie;
        private android.media.MediaPlayer player;
        private boolean videoPrepared;
        private String kind = "image";
        private int dim;
        private boolean visible;
        private boolean destroyed;
        private float xOffset = 0.5f;
        private long movieStart;
        private int surfaceWidth;
        private int surfaceHeight;
        private boolean receiverRegistered;
        private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (intent.getBooleanExtra("reload", false)) refreshSelection();
                else drawFrame();
            }
        };

        private final Runnable drawGif = new Runnable() {
            @Override public void run() {
                if (!visible || destroyed || movie == null) return;
                drawFrame();
                handler.postDelayed(this, 33L);
            }
        };

        MediaEngine() {
            setOffsetNotificationsEnabled(true);
            IntentFilter filter = new IntentFilter(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= 33) {
                HomeWallpaperService.this.registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                HomeWallpaperService.this.registerReceiver(refreshReceiver, filter);
            }
            receiverRegistered = true;
            loadSelection();
        }

        private void loadSelection() {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String stored = prefs.getString("wallpaper_uri", null);
            kind = prefs.getString("wallpaper_kind", "image");
            dim = Math.max(0, Math.min(80, prefs.getInt("wallpaper_dim", 12)));
            if (stored == null) return;
            try {
                mediaUri = Uri.parse(stored);
                if ("gif".equals(kind)) {
                    try (InputStream stream = getContentResolver().openInputStream(mediaUri)) {
                        movie = stream == null ? null : Movie.decodeStream(stream);
                    }
                    if (movie == null) kind = "image";
                }
                if ("image".equals(kind)) {
                    bitmap = decodeWallpaperImage(mediaUri);
                }
            } catch (Exception ignored) {
                mediaUri = null;
            }
        }

        private Bitmap decodeWallpaperImage(Uri uri) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream != null) BitmapFactory.decodeStream(stream, null, options);
            } catch (Exception ignored) { return null; }
            int targetWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels * 2);
            int targetHeight = Math.max(1, getResources().getDisplayMetrics().heightPixels);
            double scale = Math.max(options.outWidth / (double) targetWidth,
                    options.outHeight / (double) targetHeight);
            int sample = 1;
            while (sample * 2 <= scale) sample *= 2;
            options.inSampleSize = sample;
            options.inJustDecodeBounds = false;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                return stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
            } catch (Exception ignored) { return null; }
        }

        private void refreshSelection() {
            handler.removeCallbacks(drawGif);
            releasePlayer();
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
            movie = null;
            mediaUri = null;
            kind = "image";
            loadSelection();
            if (!visible) return;
            if ("video".equals(kind)) startVideo();
            else if (movie != null) {
                movieStart = android.os.SystemClock.uptimeMillis();
                handler.post(drawGif);
            } else drawFrame();
        }

        private void startVideo() {
            if (mediaUri == null || player != null) return;
            try {
                player = new android.media.MediaPlayer();
                player.setDataSource(HomeWallpaperService.this, mediaUri);
                player.setLooping(true);
                player.setVolume(0f, 0f);
                player.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                player.setOnPreparedListener(mp -> {
                    if (destroyed) return;
                    videoPrepared = true;
                    SurfaceHolder holder = getSurfaceHolder();
                    try {
                        mp.setDisplay(holder);
                        if (visible) mp.start();
                    } catch (RuntimeException ignored) { }
                });
                player.prepareAsync();
            } catch (Exception ignored) {
                releasePlayer();
            }
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            if (player != null) {
                try { player.setDisplay(holder); } catch (RuntimeException ignored) { }
            }
            drawFrame();
        }

        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                if ("video".equals(kind)) startVideo();
                if (player != null && videoPrepared) {
                    try { if (!player.isPlaying()) player.start(); } catch (IllegalStateException ignored) { }
                }
                if (movie != null) {
                    movieStart = android.os.SystemClock.uptimeMillis();
                    handler.removeCallbacks(drawGif);
                    handler.post(drawGif);
                } else drawFrame();
            } else {
                handler.removeCallbacks(drawGif);
                if (player != null) {
                    try { if (player.isPlaying()) player.pause(); } catch (IllegalStateException ignored) { }
                }
            }
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            handler.removeCallbacks(drawGif);
        }

        @Override public void onDestroy() {
            destroyed = true;
            handler.removeCallbacks(drawGif);
            releasePlayer();
            if (receiverRegistered) {
                try { HomeWallpaperService.this.unregisterReceiver(refreshReceiver); }
                catch (IllegalArgumentException ignored) { }
                receiverRegistered = false;
            }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            super.onDestroy();
        }

        @Override public void onOffsetsChanged(float xOffset, float yOffset, float xStep,
                                                float yStep, int xPixels, int yPixels) {
            this.xOffset = xOffset;
            if (getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean("wallpaper_parallax", true)) drawFrame();
        }

        private void drawFrame() {
            if ("video".equals(kind) || destroyed) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                int width = canvas.getWidth();
                int height = canvas.getHeight();
                canvas.drawColor(Color.rgb(12, 18, 26));
                if (movie != null) {
                    int duration = Math.max(1, movie.duration());
                    movie.setTime((int) ((android.os.SystemClock.uptimeMillis() - movieStart) % duration));
                    float scale = Math.max(width / (float) Math.max(1, movie.width()),
                            height / (float) Math.max(1, movie.height()));
                    float left = (width - movie.width() * scale) / 2f;
                    float top = (height - movie.height() * scale) / 2f;
                    canvas.save();
                    canvas.translate(left, top);
                    canvas.scale(scale, scale);
                    movie.draw(canvas, 0, 0, paint);
                    canvas.restore();
                } else if (bitmap != null && !bitmap.isRecycled()) {
                    float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
                    float scaledWidth = bitmap.getWidth() * scale;
                    float scaledHeight = bitmap.getHeight() * scale;
                    boolean parallax = getSharedPreferences(PREFS, MODE_PRIVATE)
                            .getBoolean("wallpaper_parallax", true);
                    float extraX = Math.max(0, scaledWidth - width);
                    float offset = parallax ? xOffset : 0.5f;
                    float left = -extraX * offset;
                    RectF destination = new RectF(left, (height - scaledHeight) / 2f,
                            left + scaledWidth, (height + scaledHeight) / 2f);
                    canvas.drawBitmap(bitmap, null, destination, paint);
                }
                if (dim > 0) {
                    paint.setColor(Color.argb(Math.round(dim * 2.55f), 0, 0, 0));
                    canvas.drawRect(0, 0, width, height, paint);
                    paint.setColor(Color.WHITE);
                }
            } catch (RuntimeException ignored) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); } catch (RuntimeException ignored) { }
                }
            }
        }

        private void releasePlayer() {
            if (player == null) return;
            try { player.setDisplay(null); } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
            player = null;
            videoPrepared = false;
        }
    }
}
