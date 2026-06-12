package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationRepository {

    public static class NotifItem {
        public String id;
        public String naslov;
        public String opis;
        public String tip;
        public long timestamp;
        public boolean procitana;

        public NotifItem() {}
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void sacuvaj(String naslov, String opis, String tip) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("naslov", naslov);
        data.put("opis", opis);
        data.put("tip", tip);
        data.put("timestamp", System.currentTimeMillis());
        data.put("procitana", false);

        db.collection("users").document(uid)
                .collection("notifications")
                .add(data);
    }

    public LiveData<List<NotifItem>> getNotifikacije() {
        MutableLiveData<List<NotifItem>> liveData = new MutableLiveData<>();
        String uid = getUid();
        if (uid == null) {
            liveData.setValue(new ArrayList<>());
            return liveData;
        }

        db.collection("users").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        liveData.setValue(new ArrayList<>());
                        return;
                    }
                    List<NotifItem> list = new ArrayList<>();
                    for (var doc : snapshots.getDocuments()) {
                        NotifItem item = new NotifItem();
                        item.id = doc.getId();
                        item.naslov = doc.getString("naslov");
                        item.opis = doc.getString("opis");
                        item.tip = doc.getString("tip");
                        Long ts = doc.getLong("timestamp");
                        item.timestamp = ts != null ? ts : 0L;
                        Boolean pr = doc.getBoolean("procitana");
                        item.procitana = pr != null && pr;
                        list.add(item);
                    }
                    liveData.setValue(list);
                });

        return liveData;
    }

    public void oznacKaoProcitanu(String docId) {
        String uid = getUid();
        if (uid == null || docId == null) return;

        db.collection("users").document(uid)
                .collection("notifications")
                .document(docId)
                .update("procitana", true);
    }
}
