package com.quan.miniodemo.domain.pojo;

import lombok.Data;

@Data
public class R<T> {
    private Integer code;
    private String message;
    private T data;
    
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }
    
    public static <T> R<T> ok() {
        return ok(null);
    }
    
    public static <T> R<T> error(String message) {
        R<T> r = new R<>();
        r.setCode(500);
        r.setMessage(message);
        return r;
    }
}