package com.abhayshah.localmusic;

import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.widget.FrameLayout;
import java.util.function.IntConsumer;

/** Horizontal tab gestures leave vertical library scrolling and system back gestures alone. */
public final class LibrarySwipeLayout extends FrameLayout {
    private float startX,startY;private boolean horizontal,enabled;private VelocityTracker velocity;private IntConsumer change;
    public LibrarySwipeLayout(Context context,AttributeSet attrs){super(context,attrs);}
    void configure(boolean enabled,IntConsumer change){this.enabled=enabled;this.change=change;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(!enabled||event.getPointerCount()!=1)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();horizontal=false;if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(event);return false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&startX>dp(24)){
            float dx=event.getX()-startX,dy=event.getY()-startY;
            if(Math.abs(dx)>dp(24)&&Math.abs(dx)>Math.abs(dy)*1.6f){horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!enabled||!horizontal)return super.onTouchEvent(event);
        if(velocity!=null)velocity.addMovement(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=event.getX()-startX;float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getXVelocity();}
            if(change!=null&&(Math.abs(dx)>dp(56)||Math.abs(dx)>dp(24)&&Math.abs(speed)>dp(500)))change.accept(dx<0?1:-1);
            finishGesture();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){finishGesture();return true;}return true;
    }
    private void finishGesture(){horizontal=false;getParent().requestDisallowInterceptTouchEvent(false);if(velocity!=null){velocity.recycle();velocity=null;}}
    private float dp(int value){return value*getResources().getDisplayMetrics().density;}
}
