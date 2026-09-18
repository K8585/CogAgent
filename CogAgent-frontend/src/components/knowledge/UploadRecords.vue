<script setup>
import { computed, ref } from 'vue'
import { useDocumentsStore } from '@/stores/documents'
import { formatBytes, formatDateTime, formatMs, shortId } from '@/utils/format'

/**
 * 上传记录
 *
 * 【这些记录是本地的】
 * 后端没有文档列表接口，DocumentController 只提供 upload。
 * 所以这张表记录的是「这个浏览器上传过的文件」，不是服务端的知识库全貌。
 * 换台机器打开会是空的，但那些切片确实还在 Milvus 里、也确实会被检索到。
 */
const documents = useDocumentsStore()

const confirmingId = ref(null)
const expandedId = ref(null)

const rows = computed(() => documents.sorted)

function statusTag(record) {
  if (record.status === 'error') return { cls: 'tag--error', text: '失败' }
  if (record.status === 'processing') return { cls: 'tag--warn', text: '处理中' }
  // 后端返回的状态原样展示（如 SUCCESS / COMPLETED），未知值也照实呈现
  return { cls: 'tag--accent', text: record.status || '成功' }
}

function toggleDetail(id) {
  expandedId.value = expandedId.value === id ? null : id
}
</script>

<template>
  <section class="panel">
    <div class="panel__head">
      <h2 class="panel__title">上传记录</h2>
      <div class="panel__actions">
        <span class="panel__note">保存在本浏览器</span>
        <button
          v-if="rows.length"
          type="button"
          class="btn btn--sm btn--quiet btn--danger"
          @click="documents.clearAll()"
        >
          清空记录
        </button>
      </div>
    </div>

    <div v-if="!rows.length" class="empty">
      <p class="empty__title">还没有上传记录</p>
      <p class="empty__hint">
        上传一份文档后，这里会列出文件名、切片数量和真实处理耗时。
        记录只保存在本浏览器，清空不影响 Milvus 里已入库的向量。
      </p>
    </div>

    <table v-else class="table">
      <thead>
        <tr>
          <th>文件名</th>
          <th>文档 ID</th>
          <th class="col-num">切片</th>
          <th class="col-num">耗时</th>
          <th class="col-num">大小</th>
          <th>状态</th>
          <th>上传时间</th>
          <th class="col-tight"><span class="sr-only">操作</span></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="record in rows" :key="record.id">
          <tr>
            <td>
              <button
                v-if="record.error"
                type="button"
                class="file file--button"
                @click="toggleDetail(record.id)"
              >
                <span class="file__name">{{ record.fileName }}</span>
                <span class="file__ext mono">{{ record.ext }}</span>
              </button>
              <span v-else class="file">
                <span class="file__name">{{ record.fileName }}</span>
                <span class="file__ext mono">{{ record.ext }}</span>
              </span>
            </td>
            <td class="mono cell-id">{{ shortId(record.documentId) }}</td>
            <td class="col-num">{{ record.chunkCount || '—' }}</td>
            <td class="col-num">{{ formatMs(record.processingTimeMs) }}</td>
            <td class="col-num">{{ formatBytes(record.size) }}</td>
            <td>
              <span class="tag" :class="statusTag(record).cls">{{ statusTag(record).text }}</span>
            </td>
            <td class="mono cell-time">{{ formatDateTime(record.at) }}</td>
            <td class="col-tight">
              <div v-if="confirmingId === record.id" class="row-confirm">
                <button type="button" class="btn btn--sm btn--danger" @click="documents.remove(record.id); confirmingId = null">
                  删除
                </button>
                <button type="button" class="btn btn--sm btn--quiet" @click="confirmingId = null">取消</button>
              </div>
              <button
                v-else
                type="button"
                class="btn btn--sm btn--quiet"
                :aria-label="`移除记录 ${record.fileName}`"
                @click="confirmingId = record.id"
              >
                移除
              </button>
            </td>
          </tr>

          <tr v-if="expandedId === record.id && record.error" class="detail-row">
            <td colspan="8">
              <div class="notice notice--error">
                <div>
                  <strong>{{ record.error }}</strong>
                  <p class="detail-hint">
                    这条记录只是从列表中移除，不会影响 Milvus 中已经入库的向量。
                  </p>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <p v-if="rows.length" class="panel-foot">
      「大小」与「上传时间」由前端在发起请求时补记 —— 后端的
      <code class="mono">DocumentUploadResponse</code> 只返回文档 ID、切片数、状态和耗时。
      「移除」只删除本地记录，不会删除 Milvus 中的向量数据。
    </p>
  </section>
</template>

<style scoped>
.panel__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file {
  display: inline-flex;
  align-items: baseline;
  gap: 7px;
  max-width: 320px;
}

.file--button {
  text-align: left;
}

.file--button:hover .file__name {
  color: var(--celadon);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.file__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink);
}

.file__ext {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.cell-id {
  font-size: var(--fs-xs);
  color: var(--ink-2);
}

.cell-time {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  white-space: nowrap;
}

.row-confirm {
  display: flex;
  gap: 4px;
}

.detail-row td {
  padding: 12px 12px 14px;
  background: var(--paper);
}

.detail-hint {
  margin-top: 5px;
  line-height: 1.65;
  opacity: 0.85;
}

.panel-foot {
  padding: 11px 18px 13px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-3);
}

.panel-foot code {
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
}
</style>
