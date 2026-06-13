package com.example.slagalicaapp.repositories;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalicaapp.model.NotificationModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NotificationStore {

    private static final String PREFS = "SlagalicaPrefs";
    private static final String KEY   = "local_notifications_v2";

    public static void save(Context ctx, String title, String body, String type) {
        JSONArray arr = load(ctx);
        try {
            JSONObject n = new JSONObject();
            n.put("id",        UUID.randomUUID().toString());
            n.put("title",     title);
            n.put("body",      body);
            n.put("type",      type != null ? type : "other");
            n.put("timestamp", System.currentTimeMillis());
            n.put("isRead",    false);
            arr.put(n);
            persist(ctx, arr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void markAsRead(Context ctx, String id) {
        JSONArray arr = load(ctx);
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject n = arr.getJSONObject(i);
                if (id.equals(n.optString("id"))) {
                    n.put("isRead", true);
                    break;
                }
            }
            persist(ctx, arr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static List<NotificationModel> getAll(Context ctx) {
        JSONArray arr = load(ctx);
        List<NotificationModel> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject n = arr.getJSONObject(i);
                list.add(new NotificationModel(
                        n.optString("id"),
                        n.optString("title"),
                        n.optString("body"),
                        n.optLong("timestamp"),
                        n.optBoolean("isRead", false),
                        n.optString("type", "other")
                ));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    private static JSONArray load(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void persist(Context ctx, JSONArray arr) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
