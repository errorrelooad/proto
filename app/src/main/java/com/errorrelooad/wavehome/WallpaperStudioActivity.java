package com.errorrelooad.wavehome;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Own-media picker and controls for Wave Home's live wallpaper provider. */
public class WallpaperStudioActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private static final int PICK_VIDEO = 42;
    private SharedPreferences prefs;
    private TextView selectedName;
    private ImageView preview;
    private SeekBar dimSlider;
    private Switch parallaxSwitch;
    private Uri selectedUri;
    private String selectedKind = "image";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(HomeWallpaperService.PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(11, 16, 23));
        getWindow().setNavigationBarColor(Color.rgb(11, 16, 23));
        selectedUri = parse(prefs.getString("wallpaper_uri", null));
        selectedKind = prefs.getString("wallpaper_kind", "image");
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.rgb(11, 16, 23));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(32), dp(22), dp(24));
        scroll.addView(page);

        TextView eyebrow = label("WAVE STUDIO", 12, 0xFF9DE8D0);
        eyebrow.setLetterSpacing(.16f);
        page.addView(eyebrow, lp(MATCH, dp(26)));
        TextView title = label("나만의 배경", 30, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title, lp(MATCH, dp(44)));
        TextView subtitle = label("사진, GIF, 영상으로 움직이는 홈을 만들어 보세요.", 14, 0xFF9AA8B6);
        page.addView(subtitle, lp(MATCH, dp(32)));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(background(0xFF17212B, dp(28), 0x33FFFFFF));
        preview.setClipToOutline(true);
        LinearLayout.LayoutParams previewLp = lp(MATCH, dp(215));
        previewLp.topMargin = dp(15);
        page.addView(preview, previewLp);
        selectedName = label("선택된 배경이 없습니다", 13, 0xFFBDCAD4);
        LinearLayout.LayoutParams nameLp = lp(MATCH, dp(38));
        nameLp.topMargin = dp(5);
        page.addView(selectedName, nameLp);
        updateSelectedDisplay();

        LinearLayout picks = new LinearLayout(this);
        picks.setOrientation(LinearLayout.HORIZONTAL);
        Button image = button("사진 · GIF 선택");
        Button video = button("영상 선택");
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(52), 1);
        half.rightMargin = dp(6);
        picks.addView(image, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(52), 1);
        half2.leftMargin = dp(6);
        picks.addView(video, half2);
        LinearLayout.LayoutParams pickLp = lp(MATCH, dp(58));
        pickLp.topMargin = dp(8);
        page.addView(picks, pickLp);
        image.setOnClickListener(v -> pick(PICK_IMAGE, "image/*"));
        video.setOnClickListener(v -> pick(PICK_VIDEO, "video/*"));

        TextView controlsTitle = label("표현 조절", 18, Color.WHITE);
        controlsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headingLp = lp(MATCH, dp(52));
        headingLp.topMargin = dp(20);
        page.addView(controlsTitle, headingLp);

        LinearLayout options = card();
        TextView dimTitle = label("어둡게", 14, Color.WHITE);
        options.addView(dimTitle, lp(MATCH, dp(28)));
        dimSlider = new SeekBar(this);
        dimSlider.setMax(80);
        dimSlider.setProgress(prefs.getInt("wallpaper_dim", 12));
        dimSlider.setEnabled(!"video".equals(selectedKind));
        dimSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF9DE8D0));
        dimSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF9DE8D0));
        options.addView(dimSlider, lp(MATCH, dp(42)));
        dimSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (fromUser) {
                    prefs.edit().putInt("wallpaper_dim", value).apply();
                    refreshActiveWallpaper(false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        parallaxSwitch = new Switch(this);
        parallaxSwitch.setText("사진에 기울임 시차 효과");
        parallaxSwitch.setTextColor(Color.WHITE);
        parallaxSwitch.setTextSize(14);
        parallaxSwitch.setChecked(prefs.getBoolean("wallpaper_parallax", true));
        options.addView(parallaxSwitch, lp(MATCH, dp(48)));
        parallaxSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("wallpaper_parallax", checked).apply();
            refreshActiveWallpaper(false);
        });
        parallaxSwitch.setEnabled("image".equals(selectedKind));
        page.addView(options, lp(MATCH, WRAP));

        TextView apply = button("배경화면 적용");
        apply.setBackground(background(0xFF9DE8D0, dp(20), Color.TRANSPARENT));
        apply.setTextColor(0xFF0A1515);
        apply.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams applyLp = lp(MATCH, dp(58));
        applyLp.topMargin = dp(16);
        page.addView(apply, applyLp);
        apply.setOnClickListener(v -> applyWallpaper());

        TextView note = label("배경 파일은 휴대폰에서만 읽습니다. 영상은 무음 반복 재생되며 사진/GIF에 어둡기 조절을 적용합니다.",
                12, 0xFF8392A0);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = lp(MATCH, WRAP);
        noteLp.topMargin = dp(14);
        page.addView(note, noteLp);
        setContentView(scroll);
    }

    private void pick(int request, String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        try { startActivityForResult(intent, request); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "파일 선택 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        selectedUri = uri;
        String mime = getContentResolver().getType(uri);
        selectedKind = requestCode == PICK_VIDEO ? "video"
                : (mime != null && mime.toLowerCase().contains("gif") ? "gif" : "image");
        updateSelectedDisplay();
        if (dimSlider != null) dimSlider.setEnabled(!"video".equals(selectedKind));
        if (parallaxSwitch != null) parallaxSwitch.setEnabled("image".equals(selectedKind));
    }

    private void updateSelectedDisplay() {
        if (selectedUri == null || selectedName == null) return;
        String displayName = displayName(selectedUri);
        selectedName.setText(displayName + "  ·  " + ("video".equals(selectedKind) ? "영상" : "image".equals(selectedKind) ? "사진" : "GIF"));
        if ("video".equals(selectedKind)) {
            preview.setImageDrawable(null);
            preview.setBackground(background(0xFF17212B, dp(28), 0x33FFFFFF));
            preview.setImageDrawable(getDrawable(android.R.drawable.ic_media_play));
            preview.setColorFilter(0xFF9DE8D0);
            preview.setScaleType(ImageView.ScaleType.CENTER);
            return;
        }
        try (java.io.InputStream in = getContentResolver().openInputStream(selectedUri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int target = dp(640);
            bounds.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / target);
            bounds.inJustDecodeBounds = false;
            try (java.io.InputStream second = getContentResolver().openInputStream(selectedUri)) {
                preview.setImageBitmap(BitmapFactory.decodeStream(second, null, bounds));
            }
        } catch (Exception ignored) {
            preview.setImageDrawable(null);
        }
        preview.clearColorFilter();
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    }

    private void applyWallpaper() {
        if (selectedUri == null) {
            Toast.makeText(this, "먼저 사진, GIF 또는 영상을 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("wallpaper_uri", selectedUri.toString())
                .putString("wallpaper_kind", selectedKind)
                .putInt("wallpaper_dim", dimSlider.getProgress())
                .putBoolean("wallpaper_parallax", parallaxSwitch.isChecked()).apply();
        refreshActiveWallpaper(true);
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new android.content.ComponentName(this, HomeWallpaperService.class));
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "라이브 배경화면 미리보기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment() == null ? "선택한 배경" : uri.getLastPathSegment();
    }

    private void refreshActiveWallpaper(boolean reload) {
        Intent intent = new Intent(HomeWallpaperService.ACTION_REFRESH);
        intent.setPackage(getPackageName());
        intent.putExtra("reload", reload);
        sendBroadcast(intent);
    }

    private Uri parse(String raw) { return raw == null ? null : Uri.parse(raw); }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(background(0xFF151E28, dp(22), 0x33FFFFFF));
        return card;
    }
    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setAllCaps(false);
        view.setBackground(background(0xFF1A2632, dp(18), 0x33FFFFFF));
        return view;
    }
    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
