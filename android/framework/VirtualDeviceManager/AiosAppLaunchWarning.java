package com.android.virtualdevicemanager;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.fragment.app.FragmentActivity;

/** AIOS standard handshake: block native input, ask, then hand over before replaying launch. */
final class AiosAppLaunchWarning {
    final FragmentActivity activity;
    final IntentSender target;
    final String targetPackage, promptId=java.util.UUID.randomUUID().toString();
    final Handler handler = new Handler(Looper.getMainLooper());
    AlertDialog dialog;
    boolean resolved, ready;
    String taskId="";
    int sequence;
    AiosAppLaunchWarning(FragmentActivity activity, IntentSender target, String pkg) { this.activity=activity; this.target=target; this.targetPackage=pkg; }
    void send(String command, ResultReceiver reply) {
        ResultReceiver remote=null;
        if(reply!=null){Parcel parcel=Parcel.obtain();reply.writeToParcel(parcel,0);parcel.setDataPosition(0);remote=ResultReceiver.CREATOR.createFromParcel(parcel);parcel.recycle();}
        activity.sendBroadcast(new Intent("os.aios.agent.SESSION_COMMAND")
                .setComponent(new ComponentName("os.aios.agent","os.aios.agent.SessionCommandReceiver"))
                .putExtra("task_id",taskId).putExtra("package",targetPackage).putExtra("prompt_id",promptId).putExtra("command",command).putExtra("reply",remote));
    }
    String label(){try{return activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(targetPackage,0)).toString();}catch(Exception e){return targetPackage;}}
    void show() {
        dialog=new AlertDialog.Builder(activity)
                .setTitle("APP 在后台被使用：「"+label()+"」")
                .setMessage("正在暂停新的操作，请稍候…")
                .setNegativeButton("继续后台任务",null)
                .setPositiveButton("抢占并打开",null).create();
        dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
            if(resolved)return;resolved=true;AiosAppLaunchWarning.this.send("cancel_prompt",null);dialog.dismiss();activity.finish();
        });
        requestPrompt();
    }
    void takeOver() {
            final int request=++sequence;
            if(!ready||resolved)return;resolved=true;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            dialog.setMessage("正在交还应用控制权…");
            send("takeover",new ResultReceiver(handler){protected void onReceiveResult(int code,Bundle b){
                if(activity.isFinishing()||request!=sequence)return;
                if(code==0){dialog.dismiss();AutomatedAppLaunchWarningActivityFragment.startIntentSender(activity,target);activity.finish();}
                else failure("接管未完成，请重新检查或返回 AIOS Control。");
            }});
            handler.postDelayed(()->{if(request==sequence&&!activity.isFinishing())failure("接管未确认，请返回AIOS Control检查。");},10000);
    }
    void requestPrompt() {
        final int request=++sequence;
        resolved=false;ready=false;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        send("prompt",new ResultReceiver(handler){protected void onReceiveResult(int code,Bundle b){
            if(activity.isFinishing()||resolved||request!=sequence)return;
            if(code!=0){failure("当前任务状态已变化，请返回AIOS Control检查。");return;}
            ready=true;taskId=b.getString("task_id", "");
            if(b.getBoolean("permission_handoff") && b.getBoolean("control_released")) {
                resolved=true;dialog.dismiss();
                AutomatedAppLaunchWarningActivityFragment.startIntentSender(activity,target);activity.finish();return;
            }
            if(!"confirming".equals(b.getString("state"))) {
                dialog.setTitle("打开「"+label()+"」");
                dialog.setMessage("AI 任务已暂停，应用控制权已释放。你可以直接打开应用。");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("打开应用");
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("返回");
            } else { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("继续后台任务");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("抢占并打开");dialog.setTitle("APP 在后台被使用：「"+label()+"」");dialog.setMessage("是否抢占到主屏？抢占后，后台任务将暂停。\n已完成的操作会保留。\n\n你可以用完后返回AIOS Control，手动继续任务。"); }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->takeOver());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{resolved=true;AiosAppLaunchWarning.this.send("cancel_prompt",null);dialog.dismiss();activity.finish();});
        }});
        handler.postDelayed(()->{if(request==sequence&&!ready&&!resolved&&!activity.isFinishing())failure("暂时无法确认任务已暂停，请返回AIOS Control检查。");},10000);
    }
    void failure(String message){send("abandon_prompt",null);resolved=true;dialog.setMessage(message);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("重新检查");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->requestPrompt());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("返回");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{dialog.dismiss();activity.finish();});}
    void close(){++sequence;handler.removeCallbacksAndMessages(null);if(!resolved)send("abandon_prompt",null);if(dialog!=null)dialog.dismiss();}
}
