package com.example.slagalicaapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalicaapp.data.models.User;
import com.example.slagalicaapp.repositories.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends ViewModel {
    private AuthRepository authRepository;

    public AuthViewModel() {
        authRepository = new AuthRepository();
    }

    public LiveData<String> register(User user) {
        return authRepository.registerUser(user);
    }

    public LiveData<FirebaseUser> login(String email, String password) {
        return authRepository.login(email, password);
    }

    public LiveData<String> resetPassword(String oldPass, String newPass) {
        return authRepository.changePassword(oldPass, newPass);
    }
}
