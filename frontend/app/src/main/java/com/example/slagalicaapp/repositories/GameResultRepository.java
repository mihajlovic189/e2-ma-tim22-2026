package com.example.slagalicaapp.repositories;

import com.example.slagalicaapp.model.GameResult;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Task;

public class GameResultRepository {
    private static final String COLLECTION_NAME = "ko_zna_zna_results";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<DocumentReference> saveGameResult(GameResult result) {
        return db.collection(COLLECTION_NAME).add(result);
    }

    public Task<DocumentReference> saveGameResult(GameResult result, String collectionName) {
        String targetCollection = (collectionName == null || collectionName.trim().isEmpty())
                ? COLLECTION_NAME
                : collectionName;
        return db.collection(targetCollection).add(result);
    }
}


