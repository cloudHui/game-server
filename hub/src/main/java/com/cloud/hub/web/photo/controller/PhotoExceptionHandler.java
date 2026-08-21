package com.cloud.hub.web.photo.controller;

import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.http.*;import org.springframework.web.multipart.MaxUploadSizeExceededException;import org.springframework.web.bind.annotation.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.cloud.hub.web.photo.model.PhotoException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages="com.cloud.hub.web.photo")
public class PhotoExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(PhotoExceptionHandler.class);
    @ExceptionHandler(PhotoException.class) ResponseEntity<Map<String,Object>> photo(PhotoException e){return response(e.getStatus(),e.getMessage());}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<Map<String,Object>> large(){return response(413,"上传请求超过大小限制");}
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> other(Exception e){log.error("图片库请求失败",e);return response(500,"图片库处理失败");}
    private ResponseEntity<Map<String,Object>> response(int status,String msg){Map<String,Object>m=new LinkedHashMap<>();m.put("code",status);m.put("msg",msg);return ResponseEntity.status(status).body(m);}
}
