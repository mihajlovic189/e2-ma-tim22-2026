package com.example.slagalicaapp.model;

import java.util.List;

public class Question {
    private String questionText;
    private List<String> options;
    private int correctAnswerIndex;

    public Question() {
        // Default constructor required for calls to DataSnapshot.getValue(Question.class)
    }

    public Question(String questionText, List<String> options, int correctAnswerIndex) {
        this.questionText = questionText;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }
}
