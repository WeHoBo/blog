<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="text-3xl font-extrabold text-gray-900 dark:text-gray-100 mb-3">隐私政策</h1>
    <p class="text-sm text-gray-400 mb-8">生效日期：2026-11-12　版本：v2</p>

    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8 mb-6">
      <p class="text-gray-700 dark:text-gray-300 leading-relaxed">
        我们非常重视你的个人信息保护。本政策说明「好啵博客」（含网站与鸿蒙客户端）收集哪些信息、为什么收集、如何保存，以及你拥有哪些权利。请在使用前仔细阅读。
      </p>

      <!-- 备案 / 上架审核要核对的基础信息，单独列出便于比对 -->
      <div class="mt-6 rounded-xl bg-gray-50 dark:bg-gray-900/60 p-4 text-sm">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-gray-600 dark:text-gray-300">
          <div><span class="text-gray-400">应用名称：</span>好啵博客</div>
          <div><span class="text-gray-400">应用包名：</span><code class="text-xs">com.weihaobo.myblogapp</code></div>
          <div><span class="text-gray-400">网站域名：</span>codeup.asia</div>
          <div><span class="text-gray-400">运营者：</span>好啵博客（个人开发者）</div>
        </div>
      </div>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-2xl shadow-sm p-8">
      <section
        v-for="(sec, i) in sections"
        :key="sec.title"
        :class="i > 0 ? 'mt-8' : ''"
      >
        <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100 mb-3">{{ sec.title }}</h2>
        <p
          v-for="(p, j) in sec.paragraphs"
          :key="j"
          class="text-gray-700 dark:text-gray-300 leading-relaxed mb-2 last:mb-0"
        >{{ p }}</p>
      </section>
    </div>

    <p class="mt-6 text-sm text-gray-400">
      本政策同时适用于网站 <a href="https://codeup.asia" class="text-primary-500 hover:underline">codeup.asia</a>
      与「好啵博客」鸿蒙客户端。客户端内的政策页面与本页内容一致。
    </p>
  </div>
</template>

<script setup lang="ts">
interface PolicySection {
  title: string
  paragraphs: string[]
}

/**
 * 隐私政策（公开页）。
 *
 * 用途：App 备案 + 应用市场上架时必须填「隐私政策网址」，只有 App 内页面不够。
 * 维护约定：本页与鸿蒙端 pages/Privacy.ets 内容必须一致 —— 改一处要同步另一处，
 * 并同时升 privacyStore.PRIVACY_VERSION（否则老用户不会重新弹窗同意）。
 *
 * 本页不发起任何接口请求：未登录访客、备案审核爬虫都要能直接看到全文。
 */
const sections: PolicySection[] = [
  {
    title: '一、适用范围',
    paragraphs: [
      '本政策适用于「好啵博客」网站（https://codeup.asia）与鸿蒙客户端（包名 com.weihaobo.myblogapp）。',
      '本站是个人学习笔记站点，提供文章阅读与评论功能，不提供注册、付费、交易等服务。'
    ]
  },
  {
    title: '二、我们收集哪些信息',
    paragraphs: [
      '1. 登录信息：当你选择通过 GitHub、Gitee 或华为账号登录时，我们仅获取该平台返回的账号标识、用户名、昵称与头像地址，用于展示你的身份。我们不会接触、也不会保存你的平台密码。',
      '2. 你主动发布的内容：评论正文、评论时间，以及评论所关联的文章。评论默认公开可见，请勿在其中填写手机号、住址等敏感信息。',
      '3. 访问日志：服务器会记录常规访问日志（IP 地址、访问时间、请求路径、浏览器标识），仅用于安全防护与故障排查。',
      '4. 本地存储：登录凭证（token）与「隐私政策同意版本」保存在你设备的应用私有目录或浏览器本地存储中。',
      '5. AI 问答记录：你向 AI 助手提出的问题文本、检索步骤、token 用量与耗时。用于额度控制、成本统计与故障排查，不用于用户画像。'
    ]
  },
  {
    title: '三、我们不会收集的信息',
    paragraphs: [
      '本应用不申请位置、相机、麦克风、通讯录、相册、日历等任何敏感权限，也不读取你设备上的其他应用数据。',
      '应用内不集成广告 SDK、第三方统计分析 SDK 与消息推送 SDK。',
      '未登录访客浏览文章时，我们不会创建用户画像，也不会进行跨站跟踪。'
    ]
  },
  {
    title: '四、信息的使用目的',
    paragraphs: [
      '登录信息：用于识别你的身份、展示昵称与头像、判断评论归属。',
      '评论内容：用于在文章页展示讨论。',
      '访问日志：用于发现异常访问、排查服务故障，不会用于用户画像或商业分析。'
    ]
  },
  {
    title: '五、存储与保护',
    paragraphs: [
      '上述信息存储于位于中华人民共和国境内的服务器。客户端与服务端之间的通信使用 HTTPS 加密。',
      '登录凭证仅保存在你的设备本地，你可以随时通过「个人资料 → 退出登录」清除；网站端可清除浏览器本地存储退出登录。',
      '我们仅在实现本政策所述目的所必需的期限内保留信息；超出期限后会删除或匿名化处理。'
    ]
  },
  {
    title: '六、第三方共享',
    paragraphs: [
      '除下列情形外，我们不会向任何第三方提供你的信息：',
      '1. 你选择第三方账号登录时，由该平台（GitHub / Gitee / 华为）按其自身隐私政策处理授权过程；',
      '2. 法律法规要求或有权机关依法要求提供。',
      '3. 使用 AI 助手与联网搜索时，会向第三方大模型与搜索服务发送必要内容，详见「七、AI 助手与第三方服务」。',
      '我们不会出售、出租你的任何个人信息。'
    ]
  },
  {
    title: '七、AI 助手与第三方服务',
    paragraphs: [
      '使用 AI 助手需要先登录。在提供该功能的客户端（当前为网站端）提问时，我们会把你输入的问题，以及从本站文章、你上传的文档中检索到的相关片段，发送给第三方大模型服务商（当前为 DeepSeek，运营者可在后台切换为其它兼容厂商）用于生成回答。',
      '若触发联网搜索（AI 面板中的联网检索，或文章页选中文字后点「搜一下」），我们仅将搜索关键词发送给第三方搜索服务（Tavily / 博查），用于获取公开网页结果。',
      '上述发送内容不包含你的用户名、邮箱、头像与登录凭证。',
      '问答记录（问题文本、检索步骤、token 用量、耗时与状态）保存在本站数据库，用于额度控制、成本统计与故障排查；管理员可查看。如需删除你的问答记录，可通过「十一、联系我们」告知。',
      '第三方服务对其收到的信息按其自身隐私政策处理。AI 回答可能包含来自第三方网页的内容，外部来源卡片会标注域名，点击后即离开本站。',
      '鸿蒙客户端当前不提供 AI 助手功能，本节所述的第三方发送行为仅发生在网站端。'
    ]
  },
  {
    title: '八、你的权利',
    paragraphs: [
      '查看与更正：在「个人资料」页可查看并修改昵称、头像地址。',
      '删除内容：如需删除你发表的评论，可通过下方联系方式告知具体文章与评论内容；我们已在评论区提供举报入口，并在管理后台支持删除。',
      '撤回同意：退出登录即可停止上传新的信息；卸载应用会清除设备本地的登录凭证与同意记录。',
      '注销账号：本应用不提供独立注册，账号即第三方平台账号；如需清除本站保存的关联数据，可联系我们处理。'
    ]
  },
  {
    title: '九、未成年人保护',
    paragraphs: [
      '本应用面向具备完全民事行为能力的用户。若你是未满 14 周岁的儿童，请在监护人陪同下阅读本政策，并在取得监护人同意后再使用评论等功能。'
    ]
  },
  {
    title: '十、政策更新',
    paragraphs: [
      '当功能或法规变化导致本政策发生实质调整时，我们会更新版本号，并在客户端下次启动时重新征求你的同意；网站端将在本页同步更新生效日期。'
    ]
  },
  {
    title: '十一、联系我们',
    paragraphs: [
      '如对本政策有任何疑问、意见，或需要行使上述权利，可通过本站「关于」页或任意文章评论区留言与我们联系。我们会在合理期限内答复。'
    ]
  }
]

useSeoMeta({
  title: '隐私政策 - 好啵博客',
  description: '好啵博客（网站与鸿蒙客户端）隐私政策：说明我们收集哪些信息、使用目的、存储与保护方式，以及你享有的权利。',
  ogTitle: '隐私政策 - 好啵博客',
  ogDescription: '好啵博客隐私政策：收集范围、使用目的、存储保护与用户权利。',
  ogImage: 'https://codeup.asia/apple-touch-icon.png'
})
useHead({ link: [{ rel: 'canonical', href: 'https://codeup.asia/privacy' }] })
</script>
