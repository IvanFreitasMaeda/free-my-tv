package io.freemytv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A news item from the News tab, read in the app: the article's page is downloaded once and only its text is kept
 * (headings and paragraphs), drawn with plain views. No browser, ads or scripts, so it stays quick on slow TVs.
 * Up and down scroll; Back goes home.
 */
public class NewsActivity extends Activity {
    private static final int INK = 0xFF1C1C1E, DIM = 0xFF6E6E73;
    /** Where the article's text starts, in the order tried (IGN, Eurogamer, then any page with an <article>). */
    private static final String[] BODY = {"itemprop=\"articleBody\"", "class=\"article_body_content", "<article"};
    private static final Pattern BLOCK = Pattern.compile(
            "<(p|h2|h3|hr)\\b[^>]*>(.*?)(?=<(?:p|h2|h3|hr|div|/div|/section)\\b|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern NOISE = Pattern.compile(
            "<(script|style|figure|aside|noscript)\\b.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private float k;
    private ScrollView scroll;
    private LinearLayout column;
    private TextView status;
    private volatile boolean closed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        k = getResources().getDisplayMetrics().widthPixels / 1920f;
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFEBEBF0);
        scroll.setFocusable(true);
        scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = px((1920 - 1120) / 2f);
        column.setPadding(side, px(72), side, px(120));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackgroundColor(0xFFDCDCE0);
        photo.setClipToOutline(true);
        photo.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 24 * k);
            }
        });
        column.addView(photo, new LinearLayout.LayoutParams(-1, px(560)));
        TextView meta = text(Ui.medium, 24, DIM);
        meta.setText(getIntent().getStringExtra("meta"));
        column.addView(meta, margins(36));
        TextView title = text(Ui.displaySemibold, 52, INK);
        title.setText(getIntent().getStringExtra("title"));
        title.setLineSpacing(0, 1.1f);
        column.addView(title, margins(12));
        status = text(Ui.regular, 30, DIM);
        status.setText(Ui.tr("Loading…"));
        column.addView(status, margins(40));
        setContentView(scroll);
        scroll.requestFocus();

        String link = getIntent().getStringExtra("link"), image = getIntent().getStringExtra("image");
        new Thread(() -> {
            Bitmap b = image == null ? null : load(image);
            if (b != null && !closed) runOnUiThread(() -> photo.setImageBitmap(b));
            List<String[]> blocks = new ArrayList<>();
            try {
                if (link != null && link.startsWith("https://")) blocks = article(fetch(link));
            } catch (Exception e) {
                AppLog.w("news page: " + e.getMessage());
            }
            List<String[]> shown = blocks;
            if (!closed) runOnUiThread(() -> show(shown));
        }, "news-page").start();
    }

    private void show(List<String[]> blocks) {
        if (blocks.isEmpty()) {
            status.setText(Ui.tr("Couldn't load the article."));
            return;
        }
        column.removeView(status);
        boolean first = true;
        for (String[] b : blocks) {
            boolean heading = !b[0].equals("p");
            TextView t = text(heading ? Ui.semibold : Ui.regular, heading ? 36 : 30, INK);
            t.setText(b[1]);
            t.setLineSpacing(0, heading ? 1.15f : 1.5f);
            column.addView(t, margins(first ? 40 : heading ? 44 : 22));
            first = false;
        }
    }

    // MARK: - The article's text

    /** Headings and paragraphs from the article's body, until a rule (<hr>: where IGN's sign-off starts). */
    static List<String[]> article(String page) {
        List<String[]> out = new ArrayList<>();
        int start = -1;
        for (String marker : BODY) {
            start = page.indexOf(marker);
            if (start >= 0) break;
        }
        if (start < 0) return out;
        String body = page.substring(start, Math.min(page.length(), start + 120_000));
        int end = body.indexOf("</article>");
        if (end > 0) body = body.substring(0, end);
        body = NOISE.matcher(body).replaceAll("");
        Matcher m = BLOCK.matcher(body);
        while (m.find()) {
            String tag = m.group(1).toLowerCase(Locale.ROOT);
            if (tag.equals("hr")) break;
            String text = Html.fromHtml(m.group(2), Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
            if (tag.equals("p") && text.length() < 40) continue;    // captions, credits, empty paragraphs
            if (!text.isEmpty()) out.add(new String[] {tag, text});
        }
        return out;
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) FreeMyTV");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0 && out.size() < 1_500_000) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private Bitmap load(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;    // a photo: half the memory, no visible difference
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                c.disconnect();
            }
        } catch (Exception | OutOfMemoryError e) {
            AppLog.w("news image: " + e.getMessage());
            return null;
        }
    }

    // MARK: - Views and keys

    private int px(float design) {
        return Math.round(design * k);
    }

    private TextView text(android.graphics.Typeface face, float size, int color) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams margins(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(top);
        return lp;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
            // A steady step per press (ScrollView's own jumps half a screen).
            if (event.getAction() == KeyEvent.ACTION_DOWN) scroll.smoothScrollBy(0, (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1) * px(360));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        closed = true;
        super.onDestroy();
    }
}
