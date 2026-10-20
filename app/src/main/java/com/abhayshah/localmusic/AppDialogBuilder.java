package com.abhayshah.localmusic;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

final class AppDialogBuilder extends AlertDialog.Builder {
    AppDialogBuilder(Context context){super(context);}
    @Override public AlertDialog create(){
        AlertDialog dialog=super.create();dialog.setOnShowListener(ignored->{
            View content=dialog.findViewById(android.R.id.content);if(content==null||!(content.getParent() instanceof android.view.ViewGroup))return;
            android.view.ViewGroup parent=(android.view.ViewGroup)content.getParent();if(parent instanceof Guard)return;int at=parent.indexOfChild(content);android.view.ViewGroup.LayoutParams params=content.getLayoutParams();parent.removeView(content);Guard guard=new Guard(dialog.getContext());guard.addView(content,new FrameLayout.LayoutParams(-1,-1));parent.addView(guard,at,params);
        });return dialog;
    }
    private static final class Guard extends FrameLayout {
        private boolean ignore;
        Guard(Context context){super(context);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)ignore=WindowTapGuard.ignoreDown(getContext(),getRootView(),event);
            WindowTapGuard.recordUp(getContext(),getRootView(),event);
            if(ignore){if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)ignore=false;return true;}
            return super.dispatchTouchEvent(event);
        }
    }
}
