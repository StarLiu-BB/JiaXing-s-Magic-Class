<template>
  <component :is="linkComponent" v-bind="linkAttrs">
    <slot />
  </component>
</template>

<script setup>
import { computed } from 'vue'
import { isExternal } from '@/utils/validate'

const props = defineProps({
  to: {
    type: String,
    required: true
  }
})

const linkComponent = computed(() => {
  if (isExternal(props.to)) {
    return 'a'
  }
  return 'router-link'
})

const linkAttrs = computed(() => {
  if (isExternal(props.to)) {
    return {
      href: props.to,
      target: '_blank',
      rel: 'noopener'
    }
  }
  return {
    to: props.to
  }
})
</script>

