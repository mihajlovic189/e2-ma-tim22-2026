package com.example.slagalicaapp.repositories;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.slagalicaapp.data.models.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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

    private void proceedWithAuthRegistration(User user, MutableLiveData<String> result) {
        mAuth.createUserWithEmailAndPassword(user.getEmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser fUser = mAuth.getCurrentUser();
                        if (fUser != null) {
                            fUser.sendEmailVerification();

                            Map<String, Object> userData = new HashMap<>();
                            userData.put("username", user.getUsername());
                            userData.put("region", user.getRegion());
                            userData.put("email", user.getEmail());
                            userData.put("uid", fUser.getUid());

                            db.collection("users").document(fUser.getUid())
                                    .set(userData);

                            result.setValue("Registracija uspešna. Potvrdite mejl!");
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
}