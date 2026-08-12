# 家庭图片库存储

默认以 Server 仓库根目录为运行目录：

```text
data/photos/
├── photos.sqlite              # 元数据与查看范围设置
├── archives/YYYY/MM/*.zip     # 未重新编码的原图
├── thumbnails/YYYY/MM/*.jpg   # 列表缩略图
├── cache/                     # 最多 20 张按需解压的高清图
└── staging/                   # 上传临时文件，完成后删除
```

目录可分别通过 `PHOTO_DATA_DIR`、`PHOTO_ARCHIVE_DIR`、
`PHOTO_THUMBNAIL_DIR`、`PHOTO_CACHE_DIR`、`PHOTO_STAGING_DIR` 修改。
若原图需要存到外挂磁盘，只设置 `PHOTO_ARCHIVE_DIR` 即可；数据库、
缩略图和缓存仍可留在本机快速磁盘。

ZIP 内原图使用系统生成的随机名称，图片与 ZIP 条目的对应关系保存在
`photos.sqlite`，不要直接改名或移动单个 ZIP 条目。
