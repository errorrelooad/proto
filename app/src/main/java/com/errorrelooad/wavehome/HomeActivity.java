package com.errorrelooad.wavehome;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.Manifest;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.CheckBox;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;

public class HomeActivity extends Activity {
    private static final String PREFS = "wave_home_settings";
    private static final String FAVORITES_FILTER = "@favorites";
    private static final int REQUEST_HOME_ROLE = 41;
    private int ACCENT = 0xFFB8F28B;
    private int PRIMARY = 0xFFF3F4EE;
    private int SECONDARY = 0xFF9DA39B;
    private int PANEL = 0xCC171B18;
    private int HAIRLINE = 0x30FFFFFF;
    private static final int REQUEST_CALENDAR = 51;
    private static final int REQUEST_ICON = 52;
    private static final int REQUEST_THEME_IMPORT = 53;
    private static final int REQUEST_THEME_EXPORT = 54;
    private static final int REQUEST_PICK_WIDGET = 55;
    private static final int REQUEST_BIND_WIDGET = 56;
    private static final int REQUEST_CONFIGURE_WIDGET = 57;
    private static final int WIDGET_HOST_ID = 73021;
    private static final int REQUEST_LOCK_ADMIN = 58;
    private static final int REQUEST_USAGE_ACCESS = 59;
    private static final int REQUEST_CONTACTS = 60;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ArrayList<AppEntry> installedApps = new ArrayList<>();
    private String selectedGroupId = "";
    private String currentQuery = "";
    private String pendingIconPackage = "";
    private int pendingWidgetId = -1;
    private String weatherText = "날씨 설정";
    private boolean headsetConnected;
    private boolean widgetListening;
    private boolean weatherRefreshing;
    private long lastClockTap;
    private long lastAlphabetTap;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppWidgetHost widgetHost;
    private AlertDialog widgetDialog;
    private ScrollView listScroll;
    private AlphabetIndexView alphabetRail;
    private TextView weatherView;
    private TextView agendaView;
    private final HashMap<String, Integer> sectionY = new HashMap<>();
    private final ArrayList<String> sectionOrder = new ArrayList<>();
    private final BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            headsetConnected = Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())
                    && intent.getIntExtra("state", 0) == 1;
            if (contentArea != null) renderContent();
        }
    };

    private TextView clockView;
    private TextView dateView;
    private EditText searchField;
    private TextView clearSearch;
    private LinearLayout categoryRow;
    private LinearLayout searchContainer;
    private LinearLayout contentArea;
    private LinearLayout homeRoot;
    private View defaultHomeCard;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                boolean use24 = prefs == null || prefs.getBoolean("clock_24h", true);
                clockView.setText(new SimpleDateFormat(use24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date()));
            }
            if (dateView != null) {
                dateView.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.getDefault()).format(new Date()));
            }
            checkUsageBreaker();
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


    private final class AlphabetIndexView extends View {
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<String> sections = new ArrayList<>();
        private int activeIndex = -1;
        private float downY;
        private boolean moved;

        AlphabetIndexView() {
            super(HomeActivity.this);
            setClickable(true);
            setFocusable(true);
            setContentDescription("앱 알파벳 탐색");
            textPaint.setTextAlign(Paint.Align.CENTER);
            activePaint.setColor(Color.argb(35, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT)));
        }

        void setSections(List<String> values) {
            sections = new ArrayList<>(values);
            if (activeIndex >= sections.size()) activeIndex = -1;
            setVisibility(sections.isEmpty() ? View.GONE : View.VISIBLE);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int count = sections.size();
            if (count == 0) return;
            float top = getPaddingTop();
            float usable = Math.max(1, getHeight() - top - getPaddingBottom());
            float cell = usable / count;
            float size = Math.min(dp(11), cell * 0.62f);
            textPaint.setTextSize(size);
            for (int i = 0; i < count; i++) {
                float centerY = top + cell * (i + 0.5f);
                if (i == activeIndex) {
                    float radius = Math.min(dp(13), cell * 0.43f);
                    canvas.drawRoundRect(getWidth() / 2f - radius, centerY - radius,
                            getWidth() / 2f + radius, centerY + radius,
                            radius, radius, activePaint);
                    textPaint.setColor(ACCENT);
                } else {
                    textPaint.setColor(SECONDARY);
                }
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = centerY - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(sections.get(i), getWidth() / 2f, baseline, textPaint);
            }
        }

        private int indexAt(float y) {
            if (sections.isEmpty()) return -1;
            float usable = Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom());
            float relative = Math.max(0, Math.min(0.999f,
                    (y - getPaddingTop()) / usable));
            return Math.min(sections.size() - 1, (int) (relative * sections.size()));
        }

        private void selectAt(float y) {
            int index = indexAt(y);
            if (index < 0) return;
            activeIndex = index;
            String section = sections.get(index);
            Integer childIndex = sectionY.get(section);
            if (childIndex != null && childIndex < contentArea.getChildCount()) {
                contentArea.post(() -> {
                    if (listScroll != null && contentArea != null
                            && childIndex < contentArea.getChildCount()) {
                        if (moved) listScroll.scrollTo(0, contentArea.getChildAt(childIndex).getTop());
                        else listScroll.smoothScrollTo(0, contentArea.getChildAt(childIndex).getTop());
                    }
                });
            }
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getY();
                    moved = false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    selectAt(event.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getY() - downY) > dp(5)) moved = true;
                    selectAt(event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && prefs.getBoolean("double_tap_lock", false)) {
                        long now = System.currentTimeMillis();
                        if (now - lastAlphabetTap < 420) {
                            lastAlphabetTap = 0;
                            lockScreen();
                        } else {
                            lastAlphabetTap = now;
                        }
                    }
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }


    private final class SearchGlyphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SearchGlyphView(int tint) {
            super(HomeActivity.this);
            paint.setColor(tint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(5);
            canvas.drawCircle(cx - dp(1), cy - dp(1), r, paint);
            canvas.drawLine(cx + dp(3), cy + dp(3), cx + dp(8), cy + dp(8), paint);
        }
    }

    private static final class MonogramDrawable extends Drawable {
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String monogram;

        MonogramDrawable(String label, String packageName) {
            float hue = Math.floorMod(packageName.hashCode(), 360);
            background.setColor(Color.HSVToColor(new float[]{hue, 0.30f, 0.66f}));
            letters.setColor(0xFFF9F8F4);
            letters.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            monogram = label == null || label.trim().isEmpty() ? "•"
                    : new String(Character.toChars(label.trim().codePointAt(0))).toUpperCase(Locale.getDefault());
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float inset = Math.min(bounds.width(), bounds.height()) * 0.04f;
            RectF shape = new RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
            canvas.drawRoundRect(shape, shape.width() * 0.30f, shape.width() * 0.30f, background);
            letters.setTextSize(shape.width() * (monogram.length() > 1 ? 0.43f : 0.50f));
            Paint.FontMetrics fm = letters.getFontMetrics();
            float baseline = shape.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(monogram, shape.centerX() - letters.measureText(monogram) / 2f, baseline, letters);
        }

        @Override public void setAlpha(int alpha) { background.setAlpha(alpha); letters.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { background.setColorFilter(filter); letters.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedGroupId = prefs.getString("last_group", "");
        applyThemePalette();
        widgetHost = new AppWidgetHost(this, WIDGET_HOST_ID);
        configureWindow();
        refreshApps();
        buildUi();
        startHeadsetMonitor();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (prefs.getBoolean("hide_status_bar", false)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
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
        if (widgetHost != null && !widgetListening) {
            try { widgetHost.startListening(); widgetListening = true; }
            catch (RuntimeException ignored) { }
        }
        refreshWeather();
        checkUsageBreaker();
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
        if (widgetHost != null && widgetListening) {
            try { widgetHost.stopListening(); } catch (RuntimeException ignored) { }
            widgetListening = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        try { unregisterReceiver(headsetReceiver); } catch (IllegalArgumentException ignored) { }
        io.shutdownNow();
        super.onDestroy();
    }


    private void buildUi() {
        handler.removeCallbacks(clockTick);
        LinearLayout root = new LinearLayout(this);
        homeRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(25), dp(10), dp(7), dp(7));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = column();
        clockView = label("", prefs.getInt("clock_size", 39), PRIMARY);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setGravity(Gravity.CENTER_VERTICAL);
        clockView.setContentDescription("시계. 두 번 누르면 화면 잠금");
        titleStack.addView(clockView, params(WRAP, dp(52)));
        dateView = label("", 12, SECONDARY);
        dateView.setLetterSpacing(0.015f);
        titleStack.addView(dateView, params(WRAP, dp(19)));
        header.addView(titleStack, new LinearLayout.LayoutParams(0, WRAP, 1f));

        SearchGlyphView searchButton = new SearchGlyphView(SECONDARY);
        searchButton.setContentDescription("앱 검색");
        header.addView(searchButton, params(dp(44), dp(48)));
        searchButton.setOnClickListener(v -> {
            boolean show = searchContainer.getVisibility() != View.VISIBLE;
            if (!show && searchField.length() > 0) searchField.setText("");
            setSearchVisible(show, show);
        });

        TextView more = label("⋯", 25, SECONDARY);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("홈 화면 메뉴");
        more.setOnClickListener(v -> showFeatureHub());
        header.addView(more, params(dp(40), dp(48)));
        root.addView(header, params(MATCH, dp(74)));

        clockView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && prefs.getBoolean("double_tap_lock", false)) {
                long now = System.currentTimeMillis();
                if (now - lastClockTap < 420) {
                    lockScreen();
                    lastClockTap = 0;
                } else lastClockTap = now;
            }
            return false;
        });

        defaultHomeCard = buildDefaultHomeCard();
        LinearLayout.LayoutParams homeCardLp = params(MATCH, WRAP);
        root.addView(defaultHomeCard, homeCardLp);
        updateDefaultHomeCard();

        LinearLayout glance = new LinearLayout(this);
        glance.setGravity(Gravity.CENTER_VERTICAL);
        glance.setPadding(0, 0, dp(2), 0);
        weatherView = label("날씨 설정", 12, SECONDARY);
        weatherView.setSingleLine(true);
        weatherView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherView.setOnClickListener(v -> showWeatherLocationDialog());
        glance.addView(weatherView, new LinearLayout.LayoutParams(0, MATCH, 1f));
        View separator = new View(this);
        separator.setBackgroundColor(HAIRLINE);
        glance.addView(separator, params(dp(1), dp(16)));
        agendaView = label("일정 보기  ›", 12, SECONDARY);
        agendaView.setGravity(Gravity.CENTER);
        agendaView.setSingleLine(true);
        agendaView.setOnClickListener(v -> showAgenda());
        LinearLayout.LayoutParams agendaLp = params(dp(112), MATCH);
        agendaLp.leftMargin = dp(7);
        glance.addView(agendaView, agendaLp);
        LinearLayout.LayoutParams glanceLp = params(MATCH, dp(34));
        glanceLp.topMargin = dp(4);
        root.addView(glance, glanceLp);

        searchContainer = column();
        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(2), 0, dp(4), 0);
        SearchGlyphView magnifier = new SearchGlyphView(ACCENT);
        magnifier.setContentDescription("검색어");
        search.addView(magnifier, params(dp(30), MATCH));
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(15);
        searchField.setTextColor(PRIMARY);
        searchField.setHintTextColor(SECONDARY);
        searchField.setHint("앱, 연락처 검색");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(dp(7), 0, dp(6), 0);
        searchField.setTypeface(Typeface.create(prefs.getString("font_family", "sans-serif"), Typeface.NORMAL));
        search.addView(searchField, new LinearLayout.LayoutParams(0, MATCH, 1f));
        clearSearch = label("×", 23, SECONDARY);
        clearSearch.setGravity(Gravity.CENTER);
        clearSearch.setVisibility(View.GONE);
        clearSearch.setContentDescription("검색 지우기");
        search.addView(clearSearch, params(dp(34), MATCH));
        clearSearch.setOnClickListener(v -> searchField.setText(""));
        searchContainer.addView(search, params(MATCH, dp(42)));
        View searchRule = new View(this);
        searchRule.setBackgroundColor(HAIRLINE);
        searchContainer.addView(searchRule, params(MATCH, dp(1)));
        searchContainer.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams searchLp = params(MATCH, WRAP);
        searchLp.topMargin = dp(2);
        root.addView(searchContainer, searchLp);

        LinearLayout listAndIndex = new LinearLayout(this);
        listAndIndex.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams listAndIndexLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        listScroll = new ScrollView(this);
        listScroll.setClipToPadding(false);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(false);
        contentArea = column();
        contentArea.setPadding(0, dp(5), dp(3), dp(20));
        listScroll.addView(contentArea);
        listAndIndex.addView(listScroll, new LinearLayout.LayoutParams(0, MATCH, 1f));
        alphabetRail = new AlphabetIndexView();
        alphabetRail.setPadding(0, dp(7), 0, dp(7));
        listAndIndex.addView(alphabetRail, params(dp(27), MATCH));
        root.addView(listAndIndex, listAndIndexLp);

        setContentView(root);
        if (!currentQuery.isEmpty()) {
            searchField.setText(currentQuery);
            clearSearch.setVisibility(View.VISIBLE);
        }
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                clearSearch.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                renderContent();
                renderAlphabetRail();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchField.setOnEditorActionListener((text, actionId, event) -> {
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            return true;
        });
        renderHome();
        refreshWeather();
        handler.post(clockTick);
    }

    private void setSearchVisible(boolean visible, boolean focus) {
        if (searchContainer == null) return;
        searchContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null && searchField != null) input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            return;
        }
        if (focus && searchField != null) {
            searchField.requestFocus();
            searchField.post(() -> {
                InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input != null) input.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
            });
        }
    }


    private TextView footerAction(String glyph, String title) {
        TextView action = label(glyph + "\n" + title, 11, SECONDARY);
        action.setGravity(Gravity.CENTER);
        action.setLineSpacing(0, 0.9f);
        action.setBackground(roundBg(0x55171B18, dp(15), Color.TRANSPARENT));
        action.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.72f);
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
            return false;
        });
        return action;
    }

    private String quickActionLabel(String value) {
        if (value.startsWith("app:")) {
            String pkg = value.substring(4);
            for (AppEntry app : installedApps) if (pkg.equals(app.packageName)) return shorten(app.label, 8);
        }
        if (value.startsWith("group:")) {
            AppGroup group = findGroup(value.substring(6));
            if (group != null) return shorten(group.name, 8);
        }
        return "";
    }

    private void chooseQuickAction(String key) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        labels.add("지정 해제"); values.add("");
        for (AppEntry app : installedApps) {
            labels.add("앱 · " + app.label);
            values.add("app:" + app.packageName);
        }
        for (AppGroup group : customGroups()) {
            labels.add("모음 · " + group.name);
            values.add("group:" + group.id);
        }
        new AlertDialog.Builder(this).setTitle(key.equals("quick_button") ? "버튼 동작" : "위로 쓸기 동작")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    prefs.edit().putString(key, values.get(which)).apply();
                    buildUi();
                    Toast.makeText(this, "버튼 동작을 저장했습니다.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void runQuickAction(String key) {
        String action = prefs.getString(key, "");
        if (action.startsWith("app:")) {
            String pkg = action.substring(4);
            for (AppEntry app : installedApps) if (pkg.equals(app.packageName)) { launch(app); return; }
        } else if (action.startsWith("group:")) {
            AppGroup group = findGroup(action.substring(6));
            if (group != null) { showGroupPopup(group); return; }
        }
        if (key.equals("quick_button_secondary")) chooseQuickAction(key);
        else showFeatureHub();
    }


    private View buildDefaultHomeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(0, 0, 0, 0);
        LinearLayout textColumn = column();
        TextView title = label("기본 홈으로 설정", 13, ACCENT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textColumn.addView(title, params(WRAP, dp(21)));
        textColumn.addView(label("홈 버튼을 누르면 Wave가 열립니다.", 11, SECONDARY), params(WRAP, dp(17)));
        card.addView(textColumn, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView choose = label("설정  ›", 12, ACCENT);
        choose.setGravity(Gravity.CENTER);
        card.addView(choose, params(dp(60), dp(40)));
        card.setMinimumHeight(dp(46));
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
        renderAlphabetRail();
        updateDefaultHomeCard();
    }

    private void renderCategoryChips() {
        if (categoryRow != null) categoryRow.removeAllViews();
    }

    private void renderContent() {
        if (contentArea == null) return;
        contentArea.removeAllViews();
        sectionY.clear();
        sectionOrder.clear();

        String query = currentQuery.trim().toLowerCase(Locale.getDefault());
        boolean showFavorites = query.isEmpty() && selectedGroupId.isEmpty();

        if (showFavorites) {
            List<AppEntry> favoriteApps = orderedFavorites();
            TextView favoriteHeading = label("즐겨찾기", 14, PRIMARY);
            favoriteHeading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams favHeadingLp = params(MATCH, dp(32));
            contentArea.addView(favoriteHeading, favHeadingLp);
            if (favoriteApps.isEmpty()) {
                TextView hint = label("앱을 길게 눌러 즐겨찾기에 추가하세요.", 12, SECONDARY);
                contentArea.addView(hint, params(MATCH, dp(30)));
            } else {
                for (AppEntry app : favoriteApps) {
                    contentArea.addView(appRow(app, true), params(MATCH, WRAP));
                }
            }
            if (headsetConnected) addMusicSuggestion();
            View divider = new View(this);
            divider.setBackgroundColor(HAIRLINE);
            LinearLayout.LayoutParams dividerLp = params(MATCH, dp(1));
            dividerLp.topMargin = dp(7);
            contentArea.addView(divider, dividerLp);
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
        TextView heading = label(title, 14, PRIMARY);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, dp(43), 1f));
        String filterName = selectedGroup != null ? selectedGroup.name
                : FAVORITES_FILTER.equals(selectedGroupId) ? "즐겨찾기" : "전체 앱";
        TextView filter = label("⌄  " + filterName, 12, ACCENT);
        filter.setGravity(Gravity.CENTER);
        filter.setPadding(dp(8), 0, dp(2), 0);
        filter.setContentDescription("앱 분류 선택");
        titleRow.addView(filter, params(WRAP, dp(42)));
        filter.setOnClickListener(v -> showGroupPicker());
        filter.setOnLongClickListener(v -> {
            if (selectedGroup == null) {
                manageGroups();
            } else {
                new AlertDialog.Builder(this).setTitle(selectedGroup.name)
                        .setItems(new String[]{"팝업으로 열기", "앱 추가", "분류 편집"}, (dialog, which) -> {
                            if (which == 0) showGroupPopup(selectedGroup);
                            else if (which == 1) bulkAssignToGroup(selectedGroup);
                            else manageGroup(selectedGroup);
                        }).show();
            }
            return true;
        });
        contentArea.addView(titleRow, params(MATCH, dp(43)));

        if (selectedGroup != null) {
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView addApps = smallAction("＋ 앱 추가");
            actions.addView(addApps, params(WRAP, dp(32)));
            addApps.setOnClickListener(v -> bulkAssignToGroup(selectedGroup));
            TextView edit = smallAction("분류 편집");
            LinearLayout.LayoutParams editLp = params(WRAP, dp(32));
            editLp.leftMargin = dp(6);
            actions.addView(edit, editLp);
            edit.setOnClickListener(v -> manageGroup(selectedGroup));
            contentArea.addView(actions, params(MATCH, dp(36)));
        }

        if (filtered.isEmpty()) {
            String message = query.isEmpty() ? "이 분류에는 앱이 없습니다." : "검색 결과가 없습니다.";
            TextView empty = label(message, 13, SECONDARY);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            contentArea.addView(empty, params(MATCH, dp(64)));
            if (selectedGroup != null && query.isEmpty()) {
                TextView addApps = smallAction("이 분류에 앱 추가");
                addApps.setGravity(Gravity.CENTER);
                contentArea.addView(addApps, params(MATCH, dp(42)));
                addApps.setOnClickListener(v -> bulkAssignToGroup(selectedGroup));
            }
            if (!query.isEmpty()) {
                addContactSearchRow();
                addWebSearchRow();
            }
            return;
        }

        String lastSection = "";
        for (AppEntry app : filtered) {
            String section = sectionFor(app.label);
            if (!section.equals(lastSection) && query.isEmpty()) {
                sectionY.put(section, contentArea.getChildCount());
                sectionOrder.add(section);
                TextView letter = label(section, 10, ACCENT);
                letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                letter.setLetterSpacing(0.09f);
                LinearLayout.LayoutParams sectionLp = params(MATCH, dp(27));
                sectionLp.topMargin = dp(5);
                contentArea.addView(letter, sectionLp);
                lastSection = section;
            }
            contentArea.addView(appRow(app), params(MATCH, WRAP));
        }
        if (!query.isEmpty()) {
            addContactSearchRow();
            addWebSearchRow();
        }
    }

    private void showGroupPicker() {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        labels.add("전체 앱   ·   " + installedApps.size());
        ids.add("");
        labels.add("즐겨찾기   ·   " + favorites().size());
        ids.add(FAVORITES_FILTER);
        for (AppGroup group : customGroups()) {
            labels.add(group.name + "   ·   " + countInGroup(group.id));
            ids.add(group.id);
        }
        new AlertDialog.Builder(this).setTitle("앱 분류")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which < 0 || which >= ids.size()) return;
                    selectedGroupId = ids.get(which);
                    prefs.edit().putString("last_group", selectedGroupId).apply();
                    if (listScroll != null) listScroll.scrollTo(0, 0);
                    renderHome();
                })
                .setNeutralButton("분류 관리", (dialog, which) -> manageGroups())
                .setPositiveButton("새 분류", (dialog, which) -> createGroup())
                .setNegativeButton("닫기", null)
                .show();
    }


    private void addContactSearchRow() {
        TextView contacts = label("⌕  연락처에서 ‘" + currentQuery.trim() + "’ 찾기  ›", 13, SECONDARY);
        contacts.setPadding(dp(8), 0, dp(8), 0);
        contacts.setBackground(roundBg(0x44171B18, dp(14), HAIRLINE));
        LinearLayout.LayoutParams lp = params(MATCH, dp(42));
        lp.topMargin = dp(8);
        contentArea.addView(contacts, lp);
        contacts.setOnClickListener(v -> showContactSearchDialog());
    }

    private void showContactSearchDialog() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("연락처 검색")
                    .setMessage("입력한 검색어와 일치하는 연락처 이름과 전화번호만 찾습니다.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("계속", (d, w) -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS))
                    .show();
            return;
        }
        String query = currentQuery.trim();
        if (query.isEmpty()) return;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR "
                + ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
        String match = "%" + query + "%";
        ArrayList<String> rows = new ArrayList<>();
        ArrayList<String> numbers = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, new String[]{match, match},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext() && rows.size() < 20) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    rows.add((name == null ? "연락처" : name) + "\n" + (number == null ? "" : number));
                    numbers.add(number == null ? "" : number);
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, "연락처를 읽을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rows.isEmpty()) rows.add("일치하는 연락처가 없습니다.");
        new AlertDialog.Builder(this).setTitle("연락처 검색 결과")
                .setItems(rows.toArray(new String[0]), (d, which) -> {
                    if (which >= numbers.size() || numbers.get(which).isEmpty()) return;
                    try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numbers.get(which))))); }
                    catch (ActivityNotFoundException ignored) { }
                }).setPositiveButton("닫기", null).show();
    }

    private void addWebSearchRow() {
        TextView web = label("⌕  웹에서 ‘" + currentQuery.trim() + "’ 검색  ›", 13, ACCENT);
        web.setPadding(dp(8), 0, dp(8), 0);
        web.setBackground(roundBg(0x55171B18, dp(14), HAIRLINE));
        LinearLayout.LayoutParams webLp = params(MATCH, dp(44));
        webLp.topMargin = dp(12);
        contentArea.addView(web, webLp);
        web.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra("query", currentQuery.trim());
            try { startActivity(intent); }
            catch (ActivityNotFoundException e) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(currentQuery.trim())))); }
                catch (ActivityNotFoundException ignored) { }
            }
        });
    }


    private void renderAlphabetRail() {
        if (alphabetRail == null) return;
        alphabetRail.setSections(sectionOrder);
    }

    private void addMusicSuggestion() {
        Intent musicIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
        List<ResolveInfo> players = getPackageManager().queryIntentActivities(musicIntent, 0);
        if (players.isEmpty()) return;
        TextView suggestion = label("♫   헤드폰 연결됨 · 음악 앱 열기", 12, SECONDARY);
        suggestion.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = params(MATCH, dp(34));
        contentArea.addView(suggestion, lp);
        suggestion.setOnClickListener(v -> {
            try { startActivity(musicIntent); }
            catch (ActivityNotFoundException ignored) {
                if (players.get(0).activityInfo != null) {
                    Intent launch = new Intent(Intent.ACTION_MAIN).setClassName(
                            players.get(0).activityInfo.packageName, players.get(0).activityInfo.name);
                    try { startActivity(launch); } catch (ActivityNotFoundException ignoredAgain) { }
                }
            }
        });
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
                setAppIcon(icon, app);
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
        return appRow(app, false);
    }

    private View appRow(AppEntry app, boolean favorite) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));
        ImageView icon = new ImageView(this);
        setAppIcon(icon, app);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = label(app.label, 16, PRIMARY);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, MATCH, 1f);
        nameLp.leftMargin = dp(14);
        row.addView(name, nameLp);

        if (favorite) {
            TextView star = label("★", 14, ACCENT);
            star.setGravity(Gravity.CENTER);
            star.setContentDescription("즐겨찾기 해제");
            row.addView(star, params(dp(38), dp(48)));
            star.setOnClickListener(v -> toggleFavorite(app.packageName));
        }
        row.setMinimumHeight(dp(60));
        row.setOnClickListener(v -> launch(app));
        row.setOnLongClickListener(v -> {
            showAppPopup(app);
            return true;
        });
        final float[] swipeStart = {0, 0};
        final boolean[] swipeRight = {false};
        row.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swipeStart[0] = event.getX();
                swipeStart[1] = event.getY();
                swipeRight[0] = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - swipeStart[0];
                float dy = event.getY() - swipeStart[1];
                if (dx > dp(34) && dx > Math.abs(dy) * 1.3f) {
                    swipeRight[0] = true;
                    if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && swipeRight[0]) {
                float dx = event.getX() - swipeStart[0];
                swipeRight[0] = false;
                if (dx > dp(58)) {
                    showAppPopup(app);
                    return true;
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                swipeRight[0] = false;
            }
            return false;
        });

        View divider = new View(this);
        divider.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams dividerLp = params(MATCH, dp(1));
        dividerLp.leftMargin = dp(54);
        LinearLayout item = column();
        item.addView(row, params(MATCH, dp(61)));
        item.addView(divider, dividerLp);
        return item;
    }


    private void showAppActions(AppEntry app) {
        boolean isFavorite = favorites().contains(app.packageName);
        ArrayList<String> actions = new ArrayList<>();
        actions.add(isFavorite ? "즐겨찾기 해제" : "즐겨찾기에 추가");
        if (isFavorite) actions.add("즐겨찾기 순서 변경");
        actions.add("앱 모음 지정");
        actions.add("아이콘 바꾸기");
        if (iconOverrides().containsKey(app.packageName)) actions.add("아이콘 원래대로");
        actions.add("앱 정보");
        new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String selected = actions.get(which);
                    if (selected.equals("즐겨찾기 순서 변경")) changeFavoriteOrder(app);
                    else if (selected.startsWith("즐겨찾기")) toggleFavorite(app.packageName);
                    else if (selected.equals("앱 모음 지정")) editAppGroups(app);
                    else if (selected.equals("아이콘 바꾸기")) chooseIcon(app.packageName);
                    else if (selected.equals("아이콘 원래대로")) resetIcon(app.packageName);
                    else {
                        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + app.packageName));
                        try { startActivity(details); }
                        catch (ActivityNotFoundException ignored) { }
                    }
                }).show();
    }

    private void showAppPopup(AppEntry app) {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(4), dp(18), dp(6));
        TextView launch = label("앱 열기", 14, ACCENT);
        launch.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(launch, params(MATCH, dp(42)));
        launch.setOnClickListener(v -> launch(app));
        TextView quickHeading = label("빠른 작업", 11, SECONDARY);
        quickHeading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.addView(quickHeading, params(MATCH, dp(26)));
        LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
        if (launcherApps != null && Build.VERSION.SDK_INT >= 25) {
            try {
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
                query.setPackage(app.packageName);
                query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                List<ShortcutInfo> found = launcherApps.getShortcuts(query, Process.myUserHandle());
                if (found != null) shortcuts.addAll(found);
            } catch (SecurityException | IllegalStateException ignored) { }
        }
        if (shortcuts.isEmpty()) {
            TextView noShortcuts = label("이 앱은 빠른 작업을 제공하지 않습니다.", 12, SECONDARY);
            box.addView(noShortcuts, params(MATCH, dp(34)));
        } else {
            for (ShortcutInfo shortcut : shortcuts) {
                CharSequence name = shortcut.getShortLabel();
                TextView item = label("›  " + (name == null ? "빠른 작업" : name.toString()), 13, PRIMARY);
                item.setPadding(dp(9), 0, dp(8), 0);
                item.setBackground(roundBg(0x44171B18, dp(12), Color.TRANSPARENT));
                LinearLayout.LayoutParams itemLp = params(MATCH, dp(40));
                itemLp.bottomMargin = dp(3);
                box.addView(item, itemLp);
                item.setOnClickListener(v -> {
                    try {
                        launcherApps.startShortcut(app.packageName, shortcut.getId(), null, null, Process.myUserHandle());
                        recordRecent(app.packageName);
                    } catch (RuntimeException e) {
                        Toast.makeText(this, "빠른 작업을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        TextView manage = label("앱 아이콘 · 앱 정보 · 추가 작업  ›", 12, ACCENT);
        manage.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(manage, params(MATCH, dp(40)));
        AlertDialog popup = new AlertDialog.Builder(this).setTitle(app.label).setView(box)
                .setNeutralButton("앱 모음에 추가", (d, w) -> editAppGroups(app))
                .setPositiveButton(favorites().contains(app.packageName) ? "★" : "☆", (d, w) -> toggleFavorite(app.packageName))
                .setNegativeButton("닫기", null).create();
        manage.setOnClickListener(v -> { popup.dismiss(); showAppActions(app); });
        popup.show();
    }

    private void showGroupPopup(AppGroup group) {
        ArrayList<AppEntry> apps = new ArrayList<>();
        for (AppEntry app : installedApps) if (appGroupIds(app.packageName).contains(group.id)) apps.add(app);
        ArrayList<String> choices = new ArrayList<>();
        for (AppEntry app : apps) choices.add("◦  " + app.label);
        if (choices.isEmpty()) choices.add("앱이 없습니다 · 앱 추가");
        new AlertDialog.Builder(this).setTitle(group.name)
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    if (apps.isEmpty()) bulkAssignToGroup(group);
                    else showAppPopup(apps.get(which));
                })
                .setNeutralButton("앱 추가", (dialog, which) -> bulkAssignToGroup(group))
                .setPositiveButton("위젯 스택", (dialog, which) -> showWidgetStack()).show();
    }

    private void setAppIcon(ImageView image, AppEntry app) {
        String mode = prefs.getString("icon_mode", "system");
        if ("dots".equals(mode)) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            int color = Color.HSVToColor(new float[]{Math.floorMod(app.packageName.hashCode(), 360), 0.20f, 0.72f});
            dot.setColor(color);
            image.setImageDrawable(dot);
            return;
        }
        if ("monogram".equals(mode)) {
            image.setImageDrawable(new MonogramDrawable(app.label, app.packageName));
            return;
        }
        image.setImageDrawable(app.icon);
    }

    private void chooseIcon(String packageName) {
        pendingIconPackage = packageName;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(pick, REQUEST_ICON); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "이미지 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show(); }
    }

    private HashMap<String, String> iconOverrides() {
        HashMap<String, String> icons = new HashMap<>();
        try {
            JSONObject data = new JSONObject(prefs.getString("icon_overrides", "{}"));
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) { String key = keys.next(); icons.put(key, data.optString(key)); }
        } catch (Exception ignored) { }
        return icons;
    }

    private void resetIcon(String packageName) {
        HashMap<String, String> icons = iconOverrides();
        icons.remove(packageName);
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : icons.entrySet()) putJson(json, entry.getKey(), entry.getValue());
        prefs.edit().putString("icon_overrides", json.toString()).apply();
        refreshApps(); renderHome();
    }

    private void applyThemePalette() {
        String theme = prefs == null ? "sage" : prefs.getString("theme_id", "sage");
        switch (theme) {
            case "ocean":
                ACCENT = 0xFF9DD8FF; PRIMARY = 0xFFF1F6FA; SECONDARY = 0xFF9AAEBB;
                PANEL = 0xCC111A22; HAIRLINE = 0x333E5867; break;
            case "rose":
                ACCENT = 0xFFFFB7C8; PRIMARY = 0xFFFFF3F4; SECONDARY = 0xFFB8A0A5;
                PANEL = 0xCC24191D; HAIRLINE = 0x33FFFFFF; break;
            case "amber":
                ACCENT = 0xFFFFD08A; PRIMARY = 0xFFFFF6E9; SECONDARY = 0xFFB2A18B;
                PANEL = 0xCC211B12; HAIRLINE = 0x33FFFFFF; break;
            case "violet":
                ACCENT = 0xFFD3BBFF; PRIMARY = 0xFFF7F3FF; SECONDARY = 0xFFA79BB9;
                PANEL = 0xCC1D1825; HAIRLINE = 0x33FFFFFF; break;
            default:
                ACCENT = 0xFFB8F28B; PRIMARY = 0xFFF3F4EE; SECONDARY = 0xFF9DA39B;
                PANEL = 0xCC171B18; HAIRLINE = 0x30FFFFFF; break;
        }
    }


    private void showFeatureHub() {
        String quickLabel = quickActionLabel(prefs.getString("quick_button", ""));
        String[] features = {
                "테마 라이브러리",
                "글꼴 · 시계 · 아이콘",
                "위젯 스택",
                "캘린더 일정",
                "사용 시간 알림",
                "배경화면 스튜디오",
                "앱 분류 관리",
                quickLabel.isEmpty() ? "빠른 실행 지정" : "빠른 실행 · " + quickLabel,
                "기본 홈 앱 선택"
        };
        new AlertDialog.Builder(this).setTitle("홈 화면")
                .setItems(features, (dialog, which) -> {
                    switch (which) {
                        case 0: showThemeLibrary(); break;
                        case 1: showLookSettings(); break;
                        case 2: showWidgetStack(); break;
                        case 3: showAgenda(); break;
                        case 4: showUsageBreakerSettings(); break;
                        case 5: openWallpaperStudio(); break;
                        case 6: manageGroups(); break;
                        case 7:
                            if (quickLabel.isEmpty()) chooseQuickAction("quick_button");
                            else runQuickAction("quick_button");
                            break;
                        case 8: requestDefaultHome(); break;
                    }
                }).show();
    }


    private void showThemeLibrary() {
        String[] choices = {"이끼 정원   ·   Sage", "푸른 새벽   ·   Ocean", "장미 안개   ·   Rose", "황혼빛   ·   Amber", "라일락 밤   ·   Violet", "테마 파일 불러오기", "내 테마 내보내기"};
        new AlertDialog.Builder(this).setTitle("테마 라이브러리")
                .setItems(choices, (dialog, which) -> {
                    if (which < 5) {
                        String[] ids = {"sage", "ocean", "rose", "amber", "violet"};
                        prefs.edit().putString("theme_id", ids[which]).apply();
                        applyThemePalette(); buildUi();
                    } else if (which == 5) importTheme();
                    else exportTheme();
                }).show();
    }

    private void importTheme() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, REQUEST_THEME_IMPORT); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "테마 파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show(); }
    }

    private void exportTheme() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "wave-theme.json");
        try { startActivityForResult(intent, REQUEST_THEME_EXPORT); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "테마 파일 저장기를 열 수 없습니다.", Toast.LENGTH_SHORT).show(); }
    }

    private String themeExportJson() {
        JSONObject obj = new JSONObject();
        putJson(obj, "format", "wave-home-theme-v1");
        putJson(obj, "name", "Wave Home · " + prefs.getString("theme_id", "sage"));
        putJson(obj, "palette", prefs.getString("theme_id", "sage"));
        return obj.toString();
    }

    private void showLookSettings() {
        String[] choices = {
                "글꼴 바꾸기", "시계 12시간 / 24시간", "시계 크기", "앱 아이콘 스타일", "상태 표시줄 숨기기", "시계 두 번 눌러 화면 잠금"
        };
        new AlertDialog.Builder(this).setTitle("화면 스타일")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) chooseFont();
                    else if (which == 1) {
                        boolean value = !prefs.getBoolean("clock_24h", true);
                        prefs.edit().putBoolean("clock_24h", value).apply();
                        handler.removeCallbacks(clockTick); handler.post(clockTick);
                    } else if (which == 2) chooseClockSize();
                    else if (which == 3) chooseIconStyle();
                    else if (which == 4) toggleStatusBar();
                    else toggleDoubleTapLock();
                }).show();
    }

    private void chooseFont() {
        String[] labels = {"산세리프", "둥근 글꼴", "세리프", "모노스페이스"};
        String[] values = {"sans-serif", "sans-serif-rounded", "serif", "monospace"};
        new AlertDialog.Builder(this).setTitle("글꼴")
                .setSingleChoiceItems(labels, Math.max(0, java.util.Arrays.asList(values).indexOf(prefs.getString("font_family", "sans-serif"))), (d, which) -> {
                    prefs.edit().putString("font_family", values[which]).apply();
                    applyFontTree(homeRoot);
                    d.dismiss();
                }).setNegativeButton("닫기", null).show();
    }

    private void chooseClockSize() {
        String[] sizes = {"작게", "기본", "크게"};
        int selected = prefs.getInt("clock_size", 39) == 32 ? 0 : prefs.getInt("clock_size", 39) == 46 ? 2 : 1;
        new AlertDialog.Builder(this).setTitle("시계 크기")
                .setSingleChoiceItems(sizes, selected, (d, which) -> {
                    int[] values = {32, 39, 46};
                    prefs.edit().putInt("clock_size", values[which]).apply();
                    if (clockView != null) clockView.setTextSize(values[which]);
                    d.dismiss();
                }).setNegativeButton("닫기", null).show();
    }

    private void chooseIconStyle() {
        String[] styles = {"앱 기본 아이콘", "통일 모노그램", "도트 아이콘"};
        String mode = prefs.getString("icon_mode", "system");
        int selected = "monogram".equals(mode) ? 1 : "dots".equals(mode) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("앱 아이콘 스타일")
                .setSingleChoiceItems(styles, selected, (d, which) -> {
                    String[] values = {"system", "monogram", "dots"};
                    prefs.edit().putString("icon_mode", values[which]).apply();
                    renderHome();
                    d.dismiss();
                }).setNegativeButton("닫기", null).show();
    }

    private void toggleStatusBar() {
        boolean next = !prefs.getBoolean("hide_status_bar", false);
        prefs.edit().putBoolean("hide_status_bar", next).apply();
        if (next) getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Toast.makeText(this, next ? "상태 표시줄을 숨겼습니다." : "상태 표시줄을 표시합니다.", Toast.LENGTH_SHORT).show();
    }

    private void toggleDoubleTapLock() {
        boolean next = !prefs.getBoolean("double_tap_lock", false);
        if (next) {
            DevicePolicyManager policy = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = new android.content.ComponentName(this, WaveDeviceAdmin.class);
            if (policy == null || !policy.isAdminActive(admin)) {
                new AlertDialog.Builder(this).setTitle("두 번 눌러 잠금")
                        .setMessage("Android의 기기 관리 권한을 활성화해야 시계를 두 번 눌러 화면을 잠글 수 있습니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("권한 켜기", (d, w) -> {
                            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Wave Home에서 시계를 두 번 눌러 화면을 잠급니다.");
                            try { startActivityForResult(intent, REQUEST_LOCK_ADMIN); }
                            catch (ActivityNotFoundException ignored) { }
                        }).show();
                return;
            }
        }
        prefs.edit().putBoolean("double_tap_lock", next).apply();
    }

    private void lockScreen() {
        DevicePolicyManager policy = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        android.content.ComponentName admin = new android.content.ComponentName(this, WaveDeviceAdmin.class);
        if (policy != null && policy.isAdminActive(admin)) policy.lockNow();
    }

    private void applyFontTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Typeface chosen = Typeface.create(prefs.getString("font_family", "sans-serif"), Typeface.NORMAL);
            if (text == clockView) chosen = Typeface.create("sans-serif-light", Typeface.NORMAL);
            text.setTypeface(chosen);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyFontTree(group.getChildAt(i));
        }
    }

    private void showAgenda() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQUEST_CALENDAR);
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + 24L * 60L * 60L * 1000L;
        android.net.Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, until);
        String[] projection = {CalendarContract.Events.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION};
        ArrayList<String> rows = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(builder.build(), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null) {
                int titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE);
                int beginCol = cursor.getColumnIndex(CalendarContract.Instances.BEGIN);
                int allDayCol = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY);
                int locationCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
                while (cursor.moveToNext() && rows.size() < 8) {
                    String title = titleCol >= 0 ? cursor.getString(titleCol) : "일정";
                    long begin = beginCol >= 0 ? cursor.getLong(beginCol) : now;
                    boolean allDay = allDayCol >= 0 && cursor.getInt(allDayCol) == 1;
                    String time = allDay ? "종일" : new SimpleDateFormat("M.d (E) HH:mm", Locale.getDefault()).format(new Date(begin));
                    String location = locationCol >= 0 ? cursor.getString(locationCol) : null;
                    rows.add(time + "   " + (title == null || title.isEmpty() ? "제목 없는 일정" : title)
                            + (location == null || location.isEmpty() ? "" : "\n                 " + location));
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, "일정을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rows.isEmpty()) rows.add("앞으로 24시간 안에 일정이 없습니다.");
        new AlertDialog.Builder(this).setTitle("다음 일정")
                .setItems(rows.toArray(new String[0]), null)
                .setPositiveButton("닫기", null).show();
    }

    private void showWeatherLocationDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString("weather_city", ""));
        input.setHint("예: 서울, 부산, Tokyo");
        input.setTextColor(PRIMARY);
        input.setHintTextColor(SECONDARY);
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        new AlertDialog.Builder(this).setTitle("날씨 지역")
                .setMessage("지역 이름으로 현재 날씨를 조회합니다. 위치 권한은 사용하지 않습니다. 데이터 제공: Open-Meteo.")
                .setView(input).setNegativeButton("취소", null)
                .setNeutralButton("지우기", (d, w) -> {
                    prefs.edit().remove("weather_city").remove("weather_cached").apply();
                    weatherText = "날씨 설정";
                    if (weatherView != null) weatherView.setText("◌  날씨 설정");
                }).setPositiveButton("저장", (d, w) -> {
                    String city = input.getText().toString().trim();
                    if (city.isEmpty()) return;
                    prefs.edit().putString("weather_city", city).remove("weather_cached").putLong("weather_updated", 0).apply();
                    weatherText = city + " · 확인 중";
                    if (weatherView != null) weatherView.setText("◌  " + weatherText);
                    loadWeather(city);
                }).show();
    }

    private void refreshWeather() {
        if (weatherView == null || prefs == null) return;
        String city = prefs.getString("weather_city", "");
        if (city.isEmpty()) {
            weatherText = "날씨 설정";
            weatherView.setText("◌  날씨 설정");
            return;
        }
        String cached = prefs.getString("weather_cached", "");
        if (!cached.isEmpty()) {
            weatherText = cached;
            weatherView.setText("☼  " + cached);
        } else weatherView.setText("◌  " + city + " 날씨 확인 중");
        if (!weatherRefreshing && System.currentTimeMillis() - prefs.getLong("weather_updated", 0) > 2L * 60L * 60L * 1000L) loadWeather(city);
    }

    private void loadWeather(String city) {
        if (weatherRefreshing || city == null || city.trim().isEmpty()) return;
        weatherRefreshing = true;
        io.execute(() -> {
            String result = null;
            try {
                String escaped = URLEncoder.encode(city.trim(), "UTF-8");
                JSONObject geocode = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + escaped + "&count=1&language=" + Locale.getDefault().getLanguage() + "&format=json");
                JSONArray locations = geocode.optJSONArray("results");
                if (locations != null && locations.length() > 0) {
                    JSONObject place = locations.getJSONObject(0);
                    double lat = place.getDouble("latitude");
                    double lon = place.getDouble("longitude");
                    String name = place.optString("name", city);
                    JSONObject forecast = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto");
                    JSONObject current = forecast.optJSONObject("current");
                    if (current != null) result = name + "  " + Math.round(current.optDouble("temperature_2m")) + "°  " + weatherDescription(current.optInt("weather_code"));
                }
            } catch (Exception ignored) { }
            String finalResult = result;
            runOnUiThread(() -> {
                weatherRefreshing = false;
                if (!city.equals(prefs.getString("weather_city", ""))) {
                    refreshWeather();
                    return;
                }
                if (finalResult == null) {
                    weatherText = city + " · 다시 시도";
                } else {
                    weatherText = finalResult;
                    prefs.edit().putString("weather_cached", finalResult).putLong("weather_updated", System.currentTimeMillis()).apply();
                }
                if (weatherView != null) weatherView.setText("☼  " + weatherText);
            });
        });
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "WaveHome/0.3");
        try {
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
            return new JSONObject(text.toString());
        } finally { connection.disconnect(); }
    }

    private String weatherDescription(int code) {
        if (code == 0) return "맑음";
        if (code <= 3) return "구름";
        if (code == 45 || code == 48) return "안개";
        if (code >= 51 && code <= 67) return "비";
        if (code >= 71 && code <= 77) return "눈";
        if (code >= 80 && code <= 82) return "소나기";
        if (code >= 95) return "뇌우";
        return "날씨";
    }

    private void startHeadsetMonitor() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        Intent sticky = null;
        try {
            if (Build.VERSION.SDK_INT >= 33) sticky = registerReceiver(headsetReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else sticky = registerReceiver(headsetReceiver, filter);
        } catch (RuntimeException ignored) { }
        if (sticky != null) headsetConnected = sticky.getIntExtra("state", 0) == 1;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (appOps == null) return false;
        try { return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED; }
        catch (RuntimeException ignored) { return false; }
    }

    private void showUsageBreakerSettings() {
        if (!hasUsageAccess()) {
            new AlertDialog.Builder(this).setTitle("사용 시간 알림")
                    .setMessage("선택한 앱의 오늘 사용 시간을 읽어 기준을 넘었을 때 한 번 알려줍니다. Android의 사용 정보 접근 권한을 켜 주세요.")
                    .setNegativeButton("나중에", null)
                    .setPositiveButton("권한 열기", (d, w) -> {
                        try { startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_USAGE_ACCESS); }
                        catch (ActivityNotFoundException ignored) { }
                    }).show();
            return;
        }
        LinearLayout content = column();
        content.setPadding(dp(20), dp(4), dp(20), dp(8));
        content.addView(label("하루 사용 시간이 기준에 도달하면 Wave가 쉬어가기를 제안합니다.", 12, SECONDARY), params(MATCH, dp(48)));
        EditText minutes = new EditText(this);
        minutes.setSingleLine(true);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("분 단위 · 기본 90분");
        minutes.setText(String.valueOf(prefs.getInt("focus_limit_minutes", 90)));
        minutes.setTextColor(PRIMARY);
        content.addView(minutes, params(MATCH, dp(48)));
        Set<String> selected = new HashSet<>(prefs.getStringSet("focus_packages", new HashSet<>()));
        TextView selectedSummary = label(focusSummary(selected), 12, SECONDARY);
        content.addView(selectedSummary, params(MATCH, dp(38)));
        TextView pickApps = smallAction("앱 선택");
        pickApps.setGravity(Gravity.CENTER);
        content.addView(pickApps, params(MATCH, dp(40)));
        pickApps.setOnClickListener(v -> showFocusAppPicker(selected, selectedSummary));
        new AlertDialog.Builder(this).setTitle("사용 시간 알림").setView(content)
                .setNegativeButton("끄기", (d, w) -> prefs.edit().remove("focus_packages").apply())
                .setPositiveButton("저장", (d, w) -> {
                    int limit;
                    try { limit = Integer.parseInt(minutes.getText().toString()); } catch (Exception e) { limit = 90; }
                    prefs.edit().putInt("focus_limit_minutes", Math.max(1, Math.min(limit, 1440)))
                            .putStringSet("focus_packages", selected).apply();
                    Toast.makeText(this, selected.isEmpty() ? "알림 앱을 선택해 주세요." : "사용 시간 알림을 저장했습니다.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String focusSummary(Set<String> selected) {
        if (selected.isEmpty()) return "선택한 앱 없음";
        ArrayList<String> names = new ArrayList<>();
        for (AppEntry app : installedApps) if (selected.contains(app.packageName)) names.add(app.label);
        return android.text.TextUtils.join(" · ", names);
    }

    private void showFocusAppPicker(Set<String> selected, TextView summary) {
        String[] labels = new String[installedApps.size()];
        boolean[] checked = new boolean[installedApps.size()];
        for (int i = 0; i < installedApps.size(); i++) {
            AppEntry app = installedApps.get(i);
            labels[i] = app.label;
            checked[i] = selected.contains(app.packageName);
        }
        new AlertDialog.Builder(this).setTitle("알림을 받을 앱")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                    String pkg = installedApps.get(which).packageName;
                    if (isChecked) selected.add(pkg); else selected.remove(pkg);
                    summary.setText(focusSummary(selected));
                }).setPositiveButton("완료", null).show();
    }

    private void checkUsageBreaker() {
        if (prefs == null || !hasUsageAccess()) return;
        Set<String> watched = prefs.getStringSet("focus_packages", new HashSet<>());
        if (watched.isEmpty()) return;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        UsageStatsManager usage = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usage == null) return;
        Map<String, UsageStats> totals;
        try { totals = usage.queryAndAggregateUsageStats(start.getTimeInMillis(), now); }
        catch (RuntimeException ignored) { return; }
        int limitMs = prefs.getInt("focus_limit_minutes", 90) * 60 * 1000;
        String dayKey = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        for (String pkg : watched) {
            UsageStats stat = totals == null ? null : totals.get(pkg);
            if (stat == null || stat.getTotalTimeInForeground() < limitMs) continue;
            String warnedKey = "focus_warned_" + dayKey + "_" + pkg;
            if (prefs.getBoolean(warnedKey, false)) continue;
            prefs.edit().putBoolean(warnedKey, true).apply();
            String label = pkg;
            for (AppEntry app : installedApps) if (pkg.equals(app.packageName)) { label = app.label; break; }
            new AlertDialog.Builder(this).setTitle("잠깐 쉬어 갈까요?")
                    .setMessage(label + "을(를) 오늘 " + prefs.getInt("focus_limit_minutes", 90) + "분 이상 사용했어요.")
                    .setPositiveButton("잠시 멈춤", null).setNegativeButton("계속 사용", null).show();
            break;
        }
    }

    private ArrayList<Integer> widgetIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(prefs.getString("widget_stack", "[]"));
            for (int i = 0; i < json.length(); i++) ids.add(json.optInt(i, -1));
        } catch (Exception ignored) { }
        ids.removeIf(id -> id <= 0);
        return ids;
    }

    private void saveWidgetIds(List<Integer> ids) {
        JSONArray json = new JSONArray();
        for (Integer id : ids) json.put(id);
        prefs.edit().putString("widget_stack", json.toString()).apply();
    }

    private void showWidgetStack() {
        if (widgetDialog != null && widgetDialog.isShowing()) widgetDialog.dismiss();
        ScrollView scroll = new ScrollView(this);
        LinearLayout stack = column();
        stack.setPadding(dp(18), dp(4), dp(18), dp(8));
        ArrayList<Integer> ids = widgetIds();
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info == null) continue;
            LinearLayout block = column();
            LinearLayout tools = new LinearLayout(this);
            tools.setGravity(Gravity.CENTER_VERTICAL);
            CharSequence widgetName = info.loadLabel(getPackageManager());
            TextView name = label(widgetName == null ? "위젯" : widgetName.toString(), 12, SECONDARY);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tools.addView(name, new LinearLayout.LayoutParams(0, dp(36), 1f));
            TextView up = label("↑", 16, SECONDARY); up.setGravity(Gravity.CENTER); tools.addView(up, params(dp(34), dp(34)));
            TextView down = label("↓", 16, SECONDARY); down.setGravity(Gravity.CENTER); tools.addView(down, params(dp(34), dp(34)));
            TextView remove = label("×", 20, 0xFFFF9898); remove.setGravity(Gravity.CENTER); tools.addView(remove, params(dp(34), dp(34)));
            block.addView(tools, params(MATCH, dp(38)));
            AppWidgetHostView widgetView = widgetHost.createView(this, id, info);
            widgetView.setAppWidget(id, info);
            int heightDp = Math.max(110, Math.min(info.minHeight + 28, 500));
            block.addView(widgetView, params(MATCH, dp(heightDp)));
            LinearLayout.LayoutParams blockLp = params(MATCH, WRAP);
            blockLp.bottomMargin = dp(12);
            stack.addView(block, blockLp);
            up.setOnClickListener(v -> moveWidget(id, -1));
            down.setOnClickListener(v -> moveWidget(id, 1));
            remove.setOnClickListener(v -> removeWidget(id));
        }
        if (ids.isEmpty()) {
            TextView empty = label("위젯을 추가하면 여기서 위아래로 쌓아 볼 수 있습니다.", 13, SECONDARY);
            empty.setGravity(Gravity.CENTER);
            stack.addView(empty, params(MATCH, dp(120)));
        }
        TextView add = smallAction("＋ 위젯 추가");
        add.setGravity(Gravity.CENTER);
        stack.addView(add, params(MATCH, dp(44)));
        scroll.addView(stack);
        widgetDialog = new AlertDialog.Builder(this).setTitle("위젯 스택")
                .setView(scroll).setNegativeButton("닫기", null)
                .setNeutralButton("위젯 추가", (d, w) -> startWidgetPicker()).create();
        add.setOnClickListener(v -> { widgetDialog.dismiss(); startWidgetPicker(); });
        widgetDialog.show();
    }

    private void moveWidget(int id, int direction) {
        ArrayList<Integer> ids = widgetIds();
        int index = ids.indexOf(id), next = index + direction;
        if (index >= 0 && next >= 0 && next < ids.size()) {
            Collections.swap(ids, index, next); saveWidgetIds(ids); showWidgetStack();
        }
    }

    private void removeWidget(int id) {
        ArrayList<Integer> ids = widgetIds();
        ids.remove(Integer.valueOf(id)); saveWidgetIds(ids);
        try { widgetHost.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
        showWidgetStack();
    }

    private void startWidgetPicker() {
        pendingWidgetId = widgetHost.allocateAppWidgetId();
        Intent pick = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
        try { startActivityForResult(pick, REQUEST_PICK_WIDGET); }
        catch (ActivityNotFoundException e) {
            widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = -1;
            Toast.makeText(this, "위젯 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishPickedWidget(int id) {
        pendingWidgetId = id;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) {
            Toast.makeText(this, "선택한 위젯을 연결하지 못했습니다.", Toast.LENGTH_SHORT).show();
            widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1; return;
        }
        if (info.configure != null) {
            Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            try { startActivityForResult(configure, REQUEST_CONFIGURE_WIDGET); }
            catch (ActivityNotFoundException e) { addWidgetId(id); }
        } else addWidgetId(id);
    }

    private void addWidgetId(int id) {
        ArrayList<Integer> ids = widgetIds();
        if (!ids.contains(id)) ids.add(id);
        saveWidgetIds(ids); pendingWidgetId = -1;
        Toast.makeText(this, "위젯을 스택에 추가했습니다.", Toast.LENGTH_SHORT).show();
        showWidgetStack();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { }
            HashMap<String, String> icons = iconOverrides();
            icons.put(pendingIconPackage, uri.toString());
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : icons.entrySet()) putJson(json, entry.getKey(), entry.getValue());
            prefs.edit().putString("icon_overrides", json.toString()).apply();
            refreshApps(); renderHome(); pendingIconPackage = "";
        } else if (requestCode == REQUEST_THEME_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                byte[] bytes = new byte[8192]; int count = input == null ? -1 : input.read(bytes);
                if (count > 0) {
                    JSONObject theme = new JSONObject(new String(bytes, 0, count, "UTF-8"));
                    String palette = theme.optString("palette", "sage");
                    if (!new HashSet<>(java.util.Arrays.asList("sage", "ocean", "rose", "amber", "violet")).contains(palette)) palette = "sage";
                    prefs.edit().putString("theme_id", palette).apply(); applyThemePalette(); buildUi();
                    Toast.makeText(this, "테마를 적용했습니다.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) { Toast.makeText(this, "테마 파일을 읽지 못했습니다.", Toast.LENGTH_SHORT).show(); }
        } else if (requestCode == REQUEST_THEME_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output != null) output.write(themeExportJson().getBytes("UTF-8"));
                Toast.makeText(this, "테마 파일을 저장했습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "테마를 저장하지 못했습니다.", Toast.LENGTH_SHORT).show(); }
        } else if (requestCode == REQUEST_PICK_WIDGET) {
            if (resultCode == RESULT_OK && data != null) {
                int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
                if (info == null) {
                    ComponentName provider = null;
                    try {
                        if (Build.VERSION.SDK_INT >= 33) provider = data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName.class);
                        else provider = (ComponentName) data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER);
                    } catch (RuntimeException ignored) { }
                    if (provider != null) {
                        Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
                        try { startActivityForResult(bind, REQUEST_BIND_WIDGET); }
                        catch (ActivityNotFoundException e) { widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1; }
                        return;
                    }
                }
                finishPickedWidget(id);
            } else if (pendingWidgetId > 0) { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = -1; }
        } else if (requestCode == REQUEST_BIND_WIDGET) {
            if (resultCode == RESULT_OK) finishPickedWidget(pendingWidgetId);
            else if (pendingWidgetId > 0) { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = -1; }
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK && pendingWidgetId > 0) addWidgetId(pendingWidgetId);
            else if (pendingWidgetId > 0) { widgetHost.deleteAppWidgetId(pendingWidgetId); pendingWidgetId = -1; }
        } else if (requestCode == REQUEST_LOCK_ADMIN) {
            DevicePolicyManager policy = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = new android.content.ComponentName(this, WaveDeviceAdmin.class);
            if (policy != null && policy.isAdminActive(admin)) prefs.edit().putBoolean("double_tap_lock", true).apply();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showAgenda();
        else if (requestCode == REQUEST_CONTACTS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showContactSearchDialog();
    }

    private void toggleFavorite(String packageName) {
        Set<String> next = favorites();
        ArrayList<String> order = favoriteOrder();
        if (!next.add(packageName)) {
            next.remove(packageName);
            order.remove(packageName);
        } else if (!order.contains(packageName)) {
            order.add(packageName);
        }
        prefs.edit().putStringSet("favorites", next).apply();
        saveFavoriteOrder(order);
        renderHome();
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
    }

    private ArrayList<String> favoriteOrder() {
        Set<String> selected = favorites();
        ArrayList<String> order = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("favorite_order", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                String pkg = saved.optString(i, "");
                if (selected.contains(pkg) && !order.contains(pkg)) order.add(pkg);
            }
        } catch (Exception ignored) { }
        for (AppEntry app : installedApps) {
            if (selected.contains(app.packageName) && !order.contains(app.packageName)) order.add(app.packageName);
        }
        return order;
    }

    private List<AppEntry> orderedFavorites() {
        HashMap<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry app : installedApps) byPackage.put(app.packageName, app);
        ArrayList<AppEntry> result = new ArrayList<>();
        for (String pkg : favoriteOrder()) {
            AppEntry app = byPackage.get(pkg);
            if (app != null) result.add(app);
        }
        return result;
    }

    private void saveFavoriteOrder(List<String> order) {
        JSONArray saved = new JSONArray();
        for (String pkg : order) saved.put(pkg);
        prefs.edit().putString("favorite_order", saved.toString()).apply();
    }

    private void changeFavoriteOrder(AppEntry app) {
        ArrayList<String> order = favoriteOrder();
        int current = order.indexOf(app.packageName);
        if (current < 0) return;
        String[] choices = {"맨 위로", "한 칸 위로", "한 칸 아래로", "맨 아래로"};
        new AlertDialog.Builder(this).setTitle(app.label + " · 즐겨찾기 순서")
                .setItems(choices, (dialog, which) -> {
                    order.remove(app.packageName);
                    int destination;
                    if (which == 0) destination = 0;
                    else if (which == 1) destination = Math.max(0, current - 1);
                    else if (which == 2) destination = Math.min(order.size(), current + 1);
                    else destination = order.size();
                    order.add(Math.max(0, Math.min(destination, order.size())), app.packageName);
                    saveFavoriteOrder(order);
                    renderHome();
                }).show();
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
        Set<String> selected = new HashSet<>();
        for (AppEntry app : installedApps) if (appGroupIds(app.packageName).contains(group.id)) selected.add(app.packageName);
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(6), dp(18), dp(6));
        EditText filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setHint("앱 검색");
        filter.setTextColor(PRIMARY);
        filter.setHintTextColor(SECONDARY);
        filter.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.addView(filter, params(MATCH, dp(44)));
        TextView count = label("선택 " + selected.size() + "개", 11, SECONDARY);
        panel.addView(count, params(MATCH, dp(28)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = column();
        scroll.addView(rows);
        panel.addView(scroll, params(MATCH, dp(330)));
        Runnable showMatches = () -> {
            rows.removeAllViews();
            String query = filter.getText().toString().trim().toLowerCase(Locale.getDefault());
            for (AppEntry app : installedApps) {
                if (!query.isEmpty() && !app.label.toLowerCase(Locale.getDefault()).contains(query)) continue;
                CheckBox box = new CheckBox(this);
                box.setText(app.label);
                box.setTextColor(PRIMARY);
                box.setTextSize(14);
                box.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
                box.setChecked(selected.contains(app.packageName));
                rows.addView(box, params(MATCH, dp(44)));
                box.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
                    count.setText("선택 " + selected.size() + "개");
                });
            }
        };
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int c) { showMatches.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        showMatches.run();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("‘" + group.name + "’에 앱 추가")
                .setView(panel).setNegativeButton("취소", null)
                .setPositiveButton("저장", (d, w) -> {
                    JSONObject memberships = memberships();
                    for (AppEntry app : installedApps) {
                        JSONArray old = memberships.optJSONArray(app.packageName);
                        ArrayList<String> ids = new ArrayList<>();
                        if (old != null) for (int j = 0; j < old.length(); j++) {
                            String id = old.optString(j);
                            if (!group.id.equals(id)) ids.add(id);
                        }
                        if (selected.contains(app.packageName)) ids.add(group.id);
                        JSONArray next = new JSONArray();
                        for (String id : ids) next.put(id);
                        if (next.length() == 0) memberships.remove(app.packageName);
                        else putJson(memberships, app.packageName, next);
                    }
                    saveMemberships(memberships);
                }).create();
        dialog.show();
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
        HashMap<String, String> customIcons = iconOverrides();
        for (ResolveInfo result : results) {
            if (result.activityInfo == null) continue;
            String packageName = result.activityInfo.packageName;
            if (getPackageName().equals(packageName) || unique.containsKey(packageName)) continue;
            CharSequence label = result.loadLabel(manager);
            Drawable icon = result.loadIcon(manager);
            String iconUri = customIcons.get(packageName);
            if (iconUri != null) {
                try (InputStream stream = getContentResolver().openInputStream(Uri.parse(iconUri))) {
                    Drawable override = stream == null ? null : Drawable.createFromStream(stream, null);
                    if (override != null) icon = override;
                } catch (Exception ignored) { }
            }
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
        view.setTypeface(Typeface.create(prefs == null ? "sans-serif" : prefs.getString("font_family", "sans-serif"), Typeface.NORMAL));
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

    private int dp(float value) {
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
        if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE) {
            setSearchVisible(false, false);
            return;
        }
        if (!isDefaultHome()) super.onBackPressed();
    }


    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
}
