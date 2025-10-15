
package com.socket.flipscreenmanager;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final String TAG = "ExternalWindow";
    // 外屏默认 displayId（多数设备为 1，部分设备为 2，可根据实际调整）
    private static final int EXTERNAL_DISPLAY_ID = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private WindowManager mExternalWindowManager;
    private View mExternalView;
    private Context mExternalWindowContext;
    Button createBtn;
    Button createBtn2;
    Button removeBtn;
    private boolean isWindowCreated = false; // 记录窗口是否已创建

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 先申请悬浮窗权限（Android 6.0+ 必需）
        if (!hasOverlayPermission()) {
            requestOverlayPermission();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // 用户之前拒绝过权限
                Toast.makeText(MainActivity.this,"请打开通知权限避免软件被清除后台",Toast.LENGTH_LONG).show();
            } else {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
        // 关键：启动前台服务，创建常驻通知
        startKeepAliveService();

        // 注册屏幕解锁广播
        registerScreenUnlockReceiver();

        // 2. 主屏按钮：一键创建外屏窗口（绕过 Display 获取）
        createBtn = findViewById(R.id.btn_create_external_window);
        createBtn.setOnClickListener(v -> createExternalWindow());

        // 2. 主屏按钮：一键创建外屏窗口（绕过 Display 获取）
        createBtn2 = findViewById(R.id.btn_create_wallpaper_window);
        createBtn2.setOnClickListener(v -> createWallPaperWindow());

        // 3. 主屏按钮：移除外屏窗口
        removeBtn = findViewById(R.id.btn_remove_external_window);
        removeBtn.setOnClickListener(v -> removeExternalWindow());
    }

    /**
     * 核心方法：绕过 Display 获取，直接创建外屏窗口
     */
    private void createExternalWindow() {
        try {
            // 步骤1：获取 DisplayManager（用于构建外屏 Display 对象）
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

            // 步骤2：直接通过外屏 displayId 构建 Display 对象（绕过遍历获取）
            // 若外屏未被识别，display 会为 null，需捕获异常
            android.view.Display externalDisplay = displayManager.getDisplay(EXTERNAL_DISPLAY_ID);
            if (externalDisplay == null) {
                Toast.makeText(this, "外屏未就绪，请先点亮外屏", Toast.LENGTH_LONG).show();
                // 可选：延迟2秒重试（给用户点亮外屏的时间）
                new Handler(Looper.getMainLooper()).postDelayed(this::createExternalWindow, 2000);
                return;
            }

            // 步骤3：创建外屏专属窗口上下文（UiContext）
            // 按文档要求：先绑定外屏 Display，再创建 TYPE_APPLICATION_OVERLAY 类型上下文
            mExternalWindowContext = createDisplayContext(externalDisplay)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);

            // 步骤4：获取外屏的 WindowManager（用于添加窗口）
            mExternalWindowManager = mExternalWindowContext.getSystemService(WindowManager.class);

            // 步骤5：加载外屏窗口布局（外屏显示的内容）
            mExternalView = LayoutInflater.from(mExternalWindowContext)
                    .inflate(R.layout.external_window_layout, null);

            // 关键：设置视图不拦截任何按键事件
            mExternalView.setFocusable(false);
            mExternalView.setFocusableInTouchMode(false);
            mExternalView.setOnKeyListener((v, keyCode, event) -> {
                // 所有按键事件都返回false，表示不处理，让事件继续传递
                return false;
            });

            // 步骤6：配置外屏窗口参数（按文档要求设置类型和亮屏标识）
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
            // 窗口类型必须与 createWindowContext 的类型一致（TYPE_APPLICATION_OVERLAY）
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            // 文档要求：添加亮屏标识，确保外屏被点亮
            windowParams.flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            // 额外：保持外屏常亮（避免休眠）
            windowParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // 窗口位置：居中显示
            windowParams.gravity = Gravity.CENTER;
            // 窗口大小：匹配外屏
            // 核心：设置固定分辨率为382x720
//            windowParams.width = 382;  // 宽度
//            windowParams.height = 720; // 高度

            windowParams.width = 0;  // 宽度
            windowParams.height = 0; // 高度
//            窗口不接收任何触摸事件。
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
//            窗口不获取焦点，触摸事件会传递到下层窗口
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            // 窗口格式：确保透明背景正常显示（可选）
            windowParams.format = PixelFormat.RGBA_8888;

            // 步骤7：添加外屏窗口（按文档要求，使用外屏的 WindowManager）
            mExternalWindowManager.addView(mExternalView, windowParams);
            Toast.makeText(this, "外屏窗口创建成功", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "外屏窗口已添加，displayId=" + EXTERNAL_DISPLAY_ID);
            isWindowCreated = true; // 更新状态：窗口已创建
        } catch (Exception e) {
            // 捕获常见异常（如外屏未亮、权限不足）
            Log.e(TAG, "创建外屏窗口失败：" + e.getMessage());
            Toast.makeText(this, "创建失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        createBtn.setVisibility(View.INVISIBLE);
        createBtn2.setVisibility(View.INVISIBLE);

    }


    /**
     * 核心方法：绕过 Display 获取，直接创建外屏窗口
     */
    private void createWallPaperWindow() {
        try {
            // 步骤1：获取 DisplayManager（用于构建外屏 Display 对象）
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

            // 步骤2：直接通过外屏 displayId 构建 Display 对象（绕过遍历获取）
            // 若外屏未被识别，display 会为 null，需捕获异常
            android.view.Display externalDisplay = displayManager.getDisplay(EXTERNAL_DISPLAY_ID);
            if (externalDisplay == null) {
                Toast.makeText(this, "外屏未就绪，请先点亮外屏", Toast.LENGTH_LONG).show();
                // 可选：延迟2秒重试（给用户点亮外屏的时间）
                new Handler(Looper.getMainLooper()).postDelayed(this::createExternalWindow, 2000);
                return;
            }

            // 步骤3：创建外屏专属窗口上下文（UiContext）
            // 按文档要求：先绑定外屏 Display，再创建 TYPE_APPLICATION_OVERLAY 类型上下文
            mExternalWindowContext = createDisplayContext(externalDisplay)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);

            // 步骤4：获取外屏的 WindowManager（用于添加窗口）
            mExternalWindowManager = mExternalWindowContext.getSystemService(WindowManager.class);

            // 步骤5：加载外屏窗口布局（外屏显示的内容）
            mExternalView = LayoutInflater.from(mExternalWindowContext)
                    .inflate(R.layout.external_wallpaper_layout, null);

            // 步骤6：配置外屏窗口参数（按文档要求设置类型和亮屏标识）
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
            // 窗口类型必须与 createWindowContext 的类型一致（TYPE_APPLICATION_OVERLAY）
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            // 文档要求：添加亮屏标识，确保外屏被点亮
            windowParams.flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            // 额外：保持外屏常亮（避免休眠）
            windowParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // 窗口位置：居中显示
            windowParams.gravity = Gravity.CENTER;
            // 窗口大小：匹配外屏
            // 核心：设置固定分辨率为382x720
            windowParams.width = 382;  // 宽度
            windowParams.height = 720; // 高度

            // 窗口格式：确保透明背景正常显示（可选）
            windowParams.format = PixelFormat.RGBA_8888;

            // 步骤7：添加外屏窗口（按文档要求，使用外屏的 WindowManager）
            mExternalWindowManager.addView(mExternalView, windowParams);
            Toast.makeText(this, "外屏窗口创建成功", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "外屏窗口已添加，displayId=" + EXTERNAL_DISPLAY_ID);
            isWindowCreated = true; // 更新状态：窗口已创建
        } catch (Exception e) {
            // 捕获常见异常（如外屏未亮、权限不足）
            Log.e(TAG, "创建外屏窗口失败：" + e.getMessage());
            Toast.makeText(this, "创建失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        createBtn.setVisibility(View.INVISIBLE);
        createBtn2.setVisibility(View.INVISIBLE);
    }
    /**
     * 移除外屏窗口（避免内存泄漏）
     */
// 替换原 removeExternalWindow() 方法
    private void removeExternalWindow() {
        // 双重检查：确保窗口管理器和视图不为 null
        if (mExternalWindowManager != null && mExternalView != null) {

            try {
                // 关键：使用 removeViewImmediate() 强制同步移除（避免异步延迟）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mExternalWindowManager.removeViewImmediate(mExternalView);
                } else {
                    mExternalWindowManager.removeView(mExternalView);
                }
                Log.d(TAG, "外屏窗口已从系统中移除");
            } catch (IllegalArgumentException e) {
                // 捕获"视图未添加"的异常（避免重复移除导致崩溃）
                Log.w(TAG, "窗口未添加到管理器中，无需移除：" + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "移除窗口时发生异常：" + e.getMessage());
            } finally {
                // 强制清空所有引用（关键：避免内存泄漏和残留）
                mExternalView = null;
                mExternalWindowManager = null;
                mExternalWindowContext = null;
            }
            Toast.makeText(this, "外屏窗口已移除", Toast.LENGTH_SHORT).show();
        isWindowCreated = false; // 更新状态：窗口已创建
        } else {
            Toast.makeText(this, "外屏窗口未创建或已移除", Toast.LENGTH_SHORT).show();
        }
        createBtn.setVisibility(View.VISIBLE);
        createBtn2.setVisibility(View.VISIBLE);
    }

    // 补充：在 onPause() 中主动移除窗口（应对Activity生命周期变化）
    @Override
    protected void onPause() {
        super.onPause();
        // 若应用进入后台，主动移除外屏窗口（避免系统限制导致无法移除）
        if (mExternalView != null) {
//            removeExternalWindow();
        }
    }


    /**
     * 辅助方法：检查是否有悬浮窗权限
     */
    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        // Android 6.0 以下默认有权限
        return true;
    }


    /**
     * 辅助方法：请求悬浮窗权限
     */
    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            startActivityForResult(
                    new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName())),
                    100
            );
        }
    }

    /**
     * 权限请求结果回调
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && hasOverlayPermission()) {
            Toast.makeText(this, "悬浮窗权限已授予，可创建外屏窗口", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "未授予悬浮窗权限，无法创建外屏窗口", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 注册屏幕解锁广播接收器
     */
    private void registerScreenUnlockReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT); // 设备解锁事件
        filter.addAction(Intent.ACTION_SCREEN_ON); // 屏幕点亮事件（可选）
        registerReceiver(screenUnlockReceiver, filter);
    }

    // 屏幕解锁广播接收器
    private BroadcastReceiver screenUnlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                // 设备已解锁，恢复窗口（如果之前已创建）
                Log.d(TAG, "设备已解锁，尝试恢复外屏窗口");
                if (isWindowCreated) {
                    // 延迟1秒恢复，避免系统解锁流程未完成导致失败
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        createExternalWindow();
                    }, 200);
                    Log.d(TAG, "设备已解锁，尝试恢复外屏窗口2");
                }
            }
        }
    };

    /**
     * 页面销毁时移除外屏窗口（避免内存泄漏）
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeExternalWindow();
    }

    /**
     * 启动前台服务（避免被系统杀掉）
     */
    private void startKeepAliveService() {
        Intent serviceIntent = new Intent(this, KeepAliveForegroundService.class);
        // Android 8.0+ 启动前台服务必须用 startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            // 低版本直接启动服务
            startService(serviceIntent);
        }
    }

}
