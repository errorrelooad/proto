package com.errorrelooad.wavehome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HomeActivity extends Activity {
    private static final String PREFS = "wave_home_settings";
    private static final String FAVORITES_FILTER = "@favorites";
    private static final int REQUEST_HOME_ROLE = 41;
    private static final int ACCENT = 0xFF9BE8CF;
    private static final int PRIMARY = 0xFFF4F7F8;
    private static final int SECONDARY = 0xFF9EABB5;
    private static final int PANEL = 0xCC111923;
    private static final int HAIRLINE = 0x33FFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ArrayList<AppEntry> installedApps = new ArrayList<>();
    private String selectedGroupId = "";
    private String currentQuery = "";

    private TextView clockView;
    private TextView dateView;
    private EditText searchField;
    private TextView clearSearch;
    private LinearLayout categoryRow;
    private LinearLayout contentArea;
    private View defaultHomeCard;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            }
            if (dateView != null) {
                dateView.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(new Date()));
            }
            handler.postDelayed(this, 30_000L);
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

    private static final class AppGroup {
        final String id;
        final String name;

        AppGroup(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedGroupId = prefs.getString("last_group", "");
        configureWindow();
        refreshApps();
        buildUi();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) return;
        refreshApps();
        if (contentArea != null) renderHome();
        updateDefaultHomeCard();
        if (!isDefaultHome() && !prefs.getBoolean("home_role_prompted", false)) {
            prefs.edit().putBoolean("home_role_prompted", true).apply();
            getWindow().getDecorView().postDelayed(this::requestDefaultHome, 450L);
        }
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(9), dp(20), dp(8));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = column();
        TextView brand = label("WAVE HOME", 12, ACCENT);
        brand.setLetterSpacing(0.18f);
        titleStack.addView(brand, params(WRAP, dp(22)));
        dateView = label("", 13, SECONDARY);
        titleStack.addView(dateView, params(WRAP, dp(23)));
        header.addView(titleStack, new LinearLayout.LayoutParams(0, WRAP, 1f));

        clockView = label("", 37, PRIMARY);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(clockView, params(WRAP, dp(48)));

        TextView wallpaper = pillButton("배경");
        wallpaper.setCompoundDrawablesWithIntrinsicBounds(
                getDrawable(android.R.drawable.ic_menu_gallery), null, null, null);
        wallpaper.setCompoundDrawablePadding(dp(5));
        wallpaper.setOnClickListener(v -> openWallpaperStudio());
        LinearLayout.LayoutParams wallpaperLp = params(WRAP, dp(40));
        wallpaperLp.leftMargin = dp(9);
        header.addView(wallpaper, wallpaperLp);
        root.addView(header, params(MATCH, dp(54)));

        defaultHomeCard = buildDefaultHomeCard();
        LinearLayout.LayoutParams homeCardLp = params(MATCH, WRAP);
        homeCardLp.topMargin = dp(7);
        root.addView(defaultHomeCard, homeCardLp);
        updateDefaultHomeCard();

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(15), 0, dp(12), 0);
        search.setBackground(roundBg(PANEL, dp(18), HAIRLINE));
        TextView magnifier = label("⌕", 22, SECONDARY);
        magnifier.setGravity(Gravity.CENTER);
        search.addView(magnifier, params(dp(28), MATCH));
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setTextColor(PRIMARY);
        searchField.setHintTextColor(SECONDARY);
        searchField.setHint("앱 이름 검색");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(dp(8), 0, dp(6), 0);
        search.addView(searchField, new LinearLayout.LayoutParams(0, MATCH, 1f));
        clearSearch = label("×", 24, SECONDARY);
        clearSearch.setGravity(Gravity.CENTER);
        clearSearch.setVisibility(View.GONE);
        clearSearch.setContentDescription("검색 지우기");
        search.addView(clearSearch, params(dp(32), MATCH));
        clearSearch.setOnClickListener(v -> searchField.setText(""));
        LinearLayout.LayoutParams searchLp = params(MATCH, dp(52));
        searchLp.topMargin = dp(10);
        root.addView(search, searchLp);

        LinearLayout categoriesHeading = new LinearLayout(this);
        categoriesHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView categoryLabel = label("분류", 14, PRIMARY);
        categoryLabel.setTypeface(null, Typeface.BOLD);
        categoriesHeading.addView(categoryLabel, new LinearLayout.LayoutParams(0, MATCH, 1f));
        TextView manage = label("관리", 13, ACCENT);
        manage.setGravity(Gravity.CENTER);
        manage.setPadding(dp(12), 0, dp(2), 0);
        manage.setOnClickListener(v -> manageGroups());
        categoriesHeading.addView(manage, params(WRAP, MATCH));
        LinearLayout.LayoutParams categoryHeadingLp = params(MATCH, dp(38));
        categoryHeadingLp.topMargin = dp(8);
        root.addView(categoriesHeading, categoryHeadingLp);

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        categoryScroll.addView(categoryRow);
        root.addView(categoryScroll, params(MATCH, dp(43)));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setClipToPadding(false);
        listScroll.setFillViewport(false);
        contentArea = column();
        contentArea.setPadding(0, dp(4), 0, dp(28));
        listScroll.addView(contentArea);
        root.addView(listScroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                clearSearch.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                renderContent();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        renderHome();
        handler.post(clockTick);
    }

    private View buildDefaultHomeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(4), dp(13), dp(4));
        card.setBackground(roundBg(0xCC1B292B, dp(18), 0x449BE8CF));
        LinearLayout textColumn = column();
        TextView title = label("Wave Home을 기본 홈으로 설정", 14, PRIMARY);
        title.setTypeface(null, Typeface.BOLD);
        textColumn.addView(title);
        textColumn.addView(label("홈 버튼을 누르면 이 화면이 열립니다.", 11, SECONDARY));
        card.addView(textColumn, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView choose = label("설정", 13, ACCENT);
        choose.setGravity(Gravity.CENTER);
        card.addView(choose, params(dp(48), dp(44)));
        card.setOnClickListener(v -> requestDefaultHome());
        return card;
    }

    private void updateDefaultHomeCard() {
        if (defaultHomeCard != null) defaultHomeCard.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
    }

    private boolean isDefaultHome() {
        RoleManager roles = getSystemService(RoleManager.class);
        return roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                && roles.isRoleHeld(RoleManager.ROLE_HOME);
    }

    private void requestDefaultHome() {
        RoleManager roles = getSystemService(RoleManager.class);
        try {
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE);
            } else {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "기본 홈 앱 선택 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWallpaperStudio() {
        startActivity(new Intent(this, WallpaperStudioActivity.class));
    }

    private void renderHome() {
        if (selectedGroupId != null && !selectedGroupId.isEmpty()
                && !FAVORITES_FILTER.equals(selectedGroupId) && findGroup(selectedGroupId) == null) {
            selectedGroupId = "";
            prefs.edit().putString("last_group", "").apply();
        }
        renderCategoryChips();
        renderContent();
        updateDefaultHomeCard();
    }

    private void renderCategoryChips() {
        if (categoryRow == null) return;
        categoryRow.removeAllViews();
        addCategoryChip("전체", "", installedApps.size());
        addCategoryChip("★ 즐겨찾기", FAVORITES_FILTER, favorites().size());
        for (AppGroup group : customGroups()) {
            addCategoryChip(group.name, group.id, countInGroup(group.id));
        }
        TextView add = label("＋", 20, ACCENT);
        add.setGravity(Gravity.CENTER);
        add.setBackground(roundBg(0x44111119, dp(16), HAIRLINE));
        add.setContentDescription("분류 추가");
        LinearLayout.LayoutParams addLp = params(dp(40), dp(34));
        addLp.leftMargin = dp(3);
        categoryRow.addView(add, addLp);
        add.setOnClickListener(v -> createGroup());
    }

    private void addCategoryChip(String title, String id, int count) {
        boolean selected = id.equals(selectedGroupId);
        TextView chip = label(title + "  " + count, 12, selected ? 0xFF10201B : SECONDARY);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), 0, dp(13), 0);
        chip.setBackground(roundBg(selected ? ACCENT : PANEL, dp(16),
                selected ? Color.TRANSPARENT : HAIRLINE));
        LinearLayout.LayoutParams lp = params(WRAP, dp(34));
        lp.rightMargin = dp(7);
        categoryRow.addView(chip, lp);
        chip.setOnClickListener(v -> {
            selectedGroupId = id;
            prefs.edit().putString("last_group", id).apply();
            renderHome();
        });
        if (!id.isEmpty() && !FAVORITES_FILTER.equals(id)) {
            chip.setOnLongClickListener(v -> {
                AppGroup group = findGroup(id);
                if (group != null) manageGroup(group);
                return true;
            });
        }
    }

    private void renderContent() {
        if (contentArea == null) return;
        contentArea.removeAllViews();
        String query = currentQuery.trim().toLowerCase(Locale.getDefault());
        boolean showDiscovery = query.isEmpty() && selectedGroupId.isEmpty();

        if (showDiscovery) {
            addHorizontalSection("즐겨찾기", appsForPackages(favorites()), "별표를 눌러 자주 쓰는 앱을 고정하세요.");
            List<AppEntry> recent = appsForPackages(recentPackages());
            if (!recent.isEmpty()) addHorizontalSection("최근 실행", recent, null);
        }

        ArrayList<AppEntry> filtered = new ArrayList<>();
        Set<String> favoritePackages = favorites();
        for (AppEntry app : installedApps) {
            if (!query.isEmpty() && !app.label.toLowerCase(Locale.getDefault()).contains(query)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (FAVORITES_FILTER.equals(selectedGroupId) && !favoritePackages.contains(app.packageName)) continue;
            if (!selectedGroupId.isEmpty() && !FAVORITES_FILTER.equals(selectedGroupId)
                    && !appGroupIds(app.packageName).contains(selectedGroupId)) continue;
            filtered.add(app);
        }

        AppGroup selectedGroup = findGroup(selectedGroupId);
        String title = FAVORITES_FILTER.equals(selectedGroupId) ? "즐겨찾기"
                : selectedGroup == null ? "모든 앱" : selectedGroup.name;
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 17, PRIMARY);
        heading.setTypeface(null, Typeface.BOLD);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView count = label(filtered.size() + "개", 12, SECONDARY);
        titleRow.addView(count, params(WRAP, dp(42)));
        contentArea.addView(titleRow, params(MATCH, dp(42)));

        if (selectedGroup != null) {
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView addApps = smallAction("＋ 앱 추가");
            actions.addView(addApps, params(WRAP, dp(34)));
            addApps.setOnClickListener(v -> bulkAssignToGroup(selectedGroup));
            TextView edit = smallAction("분류 편집");
            LinearLayout.LayoutParams editLp = params(WRAP, dp(34));
            editLp.leftMargin = dp(6);
            actions.addView(edit, editLp);
            edit.setOnClickListener(v -> manageGroup(selectedGroup));
            LinearLayout.LayoutParams actionsLp = params(MATCH, dp(38));
            actionsLp.bottomMargin = dp(2);
            contentArea.addView(actions, actionsLp);
        } else if (customGroups().isEmpty() && query.isEmpty()) {
            TextView hint = label("분류를 만들고 앱을 묶으면 원하는 방식으로 정리할 수 있어요.", 12, SECONDARY);
            contentArea.addView(hint, params(MATCH, dp(30)));
        }

        if (filtered.isEmpty()) {
            TextView empty = label(query.isEmpty() ? "여기에 표시할 앱이 없습니다." : "검색 결과가 없습니다.", 14, SECONDARY);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyLp = params(MATCH, dp(96));
            contentArea.addView(empty, emptyLp);
            if (selectedGroup != null && query.isEmpty()) {
                TextView addApps = smallAction("이 분류에 앱 추가");
                addApps.setGravity(Gravity.CENTER);
                contentArea.addView(addApps, params(MATCH, dp(46)));
                addApps.setOnClickListener(v -> bulkAssignToGroup(selectedGroup));
            }
            return;
        }

        String lastSection = "";
        for (AppEntry app : filtered) {
            String section = sectionFor(app.label);
            if (!section.equals(lastSection) && query.isEmpty()) {
                TextView letter = label(section, 11, ACCENT);
                letter.setTypeface(null, Typeface.BOLD);
                letter.setLetterSpacing(0.12f);
                LinearLayout.LayoutParams sectionLp = params(MATCH, dp(28));
                sectionLp.topMargin = dp(6);
                contentArea.addView(letter, sectionLp);
                lastSection = section;
            }
            contentArea.addView(appRow(app), params(MATCH, WRAP));
        }
    }

    private void addHorizontalSection(String title, List<AppEntry> apps, String emptyText) {
        if (apps.isEmpty() && emptyText == null) return;
        TextView heading = label(title, 13, SECONDARY);
        heading.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams headingLp = params(MATCH, dp(30));
        headingLp.topMargin = dp(5);
        contentArea.addView(heading, headingLp);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (apps.isEmpty()) {
            TextView empty = label(emptyText, 12, SECONDARY);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(empty, params(WRAP, dp(64)));
        } else {
            for (AppEntry app : apps) {
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(6), dp(4), dp(6), dp(2));
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(app.icon);
                tile.addView(icon, new LinearLayout.LayoutParams(dp(37), dp(37)));
                TextView name = label(shorten(app.label, 10), 10, PRIMARY);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(name, params(dp(68), dp(20)));
                tile.setOnClickListener(v -> launch(app));
                tile.setOnLongClickListener(v -> {
                    showAppActions(app);
                    return true;
                });
                row.addView(tile, params(dp(76), dp(66)));
            }
        }
        scroller.addView(row);
        contentArea.addView(scroller, params(MATCH, dp(68)));
    }

    private View appRow(AppEntry app) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(4), 0, dp(4));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout textColumn = column();
        TextView name = label(app.label, 14, PRIMARY);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(name, params(MATCH, dp(23)));
        String groupNames = groupNamesFor(app.packageName);
        TextView details = label(groupNames.isEmpty() ? app.packageName : groupNames, 10, SECONDARY);
        details.setSingleLine(true);
        details.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(details, params(MATCH, dp(18)));
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        textLp.leftMargin = dp(12);
        row.addView(textColumn, textLp);

        TextView categories = label("⊞", 18, groupNames.isEmpty() ? SECONDARY : ACCENT);
        categories.setGravity(Gravity.CENTER);
        categories.setContentDescription("분류 지정");
        row.addView(categories, params(dp(40), dp(48)));
        categories.setOnClickListener(v -> editAppGroups(app));

        boolean isFavorite = favorites().contains(app.packageName);
        TextView star = label(isFavorite ? "★" : "☆", 19, isFavorite ? ACCENT : SECONDARY);
        star.setGravity(Gravity.CENTER);
        star.setContentDescription(isFavorite ? "즐겨찾기 해제" : "즐겨찾기 추가");
        row.addView(star, params(dp(40), dp(48)));
        star.setOnClickListener(v -> toggleFavorite(app.packageName));

        row.setMinimumHeight(dp(56));
        row.setOnClickListener(v -> launch(app));
        row.setOnLongClickListener(v -> {
            showAppActions(app);
            return true;
        });
        View divider = new View(this);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dividerLp = params(MATCH, dp(1));
        LinearLayout item = column();
        item.addView(row, params(MATCH, dp(58)));
        item.addView(divider, dividerLp);
        return item;
    }

    private void showAppActions(AppEntry app) {
        String favoriteAction = favorites().contains(app.packageName)
                ? "즐겨찾기에서 빼기" : "즐겨찾기에 추가";
        new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(new String[]{favoriteAction, "분류 지정", "앱 정보"}, (dialog, which) -> {
                    if (which == 0) toggleFavorite(app.packageName);
                    else if (which == 1) editAppGroups(app);
                    else {
                        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + app.packageName));
                        try { startActivity(details); }
                        catch (ActivityNotFoundException ignored) { }
                    }
                }).show();
    }

    private void toggleFavorite(String packageName) {
        Set<String> next = favorites();
        if (!next.add(packageName)) next.remove(packageName);
        prefs.edit().putStringSet("favorites", next).apply();
        renderHome();
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
    }

    private void editAppGroups(AppEntry app) {
        ArrayList<AppGroup> groups = customGroups();
        if (groups.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("분류가 없습니다")
                    .setMessage("먼저 분류를 만든 다음 이 앱을 배정할 수 있습니다.")
                    .setNegativeButton("닫기", null)
                    .setPositiveButton("분류 만들기", (d, w) -> createGroup()).show();
            return;
        }
        String[] names = new String[groups.size()];
        String[] ids = new String[groups.size()];
        boolean[] checked = new boolean[groups.size()];
        Set<String> assigned = appGroupIds(app.packageName);
        for (int i = 0; i < groups.size(); i++) {
            AppGroup group = groups.get(i);
            ids[i] = group.id;
            names[i] = group.name;
            checked[i] = assigned.contains(group.id);
        }
        new AlertDialog.Builder(this).setTitle(app.label + " · 분류")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    JSONObject memberships = memberships();
                    JSONArray selected = new JSONArray();
                    for (int i = 0; i < ids.length; i++) if (checked[i]) selected.put(ids[i]);
                    if (selected.length() == 0) memberships.remove(app.packageName);
                    else putJson(memberships, app.packageName, selected);
                    saveMemberships(memberships);
                }).show();
    }

    private void bulkAssignToGroup(AppGroup group) {
        String[] names = new String[installedApps.size()];
        boolean[] checked = new boolean[installedApps.size()];
        for (int i = 0; i < installedApps.size(); i++) {
            AppEntry app = installedApps.get(i);
            names[i] = app.label;
            checked[i] = appGroupIds(app.packageName).contains(group.id);
        }
        new AlertDialog.Builder(this).setTitle("‘" + group.name + "’에 앱 추가")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    JSONObject memberships = memberships();
                    for (int i = 0; i < installedApps.size(); i++) {
                        AppEntry app = installedApps.get(i);
                        JSONArray old = memberships.optJSONArray(app.packageName);
                        ArrayList<String> ids = new ArrayList<>();
                        if (old != null) for (int j = 0; j < old.length(); j++) {
                            String id = old.optString(j);
                            if (!group.id.equals(id)) ids.add(id);
                        }
                        if (checked[i]) ids.add(group.id);
                        JSONArray next = new JSONArray();
                        for (String id : ids) next.put(id);
                        if (next.length() == 0) memberships.remove(app.packageName);
                        else putJson(memberships, app.packageName, next);
                    }
                    saveMemberships(memberships);
                }).show();
    }

    private void saveMemberships(JSONObject memberships) {
        prefs.edit().putString("app_groups", memberships.toString()).apply();
        renderHome();
    }

    private JSONObject memberships() {
        try { return new JSONObject(prefs.getString("app_groups", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private Set<String> appGroupIds(String packageName) {
        HashSet<String> ids = new HashSet<>();
        JSONArray saved = memberships().optJSONArray(packageName);
        if (saved != null) {
            for (int i = 0; i < saved.length(); i++) ids.add(saved.optString(i));
        }
        return ids;
    }

    private String groupNamesFor(String packageName) {
        Set<String> assigned = appGroupIds(packageName);
        ArrayList<String> names = new ArrayList<>();
        for (AppGroup group : customGroups()) if (assigned.contains(group.id)) names.add(group.name);
        return android.text.TextUtils.join(" · ", names);
    }

    private ArrayList<AppGroup> customGroups() {
        ArrayList<AppGroup> groups = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("custom_groups", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                String name = item.optString("name", "").trim();
                if (!id.isEmpty() && !name.isEmpty()) groups.add(new AppGroup(id, name));
            }
        } catch (Exception ignored) { }
        return groups;
    }

    private AppGroup findGroup(String id) {
        if (id == null || id.isEmpty() || FAVORITES_FILTER.equals(id)) return null;
        for (AppGroup group : customGroups()) if (group.id.equals(id)) return group;
        return null;
    }

    private void createGroup() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextSize(16);
        input.setTextColor(PRIMARY);
        input.setHintTextColor(SECONDARY);
        input.setHint("예: 공부, 사진, 자주 사용");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this).setTitle("새 분류 만들기").setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("만들기", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "분류 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (AppGroup group : customGroups()) {
                        if (group.name.equalsIgnoreCase(name)) {
                            Toast.makeText(this, "같은 이름의 분류가 이미 있습니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    ArrayList<AppGroup> groups = customGroups();
                    String id = UUID.randomUUID().toString();
                    groups.add(new AppGroup(id, name));
                    saveGroups(groups);
                    selectedGroupId = id;
                    prefs.edit().putString("last_group", id).apply();
                    renderHome();
                }).show();
    }

    private void manageGroups() {
        ArrayList<AppGroup> groups = customGroups();
        if (groups.isEmpty()) {
            createGroup();
            return;
        }
        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            AppGroup group = groups.get(i);
            names[i] = group.name + "  ·  " + countInGroup(group.id) + "개";
        }
        new AlertDialog.Builder(this).setTitle("분류 관리")
                .setItems(names, (dialog, which) -> manageGroup(groups.get(which)))
                .setNeutralButton("＋ 새 분류", (dialog, which) -> createGroup())
                .setNegativeButton("닫기", null)
                .show();
    }

    private void manageGroup(AppGroup group) {
        new AlertDialog.Builder(this).setTitle(group.name)
                .setItems(new String[]{"이름 바꾸기", "앱 추가 또는 정리", "분류 삭제"}, (dialog, which) -> {
                    if (which == 0) renameGroup(group);
                    else if (which == 1) bulkAssignToGroup(group);
                    else confirmDeleteGroup(group);
                }).show();
    }

    private void renameGroup(AppGroup group) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(group.name);
        input.setSelectAllOnFocus(true);
        input.setTextColor(PRIMARY);
        new AlertDialog.Builder(this).setTitle("분류 이름 바꾸기").setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    for (AppGroup item : customGroups()) {
                        if (!item.id.equals(group.id) && item.name.equalsIgnoreCase(name)) {
                            Toast.makeText(this, "같은 이름의 분류가 이미 있습니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    ArrayList<AppGroup> groups = customGroups();
                    ArrayList<AppGroup> renamed = new ArrayList<>();
                    for (AppGroup item : groups) {
                        renamed.add(item.id.equals(group.id) ? new AppGroup(item.id, name) : item);
                    }
                    saveGroups(renamed);
                    renderHome();
                }).show();
    }

    private void confirmDeleteGroup(AppGroup group) {
        new AlertDialog.Builder(this).setTitle("분류 삭제")
                .setMessage("‘" + group.name + "’에 저장된 앱 배정도 함께 지웁니다. 앱 자체는 삭제되지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    ArrayList<AppGroup> groups = customGroups();
                    groups.removeIf(item -> item.id.equals(group.id));
                    saveGroups(groups);
                    JSONObject data = memberships();
                    JSONObject cleaned = new JSONObject();
                    java.util.Iterator<String> keys = data.keys();
                    while (keys.hasNext()) {
                        String packageName = keys.next();
                        JSONArray old = data.optJSONArray(packageName);
                        JSONArray next = new JSONArray();
                        if (old != null) for (int i = 0; i < old.length(); i++) {
                            String id = old.optString(i);
                            if (!group.id.equals(id)) next.put(id);
                        }
                        if (next.length() > 0) putJson(cleaned, packageName, next);
                    }
                    prefs.edit().putString("app_groups", cleaned.toString()).apply();
                    if (group.id.equals(selectedGroupId)) selectedGroupId = "";
                    prefs.edit().putString("last_group", selectedGroupId).apply();
                    renderHome();
                }).show();
    }

    private void saveGroups(ArrayList<AppGroup> groups) {
        JSONArray json = new JSONArray();
        for (AppGroup group : groups) {
            JSONObject item = new JSONObject();
            putJson(item, "id", group.id);
            putJson(item, "name", group.name);
            json.put(item);
        }
        prefs.edit().putString("custom_groups", json.toString()).apply();
    }

    private void putJson(JSONObject object, String key, Object value) {
        try { object.put(key, value); }
        catch (org.json.JSONException ignored) { }
    }

    private int countInGroup(String id) {
        int count = 0;
        for (AppEntry app : installedApps) if (appGroupIds(app.packageName).contains(id)) count++;
        return count;
    }

    private List<AppEntry> appsForPackages(Set<String> packages) {
        ArrayList<AppEntry> found = new ArrayList<>();
        for (AppEntry app : installedApps) if (packages.contains(app.packageName)) found.add(app);
        return found;
    }

    private List<AppEntry> appsForPackages(List<String> packages) {
        ArrayList<AppEntry> found = new ArrayList<>();
        for (String packageName : packages) {
            for (AppEntry app : installedApps) if (packageName.equals(app.packageName)) {
                found.add(app);
                break;
            }
        }
        return found;
    }

    private ArrayList<String> recentPackages() {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("recent_apps", "[]"));
            for (int i = 0; i < saved.length(); i++) result.add(saved.optString(i));
        } catch (Exception ignored) { }
        return result;
    }

    private void recordRecent(String packageName) {
        ArrayList<String> recents = recentPackages();
        recents.remove(packageName);
        recents.add(0, packageName);
        while (recents.size() > 8) recents.remove(recents.size() - 1);
        JSONArray json = new JSONArray();
        for (String item : recents) json.put(item);
        prefs.edit().putString("recent_apps", json.toString()).apply();
    }

    private void refreshApps() {
        PackageManager manager = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = manager.queryIntentActivities(query, 0);
        HashMap<String, AppEntry> unique = new HashMap<>();
        for (ResolveInfo result : results) {
            if (result.activityInfo == null) continue;
            String packageName = result.activityInfo.packageName;
            if (getPackageName().equals(packageName) || unique.containsKey(packageName)) continue;
            CharSequence label = result.loadLabel(manager);
            Drawable icon = result.loadIcon(manager);
            unique.put(packageName, new AppEntry(
                    label == null ? packageName : label.toString().trim(), packageName, icon));
        }
        installedApps = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(installedApps, (left, right) -> {
            int byName = collator.compare(left.label, right.label);
            return byName != 0 ? byName : left.packageName.compareTo(right.packageName);
        });
    }

    private void launch(AppEntry app) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (intent == null) {
            Toast.makeText(this, "이 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        recordRecent(app.packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "이 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String sectionFor(String label) {
        if (label == null || label.isEmpty()) return "#";
        char first = label.charAt(0);
        if (first >= 0xAC00 && first <= 0xD7A3) {
            int index = (first - 0xAC00) / (21 * 28);
            String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
            if (index < initials.length()) return String.valueOf(initials.charAt(index));
        }
        if (Character.isLetter(first)) return String.valueOf(Character.toUpperCase(first));
        return "#";
    }

    private String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private TextView smallAction(String value) {
        TextView view = label(value, 12, ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackground(roundBg(0x44111119, dp(14), HAIRLINE));
        return view;
    }

    private TextView pillButton(String value) {
        TextView view = label(value, 12, ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(roundBg(0x44111119, dp(16), HAIRLINE));
        return view;
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private GradientDrawable roundBg(int color, int radius, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) bg.setStroke(dp(1), stroke);
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    @Override
    public void onBackPressed() {
        if (searchField != null && searchField.length() > 0) {
            searchField.setText("");
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            return;
        }
        if (!isDefaultHome()) super.onBackPressed();
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
