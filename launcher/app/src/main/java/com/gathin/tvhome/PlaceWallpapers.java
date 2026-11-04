package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.FaceDetector;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wallpapers of places to visit: once a week, takes the pictures Bing has shown on its home page over
 * the past days (landmarks, landscapes, wildlife), each with the place's name and a few lines about
 * it, keeps those with nobody in them, and deletes the week before. Needs no key and no account. The
 * launcher's only network use; "places": false in config.json turns it off.
 *
 * Runs on a background thread; every failure just leaves the previous pictures in place.
 */
final class PlaceWallpapers {
    private static final String TAG = "TvHome.Places";
    static final String DIR = "place-wallpapers";
    private static final long REFRESH_MS = 7L * 24 * 60 * 60 * 1000;
    /**
     * Words in a picture's name or headline that mean someone is in it. Only those two are read:
     * they describe the picture, where the paragraph under them tells the place's story, and
     * mentions locals or hikers far more often than it shows them. Faces are looked for as well.
     */
    private static final Pattern PEOPLE = Pattern.compile("\\b(people|persons?|man|men|woman|women|girls?|boys?"
            + "|child|children|kids?|famil(y|ies)|crowds?|festivals?|parades?|carnivals?|danc(er|ers|ing)"
            + "|surf(er|ers|ing)|hik(er|ers|ing)|climb(er|ers)|ski(er|ers)|swimm(er|ers)|fisherm[ae]n|monks?"
            + "|pilgrims?|tourists?|visitors?|weddings?|brides?|processions?|performers?|athletes?|runners?"
            + "|cyclists?|worshipp?ers?|spectators?|audiences?|musicians?|farmers?|riders?|couples?|locals"
            + "|lad(y|ies)|portraits?|selfies?|residents?|travell?ers?|crew|pedestrians?|sailors?|divers?"
            + "|kayakers?|soldiers?|guards?|vendors?|shoppers?|players?|fans)\\b", Pattern.CASE_INSENSITIVE);

    /** Raised whenever the rules for which pictures are kept change. */
    private static final int RULES = 1;

    private PlaceWallpapers() {
    }

    static File dir(Context ctx) {
        // Always the app's own files folder, even where the theme lives in the media folder (Config.dir):
        // nothing but the launcher reads these, and the media folder refuses the folder swap in refresh.
        File base = ctx.getExternalFilesDir(null);
        return base != null ? new File(base, DIR) : null;
    }

    /** The pictures on disk, in the order Bing showed them; empty when none. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0 && f.getName().endsWith(".jpg")) out.add(f);
        }
        Collections.sort(out);
        return out;
    }

    /** True when a week has passed since the last successful refresh, or there are no pictures yet. */
    static boolean due(Context ctx) {
        long last = prefs(ctx).getLong("refreshed_at", 0);
        // Pictures kept under earlier rules are fetched again once, under the current ones.
        if (prefs(ctx).getInt("rules", 0) < RULES) return true;
        return System.currentTimeMillis() - last >= REFRESH_MS || list(ctx).isEmpty();
    }

    /**
     * Downloads this week's places and replaces last week's with them. Blocking: call from a
     * background thread. Returns true when the pictures changed.
     */
    static boolean refresh(Context ctx) {
        File d = dir(ctx);
        if (d == null) return false;
        File fresh = new File(d.getParentFile(), DIR + ".new");
        try {
            deleteAll(fresh);
            if (!fresh.mkdirs()) return false;
            String mkt = Locale.getDefault().toLanguageTag();
            JSONObject model = new JSONObject(new String(get("https://www.bing.com/hp/api/model?mkt="
                    + URLEncoder.encode(mkt, "UTF-8"), 512 * 1024), StandardCharsets.UTF_8));
            JSONArray items = model.optJSONArray("MediaContents");
            int n = 0;
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                JSONObject about = item != null ? item.optJSONObject("ImageContent") : null;
                JSONObject image = about != null ? about.optJSONObject("Image") : null;
                if (image == null) continue;
                String place = about.optString("Title", "").trim();
                String headline = about.optString("Headline", "").trim();
                String text = about.optString("Description", "").trim();
                String url = image.optString("Url", "");
                if (place.isEmpty() || url.isEmpty()) continue;
                if (PEOPLE.matcher(place + " " + headline).find()) {
                    Log.i(TAG, "skipped, names people: " + place);
                    continue;
                }
                // The 1920 x 1080 picture, as a JPEG rather than the WebP Bing names first.
                byte[] jpg = get("https://www.bing.com" + url.replace(".webp", ".jpg"), 3 * 1024 * 1024);
                int faces = faces(jpg);
                if (faces != 0) {
                    Log.i(TAG, "skipped, " + (faces < 0 ? "unreadable" : faces + " face(s)") + ": " + place);
                    continue;
                }
                // Only somewhere: a picture whose caption names no country (fossils, say) is left out.
                String[] named = nameAndCountry(place);
                if (named[1].isEmpty()) {
                    Log.i(TAG, "skipped, no country: " + place);
                    continue;
                }
                String base = String.format(Locale.ROOT, "%02d", n++);
                write(new File(fresh, base + ".jpg"), jpg);
                // One line each, so a stray newline in the text cannot be read back as a name:
                // the thing's name, the description, the country.
                write(new File(fresh, base + ".txt"), (named[0].replace('\n', ' ') + "\n"
                        + text.replace('\n', ' ') + "\n" + named[1].replace('\n', ' ')).getBytes(StandardCharsets.UTF_8));
            }
            if (n == 0) {
                deleteAll(fresh);
                return false;
            }
            // Last week's pictures go once this week's are all in, and so do the film pictures
            // earlier versions kept.
            deleteAll(d);
            deleteAll(new File(d.getParentFile(), "movie-wallpapers"));
            if (!fresh.renameTo(d)) return false;
            prefs(ctx).edit().putLong("refreshed_at", System.currentTimeMillis()).putInt("rules", RULES).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "refresh failed: " + e);
            deleteAll(fresh);
            return false;
        }
    }

    private static final Pattern LEAD = Pattern.compile(
            "^((aerial|panoramic|close-up|night|winter|autumn|summer|spring)\\s+)?(views?|replicas?|life-size replica)\\s+of\\s+(the\\s+)?|^(the|a|an)\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final java.util.Set<String> SMALL = new java.util.HashSet<>(java.util.Arrays.asList(
            "a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "by", "with", "de", "del", "la", "le", "du"));
    private static final java.util.Set<String> US_STATES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida",
            "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine",
            "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
            "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio",
            "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas",
            "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"));

    /**
     * Splits Bing's caption ("Mount Sneffels in the San Juan Mountains near Ridgway, Colorado") into
     * the thing's name ("Mount Sneffels") and its country ("United States"), the country empty
     * when the caption names none ("Ammonite fossils from the Jurassic Period").
     */
    static String[] nameAndCountry(String caption) {
        String c = caption.replaceAll("\\s*\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
        int comma = c.indexOf(", ");
        String country = comma >= 0 ? c.substring(c.lastIndexOf(", ") + 2).trim() : "";
        if (US_STATES.contains(country)) country = "United States";
        // The name runs up to where the caption starts saying where or when.
        String name = comma >= 0 ? c.substring(0, comma) : c;
        java.util.regex.Matcher cut = Pattern.compile("\\s+(in|near|at|on|from|over|above|under|along|off|across)\\s+",
                Pattern.CASE_INSENSITIVE).matcher(name);
        if (cut.find() && cut.start() > 0) name = name.substring(0, cut.start());
        name = LEAD.matcher(name).replaceFirst("").trim();
        if (name.isEmpty()) name = c;
        return new String[]{titleCase(name), country};
    }

    /** Capitals for each word but the small ones, and for each part of a hyphenated word. */
    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        String[] words = s.split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) out.append(' ');
            if (i > 0 && SMALL.contains(w.toLowerCase(Locale.ROOT))) {
                out.append(w.toLowerCase(Locale.ROOT));
                continue;
            }
            String[] parts = w.split("-", -1);
            for (int j = 0; j < parts.length; j++) {
                if (j > 0) out.append('-');
                String p = parts[j];
                out.append(p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * How many faces can be seen in the picture, or -1 when it cannot be read. Android's own
     * detector, on a half-size copy: no download, no model, and run only when the week's pictures
     * arrive.
     */
    private static int faces(byte[] jpg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;   // what the detector reads
        o.inSampleSize = 2;
        Bitmap b = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
        if (b == null) return -1;
        try {
            if (b.getWidth() % 2 != 0) {   // the detector needs an even width
                Bitmap even = Bitmap.createBitmap(b, 0, 0, b.getWidth() - 1, b.getHeight());
                b.recycle();
                b = even;
            }
            FaceDetector.Face[] faces = new FaceDetector.Face[4];
            return new FaceDetector(b.getWidth(), b.getHeight(), faces.length).findFaces(b, faces);
        } finally {
            b.recycle();
        }
    }

    /**
     * The thing's name, its description and its country for a picture saved here, or null when
     * there is none: one line each in the matching .txt.
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        if (!name.endsWith(".jpg")) return null;
        File text = new File(picture.getParentFile(), name.substring(0, name.length() - 4) + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String overview = r.readLine(), country = r.readLine();
            return new String[]{title.trim(), overview == null ? "" : overview.trim(),
                    country == null ? "" : country.trim()};
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(File out, byte[] data) throws Exception {
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
    }

    private static void deleteAll(File d) {
        File[] files = d.listFiles();
        if (files != null) for (File f : files) f.delete();
        d.delete();
    }

    private static byte[] get(String url, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json, image/jpeg");
        // Asked as Android's own HTTP client, Bing answers with today's picture only; as a browser,
        // with the whole week.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > maxBytes) throw new IllegalStateException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("place_wallpapers", Context.MODE_PRIVATE);
    }
}
