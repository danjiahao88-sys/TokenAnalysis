<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    id: string
    label: string
    modelValue: string | number | null
    error?: string
    type?: string
    multiline?: boolean
    required?: boolean
  }>(),
  { type: 'text' }
)
const emit = defineEmits<{ 'update:modelValue': [value: any] }>()
function update(event: Event) {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', props.type === 'number' ? (value === '' ? null : Number(value)) : value)
}
</script>
<template>
  <div class="notebook-field">
    <label :for="id">{{ label }}{{ required ? ' *' : '' }}</label
    ><textarea
      v-if="multiline"
      :id="id"
      :value="modelValue ?? ''"
      :required="required"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${id}-error` : undefined"
      rows="3"
      @input="update"
    ></textarea
    ><input
      v-else
      :id="id"
      :type="type"
      :value="modelValue ?? ''"
      :required="required"
      :step="type === 'number' ? 'any' : undefined"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${id}-error` : undefined"
      @input="update"
    /><span v-if="error" :id="`${id}-error`" class="form-error">{{ error }}</span>
  </div>
</template>
