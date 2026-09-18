<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ACCEPTED_EXTENSIONS, MAX_FILE_SIZE, validateFile } from '@/api/document'
import { formatBytes } from '@/utils/format'

const emit = defineEmits(['upload'])

const dragging = ref(false)
const input = ref(null)
/** 本地预校验的错误：不发请求，直接告诉用户哪里不对 */
const localError = ref(null)

// dragenter / dragleave 会在子元素间来回触发，用计数器判断是否真的离开了区域
let dragDepth = 0

function onDragEnter() {
  dragDepth += 1
  dragging.value = true
}

function onDragLeave() {
  dragDepth -= 1
  if (dragDepth <= 0) {
    dragDepth = 0
    dragging.value = false
  }
}

function onDrop(e) {
  dragDepth = 0
  dragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) accept(file)
}

function onPick(e) {
  const file = e.target.files?.[0]
  if (file) accept(file)
  // 清空 value，否则连续选择同一个文件不会再触发 change
  e.target.value = ''
}

function accept(file) {
  const problem = validateFile(file)
  if (problem) {
    localError.value = problem
    return
  }
  localError.value = null
  emit('upload', file)
}

onBeforeUnmount(() => {
  dragDepth = 0
})
</script>

<template>
  <div class="drop">
    <div
      class="drop__zone"
      :class="{ 'is-dragging': dragging }"
      role="button"
      tabindex="0"
      @click="input?.click()"
      @keydown.enter.prevent="input?.click()"
      @keydown.space.prevent="input?.click()"
      @dragenter.prevent="onDragEnter"
      @dragover.prevent
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
    >
      <p class="drop__lead">
        {{ dragging ? '松开即可上传' : '把文档拖到这里，或点击选择文件' }}
      </p>
      <p class="drop__hint">
        支持 {{ ACCEPTED_EXTENSIONS.join(' / ') }}，单文件不超过
        {{ formatBytes(MAX_FILE_SIZE) }}
      </p>

      <input
        ref="input"
        type="file"
        class="sr-only"
        :accept="ACCEPTED_EXTENSIONS.map((e) => `.${e}`).join(',')"
        @change="onPick"
      />
    </div>

    <div v-if="localError" class="notice notice--error drop__error">
      <div>
        <strong>{{ localError }}</strong>
        <p class="drop__error-hint">该文件未发送到后端，可换一个文件重试。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.drop__zone {
  padding: 30px 20px;
  border: 1px dashed var(--rule-strong);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: center;
  cursor: pointer;
  transition: border-color 0.14s var(--ease), background-color 0.14s var(--ease);
}

.drop__zone:hover {
  border-color: var(--ink-4);
}

.drop__zone.is-dragging {
  border-color: var(--celadon);
  border-style: solid;
  background: var(--celadon-mist);
}

.drop__lead {
  font-size: var(--fs-lg);
  color: var(--ink);
  margin-bottom: 5px;
}

.drop__zone.is-dragging .drop__lead {
  color: var(--celadon-deep);
  font-weight: 500;
}

.drop__hint {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.drop__error {
  margin-top: 12px;
}

.drop__error-hint {
  margin-top: 4px;
  opacity: 0.85;
}
</style>
