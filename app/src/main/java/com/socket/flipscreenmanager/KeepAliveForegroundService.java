package com.socket.flipscreenmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class KeepAliveForegroundService extends Service {
    private static final String TAG = "KeepAliveService";
    private static final String NOTIFICATION_CHANNEL_ID = "keep_alive_channel_01"; // 确保ID唯一且不为空
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "前台服务创建，启动常驻通知");
        createNotificationChannel();
        Notification notification = buildPersistentNotification();
        // 关键：必须在服务创建后5秒内调用startForeground，否则会报ANR
        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * 修复通知渠道配置，确保通知能正常显示
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 渠道名称和描述需清晰，避免被系统判定为无效通知
            CharSequence channelName = "外屏管理服务";
            String channelDescription = "用于保持外屏窗口稳定运行，点击可返回应用";

            // 重要性设为DEFAULT，确保通知能在状态栏显示（LOW可能被隐藏）
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    channelName,
                    importance
            );
            channel.setDescription(channelDescription);
            channel.setSound(null, null); // 关闭声音
            channel.enableVibration(false); // 关闭震动
            channel.setShowBadge(false); // 不在 launcher 上显示角标

            // 注册渠道
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                try{
                    // 先删除旧渠道（如果有），确保新配置生效
                    notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
                    notificationManager.createNotificationChannel(channel);
                }
                catch (Exception e){
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * 构建能正常显示的自定义通知
     */
    private Notification buildPersistentNotification() {
        // 点击通知跳回应用
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        // 构建通知时明确指定渠道ID（Android 8.0+必需）
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
        )
                .setSmallIcon(R.mipmap.ic_launcher) // 必须设置小图标（不能用透明图标）
                .setContentTitle("外屏窗口服务运行中") // 标题不能为空
                .setContentText("点击返回应用控制界面") // 内容不能为空
                .setContentIntent(pendingIntent)
                .setOngoing(true) // 常驻，不可手动清除
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 优先级设为DEFAULT
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC); // 锁屏时可见（可选）

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true); // 停止前台服务并移除通知
    }
}
