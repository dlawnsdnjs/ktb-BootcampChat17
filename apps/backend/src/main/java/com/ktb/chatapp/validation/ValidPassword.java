package com.ktb.chatapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "비밀번호는 8~16자, 영문 대문자·소문자, 숫자, 특수문자 포함 조건을 모두 만족해야 합니다.";
    int min() default 8;
    int max() default 16;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
