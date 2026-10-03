<template>
	<div class="box">
	    <img class="avatar" src="/tab-icon/icon.jpg" alt="CipherWS" />
	    <span class="text">CipherWS</span>
	</div>
</template>

<script>
</script>

<style>
	.box {
		height: 150px;
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 50px;
		user-select: none;
		/* 移动端兜底：内容不撑破屏幕。
		   实测 375px 宽下原来是 423px，会把整个文档撑出横向滚动。 */
		max-width: 100%;
		padding: 0 16px;
		box-sizing: border-box;
	}

	.box .avatar {
		border-radius: 50%;
		width: 100px;
		height: 100px;
		flex: none;
	}

	.box .text {
		color: #fff;
		font-size: 60px;
		text-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
		/* 窄屏时允许收缩，不把容器顶宽 */
		min-width: 0;
	}

	.avatar {
	    transition: transform 0.3s ease;
	}

	.avatar:hover{
	    transform: scale(1.1);
	}

	/* ===== 手机竖屏：缩小头像与字号，压低头部高度 ===== */
	@media (max-width: 600px) {
		.box {
			height: 104px;
			gap: 16px;
		}

		.box .avatar {
			width: 60px;
			height: 60px;
		}

		.box .text {
			/* 60px 在 375px 宽下独占 273px，缩到 30px 才能和头像排下 */
			font-size: 30px;
		}
	}

	/* 更窄的机型（如 320px）再收一档 */
	@media (max-width: 380px) {
		.box {
			gap: 12px;
		}

		.box .avatar {
			width: 50px;
			height: 50px;
		}

		.box .text {
			font-size: 26px;
		}
	}
</style>
