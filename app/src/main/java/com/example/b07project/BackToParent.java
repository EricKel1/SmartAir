package com.example.b07project;

import static androidx.core.content.ContextCompat.startActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

public class BackToParent extends BackTo {
    void backTo(Object o){
        Context context = (Context) o;
        Intent intent = new Intent(context, ParentDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        if(context instanceof Activity){
            ((Activity) context).finish();
        }

    }
    void backTo(Context context, Class<?> previousActivity) {
        if (context == null || previousActivity == null) return;

        Intent intent = new Intent(context, previousActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
    }
}
