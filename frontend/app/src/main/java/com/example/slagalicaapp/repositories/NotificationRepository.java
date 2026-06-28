package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.model.NotificationModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration listenerRegistration;

    private String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /** Save a notification for the currently logged-in user. */
    public void save(String title, String body, String type) {
        String uid = getUid();
        if (uid == null) return;
        saveForUser(uid, title, body, type);
    }

    /** Save a notification for any user by UID (e.g. friend events affecting another user). */
    public static void saveForUser(String targetUid, String title, String body, String type) {
        if (targetUid == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("body", body);
        data.put("type", type != null ? type : "other");
        data.put("timestamp", System.currentTimeMillis());
        data.put("read", false);
        FirebaseFirestore.getInstance()
                .collection("users").document(targetUid)
                .collection("notifications")
                .add(data);
    }

    /**
     * Real-time listener on the current user's notification history.
     * Call {@link #stopListening()} in onDestroyView to clean up.
     */
    public LiveData<List<NotificationModel>> getNotifikacije() {
        MutableLiveData<List<NotificationModel>> liveData = new MutableLiveData<>();
        String uid = getUid();
        if (uid == null) {
            liveData.setValue(new ArrayList<>());
            return liveData;
        }

        listenerRegistration = db.collection("users").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        liveData.setValue(new ArrayList<>());
                        return;
                    }
                    List<NotificationModel> list = new ArrayList<>();
                    for (var doc : snapshots.getDocuments()) {
                        String title = doc.getString("title");
                        String body  = doc.getString("body");
                        String type  = doc.getString("type");
                        Long ts      = doc.getLong("timestamp");
                        Boolean read = doc.getBoolean("read");
                        list.add(new NotificationModel(
                                doc.getId(),
                                title != null ? title : "",
                                body  != null ? body  : "",
                                ts    != null ? ts    : 0L,
                                read  != null && read,
                                type  != null ? type  : "other"
                        ));
                    }
                    liveData.setValue(list);
                });

        return liveData;
    }

    /** Stop the Firestore snapshot listener — call this from onDestroyView. */
    public void stopListening() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    public void oznacKaoProcitanu(String docId) {
        String uid = getUid();
        if (uid == null || docId == null) return;
        db.collection("users").document(uid)
                .collection("notifications")
                .document(docId)
                .update("read", true);
    }

    public void oznacSveKaoProcitane() {
        String uid = getUid();
        if (uid == null) return;
        db.collection("users").document(uid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (var doc : snapshots.getDocuments()) {
                        doc.getReference().update("read", true);
                    }
                });
    }
}
