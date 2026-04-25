package com.example.slagalicaapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalicaapp.data.models.User;
import com.example.slagalicaapp.repositories.AuthRepository;

public class AuthViewModel extends ViewModel {
    private AuthRepository authRepository;

    public AuthViewModel() {
        authRepository = new AuthRepository();
    }

    public LiveData<String> register(String email, String user, String reg, String pass) {
        User newUser = new User(email, user, reg, pass);
        return authRepository.registerUser(newUser);
    }
}
