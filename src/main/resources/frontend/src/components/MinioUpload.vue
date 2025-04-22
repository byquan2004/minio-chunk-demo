<template>
  <div class="upload-container">
    <el-upload
      class="upload-demo"
      drag
      action="#"
      :auto-upload="false"
      :on-change="handleFileChange"
      :show-file-list="true"
      :multiple="true"
      :limit="5"
      :file-list="uploadFiles"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到这里或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          可以同时选择多个文件上传
        </div>
      </template>
    </el-upload>

    <div v-for="file in uploadFiles" :key="file.uid" class="upload-info">
      <h3>{{ file.name }}</h3>
      <div class="progress-info">
        <el-progress 
          :percentage="file.progress || 0" 
          :status="file.status"
        />
        <div class="file-info">
          <div class="file-stats">
            <span>文件大小: {{ formatFileSize(file.size) }}</span>
            <span>已上传: {{ formatFileSize(file.uploadedSize || 0) }}</span>
            <span v-if="file.isUploading">速度: {{ file.uploadSpeed || '0 KB/s' }}</span>
          </div>
          <div class="upload-progress">
            {{ formatProgress(file.progress || 0) }}
          </div>
        </div>
      </div>
      
      <div class="control-buttons">
        <el-button 
          type="primary" 
          @click="startUpload(file)"
          :disabled="file.isUploading"
          v-if="!file.isComplete"
        >
          开始上传
        </el-button>
        <el-button 
          v-if="file.isUploading" 
          @click="pauseUpload(file)"
        >
          暂停
        </el-button>
        <el-button 
          v-if="file.isPaused" 
          @click="resumeUpload(file)"
        >
          继续
        </el-button>
        <el-button 
          type="danger" 
          @click="removeFile(file)"
          :disabled="file.isUploading && !file.isPaused"
        >
          移除
        </el-button>
      </div>
    </div>

    <div class="batch-controls" v-if="uploadFiles.length > 0">
      <el-button 
        type="primary" 
        @click="startAllUploads"
        :disabled="isAnyFileUploading"
      >
        全部上传
      </el-button>
      <el-button 
        type="danger" 
        @click="removeAllFiles"
        :disabled="isAnyFileUploading"
      >
        清空列表
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import axios from 'axios'
import SparkMD5 from 'spark-md5'
import { ElMessage } from 'element-plus'

const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB 分片大小
const MAX_CONCURRENT_UPLOADS = 5
const uploadFiles = ref([])

const isAnyFileUploading = computed(() => {
  return uploadFiles.value.some(file => file.isUploading && !file.isPaused)
})

const handleFileChange = (file) => {
  const newFile = {
    ...file,
    uid: file.uid || Date.now() + Math.random().toString(36).substr(2, 9),
    raw: file.raw,
    progress: 0,
    uploadedSize: 0,
    isUploading: false,
    isPaused: false,
    status: '',
    chunks: [],
    currentChunkIndex: 0,
    uploadController: null,
    uploadSpeed: '0 KB/s',
    lastUploadedSize: 0,
    lastUploadTime: Date.now(),
    speedInterval: null,
    isComplete: false
  }
  
  // 检查是否已存在相同文件
  const existingFile = uploadFiles.value.find(f => f.name === file.name && f.size === file.size)
  if (!existingFile) {
    uploadFiles.value.push(newFile)
  }
}

const calculateFileMD5 = (file) => {
  return new Promise((resolve, reject) => {
    const blobSlice = File.prototype.slice
    const chunkSize = 2097152 // 2MB
    const chunks = Math.ceil(file.size / chunkSize)
    let currentChunk = 0
    const spark = new SparkMD5.ArrayBuffer()
    const fileReader = new FileReader()

    fileReader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++

      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    fileReader.onerror = (e) => {
      reject(e)
    }

    function loadNext() {
      const start = currentChunk * chunkSize
      const end = start + chunkSize >= file.size ? file.size : start + chunkSize
      fileReader.readAsArrayBuffer(blobSlice.call(file, start, end))
    }

    loadNext()
  })
}

const createFileChunks = (file) => {
  const chunks = []
  let start = 0
  while (start < file.size) {
    const end = Math.min(start + CHUNK_SIZE, file.size)
    chunks.push(file.slice(start, end))
    start = end
  }
  return chunks
}

const startSpeedCalculation = (file) => {
  if (file.speedInterval) {
    clearInterval(file.speedInterval)
  }
  
  file.speedInterval = setInterval(() => {
    const now = Date.now()
    const timeDiff = (now - file.lastUploadTime) / 1000
    const sizeDiff = file.uploadedSize - file.lastUploadedSize
    
    if (timeDiff > 0) {
      const speedBps = sizeDiff / timeDiff
      file.uploadSpeed = formatFileSize(speedBps) + '/s'
      
      file.lastUploadedSize = file.uploadedSize
      file.lastUploadTime = now
    }
  }, 1000)
}

const stopSpeedCalculation = (file) => {
  if (file.speedInterval) {
    clearInterval(file.speedInterval)
    file.speedInterval = null
  }
}

const updateFileProgress = (file, chunkProgress = 1) => {
  const completedChunks = file.currentChunkIndex
  const totalChunks = file.chunks.length
  const baseProgress = (completedChunks / totalChunks) * 100
  const chunkContribution = (chunkProgress / totalChunks) * 100
  file.progress = Math.min(Math.round(baseProgress + chunkContribution), 100)
}

const uploadChunks = async (file, fileMD5) => {
  const uploadChunk = async (chunkIndex) => {
    if (chunkIndex >= file.chunks.length || file.isPaused) {
      return
    }

    const chunk = file.chunks[chunkIndex]
    const formData = new FormData()
    formData.append('file', chunk)
    formData.append('chunkIndex', chunkIndex)
    formData.append('fileMD5', fileMD5)

    try {
      await axios.post('/api/minio/upload/chunk', formData, {
        signal: file.uploadController.signal,
        onUploadProgress: (progressEvent) => {
          const chunkProgress = progressEvent.loaded / progressEvent.total
          file.uploadedSize = (file.currentChunkIndex * CHUNK_SIZE) + 
                            (chunk.size * chunkProgress)
          updateFileProgress(file, chunkProgress)
        }
      })

      file.currentChunkIndex++
      updateFileProgress(file)
      
      // 继续上传下一个分片
      await uploadChunk(file.currentChunkIndex + MAX_CONCURRENT_UPLOADS - 1)
    } catch (error) {
      if (error.name === 'AbortError') {
        console.log('上传已暂停')
        return
      }
      throw error
    }
  }

  try {
    // 启动多个并发上传任务
    const uploadTasks = []
    for (let i = 0; i < MAX_CONCURRENT_UPLOADS && i < file.chunks.length; i++) {
      uploadTasks.push(uploadChunk(file.currentChunkIndex + i))
    }

    // 等待所有分片上传完成
    await Promise.all(uploadTasks)

    // 检查是否所有分片都已上传完成
    if (file.currentChunkIndex >= file.chunks.length) {
      file.progress = 99 // 设置为99%表示正在合并
      
      // 所有分片上传完成，合并文件
      await axios.post('/api/minio/upload/complete', {
        fileMD5: fileMD5,
        fileName: file.name,
        chunkCount: file.chunks.length
      })
      
      file.progress = 100
      file.status = 'success'
      file.isUploading = false
      file.isComplete = true
      stopSpeedCalculation(file)
      ElMessage.success(`${file.name} 上传完成`)
    }
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error(`${file.name} 上传失败: ${error.response?.data?.message || error.message}`)
    file.status = 'exception'
    file.isUploading = false
    stopSpeedCalculation(file)
    throw error
  }
}

const startUpload = async (file) => {
  if (file.isUploading) return

  try {
    file.isUploading = true
    file.status = 'active'
    file.lastUploadedSize = 0
    file.lastUploadTime = Date.now()
    startSpeedCalculation(file)
    
    // 计算文件 MD5
    const fileMD5 = await calculateFileMD5(file.raw)
    
    // 创建分片
    file.chunks = createFileChunks(file.raw)
    
    // 初始化上传
    const initResponse = await axios.post('/api/minio/upload/init', {
      fileName: file.name,
      fileMD5: fileMD5,
      chunkCount: file.chunks.length,
      fileSize: file.size
    })

    if (initResponse.data.code === 200) {
      const { status, uploadedChunks, url } = initResponse.data.data
      
      if (status === 'COMPLETED') {
        // 文件已经上传完成
        ElMessage.success(`${file.name} 已存在，无需重新上传`)
        file.progress = 100
        file.status = 'success'
        file.isUploading = false
        file.isComplete = true
        stopSpeedCalculation(file)
        return
      }
      
      // 恢复已上传的分片进度
      if (uploadedChunks && uploadedChunks.length > 0) {
        uploadedChunks.forEach(index => {
          file.currentChunkIndex = Math.max(file.currentChunkIndex, index + 1)
          file.uploadedSize = file.currentChunkIndex * CHUNK_SIZE
        })
        updateFileProgress(file)
        ElMessage.info(`继续上传 ${file.name}，已完成${file.progress}%`)
      }
      
      file.uploadController = new AbortController()
      await uploadChunks(file, fileMD5)
    }
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error(`${file.name} 上传失败: ${error.response?.data?.message || error.message}`)
    file.status = 'exception'
    file.isUploading = false
  } finally {
    stopSpeedCalculation(file)
  }
}

const pauseUpload = (file) => {
  file.isPaused = true
  file.uploadController?.abort()
  file.status = 'warning'
  stopSpeedCalculation(file)
  ElMessage.warning(`${file.name} 已暂停上传`)
}

const resumeUpload = async (file) => {
  file.isPaused = false
  file.status = 'active'
  file.uploadController = new AbortController()
  startSpeedCalculation(file)
  const fileMD5 = await calculateFileMD5(file.raw)
  await uploadChunks(file, fileMD5)
}

const removeFile = (file) => {
  if (file.isUploading && !file.isPaused) {
    ElMessage.warning('请先暂停上传后再移除')
    return
  }
  
  file.uploadController?.abort()
  stopSpeedCalculation(file)
  uploadFiles.value = uploadFiles.value.filter(f => f.uid !== file.uid)
  ElMessage.info(`已移除 ${file.name}`)
}

const startAllUploads = async () => {
  const pendingFiles = uploadFiles.value.filter(file => !file.isComplete && !file.isUploading)
  for (const file of pendingFiles) {
    await startUpload(file)
  }
}

const removeAllFiles = () => {
  if (isAnyFileUploading.value) {
    ElMessage.warning('请先暂停所有上传后再清空列表')
    return
  }
  
  uploadFiles.value.forEach(file => {
    file.uploadController?.abort()
    stopSpeedCalculation(file)
  })
  uploadFiles.value = []
  ElMessage.info('已清空上传列表')
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

const formatProgress = (progress) => {
  return `${progress}%`
}
</script>

<style scoped>
.upload-container {
  max-width: 800px;
  margin: 20px auto;
  padding: 20px;
}

.upload-info {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.progress-info {
  margin: 20px 0;
}

.file-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
}

.file-stats {
  display: flex;
  gap: 20px;
}

.upload-progress {
  font-weight: bold;
  color: #409EFF;
}

.control-buttons {
  margin-top: 20px;
  display: flex;
  gap: 10px;
}

.batch-controls {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 20px;
}

.el-upload__tip {
  margin-top: 8px;
  color: #909399;
}
</style> 