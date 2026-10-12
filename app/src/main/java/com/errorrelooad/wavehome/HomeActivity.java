package com.errorrelooad.wavehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final String PREFS = "wave_home_settings";
    private static final String FAVORITES = "favorites";
    private static final int PAGE_HOME = 0;
    private static final int PAGE_CONTROL = 1;
    private static final int PAGE_SETTINGS = 2;
    private static final int[] ACCENTS = {
            Color.rgb(142, 239, 203), Color.rgb(132, 191, 255),
            Color.rgb(255, 174, 191), Color.rgb(246, 203, 119),
            Color.rgb(200, 169, 255)
    };

    private SharedPreferences prefs;
    private int page = PAGE_HOME;
    private int downX;
    private int downY;
    private long downAt;
    private ArrayList<AppEntry> installedApps = new ArrayList<>();
    private LinearLayout appListView;
    private EditText searchField;
    private String selectedLetter = "";
    private String selectedGroupId = "";
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (page == PAGE_CONTROL) showPage(PAGE_CONTROL);
        }
    };

    private static final class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        installedApps = loadApps();
        IntentFilter filter = new IntentFilter(NotificationAccessService.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationReceiver, filter);
        }
        showPage(PAGE_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        installedApps = loadApps();
        if (page == PAGE_CONTROL) showPage(PAGE_CONTROL);
        else if (page == PAGE_SETTINGS) showPage(PAGE_SETTINGS);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(notificationReceiver); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = (int) event.getX();
            downY = (int) event.getY();
            downAt = System.currentTimeMillis();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            int dy = (int) event.getY() - downY;
            int dx = (int) event.getX() - downX;
            if (page == PAGE_HOME && downY < dp(92) && dy > dp(120)
                    && Math.abs(dx) < dp(100) && System.currentTimeMillis() - downAt < 1200) {
                showPage(PAGE_CONTROL);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void showPage(int target) {
        page = target;
        appListView = null;
        searchField = null;
        selectedLetter = "";
        selectedGroupId = "";
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        if (target == PAGE_CONTROL) shell.setPadding(dp(10), dp(24), dp(10), dp(8));
        else shell.setPadding(dp(18), dp(42), dp(18), dp(20));
        shell.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View pageView = target == PAGE_HOME ? buildHome()
                : target == PAGE_CONTROL ? buildControlCenter() : buildSettingsPage();
        pageHost.addView(pageView);
        if (target != PAGE_CONTROL) shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        setContentView(shell);
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout body = column();
        scroll.addView(body);

        TextView brand = text("WAVE HOME", 12, accent());
        brand.setLetterSpacing(0.17f);
        body.addView(brand, params(WRAP, dp(28)));

        LinearLayout hero = card();
        TextView clock = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 44, primary());
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        TextView date = text(new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()), 15, secondary());
        TextView hint = text("위에서 아래로 밀어 알림·빠른 설정", 12, secondary());
        hero.addView(clock);
        hero.addView(date, params(WRAP, dp(28)));
        hero.addView(hint, params(WRAP, dp(25)));
        body.addView(hero, params(MATCH, WRAP));

        EditText search = new EditText(this);
        searchField = search;
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(primary());
        search.setHintTextColor(secondary());
        search.setHint("앱 이름 검색");
        search.setPadding(dp(18), 0, dp(16), 0);
        search.setBackground(roundBg(panelColor(), dp(22), lineColor()));
        LinearLayout.LayoutParams searchLp = params(MATCH, dp(54));
        searchLp.topMargin = dp(2);
        body.addView(search, searchLp);

        TextView favoritesTitle = sectionTitle("즐겨찾기");
        body.addView(favoritesTitle, params(MATCH, dp(38)));
        HorizontalScrollView favoritesScroll = new HorizontalScrollView(this);
        favoritesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout favoritesRow = new LinearLayout(this);
        favoritesRow.setOrientation(LinearLayout.HORIZONTAL);
        favoritesScroll.addView(favoritesRow);
        body.addView(favoritesScroll, params(MATCH, dp(82)));
        renderFavorites(favoritesRow);

        LinearLayout rowTitle = new LinearLayout(this);
        rowTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView all = sectionTitle("모든 앱");
        rowTitle.addView(all, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView count = text(installedApps.size() + "개", 12, secondary());
        rowTitle.addView(count);
        LinearLayout.LayoutParams titleLp = params(MATCH, dp(42));
        titleLp.topMargin = dp(5);
        body.addView(rowTitle, titleLp);

        HorizontalScrollView alphabetScroll = new HorizontalScrollView(this);
        alphabetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout letters = new LinearLayout(this);
        letters.setOrientation(LinearLayout.HORIZONTAL);
        alphabetScroll.addView(letters);
        body.addView(alphabetScroll, params(MATCH, dp(42)));
        TextView reset = smallChip("전체");
        reset.setOnClickListener(v -> filterApps("", ""));
        letters.addView(reset);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
        for (int i = 0; i < alphabet.length(); i++) {
            String letter = String.valueOf(alphabet.charAt(i));
            TextView chip = smallChip(letter);
            chip.setOnClickListener(v -> filterApps(search.getText().toString(), letter));
            letters.addView(chip);
        }

        LinearLayout groupHeader = new LinearLayout(this);
        groupHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView groupTitle = sectionTitle("내 분류");
        groupHeader.addView(groupTitle, new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView addGroup = smallLink("＋ 만들기");
        groupHeader.addView(addGroup, params(WRAP, dp(38)));
        addGroup.setOnClickListener(v -> promptCreateGroup());
        body.addView(groupHeader, params(MATCH, dp(38)));
        HorizontalScrollView groupsScroll = new HorizontalScrollView(this);
        groupsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout groupChips = new LinearLayout(this);
        groupChips.setOrientation(LinearLayout.HORIZONTAL);
        groupsScroll.addView(groupChips);
        body.addView(groupsScroll, params(MATCH, dp(42)));
        renderGroupChips(groupChips, search);

        appListView = column();
        body.addView(appListView, params(MATCH, WRAP));
        filterAppsInto(appListView, "", selectedLetter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAppsInto(appListView, s.toString(), selectedLetter);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return scroll;
    }

    private void filterApps(String query, String letter) {
        selectedLetter = letter;
        if (appListView != null) filterAppsInto(appListView, query, selectedLetter);
    }

    private void filterAppsInto(LinearLayout target, String query, String letter) {
        target.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        ArrayList<AppEntry> matches = new ArrayList<>();
        for (AppEntry app : installedApps) {
            boolean textMatch = needle.isEmpty() || app.label.toLowerCase(Locale.getDefault()).contains(needle);
            boolean letterMatch = letter == null || letter.isEmpty() || matchesInitial(app.label, letter);
            boolean groupMatch = selectedGroupId.isEmpty() || appGroupIds(app.packageName).contains(selectedGroupId);
            if (textMatch && letterMatch && groupMatch) matches.add(app);
        }
        if (matches.isEmpty()) {
            TextView empty = text(needle.isEmpty() ? "표시할 앱이 없습니다." : "일치하는 앱이 없습니다.", 14, secondary());
            target.addView(empty, params(MATCH, dp(56)));
        } else {
            for (AppEntry app : matches) target.addView(appRow(app), params(MATCH, WRAP));
        }
    }

    private void renderGroupChips(LinearLayout row, EditText search) {
        row.removeAllViews();
        TextView all = smallChip("모든 분류");
        all.setOnClickListener(v -> {
            selectedGroupId = "";
            filterAppsInto(appListView, search.getText().toString(), selectedLetter);
            renderGroupChips(row, search);
        });
        row.addView(all);
        for (JSONObject group : customGroups()) {
            String id = group.optString("id", "");
            String name = group.optString("name", "분류");
            TextView chip = smallChip(name);
            if (id.equals(selectedGroupId)) chip.setTextColor(Color.WHITE);
            chip.setOnClickListener(v -> {
                selectedGroupId = id;
                filterAppsInto(appListView, search.getText().toString(), selectedLetter);
                renderGroupChips(row, search);
            });
            chip.setOnLongClickListener(v -> {
                manageGroup(id, name, search, row);
                return true;
            });
            row.addView(chip);
        }
        if (customGroups().isEmpty()) {
            TextView hint = text("직접 만든 분류에 앱을 묶어 보세요", 12, secondary());
            hint.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(hint, params(WRAP, dp(32)));
        }
    }

    private ArrayList<JSONObject> customGroups() {
        ArrayList<JSONObject> result = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("custom_groups", "[]"));
            for (int i = 0; i < saved.length(); i++) result.add(saved.getJSONObject(i));
        } catch (Exception ignored) { }
        return result;
    }

    private Set<String> appGroupIds(String packageName) {
        HashSet<String> result = new HashSet<>();
        try {
            JSONObject memberships = new JSONObject(prefs.getString("app_groups", "{}"));
            JSONArray ids = memberships.optJSONArray(packageName);
            if (ids != null) for (int i = 0; i < ids.length(); i++) result.add(ids.optString(i));
        } catch (Exception ignored) { }
        return result;
    }

    private void promptCreateGroup() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("예: 업무, 사진, 자주 사용");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("새 분류 만들기")
                .setMessage("원하는 이름으로 앱을 묶을 수 있습니다.")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("만들기", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "분류 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ArrayList<JSONObject> groups = customGroups();
                    try {
                        groups.add(new JSONObject().put("id", java.util.UUID.randomUUID().toString())
                                .put("name", name));
                        saveGroups(groups);
                        showPage(PAGE_HOME);
                    } catch (Exception ignored) { }
                }).show();
    }

    private void manageGroup(String id, String name, EditText search, LinearLayout chips) {
        new AlertDialog.Builder(this).setTitle(name)
                .setItems(new String[]{"이름 바꾸기", "분류 삭제"}, (dialog, which) -> {
                    if (which == 0) renameGroup(id, name);
                    else confirmDeleteGroup(id, name);
                }).show();
    }

    private void renameGroup(String id, String oldName) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(oldName);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("분류 이름 바꾸기").setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    ArrayList<JSONObject> groups = customGroups();
                    try {
                        for (JSONObject group : groups) if (id.equals(group.optString("id"))) {
                            group.put("name", name);
                            break;
                        }
                        saveGroups(groups);
                        showPage(PAGE_HOME);
                    } catch (Exception ignored) { }
                }).show();
    }

    private void confirmDeleteGroup(String id, String name) {
        new AlertDialog.Builder(this).setTitle("분류 삭제")
                .setMessage("‘" + name + "’ 분류와 앱 배정을 삭제할까요? 앱 자체는 삭제되지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> {
                    ArrayList<JSONObject> groups = customGroups();
                    groups.removeIf(group -> id.equals(group.optString("id")));
                    saveGroups(groups);
                    removeGroupFromApps(id);
                    if (id.equals(selectedGroupId)) selectedGroupId = "";
                    showPage(PAGE_HOME);
                }).show();
    }

    private void saveGroups(ArrayList<JSONObject> groups) {
        JSONArray array = new JSONArray();
        for (JSONObject group : groups) array.put(group);
        prefs.edit().putString("custom_groups", array.toString()).apply();
    }

    private void removeGroupFromApps(String removedId) {
        try {
            JSONObject memberships = new JSONObject(prefs.getString("app_groups", "{}"));
            JSONObject cleaned = new JSONObject();
            java.util.Iterator<String> keys = memberships.keys();
            while (keys.hasNext()) {
                String app = keys.next();
                JSONArray values = memberships.optJSONArray(app);
                JSONArray keep = new JSONArray();
                if (values != null) for (int i = 0; i < values.length(); i++) {
                    String id = values.optString(i);
                    if (!removedId.equals(id)) keep.put(id);
                }
                if (keep.length() > 0) cleaned.put(app, keep);
            }
            prefs.edit().putString("app_groups", cleaned.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void editAppGroups(AppEntry app) {
        ArrayList<JSONObject> groups = customGroups();
        if (groups.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("분류가 없습니다")
                    .setMessage("앱을 묶을 분류를 먼저 만들어 주세요.")
                    .setNegativeButton("닫기", null)
                    .setPositiveButton("분류 만들기", (d, w) -> promptCreateGroup()).show();
            return;
        }
        String[] names = new String[groups.size()];
        boolean[] checked = new boolean[groups.size()];
        Set<String> assigned = appGroupIds(app.packageName);
        String[] ids = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            ids[i] = groups.get(i).optString("id");
            names[i] = groups.get(i).optString("name");
            checked[i] = assigned.contains(ids[i]);
        }
        new AlertDialog.Builder(this).setTitle(app.label + " 분류")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    try {
                        JSONObject memberships = new JSONObject(prefs.getString("app_groups", "{}"));
                        JSONArray selected = new JSONArray();
                        for (int i = 0; i < ids.length; i++) if (checked[i]) selected.put(ids[i]);
                        if (selected.length() == 0) memberships.remove(app.packageName);
                        else memberships.put(app.packageName, selected);
                        prefs.edit().putString("app_groups", memberships.toString()).apply();
                        if (appListView != null && searchField != null)
                            filterAppsInto(appListView, searchField.getText().toString(), selectedLetter);
                    } catch (Exception ignored) { }
                }).show();
    }

    private boolean matchesInitial(String label, String selected) {
        if (label == null || label.isEmpty()) return false;
        char first = label.charAt(0);
        if (selected.length() == 1 && first >= 0xAC00 && first <= 0xD7A3) {
            int initialIndex = (first - 0xAC00) / (21 * 28);
            String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
            return initialIndex < initials.length() && String.valueOf(initials.charAt(initialIndex)).equals(selected);
        }
        return label.toUpperCase(Locale.getDefault()).startsWith(selected);
    }

    private void renderFavorites(LinearLayout row) {
        row.removeAllViews();
        Set<String> favorites = favorites();
        boolean any = false;
        for (AppEntry app : installedApps) {
            if (favorites.contains(app.packageName)) {
                any = true;
                LinearLayout tile = new LinearLayout(this);
                tile.setGravity(Gravity.CENTER);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(dp(10), dp(6), dp(10), dp(4));
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(app.icon);
                tile.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                TextView name = text(shorten(app.label, 10), 11, primary());
                name.setGravity(Gravity.CENTER);
                tile.addView(name, params(dp(76), dp(24)));
                tile.setOnClickListener(v -> launch(app.packageName));
                tile.setOnLongClickListener(v -> {
                    toggleFavorite(app.packageName);
                    showPage(PAGE_HOME);
                    return true;
                });
                row.addView(tile, params(dp(82), MATCH));
            }
        }
        if (!any) {
            TextView empty = text("앱 행의 ☆를 눌러 즐겨찾기에 추가", 13, secondary());
            empty.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(empty);
        }
    }

    private View appRow(AppEntry app) {
        int rowHeight = prefs.getBoolean("compact", true) ? 58 : 72;
        int iconSize = prefs.getBoolean("compact", true) ? 38 : 48;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.setBackground(roundBg(panelColor(), dp(18), lineColor()));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        TextView name = text(app.label, 15, primary());
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, MATCH, 1f);
        nameLp.leftMargin = dp(12);
        row.addView(name, nameLp);
        TextView grouping = text("⊞", 17, appGroupIds(app.packageName).isEmpty() ? secondary() : accent());
        grouping.setGravity(Gravity.CENTER);
        row.addView(grouping, new LinearLayout.LayoutParams(dp(38), MATCH));
        grouping.setOnClickListener(v -> editAppGroups(app));
        boolean favored = favorites().contains(app.packageName);
        TextView star = text(favored ? "★" : "☆", 19, favored ? accent() : secondary());
        star.setGravity(Gravity.CENTER);
        row.addView(star, new LinearLayout.LayoutParams(dp(38), MATCH));
        row.setOnClickListener(v -> launch(app.packageName));
        star.setOnClickListener(v -> {
            toggleFavorite(app.packageName);
            showPage(PAGE_HOME);
        });
        row.setOnLongClickListener(v -> {
            toggleFavorite(app.packageName);
            showPage(PAGE_HOME);
            return true;
        });
        LinearLayout.LayoutParams lp = params(MATCH, dp(rowHeight));
        lp.bottomMargin = dp(7);
        row.setLayoutParams(lp);
        return row;
    }

    private View buildControlCenter() {
        final int theme = notificationTheme();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(centerBackground(theme));
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout body = column();
        body.setPadding(dp(8), dp(2), dp(8), dp(18));
        scroll.addView(body);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = centerButton("×", theme);
        top.addView(close, params(dp(42), dp(42)));
        close.setOnClickListener(v -> showPage(PAGE_HOME));
        TextView title = centerText("알림 센터", 15, centerTextColor(theme));
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView themeButton = centerButton("•••", theme);
        top.addView(themeButton, params(dp(42), dp(42)));
        themeButton.setOnClickListener(v -> showPage(PAGE_SETTINGS));
        body.addView(top, params(MATCH, dp(46)));

        LinearLayout dateBlock = new LinearLayout(this);
        dateBlock.setOrientation(LinearLayout.VERTICAL);
        dateBlock.setPadding(dp(6), dp(8), dp(6), dp(17));
        dateBlock.addView(centerText(new SimpleDateFormat("EEEE, M월 d일", Locale.KOREAN).format(new Date()),
                14, centerSecondary(theme)), params(MATCH, dp(27)));
        TextView clock = centerText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()),
                45, centerTextColor(theme));
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        dateBlock.addView(clock, params(MATCH, dp(58)));
        body.addView(dateBlock, params(MATCH, dp(94)));

        LinearLayout quick = centerCard(theme);
        TextView quickHeading = centerText("빠른 설정", 17, centerTextColor(theme));
        quickHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        quick.addView(quickHeading, params(MATCH, dp(30)));
        quick.addView(centerText("기기 설정은 Android의 공식 패널에서 열립니다.", 12,
                centerSecondary(theme)), params(MATCH, dp(27)));
        addCenterTiles(quick, theme);
        TextView brightnessTitle = centerText("화면 밝기", 12, centerSecondary(theme));
        LinearLayout.LayoutParams brightnessTitleLp = params(MATCH, dp(27));
        brightnessTitleLp.topMargin = dp(5);
        quick.addView(brightnessTitle, brightnessTitleLp);
        if (Settings.System.canWrite(this)) {
            SeekBar brightness = new SeekBar(this);
            brightness.setMax(255);
            brightness.setProgress(getBrightness());
            tintSeekBar(brightness, centerAccent(theme));
            brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) try {
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                                Math.max(1, progress));
                    } catch (SecurityException ignored) { }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            quick.addView(brightness, params(MATCH, dp(37)));
        } else {
            TextView grant = centerButton("밝기 권한 설정", theme);
            quick.addView(grant, params(MATCH, dp(42)));
            grant.setOnClickListener(v -> requestWriteSettings());
        }
        body.addView(quick, params(MATCH, WRAP));

        LinearLayout notifHeading = new LinearLayout(this);
        notifHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView notifTitle = centerText("알림", 20, centerTextColor(theme));
        notifTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        notifHeading.addView(notifTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView access = centerText(notificationAccessEnabled() ? "실시간" : "권한 필요", 12,
                notificationAccessEnabled() ? centerAccent(theme) : centerSecondary(theme));
        notifHeading.addView(access);
        body.addView(notifHeading, params(MATCH, dp(48)));

        boolean hasAccess = notificationAccessEnabled();
        if (!hasAccess) {
            LinearLayout card = centerCard(theme);
            card.addView(centerText("앱의 알림을 이 화면에서 모아볼 수 있습니다. 알림 접근은 설정에서 직접 허용할 수 있어요.",
                    14, centerSecondary(theme)), params(MATCH, WRAP));
            TextView grant = centerButton("알림 접근 허용", theme);
            LinearLayout.LayoutParams grantLp = params(MATCH, dp(48));
            grantLp.topMargin = dp(10);
            card.addView(grant, grantLp);
            grant.setOnClickListener(v -> openNotificationSettings());
            body.addView(card, params(MATCH, WRAP));
        } else {
            List<NotificationAccessService.Notice> list = NotificationAccessService.snapshot();
            if (!list.isEmpty()) {
                TextView clear = centerButton("모두 지우기", theme);
                LinearLayout.LayoutParams clearLp = params(WRAP, dp(34));
                clearLp.gravity = Gravity.RIGHT;
                body.addView(clear, clearLp);
                clear.setOnClickListener(v -> NotificationAccessService.clearAll());
                int limit = Math.min(20, list.size());
                for (int i = 0; i < limit; i++) body.addView(notificationRow(list.get(i), theme), params(MATCH, WRAP));
            } else {
                LinearLayout empty = centerCard(theme);
                TextView emptyTitle = centerText("새로운 알림이 없습니다", 15, centerTextColor(theme));
                emptyTitle.setGravity(Gravity.CENTER);
                empty.addView(emptyTitle, params(MATCH, dp(58)));
                body.addView(empty, params(MATCH, WRAP));
            }
        }
        return root;
    }

    private void addCenterTiles(LinearLayout parent, int theme) {
        addCenterTileRow(parent, theme,
                new String[]{"◉  인터넷", "⌁  Wi-Fi"},
                new String[]{Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.Panel.ACTION_WIFI});
        addCenterTileRow(parent, theme,
                new String[]{"◖  소리", "☼  디스플레이"},
                new String[]{Settings.Panel.ACTION_VOLUME, Settings.ACTION_DISPLAY_SETTINGS});
    }

    private void addCenterTileRow(LinearLayout parent, int theme, String[] labels, String[] actions) {
        LinearLayout row = new LinearLayout(this);
        TextView left = centerButton(labels[0], theme);
        TextView right = centerButton(labels[1], theme);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.rightMargin = dp(5);
        row.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(52), 1);
        rp.leftMargin = dp(5);
        row.addView(right, rp);
        left.setOnClickListener(v -> openPanel(actions[0]));
        right.setOnClickListener(v -> openPanel(actions[1]));
        LinearLayout.LayoutParams rowLp = params(MATCH, dp(57));
        rowLp.topMargin = dp(4);
        parent.addView(row, rowLp);
    }

    private View notificationRow(NotificationAccessService.Notice notice, int theme) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(12), dp(12), dp(10));
        card.setBackground(roundBg(centerCardColor(theme), dp(22), centerStroke(theme)));
        String appName = appLabel(notice.packageName);
        TextView source = centerText(appName + "  ·  "
                + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(notice.postTime)),
                11, centerSecondary(theme));
        TextView title = centerText(notice.title.isEmpty() ? appName : notice.title, 15, centerTextColor(theme));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView message = centerText(notice.text, 13, centerSecondary(theme));
        message.setMaxLines(3);
        message.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(source);
        card.addView(title, params(MATCH, dp(25)));
        if (!notice.text.isEmpty()) card.addView(message, params(MATCH, WRAP));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT);
        TextView dismiss = centerButton("닫기", theme);
        dismiss.setPadding(dp(12), 0, dp(12), 0);
        dismiss.setOnClickListener(v -> {
            NotificationAccessService.dismiss(notice.key);
        });
        TextView open = centerButton("열기", theme);
        open.setPadding(dp(12), 0, dp(12), 0);
        open.setOnClickListener(v -> notice.open());
        actions.addView(dismiss);
        actions.addView(open);
        card.addView(actions);
        LinearLayout.LayoutParams lp = params(MATCH, WRAP);
        lp.bottomMargin = dp(9);
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> notice.open());
        return card;
    }

    private View buildSettingsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column();
        scroll.addView(body);
        addHeader(body, "설정", "홈 화면과 알림 센터를 내 취향에 맞게 바꿉니다.");

        LinearLayout appearance = card();
        appearance.addView(sectionTitle("화면 모양"));
        addSwitch(appearance, "어두운 카드", "반투명한 어두운 카드 테마 사용", "dark", true);
        addSwitch(appearance, "간결한 앱 목록", "앱 행 높이를 줄여 더 많이 표시", "compact", true);
        TextView accentTitle = text("강조 색상", 13, secondary());
        LinearLayout.LayoutParams at = params(MATCH, dp(34));
        at.topMargin = dp(8);
        appearance.addView(accentTitle, at);
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < ACCENTS.length; i++) {
            final int index = i;
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(ACCENTS[i]);
            shape.setShape(GradientDrawable.OVAL);
            if (prefs.getInt("accent", 0) == i) shape.setStroke(dp(3), Color.WHITE);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            dotLp.rightMargin = dp(12);
            colors.addView(dot, dotLp);
            dot.setOnClickListener(v -> {
                prefs.edit().putInt("accent", index).apply();
                showPage(PAGE_SETTINGS);
            });
        }
        appearance.addView(colors, params(MATCH, dp(48)));
        body.addView(appearance, params(MATCH, WRAP));

        LinearLayout notificationAppearance = card();
        notificationAppearance.addView(sectionTitle("알림 센터 테마"));
        notificationAppearance.addView(text("앱 안에서 여는 알림 화면에만 적용됩니다.", 12, secondary()),
                params(MATCH, dp(28)));
        LinearLayout notificationThemes = new LinearLayout(this);
        notificationThemes.setOrientation(LinearLayout.HORIZONTAL);
        addNotificationThemeOption(notificationThemes, "프로스트", 0);
        addNotificationThemeOption(notificationThemes, "미드나잇", 1);
        addNotificationThemeOption(notificationThemes, "웜 라이트", 2);
        notificationAppearance.addView(notificationThemes, params(MATCH, dp(48)));
        body.addView(notificationAppearance, params(MATCH, WRAP));

        LinearLayout wallpaper = card();
        wallpaper.addView(sectionTitle("배경화면 스튜디오"));
        wallpaper.addView(text("휴대폰의 사진, GIF, 영상으로 나만의 움직이는 배경을 만드세요. 외부 앱 연결은 필요하지 않습니다.", 13, secondary()),
                params(MATCH, WRAP));
        TextView active = text(currentWallpaperText(), 12, accent());
        LinearLayout.LayoutParams activeLp = params(MATCH, dp(36));
        activeLp.topMargin = dp(8);
        wallpaper.addView(active, activeLp);
        TextView studio = actionButton("배경화면 만들기 · 사진 / GIF / 영상");
        wallpaper.addView(studio, params(MATCH, dp(50)));
        studio.setOnClickListener(v -> safeStart(new Intent(this, WallpaperStudioActivity.class)));
        body.addView(wallpaper, params(MATCH, WRAP));

        LinearLayout permissions = card();
        permissions.addView(sectionTitle("권한과 시스템 설정"));
        addAction(permissions, "알림 접근 설정", "앱 내부 알림 패널에 필요한 선택 권한", this::openNotificationSettings);
        addAction(permissions, "기본 홈 앱 선택", "Wave Home을 기기의 기본 런처로 지정", this::openHomeSettings);
        addAction(permissions, "밝기 변경 권한", "제어 센터의 시스템 밝기 슬라이더에 사용", this::requestWriteSettings);
        addAction(permissions, "안드로이드 설정 열기", "기기의 원래 설정 앱을 엽니다",
                () -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
        body.addView(permissions, params(MATCH, WRAP));

        LinearLayout about = card();
        about.addView(sectionTitle("앱 정보"));
        about.addView(text("Wave Home 0.2.0 · Android 10 이상\n앱 분류와 알림 센터 테마는 이 기기에 저장됩니다.", 12, secondary()));
        body.addView(about, params(MATCH, WRAP));
        return scroll;
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(7), dp(6), dp(7), dp(6));
        nav.setBackground(roundBg(isDark() ? 0xCC111722 : 0xDDEEF1F7, dp(26), lineColor()));
        addNav(nav, "⌂  홈", PAGE_HOME);
        addNav(nav, "◉  제어", PAGE_CONTROL);
        addNav(nav, "⚙  설정", PAGE_SETTINGS);
        return nav;
    }

    private void addNav(LinearLayout row, String label, int target) {
        TextView button = text(label, 13, page == target ? accent() : secondary());
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundBg(page == target ? (isDark() ? 0x443A8A70 : 0x223A8A70) : Color.TRANSPARENT,
                dp(18), Color.TRANSPARENT));
        row.addView(button, new LinearLayout.LayoutParams(0, MATCH, 1f));
        button.setOnClickListener(v -> showPage(target));
    }

    private void addHeader(LinearLayout body, String title, String subtitle) {
        LinearLayout header = card();
        TextView titleView = text(title, 27, primary());
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(titleView);
        header.addView(text(subtitle, 13, secondary()), params(MATCH, WRAP));
        body.addView(header, params(MATCH, WRAP));
    }

    private void addPanelRow(LinearLayout parent, String leftLabel, String leftAction,
                             String rightLabel, String rightAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView left = actionButton(leftLabel);
        TextView right = actionButton(rightLabel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.rightMargin = dp(7);
        row.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        rp.leftMargin = dp(7);
        row.addView(right, rp);
        left.setOnClickListener(v -> openPanel(leftAction));
        right.setOnClickListener(v -> openPanel(rightAction));
        parent.addView(row, params(MATCH, dp(52)));
    }

    private void addSwitch(LinearLayout parent, String title, String subtitle,
                           String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column();
        labels.addView(text(title, 14, primary()));
        labels.addView(text(subtitle, 11, secondary()));
        row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            showPage(PAGE_SETTINGS);
        });
        row.addView(toggle);
        parent.addView(row, params(MATCH, dp(58)));
    }

    private void addNotificationThemeOption(LinearLayout parent, String label, int value) {
        TextView option = text(label, 10, prefs.getInt("notification_theme", 0) == value
                ? 0xFF0B1117 : secondary());
        option.setGravity(Gravity.CENTER);
        option.setSingleLine(true);
        option.setBackground(roundBg(prefs.getInt("notification_theme", 0) == value
                        ? centerAccent(value) : panelColor(), dp(16), lineColor()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        parent.addView(option, lp);
        option.setOnClickListener(v -> {
            prefs.edit().putInt("notification_theme", value).apply();
            showPage(PAGE_SETTINGS);
        });
    }

    private int notificationTheme() {
        return Math.max(0, Math.min(2, prefs.getInt("notification_theme", 0)));
    }

    private int centerBackground(int theme) {
        if (theme == 1) return 0xFF0B1118;
        if (theme == 2) return 0xFFE9E3D8;
        return 0xFFDCE5EF;
    }

    private int centerCardColor(int theme) {
        if (theme == 1) return 0xFF19232E;
        if (theme == 2) return 0xFFF9F5EC;
        return 0xFFF5F8FB;
    }

    private int centerTextColor(int theme) {
        if (theme == 1) return Color.WHITE;
        if (theme == 2) return 0xFF332F29;
        return 0xFF1D2B3A;
    }

    private int centerSecondary(int theme) {
        if (theme == 1) return 0xFFA9B7C5;
        if (theme == 2) return 0xFF766F64;
        return 0xFF5C6E7D;
    }

    private int centerAccent(int theme) {
        if (theme == 1) return 0xFFA7F3D0;
        if (theme == 2) return 0xFFA2634F;
        return 0xFF3979C5;
    }

    private int centerStroke(int theme) {
        return theme == 1 ? 0x22FFFFFF : 0x220A2638;
    }

    private LinearLayout centerCard(int theme) {
        LinearLayout result = column();
        result.setPadding(dp(16), dp(12), dp(16), dp(13));
        result.setBackground(roundBg(centerCardColor(theme), dp(24), centerStroke(theme)));
        LinearLayout.LayoutParams lp = params(MATCH, WRAP);
        lp.bottomMargin = dp(9);
        result.setLayoutParams(lp);
        return result;
    }

    private TextView centerText(String value, float size, int color) {
        return text(value, size, color);
    }

    private TextView centerButton(String value, int theme) {
        TextView button = centerText(value, 13, centerTextColor(theme));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(roundBg(centerButtonColor(theme),
                dp(16), centerStroke(theme)));
        return button;
    }

    private int centerButtonColor(int theme) {
        if (theme == 1) return 0xFF283744;
        if (theme == 2) return 0xFFECE4D8;
        return 0xFFDDE6EF;
    }

    private void tintSeekBar(SeekBar seekBar, int tint) {
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(tint));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(tint));
    }

    private void addAction(LinearLayout parent, String title, String detail, Runnable action) {
        TextView item = text(title + "\n" + detail, 14, primary());
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(4), dp(8), dp(4));
        item.setBackground(roundBg(isDark() ? 0x55FFFFFF : 0x99FFFFFF, dp(16), lineColor()));
        LinearLayout.LayoutParams lp = params(MATCH, dp(60));
        lp.topMargin = dp(7);
        parent.addView(item, lp);
        item.setOnClickListener(v -> action.run());
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setClipToPadding(false);
        return result;
    }

    private LinearLayout card() {
        LinearLayout result = column();
        result.setPadding(dp(16), dp(14), dp(16), dp(14));
        result.setBackground(roundBg(panelColor(), dp(24), lineColor()));
        LinearLayout.LayoutParams lp = params(MATCH, WRAP);
        lp.bottomMargin = dp(12);
        result.setLayoutParams(lp);
        return result;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 16, primary());
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView actionButton(String value) {
        TextView view = text(value, 13, primary());
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundBg(isDark() ? 0x552A3544 : 0xCCFFFFFF, dp(16), lineColor()));
        return view;
    }

    private TextView smallChip(String value) {
        TextView view = text(value, 12, accent());
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(roundBg(isDark() ? 0x552A3544 : 0xCCFFFFFF, dp(14), lineColor()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, dp(32));
        lp.rightMargin = dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView smallLink(String value) {
        TextView view = text(value, 12, accent());
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable roundBg(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int panelColor() { return isDark() ? 0xCC111923 : 0xDDF2F5F8; }
    private int lineColor() { return isDark() ? 0x33FFFFFF : 0x55FFFFFF; }
    private int primary() { return isDark() ? Color.WHITE : Color.rgb(24, 34, 43); }
    private int secondary() { return isDark() ? 0xFFB2BEC9 : 0xFF55636E; }
    private int accent() {
        int index = prefs == null ? 0 : prefs.getInt("accent", 0);
        return ACCENTS[Math.max(0, Math.min(index, ACCENTS.length - 1))];
    }
    private boolean isDark() { return prefs == null || prefs.getBoolean("dark", true); }
    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet(FAVORITES, Collections.emptySet()));
    }

    private void toggleFavorite(String packageName) {
        Set<String> set = favorites();
        if (!set.add(packageName)) set.remove(packageName);
        prefs.edit().putStringSet(FAVORITES, set).apply();
    }

    private ArrayList<AppEntry> loadApps() {
        ArrayList<AppEntry> result = new ArrayList<>();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities;
        if (Build.VERSION.SDK_INT >= 33) {
            activities = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0));
        } else {
            activities = pm.queryIntentActivities(intent, 0);
        }
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : activities) {
            String packageName = info.activityInfo.packageName;
            String key = packageName + "/" + info.activityInfo.name;
            if (!seen.add(key)) continue;
            CharSequence label = info.loadLabel(pm);
            Drawable icon;
            try { icon = info.loadIcon(pm); }
            catch (RuntimeException ex) { icon = getDrawable(android.R.drawable.sym_def_app_icon); }
            result.add(new AppEntry(label == null ? packageName : label.toString(), packageName, icon));
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort((a, b) -> collator.compare(a.label, b.label));
        return result;
    }

    private void launch(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            safeStart(intent);
        } else {
            Toast.makeText(this, "이 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String currentWallpaperText() {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(this);
            android.app.WallpaperInfo info = manager.getWallpaperInfo();
            if (info == null) return "현재 배경화면: 정적 배경화면 또는 정보를 확인할 수 없음";
            CharSequence label = info.loadLabel(getPackageManager());
            return "현재 라이브 배경화면: " + (label == null ? info.getPackageName() : label);
        } catch (RuntimeException e) {
            return "현재 배경화면 정보를 읽지 못했습니다.";
        }
    }

    private void openPanel(String action) {
        Intent intent = new Intent(action);
        if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action)) {
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        safeStart(intent);
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "이 기기에서 해당 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        safeStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private boolean notificationAccessEnabled() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, NotificationAccessService.class));
    }

    private void openHomeSettings() {
        safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private void requestWriteSettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            safeStart(intent);
        }
    }

    private int getBrightness() {
        try { return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128); }
        catch (Settings.SettingNotFoundException e) { return 128; }
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    @Override
    public void onBackPressed() {
        if (page != PAGE_HOME) showPage(PAGE_HOME);
        else super.onBackPressed();
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
