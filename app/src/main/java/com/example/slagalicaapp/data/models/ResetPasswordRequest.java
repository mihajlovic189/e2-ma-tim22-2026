package com.example.slagalicaapp.data.models;

public class ResetPasswordRequest {
    private String oldPassword;
    private String newPassword;

    public ResetPasswordRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }
}
