
package com.socket.flipscreenmanager;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String TAG = "ExternalWindow";
    // 外屏默认 displayId（多数设备为 1，部分设备为 2，可根据实际调整）
    private static final int EXTERNAL_DISPLAY_ID = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final int OPEN_GALLERY_REQUEST_CODE = 1;
    private static final int TAKE_PHOTO_REQUEST_CODE = 2;
    private WindowManager mExternalWindowManager;
    private View mExternalView;
    private Context mExternalWindowContext;
    Button createBtn;
    Button createBtnnotouch;
    Button createBtn2;
    Button btn_choose_wallpaper;
    Button removeBtn;
    private ImageView choosewallpaper;
    private ImageView wallpaper;
    private boolean isWindowCreated = false; // 记录窗口是否已创建

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 申请悬浮窗权限
        if (!hasOverlayPermission()) {
            requestOverlayPermission();
            return;
        }
        // 申请通知权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // 用户之前拒绝过权限
                Toast.makeText(MainActivity.this,"请打开通知权限避免软件被清除后台",Toast.LENGTH_LONG).show();
            } else {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }

        // 申请相册选取图片的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上：使用细化的图片权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
                    // 用户之前拒绝过权限，显示说明
                    Toast.makeText(MainActivity.this, "需要相册权限以选取图片", Toast.LENGTH_LONG).show();
                } else {
                    // 直接请求权限
                    requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 5);
                }
            }
        } else {
            // Android 13以下：使用存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(MainActivity.this, "需要存储权限以访问相册", Toast.LENGTH_LONG).show();
                } else {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 5);
                }
            }
        }

        // 关键：启动前台服务，创建常驻通知
        startKeepAliveService();

        // 注册屏幕解锁广播
        registerScreenUnlockReceiver();

        // 2. 主屏按钮：一键创建外屏窗口
        createBtn = findViewById(R.id.btn_create_external_window);
        createBtn.setOnClickListener(v -> createExternalWindow());

        // 2. 主屏按钮：一键创建外屏窗口无触摸
        createBtnnotouch = findViewById(R.id.btn_create_external_window_notouch);
        createBtnnotouch.setOnClickListener(v -> createExternalWindownotouch());

        // 2. 主屏按钮：一键创建外屏窗口2
        createBtn2 = findViewById(R.id.btn_create_wallpaper_window);
        createBtn2.setOnClickListener(v -> createWallPaperWindow());

        //选择壁纸
        btn_choose_wallpaper = findViewById(R.id.btn_choose_wallpaper);
        btn_choose_wallpaper.setOnClickListener(v -> choosewallpaper());

        // 3. 主屏按钮：移除外屏窗口
        removeBtn = findViewById(R.id.btn_remove_external_window);
        removeBtn.setOnClickListener(v -> removeExternalWindow());

        choosewallpaper = findViewById(R.id.im_choose);
        loadImageFromPrivateDir(choosewallpaper,"img.png");
    }

    private void choosewallpaper() {
        applyPermission();
    }

    /**
     * 申请相册动态权限
     */
    private void applyPermission() {
        //检测权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则申请需要的权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 5);
        }else {
            // 已经申请了权限
            openGallery();
        }
    }
    /**
     * 用户选择是否开启权限操作后的回调；TODO 同意/拒绝
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户同样授权
                openGallery();
            }else {
                // 用户拒绝授权
                Toast.makeText(this, "你拒绝使用存储权限！", Toast.LENGTH_SHORT).show();
                Log.d("HL", "你拒绝使用存储权限！");
            }
        }
    }

    /**
     * 打开相册
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI , "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == 100 && hasOverlayPermission()) {
//            Toast.makeText(this, "悬浮窗权限已授予，可创建外屏窗口", Toast.LENGTH_SHORT).show();
//        } else {
//            Toast.makeText(this, "未授予悬浮窗权限，无法创建外屏窗口", Toast.LENGTH_SHORT).show();
//        }

        if (requestCode == OPEN_GALLERY_REQUEST_CODE) { // 检测请求码
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(data.getData());
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    // TODO 把获取到的图片放到ImageView上
                    choosewallpaper.setImageBitmap(bitmap);
                    savebitmap(bitmap,"img.png");
                    System.out.println("img.png保存成功");
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String  savebitmap(Bitmap bitmap, String fileName) {
        if (bitmap == null || fileName.isEmpty()) return null;

        // 获取应用私有文件目录下的 images 子目录（不存在则创建）
        File imagesDir = new File(getFilesDir(), "images");
        if (!imagesDir.exists()) {
            if (!imagesDir.mkdirs()) { // 创建目录
                Log.e("SaveBitmap", "无法创建目录");
                return null;
            }
        }

        // 创建目标文件
        File targetFile = new File(imagesDir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(targetFile);
            // 压缩保存（格式为 PNG，质量 100%，可根据需要改为 JPEG）
            boolean isSuccess = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            if (isSuccess) {
                Log.d("SaveBitmap", "保存成功：" + targetFile.getAbsolutePath());
                return targetFile.getAbsolutePath();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;

    }

    /**
     * 从应用私有目录加载图片到ImageView
     * @param imageView 要显示图片的ImageView
     * @param fileName 图片文件名（如"img.png"）
     */
    private void loadImageFromPrivateDir(ImageView imageView, String fileName) {
        // 构建图片文件的完整路径
        File imageFile = new File(new File(getFilesDir(), "images"), fileName);

        // 检查文件是否存在
        if (!imageFile.exists()) {
            Log.e("LoadImage", "图片文件不存在：" + imageFile.getAbsolutePath());
            Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        // 方法2：通过Drawable.createFromPath（更简洁，无需手动处理Bitmap）

        Drawable drawable = Drawable.createFromPath(imageFile.getAbsolutePath());
        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        } else {
            Log.e("LoadImage", "无法创建Drawable");
        }

    }

    /**
     * 核心方法：绕过 Display 获取，直接创建外屏窗口
     */
    private void createExternalWindow() {
        try {
            // 获取 DisplayManager（用于构建外屏 Display 对象）
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

            // 直接通过外屏 displayId 构建 Display 对象（绕过遍历获取）
            // 若外屏未被识别，display 会为 null，需捕获异常
            android.view.Display externalDisplay = displayManager.getDisplay(EXTERNAL_DISPLAY_ID);
            if (externalDisplay == null) {
                Toast.makeText(this, "外屏未就绪，请先点亮外屏", Toast.LENGTH_LONG).show();
                // 可选：延迟2秒重试（给用户点亮外屏的时间）
                new Handler(Looper.getMainLooper()).postDelayed(this::createExternalWindow, 2000);
                return;
            }

            // 创建外屏专属窗口上下文（UiContext）
            // 按文档要求：先绑定外屏 Display，再创建 TYPE_APPLICATION_OVERLAY 类型上下文
            mExternalWindowContext = createDisplayContext(externalDisplay)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);

            // 取外屏的 WindowManager（用于添加窗口）
            mExternalWindowManager = mExternalWindowContext.getSystemService(WindowManager.class);

            // 加载外屏窗口布局（外屏显示的内容）
            mExternalView = LayoutInflater.from(mExternalWindowContext)
                    .inflate(R.layout.external_window_layout, null);

            // 关键：设置视图不拦截任何按键事件
            mExternalView.setFocusable(false);
            mExternalView.setFocusableInTouchMode(false);
            mExternalView.setOnKeyListener((v, keyCode, event) -> {
                // 所有按键事件都返回false，表示不处理，让事件继续传递
                Toast.makeText(MainActivity.this,"123",Toast.LENGTH_SHORT).show();
                System.out.println("12345677");
                return false;
            });


            // 配置外屏窗口参数（按文档要求设置类型和亮屏标识）
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
            // 窗口类型必须与 createWindowContext 的类型一致（TYPE_APPLICATION_OVERLAY）
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            // 文档要求：添加亮屏标识，确保外屏被点亮
            windowParams.flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            // 额外：保持外屏常亮（避免休眠）
            windowParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // 窗口位置：居中显示
            windowParams.gravity = Gravity.CENTER;
            // 窗口大小：不可见
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



            // 添加外屏窗口（按文档要求，使用外屏的 WindowManager）
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
        createBtnnotouch.setVisibility(View.INVISIBLE);
        createBtn2.setVisibility(View.INVISIBLE);

    }

    /**
     * 核心方法：绕过 Display 获取，直接创建外屏窗口
     */
    private void createExternalWindownotouch() {
        try {
            // 获取 DisplayManager（用于构建外屏 Display 对象）
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

            // 直接通过外屏 displayId 构建 Display 对象（绕过遍历获取）
            // 若外屏未被识别，display 会为 null，需捕获异常
            android.view.Display externalDisplay = displayManager.getDisplay(EXTERNAL_DISPLAY_ID);
            if (externalDisplay == null) {
                Toast.makeText(this, "外屏未就绪，请先点亮外屏", Toast.LENGTH_LONG).show();
                // 可选：延迟2秒重试（给用户点亮外屏的时间）
                new Handler(Looper.getMainLooper()).postDelayed(this::createExternalWindow, 2000);
                return;
            }

            // 创建外屏专属窗口上下文（UiContext）
            // 按文档要求：先绑定外屏 Display，再创建 TYPE_APPLICATION_OVERLAY 类型上下文
            mExternalWindowContext = createDisplayContext(externalDisplay)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);

            // 取外屏的 WindowManager（用于添加窗口）
            mExternalWindowManager = mExternalWindowContext.getSystemService(WindowManager.class);

            // 加载外屏窗口布局（外屏显示的内容）
            mExternalView = LayoutInflater.from(mExternalWindowContext)
                    .inflate(R.layout.external_wallpaper_layout_notouch, null);

            // 关键：设置视图不拦截任何按键事件
            mExternalView.setFocusable(true);
            mExternalView.setFocusableInTouchMode(true);



            // 配置外屏窗口参数（按文档要求设置类型和亮屏标识）
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
            // 窗口类型必须与 createWindowContext 的类型一致（TYPE_APPLICATION_OVERLAY）
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            // 文档要求：添加亮屏标识，确保外屏被点亮
            windowParams.flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            // 额外：保持外屏常亮（避免休眠）
            windowParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // 窗口位置：居中显示
            windowParams.gravity = Gravity.CENTER;
            // 窗口大小：不可见
            windowParams.width = 1;  // 宽度
            windowParams.height = 1; // 高度
            // 窗口格式：确保透明背景正常显示（可选）
            windowParams.format = PixelFormat.RGBA_8888;

            // 添加外屏窗口（按文档要求，使用外屏的 WindowManager）
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
        createBtnnotouch.setVisibility(View.INVISIBLE);
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

            // 从外屏布局中获取ImageView（必须使用mExternalView.findViewById）
            wallpaper = mExternalView.findViewById(R.id.im_wallpaper);
            // 调用加载方法，传入外屏的ImageView
            loadImageFromPrivateDir(wallpaper, "img.png");

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
        createBtnnotouch.setVisibility(View.INVISIBLE);
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
        createBtnnotouch.setVisibility(View.VISIBLE);
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

//    /**
//     * 权限请求结果回调
//     */
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//    }

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
