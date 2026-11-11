<template>
  <!--
    横幅底部的水波纹。
    四层同一条 path 叠起来，opacity 从 0.25 → 1、动画时长从 7s → 20s 递减，
    慢的那几层被快的那几层拖着走，看起来就有"水深浅不同"的视差感。
    参考 Mizuki 的 Waves 实现（path 与分层参数一致），只把配色换成本站的纸面底色。
  -->
  <div class="banner-waves" aria-hidden="true">
    <svg viewBox="0 20 150 32" preserveAspectRatio="none" shape-rendering="auto">
      <defs>
        <path id="blog-gentle-wave" d="M-160 44c30 0 58-18 88-18s 58 18 88 18 58-18 88-18 58 18 88 18v48h-352z" />
      </defs>
      <g class="wave-parallax">
        <use href="#blog-gentle-wave" x="48" y="0" class="wave-use wave-1" />
        <use href="#blog-gentle-wave" x="48" y="3" class="wave-use wave-2" />
        <use href="#blog-gentle-wave" x="48" y="5" class="wave-use wave-3" />
        <use href="#blog-gentle-wave" x="48" y="7" class="wave-use wave-4" />
      </g>
    </svg>
  </div>
</template>
