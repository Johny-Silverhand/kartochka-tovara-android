package labs.victimok.kartochka;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/** Local SQLite for product cards + photo files under app filesDir/photos. */
public class CardDb extends SQLiteOpenHelper {
    public static final int MAX_PHOTOS = 4;
    private static final String DB = "kartochka.db";
    private static final int VER = 1;
    private final File photoDir;

    public CardDb(Context ctx) {
        super(ctx, DB, null, VER);
        photoDir = new File(ctx.getFilesDir(), "photos");
        if (!photoDir.exists()) photoDir.mkdirs();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE cards (" +
            "id TEXT PRIMARY KEY," +
            "name TEXT NOT NULL," +
            "description TEXT," +
            "photos TEXT NOT NULL DEFAULT '[]'," +
            "created_at INTEGER NOT NULL," +
            "updated_at INTEGER NOT NULL)"
        );
        db.execSQL("CREATE INDEX idx_cards_updated ON cards(updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}

    public JSONArray listCards() throws Exception {
        JSONArray out = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT id,name,description,photos,created_at,updated_at FROM cards ORDER BY updated_at DESC",
            null)) {
            while (c.moveToNext()) {
                out.put(rowToJson(c));
            }
        }
        return out;
    }

    public JSONObject getCard(String id) throws Exception {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
            "SELECT id,name,description,photos,created_at,updated_at FROM cards WHERE id=?",
            new String[]{id})) {
            if (!c.moveToFirst()) return null;
            return rowToJson(c);
        }
    }

    public JSONObject createCard(String name, String description) throws Exception {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString().replace("-", "");
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("name", name == null || name.isEmpty() ? "Новая карточка" : name);
        v.put("description", description == null ? "" : description);
        v.put("photos", "[]");
        v.put("created_at", now);
        v.put("updated_at", now);
        getWritableDatabase().insertOrThrow("cards", null, v);
        return getCard(id);
    }

    public JSONObject updateCard(String id, String name, String description) throws Exception {
        ContentValues v = new ContentValues();
        v.put("name", name == null ? "" : name);
        v.put("description", description == null ? "" : description);
        v.put("updated_at", System.currentTimeMillis());
        int n = getWritableDatabase().update("cards", v, "id=?", new String[]{id});
        if (n == 0) return null;
        return getCard(id);
    }

    public boolean deleteCard(String id) throws Exception {
        JSONObject card = getCard(id);
        if (card == null) return false;
        JSONArray photos = card.getJSONArray("photos");
        for (int i = 0; i < photos.length(); i++) {
            File f = new File(photoDir, photos.getString(i));
            if (f.exists()) f.delete();
        }
        return getWritableDatabase().delete("cards", "id=?", new String[]{id}) > 0;
    }

    public JSONObject addPhoto(String id, String mime, String base64) throws Exception {
        JSONObject card = getCard(id);
        if (card == null) return null;
        JSONArray photos = card.getJSONArray("photos");
        if (photos.length() >= MAX_PHOTOS) {
            throw new IllegalStateException("max " + MAX_PHOTOS + " photos");
        }
        String ext = ".jpg";
        if (mime != null) {
            if (mime.contains("png")) ext = ".png";
            else if (mime.contains("webp")) ext = ".webp";
            else if (mime.contains("gif")) ext = ".gif";
        }
        byte[] data = Base64.decode(base64, Base64.DEFAULT);
        String name = id + "_" + (photos.length() + 1) + "_" + System.currentTimeMillis() + ext;
        File out = new File(photoDir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(data);
        }
        photos.put(name);
        ContentValues v = new ContentValues();
        v.put("photos", photos.toString());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("cards", v, "id=?", new String[]{id});
        return getCard(id);
    }

    public JSONObject removePhoto(String id, int index) throws Exception {
        JSONObject card = getCard(id);
        if (card == null) return null;
        JSONArray photos = card.getJSONArray("photos");
        if (index < 0 || index >= photos.length()) {
            throw new IllegalArgumentException("bad index");
        }
        String file = photos.getString(index);
        File f = new File(photoDir, file);
        if (f.exists()) f.delete();
        JSONArray next = new JSONArray();
        for (int i = 0; i < photos.length(); i++) {
            if (i != index) next.put(photos.getString(i));
        }
        ContentValues v = new ContentValues();
        v.put("photos", next.toString());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("cards", v, "id=?", new String[]{id});
        return getCard(id);
    }

    public File photoFile(String name) {
        if (name == null || name.contains("..") || name.contains("/")) return null;
        return new File(photoDir, name);
    }

    private JSONObject rowToJson(Cursor c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", c.getString(0));
        o.put("name", c.getString(1));
        o.put("description", c.getString(2));
        String photos = c.getString(3);
        o.put("photos", new JSONArray(photos == null || photos.isEmpty() ? "[]" : photos));
        o.put("createdAt", c.getLong(4));
        o.put("updatedAt", c.getLong(5));
        return o;
    }
}
