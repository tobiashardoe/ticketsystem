package app.utils;

import app.exceptions.ApiException;

public class InputValidation {

    public static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, field + " must not be empty");
        }
    }

    public static void requireChoice(String value, String field, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return;
            }
        }
        throw new ApiException(400, field + " has an invalid value");
    }
}
