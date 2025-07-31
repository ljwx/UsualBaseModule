发送和订阅事件
```kotlin
// 在 ViewModel 或 Activity 中

// --- 发送事件 ---
fun performLogin() {
    val user = User("id-456", "Gemini V3")
    // 发送带数据的事件，类型由 data 参数自动推断
    FlowEventBus.getInstance().post(EventKeys.LOGIN_SUCCESS, user)
}

fun performLogout() {
    // 发送无数据事件，调用简洁版本
    FlowEventBus.getInstance().post(EventKeys.LOGOUT)
}

fun updateProfile() {
    val user = User("id-456", "Gemini V3 Pro")
    // 发送粘性事件
    FlowEventBus.getInstance().postSticky(EventKeys.PROFILE_UPDATED, user)
}


// --- 订阅事件 ---
fun setupSubscriptions(scope: CoroutineScope) { // e.g., lifecycleScope

    // 订阅带数据的事件，必须在泛型中指定期望的类型
    FlowEventBus.getInstance().subscribe<User>(EventKeys.LOGIN_SUCCESS, scope) { user ->
        // user 是安全的 User 类型
        println("用户登录: ${user.name}")
    }

    // 订阅无数据的事件，使用简洁版本
    FlowEventBus.getInstance().subscribe(EventKeys.LOGOUT, scope) {
        println("用户已登出")
    }

    // 订阅粘性事件
    FlowEventBus.getInstance().subscribeSticky<User>(EventKeys.PROFILE_UPDATED, scope) { user ->
        println("收到粘性事件，用户信息已更新: ${user.name}")
    }
}
```