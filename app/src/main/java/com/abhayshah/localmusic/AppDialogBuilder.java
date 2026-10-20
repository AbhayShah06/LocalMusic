package com.abhayshah.localmusic;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

final class AppDialogBuilder extends AlertDialog.Builder {
    private static final java.util.List<java.lang.ref.WeakReference<AlertDialog>> dialogs=new java.util.ArrayList<>();
    AppDialogBuilder(Context context){super(context);}
    @Override public AlertDialog create(){
        AlertDialog dialog=super.create();dialogs.removeIf(ref->{AlertDialog existing=ref.get();return existing==null||!existing.isShowing();});dialogs.add(new java.lang.ref.WeakReference<>(dialog));dialog.setOnShowListener(ignored->{
            View content=dialog.findViewById(android.R.id.content);if(content==null||!(content.getParent() instanceof android.view.ViewGroup))return;
            android.view.ViewGroup parent=(android.view.ViewGroup)content.getParent();if(parent instanceof Guard)return;int at=parent.indexOfChild(content);android.view.ViewGroup.LayoutParams params=content.getLayoutParams();parent.removeView(content);Guard guard=new Guard(dialog.getContext());guard.addView(content,new FrameLayout.LayoutParams(-1,-1));parent.addView(guard,at,params);
        });return dialog;
    }
    static void dismissAll(Context context){for(java.lang.ref.WeakReference<AlertDialog> ref:new java.util.ArrayList<>(dialogs)){AlertDialog dialog=ref.get();if(dialog!=null&&dialog.isShowing()&&WindowTapGuard.owner(dialog.getContext())==WindowTapGuard.owner(context))dialog.dismiss();}}
    private static final class Guard extends FrameLayout {
        private boolean ignore;private float x,y;
        Guard(Context context){super(context);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){ignore=WindowTapGuard.ignoreDown(getContext(),getRootView(),event);x=event.getRawX();y=event.getRawY();}
            if(WindowTapGuard.moved(getContext(),x,y,event))ignore=false;
            WindowTapGuard.recordUp(getContext(),getRootView(),event);
            if(ignore&&event.getActionMasked()==MotionEvent.ACTION_UP){ignore=false;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)ignore=false;
            return super.dispatchTouchEvent(event);
        }
    }
}
