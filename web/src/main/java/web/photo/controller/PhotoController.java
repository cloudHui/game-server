package web.photo.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import web.identity.SessionResolver;
import web.service.UserService;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import web.photo.service.PhotoService;
import web.photo.service.PhotoUploadTaskService;
import web.photo.config.PhotoProperties;
import web.photo.model.PhotoException;
import web.photo.storage.PhotoCache;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    private final PhotoService photos; private final PhotoUploadTaskService uploadTasks; private final UserService users; private final SessionResolver sessions; private final PhotoProperties properties;
    public PhotoController(PhotoService photos,PhotoUploadTaskService uploadTasks,UserService users,SessionResolver sessions,PhotoProperties properties){this.photos=photos;this.uploadTasks=uploadTasks;this.users=users;this.sessions=sessions;this.properties=properties;}
    private UserService.UserInfo user(HttpServletRequest req){UserService.UserInfo u=users.getSession(sessions.resolve(req));if(u==null)throw new PhotoException(401,"请先登录");return u;}
    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> upload(@RequestPart("files") MultipartFile[] files,@RequestHeader(value="X-Upload-Task-ID",required=false)String taskId,HttpServletRequest req){UserService.UserInfo u=user(req);if(files==null||files.length==0)throw new PhotoException(400,"请选择图片");if(files.length>properties.getMaxFilesPerRequest())throw new PhotoException(400,"单次最多上传 "+properties.getMaxFilesPerRequest()+" 张");List<Map<String,Object>> results=new ArrayList<>();for(MultipartFile f:files){Map<String,Object> item=new LinkedHashMap<>();item.put("filename",f.getOriginalFilename());try{item.put("success",true);item.put("photo",uploadTasks.execute(taskId,f.getOriginalFilename(),u,()->photos.upload(f,u)));}catch(PhotoException e){item.put("success",false);item.put("error",e.getMessage());}results.add(item);}Map<String,Object> out=new LinkedHashMap<>();out.put("results",results);out.put("successCount",results.stream().filter(x->Boolean.TRUE.equals(x.get("success"))).count());return out;}
    @GetMapping("/upload/tasks") public List<Map<String,Object>> uploadTasks(HttpServletRequest req){return uploadTasks.list(user(req));}
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="24")int pageSize,@RequestParam(required=false)String owner,HttpServletRequest req)throws Exception{return photos.list(user(req),page,pageSize,owner);}
    @GetMapping("/{id}") public Map<String,Object> one(@PathVariable long id,HttpServletRequest req)throws Exception{return photos.requireVisible(id,user(req)).publicView();}
    @GetMapping("/{id}/thumbnail") public ResponseEntity<byte[]> thumbnail(@PathVariable long id,HttpServletRequest req)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.IMAGE_JPEG).body(photos.thumbnail(id,user(req)));}
    @GetMapping("/{id}/original") public ResponseEntity<StreamingResponseBody> original(@PathVariable long id,HttpServletRequest req)throws Exception{PhotoService.Original o=photos.original(id,user(req));ContentDisposition cd=ContentDisposition.inline().filename(o.name,StandardCharsets.UTF_8).build();StreamingResponseBody body=output->{try(PhotoCache.Lease lease=o.lease){Files.copy(lease.getPath(),output);}};return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType(o.mediaType)).header(HttpHeaders.CONTENT_DISPOSITION,cd.toString()).contentLength(o.lease.getSize()).body(body);}
    @PatchMapping("/{id}") public Map<String,Object> rename(@PathVariable long id,@RequestBody Map<String,String> body,HttpServletRequest req)throws Exception{photos.rename(id,body.get("displayName"),user(req));return ok("名称已更新");}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable long id,HttpServletRequest req)throws Exception{photos.delete(id,user(req));return ok("图片已删除");}
    public static Map<String,Object> ok(String msg){Map<String,Object>m=new LinkedHashMap<>();m.put("success",true);m.put("msg",msg);return m;}
}
