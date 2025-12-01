package com.example.b07project;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

public class TopMover {
    private Activity activity;

    public TopMover(Activity activity) {
        this.activity = activity;
    }

   public void adjustTop(){
        int statusBarHeight = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if(resourceId > 0){
            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);

        }

        View rootView = activity.findViewById(android.R.id.content);
        if(rootView instanceof ViewGroup){
            ViewGroup rootGroup = (ViewGroup) rootView;
            for(int i = 0; i < rootGroup.getChildCount(); i++){
                View child = rootGroup.getChildAt(i);

                if(child.getY() <= 0){
                    child.setPadding(child.getPaddingLeft(),
                            child.getPaddingTop() + statusBarHeight,
                            child.getPaddingRight(),
                            child.getPaddingBottom());
                }
            }
        }
   }
    public void adjustTopColored(String color){
        int statusBarHeight = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if(resourceId > 0){
            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);

        }

        View rootView = activity.findViewById(android.R.id.content);
        if(rootView instanceof ViewGroup){
            ViewGroup rootGroup = (ViewGroup) rootView;
            for(int i = 0; i < rootGroup.getChildCount(); i++){
                View child = rootGroup.getChildAt(i);

                if(child.getY() <= 0){
                    child.setPadding(child.getPaddingLeft(),
                            child.getPaddingTop() + statusBarHeight,
                            child.getPaddingRight(),
                            child.getPaddingBottom());
                    child.setBackgroundColor(Color.parseColor(color));
                }
            }
        }
    }

}