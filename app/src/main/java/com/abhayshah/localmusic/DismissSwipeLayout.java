package com.abhayshah.localmusic;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Dismiss a sheet horizontally, while preserving scrolling, seeking and text input. */
final class DismissSwipeLayout extends FrameLayout {
    private final Runnable dismiss;private float x,y;private boolean blocked,dragging,closing;
    private View moving;
    DismissSwipeLayout(Context context,Runnable dismiss){super(context);this.dismiss=dismiss;setContentDescription("Dismiss panel");}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(closing)return true;
        if(action==MotionEvent.ACTION_DOWN){moving=getRootView();moving.animate().cancel();moving.setTranslationX(0);x=event.getRawX();y=event.getRawY();dragging=false;blocked=protectedTarget(this,event.getX(),event.getY());}
        if(event.getPointerCount()!=1){blocked=true;if(dragging){dragging=false;restore();return true;}}
        float dx=event.getRawX()-x,dy=event.getRawY()-y;
        // Observe the complete gesture even when a child list disallows interception.
        if(!blocked&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)
                &&Math.abs(dx)>dp(28)&&Math.abs(dx)>Math.abs(dy)*1.8f){
            if(!dragging){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();dragging=true;}
        }
        if(dragging){
            moving.setTranslationX(dx);
            if(action==MotionEvent.ACTION_UP){
                dragging=false;
                if(Math.abs(dx)>Math.max(dp(64),getWidth()*.2f)){
                    closing=true;
                    moving.animate().translationX(Math.copySign(Math.max(getWidth(),dp(320)),dx)).setDuration(150).withEndAction(()->{dismiss.run();closing=false;}).start();
                }else restore();
            }else if(action==MotionEvent.ACTION_CANCEL){dragging=false;restore();}
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
    @Override public boolean performClick(){return super.performClick();}
    private void restore(){if(moving!=null)moving.animate().translationX(0).setDuration(120).start();}
    @Override protected void onDetachedFromWindow(){if(moving!=null){moving.animate().cancel();moving.setTranslationX(0);}super.onDetachedFromWindow();}
    private boolean protectedTarget(View view,float x,float y){
        if(view instanceof SeekBar||view instanceof EditText||view instanceof HorizontalScrollView||view instanceof CompoundButton||view.canScrollHorizontally(-1)||view.canScrollHorizontally(1))return true;
        if(view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith("Reorder "))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--){View child=group.getChildAt(i);float cx=x+view.getScrollX()-child.getLeft(),cy=y+view.getScrollY()-child.getTop();if(child.getVisibility()==VISIBLE&&cx>=0&&cy>=0&&cx<child.getWidth()&&cy<child.getHeight())return protectedTarget(child,cx,cy);}}
        return false;
    }
    private float dp(int value){return value*getResources().getDisplayMetrics().density;}
}
