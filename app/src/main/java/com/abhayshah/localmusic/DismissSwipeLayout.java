package com.abhayshah.localmusic;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Dismiss a sheet horizontally, while preserving scrolling, seeking and text input. */
final class DismissSwipeLayout extends FrameLayout {
    private final Runnable dismiss;private float x,y;private boolean blocked,dragging;
    DismissSwipeLayout(Context context,Runnable dismiss){super(context);this.dismiss=dismiss;setContentDescription("Dismiss panel");}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getPointerCount()!=1)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){x=event.getX();y=event.getY();dragging=false;blocked=protectedTarget(this,x,y);return false;}
        if(!blocked&&event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getX()-x,dy=event.getY()-y;if(Math.abs(dx)>dp(28)&&Math.abs(dx)>Math.abs(dy)*1.8f){dragging=true;return true;}}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
        if(!dragging&&!blocked&&event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getX()-x,dy=event.getY()-y;dragging=Math.abs(dx)>dp(28)&&Math.abs(dx)>Math.abs(dy)*1.8f;}
        if(!dragging){if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){dragging=false;if(Math.abs(event.getX()-x)>dp(64))dismiss.run();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;return true;
    }
    @Override public boolean performClick(){return super.performClick();}
    private boolean protectedTarget(View view,float x,float y){
        if(view instanceof SeekBar||view instanceof EditText||view instanceof HorizontalScrollView||view instanceof CompoundButton)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--){View child=group.getChildAt(i);float cx=x+view.getScrollX()-child.getLeft(),cy=y+view.getScrollY()-child.getTop();if(child.getVisibility()==VISIBLE&&cx>=0&&cy>=0&&cx<child.getWidth()&&cy<child.getHeight())return protectedTarget(child,cx,cy);}}
        return false;
    }
    private float dp(int value){return value*getResources().getDisplayMetrics().density;}
}
