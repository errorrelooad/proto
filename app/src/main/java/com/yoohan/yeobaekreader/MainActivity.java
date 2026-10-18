package com.yoohan.yeobaekreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int PICK_BOOK = 41;
    private static final int BG_PAPER = Color.rgb(248, 245, 237);
    private static final int BG_SEPIA = Color.rgb(239, 226, 204);
    private static final int BG_NIGHT = Color.rgb(24, 27, 26);
    private static final int INK_PAPER = Color.rgb(43, 43, 39);
    private static final int INK_NIGHT = Color.rgb(224, 221, 212);
    private static final int GREEN = Color.rgb(68, 92, 74);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::savePosition;
    private SharedPreferences prefs;
    private ProgressDialog loading;

    private LinearLayout screen;
    private LinearLayout chapterContent;
    private ScrollView readerScroll;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView chapterMeta;
    private TextView chapterHeading;
    private TextView readerTitle;
    private final ArrayList<TextView> bodyViews = new ArrayList<>();

    private Uri currentUri;
    private String currentTitle = "";
    private String currentAuthor = "";
    private String currentFormat = "TXT";
    private String currentKey = "";
    private ArrayList<Chapter> chapters = new ArrayList<>();
    private int chapterIndex = 0;
    private int textSize = 21;
    private float lineSpacing = 1.9f;
    private int brightness = 100;
    private String theme = "paper";
    private String width = "medium";

    private static class Chapter {
        final String title;
        final String text;
        Chapter(String title, String text) { this.title = title; this.text = text; }
    }

    private static class ParsedBook {
        String title;
        String author = "";
        String format;
        ArrayList<Chapter> chapters = new ArrayList<>();
    }

    private static class ManifestItem {
        String path;
        String mediaType;
        String properties;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        textSize = prefs.getInt("textSize", 21);
        lineSpacing = prefs.getFloat("lineSpacing", 1.9f);
        brightness = Math.max(10, Math.min(100, prefs.getInt("brightness", 100)));
        theme = prefs.getString("theme", "paper");
        width = prefs.getString("width", "medium");
        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) openBook(incoming); else showLibrary();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) openBook(intent.getData());
    }

    @Override protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(saveRunnable);
        io.shutdown();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int paperColor() {
        if ("night".equals(theme)) return BG_NIGHT;
        if ("sepia".equals(theme)) return BG_SEPIA;
        return BG_PAPER;
    }

    private int textColor() { return "night".equals(theme) ? INK_NIGHT : INK_PAPER; }
    private int mutedColor() { return "night".equals(theme) ? Color.rgb(155, 157, 149) : Color.rgb(117, 115, 106); }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : textColor());
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? GREEN : ("night".equals(theme) ? Color.rgb(48, 52, 50) : Color.rgb(234, 231, 222)));
        bg.setCornerRadius(dp(18));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    private void installScreen(LinearLayout root) {
        screen = root;
        screen.setBackgroundColor(paperColor());
        setContentView(screen);
        applySystemBars();
        if (Build.VERSION.SDK_INT >= 30) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            screen.requestApplyInsets();
        }
    }

    private void applySystemBars() {
        Window window = getWindow();
        boolean night = "night".equals(theme);
        window.setStatusBarColor(paperColor());
        window.setNavigationBarColor(paperColor());
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.screenBrightness = Math.max(0.1f, Math.min(1f, brightness / 100f));
        window.setAttributes(attrs);
        if (Build.VERSION.SDK_INT >= 23) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (night) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (night) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private void showLibrary() {
        readerScroll = null;
        chapterContent = null;
        progressBar = null;
        progressText = null;
        chapterMeta = null;
        chapterHeading = null;
        readerTitle = null;
        LinearLayout outer = column();
        outer.setPadding(dp(18), dp(8), dp(18), dp(12));
        installScreen(outer);

        LinearLayout brand = row();
        TextView mark = text("여", 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundRect(GREEN, dp(12)));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        brand.addView(mark, markLp);
        LinearLayout brandText = column();
        TextView brandName = text("여백", 18, textColor(), true);
        TextView brandHint = text("TXT · EPUB 소설 리더", 11, mutedColor(), false);
        brandText.addView(brandName);
        brandText.addView(brandHint);
        LinearLayout.LayoutParams brandTextLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandTextLp.leftMargin = dp(11);
        brand.addView(brandText, brandTextLp);
        outer.addView(brand);

        Button open = button("책 파일 열기", true);
        open.setMinHeight(dp(50));
        open.setOnClickListener(v -> pickBook());
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, -2);
        openLp.topMargin = dp(16);
        outer.addView(open, openLp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(0, dp(22), 0, dp(20));

        TextView recentTitle = text("최근 읽은 책", 18, textColor(), true);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(-1, -2);
        content.addView(recentTitle, rtLp);
        TextView recentHint = text("읽던 위치부터 바로 이어 읽을 수 있어요", 12, mutedColor(), false);
        content.addView(recentHint);
        addRecentBooks(content);

        scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private void addRecentBooks(LinearLayout parent) {
        List<RecentBook> recents = readRecents();
        if (recents.isEmpty()) {
            TextView empty = text("아직 열린 책이 없습니다.\n파일을 열면 여기에서 이어 읽을 수 있어요.", 13, mutedColor(), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(22), dp(12), dp(22));
            GradientDrawable outline = roundRect(paperColor(), dp(16));
            outline.setStroke(dp(1), "night".equals(theme) ? Color.rgb(65, 69, 66) : Color.rgb(224, 218, 207));
            empty.setBackground(outline);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(12);
            parent.addView(empty, lp);
            return;
        }
        for (RecentBook book : recents) {
            LinearLayout card = row();
            card.setPadding(dp(13), dp(11), dp(13), dp(11));
            card.setBackground(roundRect("night".equals(theme) ? Color.rgb(35, 39, 37) : Color.rgb(255, 253, 248), dp(15)));
            TextView icon = text(book.format.equals("EPUB") ? "E" : "T", 15, GREEN, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(roundRect("night".equals(theme) ? Color.rgb(49, 57, 50) : Color.rgb(229, 236, 225), dp(10)));
            card.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(46)));
            LinearLayout details = column();
            TextView name = text(book.title, 14, textColor(), true);
            TextView meta = text(book.format + "  ·  이어 읽기", 11, mutedColor(), false);
            details.addView(name); details.addView(meta);
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
            detailsLp.leftMargin = dp(12);
            card.addView(details, detailsLp);
            TextView arrow = text("›", 23, mutedColor(), false);
            arrow.setGravity(Gravity.CENTER);
            card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(46)));
            card.setOnClickListener(v -> openBook(Uri.parse(book.uri)));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.topMargin = dp(9);
            parent.addView(card, cardLp);
        }
    }

    private static class RecentBook {
        String uri, title, format;
        RecentBook(String uri, String title, String format) { this.uri=uri; this.title=title; this.format=format; }
    }

    private List<RecentBook> readRecents() {
        ArrayList<RecentBook> list = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(prefs.getString("recent", "[]"));
            for (int i=0;i<arr.length();i++) {
                org.json.JSONObject o=arr.getJSONObject(i);
                list.add(new RecentBook(o.optString("uri"),o.optString("title"),o.optString("format","TXT")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void saveRecent(Uri uri, String title, String format) {
        try {
            org.json.JSONArray next = new org.json.JSONArray();
            next.put(new org.json.JSONObject().put("uri",uri.toString()).put("title",title).put("format",format));
            for (RecentBook b:readRecents()) {
                if (!b.uri.equals(uri.toString()) && next.length()<12)
                    next.put(new org.json.JSONObject().put("uri",b.uri).put("title",b.title).put("format",b.format));
            }
            prefs.edit().putString("recent",next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void pickBook() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","application/epub+zip","application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_BOOK);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_BOOK && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri,data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch(Exception ignored) {}
            openBook(uri);
        }
    }

    private String displayName(Uri uri) {
        String name=null;
        try(Cursor c=getContentResolver().query(uri,null,null,null,null)) {
            if(c!=null && c.moveToFirst()) {
                int col=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(col>=0)name=c.getString(col);
            }
        } catch(Exception ignored) {}
        return name==null?"책":name;
    }

    private void openBook(Uri uri) {
        if(uri==null)return;
        if(loading!=null && loading.isShowing())loading.dismiss();
        loading=new ProgressDialog(this);
        loading.setMessage("책을 여는 중…");
        loading.setCancelable(false);
        loading.show();
        final String name=displayName(uri);
        io.execute(() -> {
            try {
                byte[] bytes;
                try(InputStream in=getContentResolver().openInputStream(uri)) {
                    if(in==null)throw new IllegalArgumentException("파일을 열 수 없습니다.");
                    bytes=readAll(in);
                }
                String ext=name.toLowerCase(Locale.ROOT);
                ParsedBook parsed;
                if(ext.endsWith(".epub")) parsed=parseEpub(bytes,name);
                else if(ext.endsWith(".txt") || "text/plain".equals(getContentResolver().getType(uri))) parsed=parseTxt(decodeText(bytes),name);
                else throw new IllegalArgumentException("TXT 또는 EPUB 파일을 선택해 주세요.");
                runOnUiThread(() -> {
                    if(loading!=null)loading.dismiss();
                    showBook(uri,parsed);
                });
            } catch(Exception error) {
                runOnUiThread(() -> {
                    if(loading!=null)loading.dismiss();
                    Toast.makeText(this,error.getMessage()==null?"책을 열지 못했습니다.":error.getMessage(),Toast.LENGTH_LONG).show();
                    if(screen==null)showLibrary();
                });
            }
        });
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[32768];int n;long total=0;
        while((n=in.read(buffer))!=-1){total+=n;if(total>80L*1024*1024)throw new IllegalArgumentException("80MB 이하의 책 파일을 열어 주세요.");out.write(buffer,0,n);}
        return out.toByteArray();
    }

    private String decodeText(byte[] bytes) {
        if(bytes.length>=2 && (bytes[0]&255)==0xff && (bytes[1]&255)==0xfe)
            return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2 && (bytes[0]&255)==0xfe && (bytes[1]&255)==0xff)
            return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch(CharacterCodingException ignored) {
            try{return Charset.forName("EUC-KR").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
            catch(Exception e){return new String(bytes,StandardCharsets.UTF_8);}
        }
    }

    private ParsedBook parseTxt(String text,String filename) {
        String cleaned=normalizeText(text);
        if(cleaned.isEmpty())throw new IllegalArgumentException("파일에 읽을 내용이 없습니다.");
        ParsedBook book=new ParsedBook();book.format="TXT";book.title=filename.replaceAll("(?i)\\.txt$","");
        String[] lines=cleaned.split("\\n",-1);
        Pattern heading=Pattern.compile("^\\s*(?:제\\s*\\d+\\s*(?:장|화|회|편)|\\d{1,5}\\s*화|chapter\\s+(?:\\d+|[ivxlcdm]+)|프롤로그|에필로그|작가의\\s*말|후기|외전|번외|prologue|epilogue).{0,90}$",Pattern.CASE_INSENSITIVE);
        ArrayList<Integer> starts=new ArrayList<>();
        for(int i=0;i<lines.length;i++)if(lines[i].trim().length()<100&&heading.matcher(lines[i].trim()).matches())starts.add(i);
        if(starts.isEmpty()) { book.chapters.addAll(splitLarge("본문",cleaned));return book; }
        String prefix=join(lines,0,starts.get(0)).trim();
        if(prefix.length()>80)book.chapters.addAll(splitLarge("앞부분",prefix));
        for(int i=0;i<starts.size();i++) {
            int start=starts.get(i),end=i+1<starts.size()?starts.get(i+1):lines.length;
            String title=lines[start].trim();String body=join(lines,start+1,end).trim();
            if(!body.isEmpty())book.chapters.addAll(splitLarge(title,body));
        }
        if(book.chapters.isEmpty())book.chapters.addAll(splitLarge("본문",cleaned));
        return book;
    }

    private String join(String[] lines,int start,int end) {
        StringBuilder b=new StringBuilder();
        for(int i=start;i<end;i++){if(i>start)b.append('\n');b.append(lines[i]);}
        return b.toString();
    }

    private ArrayList<Chapter> splitLarge(String title,String body) {
        ArrayList<Chapter> out=new ArrayList<>();
        int max=14000,start=0;
        while(start<body.length()) {
            int end=Math.min(body.length(),start+max);
            if(end<body.length()) {
                int cut=body.lastIndexOf('\n',end);
                if(cut>start+max/2)end=cut;
            }
            String piece=body.substring(start,end).trim();
            if(!piece.isEmpty())out.add(new Chapter(out.isEmpty()?title:title+" · 이어 읽기",piece));
            start=end;
            while(start<body.length() && body.charAt(start)=='\n')start++;
        }
        return out;
    }

    private String normalizeText(String value) {
        return value.replace("\u0000","").replace("\r\n","\n").replace('\r','\n').replaceAll("^\\uFEFF","").trim();
    }

    private ParsedBook parseEpub(byte[] bytes,String filename) throws Exception {
        Map<String,byte[]> files=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String path=normalizePath(entry.getName());
                String lower=path.toLowerCase(Locale.ROOT);
                if(!entry.isDirectory() && (lower.equals("meta-inf/container.xml") || lower.endsWith(".opf") || lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".ncx"))) {
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;long size=0;
                    while((n=zip.read(buf))!=-1){size+=n;if(size>30L*1024*1024)throw new IllegalArgumentException("EPUB 내부 문서가 너무 큽니다.");out.write(buf,0,n);}
                    files.put(path,out.toByteArray());
                }
                zip.closeEntry();
            }
        }
        byte[] containerBytes=files.get("META-INF/container.xml");
        if(containerBytes==null)throw new IllegalArgumentException("EPUB 책 정보를 찾을 수 없습니다.");
        Document container=parseXml(containerBytes);
        Element rootfile=firstElement(container,"rootfile");
        if(rootfile==null)throw new IllegalArgumentException("EPUB 책 정보를 읽을 수 없습니다.");
        String opfPath=normalizePath(rootfile.getAttribute("full-path"));
        byte[] opfBytes=files.get(opfPath);
        if(opfBytes==null)throw new IllegalArgumentException("EPUB 목차 파일을 찾을 수 없습니다.");
        Document opf=parseXml(opfBytes);
        ParsedBook book=new ParsedBook();book.format="EPUB";
        book.title=firstText(opf,"title");if(book.title.isEmpty())book.title=filename.replaceAll("(?i)\\.epub$","");
        book.author=firstText(opf,"creator");
        LinkedHashMap<String,ManifestItem> manifest=new LinkedHashMap<>();
        for(Element item:elements(opf,"item")) {
            ManifestItem m=new ManifestItem();m.path=resolvePath(opfPath,item.getAttribute("href"));
            m.mediaType=item.getAttribute("media-type");m.properties=item.getAttribute("properties");
            manifest.put(item.getAttribute("id"),m);
        }
        HashMap<String,String> toc=new HashMap<>();
        ManifestItem nav=null,ncx=null;
        for(ManifestItem item:manifest.values()) {
            if(item.properties.contains("nav"))nav=item;
            if("application/x-dtbncx+xml".equals(item.mediaType))ncx=item;
        }
        if(nav!=null && files.containsKey(nav.path)) {
            Document navDoc=parseXml(files.get(nav.path));
            for(Element a:elements(navDoc,"a")) {
                String href=a.getAttribute("href"),label=a.getTextContent().trim();
                if(!href.isEmpty()&&!label.isEmpty())toc.put(resolvePath(nav.path,href),label);
            }
        } else if(ncx!=null && files.containsKey(ncx.path)) {
            Document ncxDoc=parseXml(files.get(ncx.path));
            for(Element point:elements(ncxDoc,"navPoint")) {
                Element content=firstChild(point,"content"),label=firstChild(point,"text");
                if(content!=null&&label!=null)toc.put(resolvePath(ncx.path,content.getAttribute("src")),label.getTextContent().trim());
            }
        }
        Element spine=firstElement(opf,"spine");
        if(spine!=null)for(Element ref:childElements(spine,"itemref")) {
            ManifestItem item=manifest.get(ref.getAttribute("idref"));
            if(item==null || !(item.mediaType.contains("xhtml")||item.mediaType.contains("html")) || item.properties.contains("nav"))continue;
            byte[] chapterBytes=files.get(item.path);if(chapterBytes==null)continue;
            Document doc=parseXml(chapterBytes);
            String title=firstText(doc,"h1");if(title.isEmpty())title=firstText(doc,"h2");
            String content=extractDocumentText(doc);
            if(content.isEmpty())continue;
            if(!title.isEmpty() && content.startsWith(title))content=content.substring(title.length()).trim();
            String sectionTitle=toc.get(item.path);
            if(sectionTitle==null||sectionTitle.isEmpty())sectionTitle=title.isEmpty()?"제 "+(book.chapters.size()+1)+"장":title;
            book.chapters.addAll(splitLarge(sectionTitle,content));
        }
        if(book.chapters.isEmpty())throw new IllegalArgumentException("EPUB에서 읽을 수 있는 본문이 없습니다.");
        return book;
    }

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);factory.setExpandEntityReferences(false);
        try{factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);}catch(Exception ignored){}
        try{factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}
        try{factory.setFeature("http://xml.org/sax/features/external-general-entities",false);}catch(Exception ignored){}
        try{factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private ArrayList<Element> elements(Document doc,String name) {
        ArrayList<Element> result=new ArrayList<>();NodeList nodes=doc.getElementsByTagName("*");
        for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element && localName(nodes.item(i)).equalsIgnoreCase(name))result.add((Element)nodes.item(i));
        return result;
    }

    private ArrayList<Element> childElements(Element parent,String name) {
        ArrayList<Element> result=new ArrayList<>();NodeList nodes=parent.getChildNodes();
        for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element && localName(nodes.item(i)).equalsIgnoreCase(name))result.add((Element)nodes.item(i));
        return result;
    }

    private Element firstElement(Document doc,String name) {ArrayList<Element> list=elements(doc,name);return list.isEmpty()?null:list.get(0);}
    private Element firstChild(Element parent,String name) {ArrayList<Element> list=childElements(parent,name);return list.isEmpty()?null:list.get(0);}
    private String firstText(Document doc,String name) {Element el=firstElement(doc,name);return el==null?"":el.getTextContent().trim();}
    private String localName(Node node) {String n=node.getLocalName();if(n==null)n=node.getNodeName();int colon=n.indexOf(':');return colon>=0?n.substring(colon+1):n;}

    private String normalizePath(String value) {
        String decoded=Uri.decode(value.replace('\\','/'));
        ArrayList<String> stack=new ArrayList<>();
        for(String part:decoded.split("/")) {
            if(part.isEmpty()||".".equals(part))continue;
            if("..".equals(part)){if(!stack.isEmpty())stack.remove(stack.size()-1);}else stack.add(part);
        }
        return String.join("/",stack);
    }

    private String resolvePath(String base,String href) {
        String clean=href.split("#",2)[0].split("\\?",2)[0];
        String decoded=Uri.decode(clean.replace('\\','/'));
        if(decoded.startsWith("/"))return normalizePath(decoded);
        int slash=base.lastIndexOf('/');String parent=slash>=0?base.substring(0,slash+1):"";
        return normalizePath(parent+decoded);
    }

    private String extractDocumentText(Document doc) {
        Element body=firstElement(doc,"body");if(body==null)body=doc.getDocumentElement();
        StringBuilder out=new StringBuilder();appendNode(body,out);
        return normalizeText(out.toString()).replaceAll("[ \\t]+\\n","\n").replaceAll("\\n{3,}","\n\n");
    }

    private void appendNode(Node node,StringBuilder out) {
        if(node.getNodeType()==Node.TEXT_NODE) {out.append(node.getNodeValue());return;}
        if(!(node instanceof Element)) {NodeList kids=node.getChildNodes();for(int i=0;i<kids.getLength();i++)appendNode(kids.item(i),out);return;}
        String tag=localName(node).toLowerCase(Locale.ROOT);
        if(tag.equals("script")||tag.equals("style")||tag.equals("nav")||tag.equals("svg")||tag.equals("head"))return;
        if(tag.equals("br")){out.append('\n');return;}
        boolean block=tag.matches("p|div|h[1-6]|li|blockquote|section|article|pre|tr|title");
        if(block)addParagraphBreak(out);
        NodeList kids=node.getChildNodes();for(int i=0;i<kids.getLength();i++)appendNode(kids.item(i),out);
        if(block)addParagraphBreak(out);
    }

    private void addParagraphBreak(StringBuilder out) {
        int length=out.length();if(length==0)return;
        if(out.charAt(length-1)!='\n')out.append('\n');
        if(out.length()<2||out.charAt(out.length()-2)!='\n')out.append('\n');
    }

    private void showBook(Uri uri,ParsedBook book) {
        currentUri=uri;currentTitle=book.title;currentAuthor=book.author;currentFormat=book.format;chapters=book.chapters;
        currentKey=Integer.toHexString(uri.toString().hashCode());
        saveRecent(uri,currentTitle,currentFormat);
        chapterIndex=Math.max(0,Math.min(chapters.size()-1,prefs.getInt("chapter_"+currentKey,0)));
        buildReader(true);
    }

    private void buildReader(boolean restorePosition) {
        LinearLayout root=column();root.setPadding(0,0,0,0);installScreen(root);
        root.setBackgroundColor(paperColor());bodyViews.clear();

        LinearLayout toolbar=row();toolbar.setPadding(dp(8),dp(4),dp(8),dp(4));
        TextView back=text("‹",32,textColor(),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->{savePosition();showLibrary();});
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        LinearLayout titles=column();
        readerTitle=text(currentTitle,14,textColor(),true);readerTitle.setSingleLine(true);readerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chapterMeta=text("",11,mutedColor(),false);chapterMeta.setSingleLine(true);
        titles.addView(readerTitle);titles.addView(chapterMeta);
        toolbar.addView(titles,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView toc=text("목차",11,textColor(),true);toc.setGravity(Gravity.CENTER);toc.setOnClickListener(v->showToc());
        TextView mark=text("☆",20,textColor(),false);mark.setGravity(Gravity.CENTER);mark.setOnClickListener(v->toggleBookmark());
        TextView settings=text("Aa",13,textColor(),true);settings.setGravity(Gravity.CENTER);settings.setOnClickListener(v->showSettings());
        toolbar.addView(toc,new LinearLayout.LayoutParams(dp(48),dp(48)));
        toolbar.addView(mark,new LinearLayout.LayoutParams(dp(48),dp(48)));
        toolbar.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(toolbar);

        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf("night".equals(theme)?Color.rgb(54,58,56):Color.rgb(228,222,211)));
        root.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(2)));

        readerScroll=new ScrollView(this);readerScroll.setFillViewport(false);readerScroll.setClipToPadding(false);
        chapterContent=column();chapterContent.setPadding(horizontalInset(),dp(34),horizontalInset(),dp(44));
        chapterMeta.setText(chapters.get(chapterIndex).title+(bookAuthor().isEmpty()?"":" · "+bookAuthor()));
        TextView kicker=text(String.format(Locale.KOREA,"%02d / %02d",chapterIndex+1,chapters.size()),11,mutedColor(),true);
        chapterContent.addView(kicker);
        chapterHeading=text(chapters.get(chapterIndex).title,27,textColor(),true);
        chapterHeading.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams headingLp=new LinearLayout.LayoutParams(-1,-2);headingLp.topMargin=dp(16);headingLp.bottomMargin=dp(28);
        chapterContent.addView(chapterHeading,headingLp);
        String[] paragraphs=chapters.get(chapterIndex).text.split("\\n[ \\t]*\\n+");
        if(paragraphs.length==0)paragraphs=new String[]{chapters.get(chapterIndex).text};
        for(String paragraph:paragraphs) {
            if(paragraph.trim().isEmpty())continue;
            TextView body=text(paragraph.trim(),textSize,textColor(),false);
            body.setTypeface("night".equals(theme)?Typeface.SERIF:Typeface.SERIF);
            body.setLineSpacing(0,lineSpacing);body.setGravity(Gravity.TOP|Gravity.START);
            body.setTextIsSelectable(true);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(22);
            chapterContent.addView(body,p);bodyViews.add(body);
        }
        readerScroll.addView(chapterContent);
        root.addView(readerScroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=row();footer.setGravity(Gravity.CENTER);footer.setPadding(dp(10),dp(7),dp(10),dp(7));
        Button prev=button("이전 장",false);prev.setEnabled(chapterIndex>0);prev.setOnClickListener(v->moveChapter(-1));
        Button next=button("다음 장",false);next.setEnabled(chapterIndex<chapters.size()-1);next.setOnClickListener(v->moveChapter(1));
        progressText=text("0%",12,mutedColor(),false);progressText.setGravity(Gravity.CENTER);
        footer.addView(prev);LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(dp(52),dp(42));progressLp.leftMargin=dp(6);progressLp.rightMargin=dp(6);footer.addView(progressText,progressLp);footer.addView(next);
        root.addView(footer);
        applyReaderStyle();
        readerScroll.setOnScrollChangeListener((View v,int x,int y,int oldX,int oldY)->{
            updateProgress();handler.removeCallbacks(saveRunnable);handler.postDelayed(saveRunnable,600);
        });
        if(restorePosition)readerScroll.post(()->readerScroll.scrollTo(0,prefs.getInt("scroll_"+currentKey,0)));
        updateProgress();
    }

    private String bookAuthor() { return currentAuthor; }

    private int horizontalInset() {
        if("narrow".equals(width))return dp(34);
        if("wide".equals(width))return dp(17);
        return dp(23);
    }

    private void applyReaderStyle() {
        if(screen!=null)screen.setBackgroundColor(paperColor());
        if(readerScroll!=null)readerScroll.setBackgroundColor(paperColor());
        if(chapterContent!=null){chapterContent.setBackgroundColor(paperColor());chapterContent.setPadding(horizontalInset(),dp(34),horizontalInset(),dp(44));}
        if(chapterHeading!=null){chapterHeading.setTextColor(textColor());chapterHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP,Math.min(36,textSize+6));}
        for(TextView view:bodyViews){view.setTextColor(textColor());view.setTextSize(TypedValue.COMPLEX_UNIT_SP,textSize);view.setLineSpacing(0,lineSpacing);}
        if(readerTitle!=null)readerTitle.setTextColor(textColor());
        if(chapterMeta!=null)chapterMeta.setTextColor(mutedColor());
        applySystemBars();
    }

    private void updateProgress() {
        if(readerScroll==null||progressBar==null||chapters.isEmpty())return;
        int max=Math.max(0,readerScroll.getChildAt(0).getHeight()-readerScroll.getHeight());
        int scrollY=readerScroll.getScrollY();
        int before=0,total=0;
        for(int i=0;i<chapters.size();i++){int len=chapters.get(i).text.length();total+=len;if(i<chapterIndex)before+=len;}
        float ratio=max==0?0:Math.min(1f,Math.max(0f,(float)scrollY/max));
        float value=total==0?0:(float)(before+chapters.get(chapterIndex).text.length()*ratio)/total;
        int p=Math.max(0,Math.min(1000,Math.round(value*1000)));
        progressBar.setProgress(p);
        if(progressText!=null)progressText.setText(Math.round(value*100)+"%");
    }

    private void savePosition() {
        if(currentKey.isEmpty()||readerScroll==null)return;
        prefs.edit().putInt("chapter_"+currentKey,chapterIndex).putInt("scroll_"+currentKey,readerScroll.getScrollY()).apply();
    }

    private void moveChapter(int offset) {
        if(chapters.isEmpty())return;
        savePosition();chapterIndex=Math.max(0,Math.min(chapters.size()-1,chapterIndex+offset));
        prefs.edit().putInt("chapter_"+currentKey,chapterIndex).putInt("scroll_"+currentKey,0).apply();
        buildReader(false);
    }

    private void showToc() {
        if(chapters.isEmpty())return;
        String[] items=new String[chapters.size()];
        for(int i=0;i<chapters.size();i++)items[i]=String.format(Locale.KOREA,"%02d  %s",i+1,chapters.get(i).title);
        new AlertDialog.Builder(this).setTitle("목차").setItems(items,(dialog,which)->{
            savePosition();chapterIndex=which;prefs.edit().putInt("chapter_"+currentKey,which).putInt("scroll_"+currentKey,0).apply();buildReader(false);
        }).setNeutralButton("책갈피",(dialog,which)->showBookmarks()).setNegativeButton("닫기",null).show();
    }

    private String bookmarksKey(){return "bookmarks_"+currentKey;}
    private void toggleBookmark() {
        if(currentKey.isEmpty())return;
        savePosition();
        try {
            org.json.JSONArray arr=new org.json.JSONArray(prefs.getString(bookmarksKey(),"[]"));
            int y=readerScroll==null?0:readerScroll.getScrollY(),found=-1;
            for(int i=0;i<arr.length();i++){org.json.JSONObject b=arr.getJSONObject(i);if(b.optInt("chapter")==chapterIndex&&Math.abs(b.optInt("scroll")-y)<dp(90)){found=i;break;}}
            if(found>=0){org.json.JSONArray next=new org.json.JSONArray();for(int i=0;i<arr.length();i++)if(i!=found)next.put(arr.get(i));arr=next;Toast.makeText(this,"책갈피를 삭제했습니다.",Toast.LENGTH_SHORT).show();}
            else {arr.put(new org.json.JSONObject().put("chapter",chapterIndex).put("scroll",y));Toast.makeText(this,"책갈피를 저장했습니다.",Toast.LENGTH_SHORT).show();}
            prefs.edit().putString(bookmarksKey(),arr.toString()).apply();
        }catch(Exception ignored){}
    }

    private void showBookmarks() {
        try {
            org.json.JSONArray arr=new org.json.JSONArray(prefs.getString(bookmarksKey(),"[]"));
            if(arr.length()==0){new AlertDialog.Builder(this).setTitle("책갈피").setMessage("저장한 책갈피가 없습니다.").setPositiveButton("확인",null).show();return;}
            String[] labels=new String[arr.length()];
            for(int i=0;i<arr.length();i++){
                org.json.JSONObject b=arr.getJSONObject(i);int ci=Math.max(0,Math.min(chapters.size()-1,b.optInt("chapter")));
                labels[i]=String.format(Locale.KOREA,"%02d  %s · %d%%",ci+1,chapters.get(ci).title,(int)(100f*b.optInt("scroll")/Math.max(1,readerScroll.getChildAt(0).getHeight())));
            }
            new AlertDialog.Builder(this).setTitle("책갈피").setItems(labels,(d,which)->{
                try{org.json.JSONObject b=arr.getJSONObject(which);chapterIndex=Math.max(0,Math.min(chapters.size()-1,b.optInt("chapter")));prefs.edit().putInt("chapter_"+currentKey,chapterIndex).apply();buildReader(false);readerScroll.post(()->readerScroll.scrollTo(0,b.optInt("scroll")));}catch(Exception ignored){}
            }).setPositiveButton("닫기",null).show();
        }catch(Exception ignored){}
    }

    private void showSettings() {
        LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(6));
        addSeekSetting(panel,"글자 크기",16,34,textSize,value->{textSize=value;prefs.edit().putInt("textSize",textSize).apply();applyReaderStyle();});
        addSeekSetting(panel,"줄 간격",150,250,Math.round(lineSpacing*100),value->{lineSpacing=value/100f;prefs.edit().putFloat("lineSpacing",lineSpacing).apply();applyReaderStyle();});
        addSeekSetting(panel,"화면 밝기",10,100,brightness,value->{brightness=value;prefs.edit().putInt("brightness",brightness).apply();applyReaderStyle();});
        TextView themeLabel=text("화면 분위기",13,textColor(),true);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(14);panel.addView(themeLabel,tlp);
        LinearLayout themes=row();
        for(String[] item:new String[][]{{"종이","paper"},{"세피아","sepia"},{"야간","night"}}){Button b=button(item[0],theme.equals(item[1]));b.setOnClickListener(v->{theme=item[1];prefs.edit().putString("theme",theme).apply();applyReaderStyle();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.rightMargin=dp(5);themes.addView(b,lp);}
        LinearLayout.LayoutParams themeLp=new LinearLayout.LayoutParams(-1,-2);themeLp.topMargin=dp(8);panel.addView(themes,themeLp);
        TextView widthLabel=text("본문 너비",13,textColor(),true);LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,-2);wlp.topMargin=dp(16);panel.addView(widthLabel,wlp);
        LinearLayout widths=row();
        for(String[] item:new String[][]{{"좁게","narrow"},{"보통","medium"},{"넓게","wide"}}){Button b=button(item[0],width.equals(item[1]));b.setOnClickListener(v->{width=item[1];prefs.edit().putString("width",width).apply();applyReaderStyle();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.rightMargin=dp(5);widths.addView(b,lp);}
        LinearLayout.LayoutParams widthLp=new LinearLayout.LayoutParams(-1,-2);widthLp.topMargin=dp(8);panel.addView(widths,widthLp);
        ScrollView scroller=new ScrollView(this);scroller.addView(panel);
        new AlertDialog.Builder(this).setTitle("읽기 설정").setView(scroller).setPositiveButton("닫기",null).show();
    }

    private interface IntChange { void changed(int value); }
    private void addSeekSetting(LinearLayout parent,String label,int min,int max,int value,IntChange change) {
        LinearLayout holder=column();TextView caption=text(label+"  ·  "+formatSettingValue(label,value),13,textColor(),true);holder.addView(caption);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(Math.max(0,Math.min(max-min,value-min)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){int v=min+progress;caption.setText(label+"  ·  "+formatSettingValue(label,v));if(fromUser)change.changed(v);}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        holder.addView(seek);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);parent.addView(holder,lp);
    }

    private String formatSettingValue(String label,int value) {
        if(label.equals("줄 간격"))return String.format(Locale.ROOT,"%.2f",value/100f);
        if(label.equals("화면 밝기"))return value+"%";
        if(label.equals("글자 크기"))return value+"sp";
        return String.valueOf(value);
    }

    private void goHome() { savePosition();showLibrary(); }

    @Override public void onBackPressed() {
        if(readerScroll!=null){goHome();return;}
        super.onBackPressed();
    }
}
