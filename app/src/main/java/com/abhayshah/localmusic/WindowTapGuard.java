package com.abhayshah.localmusic;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.*;
import java.util.WeakHashMap;

/** A second tap at the same point must not hit a different control in a newly opened window. */
final class WindowTapGuard {
    private static final WeakHashMap<Context,Tap> LAST=new WeakHashMap<>();
    private static Context owner(Context context){while(context instanceof ContextWrapper&&!(context instanceof android.app.Activity)){Context base=((ContextWrapper)context).getBaseContext();if(base==context)break;context=base;}return context;}
    static boolean ignoreDown(Context context,View root,MotionEvent event){
        Tap tap=LAST.get(owner(context));if(tap==null||tap.root.get()==root)return false;
        long elapsed=event.getEventTime()-tap.time;float distance=Math.max(12*context.getResources().getDisplayMetrics().density,ViewConfiguration.get(context).getScaledTouchSlop()*2);
        return elapsed>=0&&elapsed<=ViewConfiguration.getDoubleTapTimeout()&&Math.hypot(event.getRawX()-tap.x,event.getRawY()-tap.y)<=distance;
    }
    static void recordUp(Context context,View root,MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_UP)LAST.put(owner(context),new Tap(root,event));}
    private static final class Tap{final java.lang.ref.WeakReference<View> root;final long time;final float x,y;Tap(View root,MotionEvent event){this.root=new java.lang.ref.WeakReference<>(root);time=event.getEventTime();x=event.getRawX();y=event.getRawY();}}
    private WindowTapGuard(){}
}
