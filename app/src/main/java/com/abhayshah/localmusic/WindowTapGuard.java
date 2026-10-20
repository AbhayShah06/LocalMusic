package com.abhayshah.localmusic;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.*;
import java.util.WeakHashMap;

/** A second tap at the same point must not hit a different control in a newly opened window. */
final class WindowTapGuard {
    private static final WeakHashMap<Context,State> STATES=new WeakHashMap<>();
    static Context owner(Context context){while(context instanceof ContextWrapper&&!(context instanceof android.app.Activity)){Context base=((ContextWrapper)context).getBaseContext();if(base==context)break;context=base;}return context;}
    static boolean ignoreDown(Context context,View root,MotionEvent event){
        State state=STATES.get(owner(context));if(state==null){state=new State();STATES.put(owner(context),state);}Tap tap=state.last;
        state.root=new java.lang.ref.WeakReference<>(root);state.x=event.getRawX();state.y=event.getRawY();state.time=event.getEventTime();
        if(tap==null||tap.root.get()==root)return false;
        long elapsed=event.getEventTime()-tap.time;float distance=Math.max(12*context.getResources().getDisplayMetrics().density,ViewConfiguration.get(context).getScaledTouchSlop()*2);
        return elapsed>=0&&elapsed<=ViewConfiguration.getDoubleTapTimeout()&&Math.hypot(event.getRawX()-tap.x,event.getRawY()-tap.y)<=distance;
    }
    static boolean moved(Context context,float x,float y,MotionEvent event){return Math.hypot(event.getRawX()-x,event.getRawY()-y)>ViewConfiguration.get(context).getScaledTouchSlop();}
    static void recordUp(Context context,View root,MotionEvent event){if(event.getActionMasked()!=MotionEvent.ACTION_UP)return;State state=STATES.get(owner(context));if(state==null||state.root==null||state.root.get()!=root)return;state.last=!moved(context,state.x,state.y,event)&&event.getEventTime()-state.time<ViewConfiguration.getLongPressTimeout()?new Tap(root,event):null;}
    private static final class State{Tap last;java.lang.ref.WeakReference<View> root;float x,y;long time;}
    private static final class Tap{final java.lang.ref.WeakReference<View> root;final long time;final float x,y;Tap(View root,MotionEvent event){this.root=new java.lang.ref.WeakReference<>(root);time=event.getEventTime();x=event.getRawX();y=event.getRawY();}}
    private WindowTapGuard(){}
}
