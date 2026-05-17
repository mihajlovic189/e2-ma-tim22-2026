package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.slagalicaapp.data.models.ProfileData;
import com.example.slagalicaapp.data.models.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<String> registerUser(User user) {
        MutableLiveData<String> result = new MutableLiveData<>();

        db.collection("users")
                .whereEqualTo("username", user.getUsername())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        result.setValue("Greška: Korisničko ime je već zauzeto.");
                    } else if (task.isSuccessful()) {
                        proceedWithAuthRegistration(user, result);
                    } else {
                        result.setValue("Greška pri proveri baze.");
                    }
                });

        return result;
    }

    public LiveData<String> registerGuestUser(User user) {
        MutableLiveData<String> result = new MutableLiveData<>();

        mAuth.signInAnonymously().addOnCompleteListener(authTask -> {
            if (authTask.isSuccessful() && mAuth.getCurrentUser() != null) {
                String uid = mAuth.getCurrentUser().getUid();

                db.collection("users").document(uid).get().addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                        result.setValue("GUEST_SUCCESS");
                    } else {
                        checkUsernameAndCreateGuest(user, uid, result);
                    }
                });
            } else {
                result.setValue("Greška pri anonimnoj prijavi.");
            }
        });

        return result;
    }

    private void checkUsernameAndCreateGuest(User user, String uid, MutableLiveData<String> result) {
        db.collection("users")
                .whereEqualTo("username", user.getUsername())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String newName = user.getUsername() + (int)(Math.random() * 100);
                        user.setUsername(newName);
                    }
                    saveUserToFirestore(user, uid, true, result);
                });
    }

    private void saveUserToFirestore(User user, String uid, boolean isGuest, MutableLiveData<String> result) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("username", user.getUsername());
        userData.put("region", user.getRegion());
        userData.put("isGuest", isGuest);
        String email = isGuest ? ("guest_" + uid + "@slagalica.app") : user.getEmail();
        userData.put("email", email);
        userData.put("avatarUri", "");
        userData.put("tokenCount", 0);
        userData.put("totalStars", 0);
        userData.put("totalGamesPlayed", 0);
        userData.put("wins", 0);
        userData.put("losses", 0);
        userData.put("qrPayload", buildQrPayload(uid, user.getUsername()));
        userData.put("averageScoreRanges", buildDefaultAverageScoreRanges());
        userData.put("detailedStats", buildDefaultDetailedStats());
        userData.put("leagueName", isGuest ? "Nema lige" : "Bronzana liga");

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    if (isGuest) {
                        result.setValue("GUEST_SUCCESS");
                    } else {
                        result.setValue("Registracija uspešna. Potvrdite mejl!");
                    }
                })
                .addOnFailureListener(e -> result.setValue("Greška pri upisu u bazu."));
    }

    private void proceedWithAuthRegistration(User user, MutableLiveData<String> result) {
        mAuth.createUserWithEmailAndPassword(user.getEmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser fUser = mAuth.getCurrentUser();
                        if (fUser != null) {
                            fUser.sendEmailVerification();
                            saveUserToFirestore(user, fUser.getUid(), false, result);
                        }
                    } else {
                        result.setValue("Greška: " + task.getException().getMessage());
                    }
                });
    }

    public LiveData<FirebaseUser> login(String identity, String password) {
        MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();

        if (identity.contains("@")) {
            performFirebaseLogin(identity, password, userLiveData);
        } else {
            db.collection("users")
                    .whereEqualTo("username", identity)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String email = task.getResult().getDocuments().get(0).getString("email");
                            performFirebaseLogin(email, password, userLiveData);
                        } else {
                            userLiveData.setValue(null);
                        }
                    });
        }
        return userLiveData;
    }

    public LiveData<String> loginGuest(String guestName, String region) {
        User guestUser = new User();
        guestUser.setUsername(guestName);
        guestUser.setRegion(region);
        guestUser.setGuest(true);

        return registerGuestUser(guestUser);
    }

    public LiveData<ProfileData> loadCurrentUserProfile() {
        MutableLiveData<ProfileData> result = new MutableLiveData<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            result.setValue(null);
            return result;
        }

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> result.setValue(mapProfile(currentUser, documentSnapshot)))
                .addOnFailureListener(e -> result.setValue(null));

        return result;
    }

    public LiveData<String> updateAvatarUri(String avatarUri) {
        MutableLiveData<String> result = new MutableLiveData<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            result.setValue("Niste prijavljeni.");
            return result;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("avatarUri", avatarUri);

        db.collection("users").document(currentUser.getUid())
                .update(update)
                .addOnSuccessListener(unused -> result.setValue("Avatar je uspešno ažuriran."))
                .addOnFailureListener(e -> result.setValue("Neuspešno ažuriranje avatara."));

        return result;
    }

    public void logout() {
        mAuth.signOut();
    }

    private void performFirebaseLogin(String email, String password, MutableLiveData<FirebaseUser> liveData) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            liveData.setValue(user);
                        } else {
                            mAuth.signOut();
                            liveData.setValue(null);
                        }
                    } else {
                        liveData.setValue(null);
                    }
                });
    }

    public LiveData<String> changePassword(String oldPass, String newPass) {
        MutableLiveData<String> result = new MutableLiveData<>();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null && user.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPass);
            user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
                if (reAuthTask.isSuccessful()) {
                    user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            result.setValue("Lozinka uspešno promenjena.");
                        } else {
                            result.setValue("Greška pri ažuriranju.");
                        }
                    });
                } else {
                    result.setValue("Stara lozinka nije ispravna.");
                }
            });
        }
        return result;
    }

    private ProfileData mapProfile(FirebaseUser currentUser, DocumentSnapshot documentSnapshot) {
        ProfileData profileData = new ProfileData();
        profileData.setUid(currentUser.getUid());
        profileData.setUsername(stringValue(documentSnapshot.getString("username"), "Igrač"));
        profileData.setEmail(stringValue(documentSnapshot.getString("email"), currentUser.getEmail()));
        profileData.setRegion(stringValue(documentSnapshot.getString("region"), "Nepoznat region"));
        profileData.setAvatarUri(stringValue(documentSnapshot.getString("avatarUri"), ""));
        profileData.setLeagueName(stringValue(documentSnapshot.getString("leagueName"), "Bronzana liga"));
        profileData.setQrPayload(stringValue(documentSnapshot.getString("qrPayload"), buildQrPayload(currentUser.getUid(), profileData.getUsername())));
        profileData.setTokenCount(readInt(documentSnapshot.get("tokenCount"), 0));
        profileData.setTotalStars(readInt(documentSnapshot.get("totalStars"), 0));
        profileData.setTotalGamesPlayed(readInt(documentSnapshot.get("totalGamesPlayed"), 0));
        profileData.setWins(readInt(documentSnapshot.get("wins"), 0));
        profileData.setLosses(readInt(documentSnapshot.get("losses"), 0));
        profileData.setAverageScoreRanges(readMap(documentSnapshot.get("averageScoreRanges"), buildDefaultAverageScoreRanges()));
        profileData.setDetailedStats(readMap(documentSnapshot.get("detailedStats"), buildDefaultDetailedStats()));
        return profileData;
    }

    private String stringValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }

    private Map<String, String> readMap(Object value, Map<String, String> fallback) {
        if (value instanceof Map<?, ?>) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result.isEmpty() ? new LinkedHashMap<>(fallback) : result;
        }

        return new LinkedHashMap<>(fallback);
    }

    private Map<String, String> buildDefaultAverageScoreRanges() {
        Map<String, String> ranges = new LinkedHashMap<>();
        ranges.put("Ko zna zna", "20–50 poena");
        ranges.put("Moj broj", "10–30 poena");
        ranges.put("Korak po korak", "15–40 poena");
        ranges.put("Asocijacije", "25–60 poena");
        ranges.put("Skočko", "10–25 poena");
        ranges.put("Spojnice", "15–35 poena");
        return ranges;
    }

    private Map<String, String> buildDefaultDetailedStats() {
        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Ko zna zna", "Pogođeno 68% / promašeno 32%");
        stats.put("Moj broj", "Tačan broj pronađen u 54% partija");
        stats.put("Korak po korak", "Korak 1: 81%, Korak 2: 74%, Korak 3: 69%");
        stats.put("Asocijacije", "Rešene 46% / nerešene 54%");
        stats.put("Skočko", "Kombinacija pogođena u 38% pokušaja");
        stats.put("Spojnice", "Povezano 72% pojmova");
        stats.put("Ukupno odigranih partija", "128");
        stats.put("Pobeđene / izgubljene partije", "61% / 39%");
        return stats;
    }

    private String buildQrPayload(String uid, String username) {
        return "slagalica://invite?uid=" + uid + "&username=" + username;
    }
}