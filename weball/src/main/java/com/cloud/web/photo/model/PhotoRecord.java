package com.cloud.web.photo.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class PhotoRecord {
    public long id, ownerUserId, capturedAt, uploadedAt, originalSize;
    public int width, height;
    public String displayName, originalName, ownerUsername, capturedAtSource, capturedAtRaw;
    public String mediaType, extension, checksum, archivePath, archiveEntry, thumbnailPath, status;
    public Map<String,Object> publicView() {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",id); m.put("displayName",displayName); m.put("originalName",originalName);
        m.put("ownerUsername",ownerUsername); m.put("capturedAt",capturedAt);
        m.put("capturedAtSource",capturedAtSource); m.put("uploadedAt",uploadedAt);
        m.put("mediaType",mediaType); m.put("width",width); m.put("height",height);
        m.put("originalSize",originalSize); m.put("thumbnailUrl","/api/photos/"+id+"/thumbnail");
        m.put("originalUrl","/api/photos/"+id+"/original"); return m;
    }
}
