const originalTitle = document.title;

// 定义离开时的标题
const awayTitle = '人呢，哪去了(〒︿〒)';
const backTitle = '我就知道你不会抛弃我(´・ω・)つ旦';

// 计时器变量
let timer = null;
let isAway = false;

// 页面可见性变化事件
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        // 页面被隐藏
        isAway = true;
        document.title = awayTitle;
    } else {
        // 页面重新可见
        isAway = false;
        document.title = backTitle;
        setTimeout(function() {
            document.title = originalTitle;
        }, 3000);
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    }
});