package com.example.slagalicaapp.repositories;

import androidx.lifecycle.MutableLiveData;

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
}
