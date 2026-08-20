package com.cloud.weball.web.photo.repository;

import org.springframework.stereotype.Repository;
import web.photo.config.PhotoProperties;
import web.photo.model.PhotoRecord;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PhotoRepository {
    private final PhotoProperties properties;
    private String jdbc;
    public PhotoRepository(PhotoProperties properties){this.properties=properties;}

    @PostConstruct public void init() throws Exception {
        Path root=Paths.get(properties.getDataDir()).toAbsolutePath().normalize(); Files.createDirectories(root);
        jdbc="jdbc:sqlite:"+root.resolve("photos.sqlite");
        try(Connection c=open(); Statement s=c.createStatement()){
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE IF NOT EXISTS photo (id INTEGER PRIMARY KEY AUTOINCREMENT, display_name TEXT NOT NULL, original_name TEXT NOT NULL, owner_user_id INTEGER NOT NULL, owner_username TEXT NOT NULL, captured_at INTEGER NOT NULL, captured_at_source TEXT NOT NULL, captured_at_raw TEXT, uploaded_at INTEGER NOT NULL, media_type TEXT NOT NULL, extension TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, original_size INTEGER NOT NULL, checksum TEXT NOT NULL, archive_path TEXT NOT NULL, archive_entry TEXT NOT NULL, thumbnail_path TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE', deleted_at INTEGER)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_photo_timeline ON photo(status,captured_at DESC,id DESC)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_photo_owner ON photo(owner_user_id,status,captured_at DESC)");
            s.execute("CREATE TABLE IF NOT EXISTS photo_setting (setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL, updated_at INTEGER NOT NULL, updated_by TEXT NOT NULL)");
            s.execute("INSERT OR IGNORE INTO photo_setting(setting_key,setting_value,updated_at,updated_by) VALUES('visibility_mode','ALL',strftime('%s','now')*1000,'system')");
        }
    }
    Connection open() throws SQLException {Connection c=DriverManager.getConnection(jdbc); try(Statement s=c.createStatement()){s.execute("PRAGMA busy_timeout=5000");} return c;}
    public long insert(PhotoRecord p) throws SQLException {
        String q="INSERT INTO photo(display_name,original_name,owner_user_id,owner_username,captured_at,captured_at_source,captured_at_raw,uploaded_at,media_type,extension,width,height,original_size,checksum,archive_path,archive_entry,thumbnail_path,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')";
        try(Connection c=open(); PreparedStatement s=c.prepareStatement(q,Statement.RETURN_GENERATED_KEYS)){bind(s,p);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){if(r.next())return r.getLong(1);}} throw new SQLException("无法生成图片 ID");
    }
    private void bind(PreparedStatement s,PhotoRecord p)throws SQLException{int i=1;s.setString(i++,p.displayName);s.setString(i++,p.originalName);s.setLong(i++,p.ownerUserId);s.setString(i++,p.ownerUsername);s.setLong(i++,p.capturedAt);s.setString(i++,p.capturedAtSource);s.setString(i++,p.capturedAtRaw);s.setLong(i++,p.uploadedAt);s.setString(i++,p.mediaType);s.setString(i++,p.extension);s.setInt(i++,p.width);s.setInt(i++,p.height);s.setLong(i++,p.originalSize);s.setString(i++,p.checksum);s.setString(i++,p.archivePath);s.setString(i++,p.archiveEntry);s.setString(i,p.thumbnailPath);}
    public PhotoRecord find(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM photo WHERE id=? AND status='ACTIVE'")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?map(r):null;}}}
    public List<PhotoRecord> list(Long owner,int page,int size,String username)throws SQLException{
        String where="status='ACTIVE'"+(owner!=null?" AND owner_user_id=?":"")+(username!=null&&!username.isEmpty()?" AND owner_username LIKE ?":"");
        String q="SELECT * FROM photo WHERE "+where+" ORDER BY captured_at DESC,id DESC LIMIT ? OFFSET ?";
        try(Connection c=open();PreparedStatement s=c.prepareStatement(q)){int i=1;if(owner!=null)s.setLong(i++,owner);if(username!=null&&!username.isEmpty())s.setString(i++,"%"+username+"%");s.setInt(i++,size);s.setInt(i,(page-1)*size);try(ResultSet r=s.executeQuery()){List<PhotoRecord> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}}
    }
    public long count(Long owner,String username)throws SQLException{String q="SELECT count(*) FROM photo WHERE status='ACTIVE'"+(owner!=null?" AND owner_user_id=?":"")+(username!=null&&!username.isEmpty()?" AND owner_username LIKE ?":"");try(Connection c=open();PreparedStatement s=c.prepareStatement(q)){int i=1;if(owner!=null)s.setLong(i++,owner);if(username!=null&&!username.isEmpty())s.setString(i,"%"+username+"%");try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):0;}}}
    public void rename(long id,String name)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE photo SET display_name=? WHERE id=? AND status='ACTIVE'")){s.setString(1,name);s.setLong(2,id);s.executeUpdate();}}
    public void delete(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE photo SET status='DELETED',deleted_at=? WHERE id=? AND status='ACTIVE'")){s.setLong(1,System.currentTimeMillis());s.setLong(2,id);s.executeUpdate();}}
    public String visibility()throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT setting_value FROM photo_setting WHERE setting_key='visibility_mode'");ResultSet r=s.executeQuery()){return r.next()&&("ALL".equals(r.getString(1))||"OWN".equals(r.getString(1)))?r.getString(1):"ALL";}}
    public void visibility(String mode,String by)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO photo_setting(setting_key,setting_value,updated_at,updated_by) VALUES('visibility_mode',?,?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value,updated_at=excluded.updated_at,updated_by=excluded.updated_by")){s.setString(1,mode);s.setLong(2,System.currentTimeMillis());s.setString(3,by);s.executeUpdate();}}
    private PhotoRecord map(ResultSet r)throws SQLException{PhotoRecord p=new PhotoRecord();p.id=r.getLong("id");p.displayName=r.getString("display_name");p.originalName=r.getString("original_name");p.ownerUserId=r.getLong("owner_user_id");p.ownerUsername=r.getString("owner_username");p.capturedAt=r.getLong("captured_at");p.capturedAtSource=r.getString("captured_at_source");p.capturedAtRaw=r.getString("captured_at_raw");p.uploadedAt=r.getLong("uploaded_at");p.mediaType=r.getString("media_type");p.extension=r.getString("extension");p.width=r.getInt("width");p.height=r.getInt("height");p.originalSize=r.getLong("original_size");p.checksum=r.getString("checksum");p.archivePath=r.getString("archive_path");p.archiveEntry=r.getString("archive_entry");p.thumbnailPath=r.getString("thumbnail_path");p.status=r.getString("status");return p;}
}
