package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A game's page, opened from the Games tab: the artwork behind, the details on the left (IGDB's, with Steam's
 * description in the TV's language and its price when the game is on Steam) and the trailer's picture on the right,
 * which opens it in the YouTube app (a player inside the page stutters on slow TVs). Built from plain views; Back
 * goes home.
 */
public class GameActivity extends Activity {
    private static final int INK = 0xFF0B0C10;

    private float k;
    private TextView description, credits;
    private LinearLayout chips;
    private String video;
    private volatile boolean closed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        k = getResources().getDisplayMetrics().widthPixels / 1920f;
        Intent in = getIntent();
        String link = in.getStringExtra("link");
        video = link == null ? null : Uri.parse(link).getQueryParameter("v");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(art, new FrameLayout.LayoutParams(-1, -1));
        View side = new View(this);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xF50B0C10, 0xD90B0C10, 0x590B0C10}));
        root.addView(side, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x000B0C10, 0xE60B0C10}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(96), px(80), px(96), px(80));
        row.setClipChildren(false);     // the focused trailer picture grows past the margin
        row.setClipToPadding(false);
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(px(820), -2));
        TextView title = text(Ui.displaySemibold, 64, Color.WHITE);
        title.setText(in.getStringExtra("title"));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        TextView meta = text(Ui.regular, 26, 0xD9FFFFFF);
        // On the card the trailer is the last part ("· Trailer on YouTube"): here it has a picture of its own.
        String m = in.getStringExtra("meta") == null ? "" : in.getStringExtra("meta");
        int cut = m.lastIndexOf(" · " + Ui.tr("Trailer on YouTube"));
        meta.setText(cut > 0 ? m.substring(0, cut) : m);
        info.addView(meta, margins(-1, 16));
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        info.addView(chips, margins(-2, 28));
        description = text(Ui.regular, 26, 0xE6FFFFFF);
        description.setLineSpacing(0, 1.35f);
        description.setMaxLines(8);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setText(Ui.tr("Loading details…"));
        info.addView(description, margins(-1, 32));
        credits = text(Ui.regular, 22, 0x99FFFFFF);
        info.addView(credits, margins(-1, 24));
        if (video != null) {
            TextView youtube = button(Ui.tr("Watch on YouTube"));
            youtube.setOnClickListener(v -> openYouTube());
            info.addView(youtube, margins(-2, 40));
            youtube.requestFocus();
        }

        row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.setClipToOutline(true);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 24 * k);
            }
        });
        row.addView(frame, new LinearLayout.LayoutParams(px(860), px(484)));
        ImageView thumb = new ImageView(this);
        if (video != null) {
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
            TextView play = text(Ui.semibold, 40, INK);
            play.setText("\u25B6");
            play.setGravity(Gravity.CENTER);
            play.setPadding(px(8), 0, 0, 0);    // the triangle looks centered a little to the right
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xF2FFFFFF);
            play.setBackground(circle);
            frame.addView(play, new FrameLayout.LayoutParams(px(120), px(120), Gravity.CENTER));
            // The picture is a button too: the focus grows it a little, like the cards on the home screen.
            frame.setFocusable(true);
            frame.setClickable(true);
            frame.setOnClickListener(v -> openYouTube());
            frame.setOnFocusChangeListener((v, focus) ->
                    v.animate().scaleX(focus ? 1.04f : 1).scaleY(focus ? 1.04f : 1).setDuration(150).start());
        } else {
            TextView none = text(Ui.medium, 26, 0x99FFFFFF);
            none.setText(Ui.tr("No trailer yet"));
            none.setGravity(Gravity.CENTER);
            frame.addView(none, new FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(root);

        String image = in.getStringExtra("image");
        int id = in.getIntExtra("id", 0);
        new Thread(() -> {
            Bitmap b = image == null ? null : load(image);
            if (b != null) runOnUiThread(() -> art.setImageBitmap(b));
            if (video != null) {
                // YouTube's widest picture first; not every video has one.
                Bitmap t = load("https://i.ytimg.com/vi/" + video + "/maxresdefault.jpg");
                if (t == null) t = load("https://i.ytimg.com/vi/" + video + "/hqdefault.jpg");
                Bitmap shown = t;
                if (shown != null && !closed) runOnUiThread(() -> thumb.setImageBitmap(shown));
            }
            try {
                JSONObject game = Recs.get(this).gameDetails(id);
                if (!closed) runOnUiThread(() -> show(game));
            } catch (Exception e) {
                AppLog.w("game page: " + e.getMessage());
                if (!closed) runOnUiThread(() -> description.setText(Ui.tr("Couldn't load the details.")));
            }
        }, "game-page").start();
    }

    /** Fills the page: Steam's description, genres and makers when there are, IGDB's otherwise. */
    private void show(JSONObject game) {
        JSONObject steam = game.optJSONObject("steam");
        String about = steam == null ? "" : Html.plain(steam.optString("short_description"));
        if (about.isEmpty()) about = game.optString("summary");
        description.setText(about.isEmpty() ? Ui.tr("No description yet.") : about);

        chips.removeAllViews();
        double rating = game.optDouble("aggregated_rating", Double.NaN);
        if (Double.isNaN(rating)) rating = game.optDouble("total_rating", Double.NaN);
        if (!Double.isNaN(rating)) chip(Ui.trf("Rating {n}", "n", Math.round(rating)), false);
        if (steam != null) {
            JSONObject price = steam.optJSONObject("price_overview");
            if (steam.optBoolean("is_free")) {
                chip(Ui.tr("Free"), true);
            } else if (price != null && !price.optString("final_formatted").isEmpty()) {
                chip(Ui.trf("{price} on Steam", "price", price.optString("final_formatted")), true);
                if (price.optInt("discount_percent") > 0) chip(Ui.trf("{n}% off", "n", price.optInt("discount_percent")), true);
            }
        }
        List<String> genres = names(steam == null ? null : steam.optJSONArray("genres"), "description", 3);
        if (genres.isEmpty()) genres = names(game.optJSONArray("genres"), "name", 3);
        for (String g : genres) chip(g, false);

        List<String> developers = new ArrayList<>(), publishers = new ArrayList<>();
        JSONArray companies = game.optJSONArray("involved_companies");
        for (int i = 0; companies != null && i < companies.length(); i++) {
            JSONObject c = companies.optJSONObject(i);
            String name = c == null || c.optJSONObject("company") == null ? "" : c.optJSONObject("company").optString("name");
            if (name.isEmpty()) continue;
            if (c.optBoolean("developer") && !developers.contains(name)) developers.add(name);
            if (c.optBoolean("publisher") && !publishers.contains(name)) publishers.add(name);
        }
        List<String> lines = new ArrayList<>();
        if (!developers.isEmpty()) lines.add(Ui.trf("Developer: {name}", "name", TextUtils.join(", ", developers)));
        if (!publishers.isEmpty()) lines.add(Ui.trf("Publisher: {name}", "name", TextUtils.join(", ", publishers)));
        credits.setText(TextUtils.join("   ·   ", lines));
    }

    private static List<String> names(JSONArray a, String field, int max) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length() && out.size() < max; i++) {
            String n = a.optJSONObject(i) == null ? "" : a.optJSONObject(i).optString(field);
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return out;
    }

    private void openYouTube() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + video))
                .setPackage("com.google.android.youtube.tv");
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            AppLog.w("youtube: " + e.getMessage());
        }
    }

    private static Bitmap load(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                c.disconnect();
            }
        } catch (Exception | OutOfMemoryError e) {
            AppLog.w("game page image: " + e.getMessage());
            return null;
        }
    }

    // MARK: - Views

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

    private LinearLayout.LayoutParams margins(int width, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, -2);
        lp.topMargin = px(top);
        return lp;
    }

    private void chip(String label, boolean accent) {
        TextView t = text(Ui.medium, 22, accent ? INK : Color.WHITE);
        t.setText(label);
        t.setPadding(px(20), px(10), px(20), px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(999));
        bg.setColor(accent ? 0xFFFFFFFF : 0x33FFFFFF);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = px(12);
        chips.addView(t, lp);
    }

    /** A pill button: glassy at rest, white with dark text and a little bigger with the focus (as on the home screen). */
    private TextView button(String label) {
        TextView t = text(Ui.semibold, 26, Color.WHITE);
        t.setText(label);
        t.setPadding(px(36), px(18), px(36), px(18));
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(999));
        bg.setColor(0x33FFFFFF);
        t.setBackground(bg);
        t.setOnFocusChangeListener((v, focus) -> {
            bg.setColor(focus ? 0xFFFFFFFF : 0x33FFFFFF);
            t.setTextColor(focus ? INK : Color.WHITE);
            v.animate().scaleX(focus ? 1.06f : 1).scaleY(focus ? 1.06f : 1).setDuration(150).start();
        });
        t.setPivotX(0);
        return t;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override protected void onDestroy() {
        closed = true;
        super.onDestroy();
    }

    /** Steam's descriptions are HTML: the text alone. */
    private static final class Html {
        static String plain(String html) {
            if (html == null || html.isEmpty()) return "";
            // Line breaks stay (Steam often ends a sentence with one, without a period).
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    .replaceAll("[ \\t\\u00A0]+", " ").replaceAll(" *\\n[\\s]*", "\n").trim();
        }
    }
}
