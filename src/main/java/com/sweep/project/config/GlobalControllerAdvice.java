package com.sweep.project.config;

import com.sweep.project.util.ApiResponseUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> notFoundException(NoSuchElementException e) {
        return new ResponseEntity<>(ApiResponseUtil.FailApiResponse(e.getMessage()),
                null, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> globalException(Exception e){
        return new ResponseEntity<>(ApiResponseUtil.FailApiResponse(e.getMessage())
                ,null, HttpStatus.BAD_REQUEST);
    }
}
