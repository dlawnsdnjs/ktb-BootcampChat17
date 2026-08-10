package com.ktb.chatapp.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern REQUIRED_CHARACTER_CLASSES =
            Pattern.compile("^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[\\W_]).+$");

    private int minLength;
    private int maxLength;

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        this.minLength = constraintAnnotation.min();
        this.maxLength = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        if (password.length() < minLength || password.length() > maxLength) {
            return false;
        }
        return REQUIRED_CHARACTER_CLASSES.matcher(password).matches();
    }
}
