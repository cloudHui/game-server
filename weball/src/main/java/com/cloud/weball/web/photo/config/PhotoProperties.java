package com.cloud.weball.web.photo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "photo-library")
public class PhotoProperties {
    private String dataDir = "data/photos", archiveDir = "data/photos/archives";
    private String thumbnailDir = "data/photos/thumbnails", cacheDir = "data/photos/cache";
    private String stagingDir = "data/photos/staging", defaultZone = "Asia/Shanghai";
    private int cacheMaxFiles = 20, thumbnailMaxWidth = 320, thumbnailMaxHeight = 320;
    private int maxFilesPerRequest = 20;
    private long archiveMaxBytes = 4294967296L, maxFileBytes = 52428800L, maxPixels = 100000000L;
    public String getDataDir(){return dataDir;} public void setDataDir(String v){dataDir=v;}
    public String getArchiveDir(){return archiveDir;} public void setArchiveDir(String v){archiveDir=v;}
    public String getThumbnailDir(){return thumbnailDir;} public void setThumbnailDir(String v){thumbnailDir=v;}
    public String getCacheDir(){return cacheDir;} public void setCacheDir(String v){cacheDir=v;}
    public String getStagingDir(){return stagingDir;} public void setStagingDir(String v){stagingDir=v;}
    public String getDefaultZone(){return defaultZone;} public void setDefaultZone(String v){defaultZone=v;}
    public int getCacheMaxFiles(){return cacheMaxFiles;} public void setCacheMaxFiles(int v){cacheMaxFiles=v;}
    public int getThumbnailMaxWidth(){return thumbnailMaxWidth;} public void setThumbnailMaxWidth(int v){thumbnailMaxWidth=v;}
    public int getThumbnailMaxHeight(){return thumbnailMaxHeight;} public void setThumbnailMaxHeight(int v){thumbnailMaxHeight=v;}
    public int getMaxFilesPerRequest(){return maxFilesPerRequest;} public void setMaxFilesPerRequest(int v){maxFilesPerRequest=v;}
    public long getArchiveMaxBytes(){return archiveMaxBytes;} public void setArchiveMaxBytes(long v){archiveMaxBytes=v;}
    public long getMaxFileBytes(){return maxFileBytes;} public void setMaxFileBytes(long v){maxFileBytes=v;}
    public long getMaxPixels(){return maxPixels;} public void setMaxPixels(long v){maxPixels=v;}
}
