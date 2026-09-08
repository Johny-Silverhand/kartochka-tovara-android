package labs.victimok.kartochka;

import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/** JS bridge: local SQLite CRUD for the embedded Web UI. */
public class CardBridge {
    private final CardDb db;

    public CardBridge(CardDb db) {
        this.db = db;
    }

    @JavascriptInterface
    public String listCards() {
        try {
            return db.listCards().toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String getCard(String id) {
        try {
            JSONObject c = db.getCard(id);
            return c == null ? "{\"error\":\"not found\"}" : c.toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String createCard(String name, String description) {
        try {
            return db.createCard(name, description).toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String updateCard(String id, String name, String description) {
        try {
            JSONObject c = db.updateCard(id, name, description);
            return c == null ? "{\"error\":\"not found\"}" : c.toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String deleteCard(String id) {
        try {
            boolean ok = db.deleteCard(id);
            return ok ? "{\"ok\":true}" : "{\"error\":\"not found\"}";
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String addPhoto(String id, String mime, String base64) {
        try {
            return db.addPhoto(id, mime, base64).toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public String removePhoto(String id, int index) {
        try {
            return db.removePhoto(id, index).toString();
        } catch (Exception e) {
            return err(e);
        }
    }

    @JavascriptInterface
    public int maxPhotos() {
        return CardDb.MAX_PHOTOS;
    }

    private String err(Exception e) {
        try {
            return new JSONObject().put("error", e.getMessage() == null ? "error" : e.getMessage()).toString();
        } catch (Exception ignored) {
            return "{\"error\":\"error\"}";
        }
    }
}
