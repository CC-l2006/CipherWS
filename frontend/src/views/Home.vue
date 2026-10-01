<template>
  <section class="page home">
    <div class="home-body">
      <div id="say" @click="nextSaying">
        <p>{{ saying.saying }}</p>
        <p>--{{ saying.person }}</p>
      </div>
    </div>

    <div id="button-wrapper">
      <setting-button />
    </div>

    <div id="music-wrapper">
      <music-player />
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { getSaying } from '@/api/say-api'
import settingButton from '@/components/button/SettingButton.vue'
import musicPlayer from '@/components/music-player/music-player.vue'

const saying = ref({
  saying: '今天是明天的昨天',
  person: '作者'
})
const randomId = ref(Math.floor(Math.random() * 100) + 1)

const fetchSaying = async (id) => {
  const res = await getSaying(id)
  if (res.success) {
    saying.value = res.data
    console.log('获取到数据', res)
  } else {
    console.log('获取数据失败', res.errMessage)
  }
}

const nextSaying = () => {
  randomId.value = randomId.value >= 100 ? 1 : randomId.value + 1
  fetchSaying(randomId.value)
}

fetchSaying(randomId.value)
</script>

<style scoped>
  .page {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .home-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    padding-bottom: 200px;
  }

  #say {
    display: flex;
    flex-direction: column;
    align-items: center;
    transition: transform 0.3s ease;
    user-select: none;
    cursor: pointer;
  }

  #say p {
    margin-top: 20px;
    font-size: 20px;
    color: #e2e2e2;
  }

  #say p:nth-child(2) {
    font-size: 16px;
    color: #b4b4b4;
    margin-top: 0;
  }

  #say:hover p {
    color: #ffffff;
  }

  #say:hover p:nth-child(2) {
    color: #d9d9d9;
  }

  #say:active {
    transform: scale(0.9);
  }

  #button-wrapper {
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 999;
  }
</style>
