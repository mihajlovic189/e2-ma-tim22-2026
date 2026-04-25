package com.example.slagalicaapp.repositories;

import androidx.lifecycle.MutableLiveData;

import com.example.slagalicaapp.data.models.LoginRequest;
import com.example.slagalicaapp.data.models.ResetPasswordRequest;
import com.example.slagalicaapp.data.models.User;
import com.example.slagalicaapp.data.remote.ApiService;
import com.example.slagalicaapp.data.remote.RetrofitClient;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private ApiService apiService;

    public AuthRepository() {
        apiService = RetrofitClient.getInstance().getApi();
    }

    public MutableLiveData<String> registerUser(User user) {
        MutableLiveData<String> registerResponse = new MutableLiveData<>();
        apiService.register(user).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    registerResponse.setValue("Uspešno! Proverite mejl za potvrdu.");
                } else {
                    registerResponse.setValue("Greška pri registraciji.");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                registerResponse.setValue("Serverska greška: " + t.getMessage());
            }
        });
        return registerResponse;
    }

    public MutableLiveData<User> login(String identity, String password) {
        MutableLiveData<User> data = new MutableLiveData<>();

        LoginRequest request = new LoginRequest(identity, password);

        apiService.login(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful()) {
                    data.setValue(response.body());
                } else {
                    data.setValue(null);
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                data.setValue(null);
            }
        });

        return data;
    }

    public MutableLiveData<String> resetPassword(String token, String oldPass, String newPass) {
        MutableLiveData<String> data = new MutableLiveData<>();

        ResetPasswordRequest req = new ResetPasswordRequest(oldPass, newPass);

        apiService.resetPassword("Bearer " + token, req).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    data.setValue("Lozinka promijenjena");
                } else {
                    data.setValue("Greška: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                data.setValue("Server error: " + t.getMessage());
            }
        });

        return data;
    }
}
