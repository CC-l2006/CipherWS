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
	}
	
	.box .avatar {
		border-radius: 50%;
		width: 100px;
		height: 100px;
	}
	
	.box .text {
		color: #fff;
		font-size: 60px;
		text-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
	}
	
	.avatar {
	    transition: transform 0.3s ease;
	}
	
	.avatar:hover{
	    transform: scale(1.1);
	}
</style>