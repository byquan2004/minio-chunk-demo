<template>
  <div class="app">
    <h1>MinIO 分片上传示例</h1>
    <MinioUpload />
  </div>
</template>

<script setup>
import MinioUpload from './components/MinioUpload.vue'
</script>

<style>
.app {
  font-family: Arial, sans-serif;
  padding: 20px;
}

h1 {
  text-align: center;
  color: #409EFF;
  margin-bottom: 30px;
}
</style> 