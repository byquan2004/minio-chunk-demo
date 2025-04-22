# MinIO 分片上传示例

这是一个基于 Vue 3 + Element Plus + MinIO 的大文件分片上传示例项目。支持断点续传、暂停/继续、多文件上传等功能。

## 功能特性

- ✨ 支持大文件分片上传
- 🚀 支持多文件同时上传
- 📱 实时显示上传进度和速度
- ⏸️ 支持暂停/继续上传
- 🔄 支持断点续传
- 🎯 文件秒传（相同文件自动跳过）
- 📊 精确的进度显示
- 🛠️ 完善的错误处理

## 技术栈

- Vue 3
- Element Plus
- Axios
- MinIO
- Spring Boot

## 安装和使用

### 前端部分

1. 安装依赖：
```bash
npm install
# 或
pnpm install
```

2. 启动开发服务器：
```bash
npm run dev
# 或
pnpm dev
```

### 后端部分

1. 确保已安装并启动 MinIO 服务器
2. 在 `application.yml` 中配置 MinIO 连接信息：
```yaml
minio:
  endpoint: http://your-minio-server:9000
  access-key: your-access-key
  secret-key: your-secret-key
  bucket-name: your-bucket-name
```

3. 启动 Spring Boot 应用

## 使用说明

1. 文件上传：
   - 点击上传区域或拖拽文件到上传区域
   - 支持同时选择多个文件（最多 5 个）
   - 自动检测重复文件

2. 上传控制：
   - 单个文件控制：开始/暂停/继续/移除
   - 批量控制：全部上传/清空列表
   - 上传过程中显示实时速度和进度

3. 断点续传：
   - 支持暂停后继续上传
   - 页面刷新后可以继续上传
   - 自动跳过已上传的分片

## 核心配置

- 分片大小：5MB（可在代码中调整）
- 并发上传数：5（可在代码中调整）
- 最大文件数：5（可在代码中调整）

## 项目结构

```
src/
├── components/
│   └── MinioUpload.vue    # 上传组件
├── App.vue                 # 主应用
└── main.js                # 入口文件
```

## API 接口

1. 初始化上传
```
POST /api/minio/upload/init
请求体：{
  fileName: string,
  fileMD5: string,
  chunkCount: number,
  fileSize: number
}
```

2. 上传分片
```
POST /api/minio/upload/chunk
表单数据：
- file: 文件分片
- chunkIndex: 分片索引
- fileMD5: 文件MD5
```

3. 完成上传
```
POST /api/minio/upload/complete
请求体：{
  fileName: string,
  fileMD5: string,
  chunkCount: number
}
```

## 注意事项

1. 文件大小限制：
   - 单个分片：5MB
   - 总文件大小：无限制

2. 并发限制：
   - 单文件并发分片：5
   - 最大文件数：5

3. 浏览器兼容性：
   - 支持现代浏览器
   - 需要支持 File API
   - 需要支持 Fetch API

## 开发建议

1. 调整分片大小：
   ```javascript
   const CHUNK_SIZE = 5 * 1024 * 1024 // 修改分片大小
   ```

2. 调整并发数：
   ```javascript
   const MAX_CONCURRENT_UPLOADS = 5 // 修改并发数
   ```

3. 调整文件数限制：
   ```html
   <el-upload :limit="5"> <!-- 修改最大文件数 -->
   ```
