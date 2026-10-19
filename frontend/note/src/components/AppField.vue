<template>
  <label class="field" :class="{ 'is-invalid': !!error }">
    <span v-if="label" class="field__label">
      {{ label }}
      <span v-if="required" class="field__req" aria-hidden="true">*</span>
    </span>

    <textarea
      v-if="type === 'textarea'"
      class="field__control field__control--area"
      :value="modelValue"
      :placeholder="placeholder"
      :rows="rows"
      :maxlength="maxlength"
      :disabled="disabled"
      :aria-invalid="!!error"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <input
      v-else
      class="field__control"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :disabled="disabled"
      :aria-invalid="!!error"
      @input="$emit('update:modelValue', $event.target.value)"
      @keyup.enter="$emit('submit')"
    />

    <span v-if="error" class="field__error">{{ error }}</span>
    <span v-else-if="hint" class="field__hint">{{ hint }}</span>
  </label>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  rows: { type: Number, default: 4 },
  maxlength: { type: [Number, String], default: undefined },
  disabled: { type: Boolean, default: false },
  required: { type: Boolean, default: false }
})

defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field__label {
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.field__req {
  color: var(--danger);
  margin-left: 2px;
}

.field__control {
  width: 100%;
  padding: 9px 12px;
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-base);
  color: var(--ink);
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.field__control::placeholder {
  color: var(--ink-3);
}

.field__control:hover:not(:disabled) {
  border-color: var(--ink-3);
}

.field__control:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.field__control:disabled {
  background: var(--paper-deep);
  color: var(--ink-3);
  cursor: not-allowed;
}

.field__control--area {
  resize: vertical;
  min-height: 84px;
  line-height: 1.7;
}

.field__error {
  font-size: var(--fs-xs);
  color: var(--danger);
}

.field__hint {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.is-invalid .field__control {
  border-color: var(--danger);
}

.is-invalid .field__control:focus {
  box-shadow: 0 0 0 3px var(--danger-soft);
}
</style>
