package com.example.slagalicaapp.data.remote;

import com.example.slagalicaapp.data.models.LoginRequest;
import com.example.slagalicaapp.data.models.ResetPasswordRequest;
import com.example.slagalicaapp.data.models.User;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("auth/register")
    Call<ResponseBody> register(@Body User user);

    @POST("auth/login")
    Call<User> login(@Body LoginRequest loginRequest);

    @POST("auth/reset-password")
    Call<ResponseBody> resetPassword(@Body ResetPasswordRequest request);
}
