package com.lsh.XXRecyclerview;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

/**
 * Author:lsh
 * Version: 1.0
 * Description:
 * Date: 2017/2/24
 */

public class PullRefreshRecycleView extends WrapRecyclerView {


    // 下拉刷新的辅助类
    private RefreshViewCreator mRefreshCreator;
    // 下拉刷新头部的高度
    private int mRefreshViewHeight = 0;
    // 下拉刷新的头部View
    private View mRefreshView;
    // 手指按下的Y位置
    private int mFingerDownY;
    // 手指拖拽的阻力指数
    public float mDragIndex = 0.55f;
    // 当前是否正在拖动
    private boolean mCurrentDrag = false;
    // 当前的状态
    private int mCurrentRefreshStatus;
    // 默认状态
    public static int REFRESH_STATUS_NORMAL = 0x0011;
    // 下拉刷新状态
    public static int REFRESH_STATUS_PULL_DOWN_REFRESH = 0x0022;
    // 松开刷新状态
    public static int REFRESH_STATUS_LOOSEN_REFRESHING = 0x0033;
    // 正在刷新状态
    public static int REFRESH_STATUS_REFRESHING = 0x0044;

    public boolean canRefresh = true;

    public PullRefreshRecycleView(Context context) {
        super(context);
    }

    public PullRefreshRecycleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PullRefreshRecycleView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // 先处理下拉刷新，同时考虑刷新列表的不同风格样式，确保这个项目还是下一个项目都能用
    // 所以我们不能直接添加View，需要利用辅助类
    public void addRefreshViewCreator(RefreshViewCreator refreshCreator) {
        this.mRefreshCreator = refreshCreator;
        addRefreshView();
    }

    @Override
    public void setAdapter(RecyclerView.Adapter adapter) {
        super.setAdapter(adapter);
        addRefreshView();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录手指按下的位置 ,之所以写在dispatchTouchEvent那是因为如果我们处理了条目点击事件，
                // 那么就不会进入onTouchEvent里面，所以只能在这里获取
                mFingerDownY = (int) ev.getRawY();
                break;

            case MotionEvent.ACTION_UP:
                if (mRefreshCreator == null||mRefreshView == null) return super.dispatchTouchEvent(ev);
                if (mCurrentDrag) {
//                    updateRefreshStatus(marginTop);
                    restoreRefreshView();
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 重置当前刷新状态状态
     */
    private void restoreRefreshView() {
        if (mRefreshView == null) return;
        if (mRefreshView.getLayoutParams() == null) return;
        int currentTopMargin = ((ViewGroup.MarginLayoutParams) mRefreshView.getLayoutParams()).topMargin;
        int finalTopMargin = -mRefreshViewHeight + 1;
        if (mCurrentRefreshStatus == REFRESH_STATUS_LOOSEN_REFRESHING) {
            finalTopMargin = 0;
            mCurrentRefreshStatus = REFRESH_STATUS_REFRESHING;
            if (mRefreshCreator != null) {
                mRefreshCreator.onRefreshing();
            }
            if (mListener != null) {
                mListener.onRefresh();
            }
        }

        int distance = currentTopMargin - finalTopMargin;

        // 回弹到指定位置
        if (distance > 0) {
            ValueAnimator animator = ObjectAnimator.ofFloat(currentTopMargin, finalTopMargin).setDuration(distance);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float currentTopMargin = (float) animation.getAnimatedValue();
                    setRefreshViewMarginTop((int) currentTopMargin);
                }
            });
            animator.start();
        }
        mCurrentDrag = false;
    }


    int marginTop;


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:


                if (mRefreshCreator == null || mRefreshView == null || !canRefresh) return super.onTouchEvent(e);
                // 如果是在最顶部才处理，否则不需要处理
                if (canScrollUp()) {
                    // 如果没有到达最顶端，也就是说还可以向上滚动就什么都不处理
                    return super.onTouchEvent(e);
                }
                if (mCurrentRefreshStatus == REFRESH_STATUS_REFRESHING && !mCurrentDrag) {
                    return super.onTouchEvent(e);
                }

                // 解决下拉刷新自动滚动问题
                if (mCurrentDrag) {
                    scrollToPosition(0);
                }

                // 获取手指触摸拖拽的距离
                int distanceY = (int) ((e.getRawY() - mFingerDownY) * mDragIndex);
                // 如果是已经到达头部，并且不断的向下拉，那么不断的改变refreshView的marginTop的值
                if (distanceY > 0) {
                    marginTop = distanceY - mRefreshViewHeight;
                    setRefreshViewMarginTop(marginTop);
                    updateRefreshStatus(marginTop);
                    mRefreshCreator.onPull(marginTop, mRefreshViewHeight, mCurrentRefreshStatus);
                    mCurrentDrag = true;
                    return false;
                }
                break;
        }

        return super.onTouchEvent(e);
    }

    /**
     * 更新刷新的状态
     */
    private void updateRefreshStatus(int marginTop) {
        if (marginTop <= -mRefreshViewHeight) {
            mCurrentRefreshStatus = REFRESH_STATUS_NORMAL;
        } else if (marginTop < 0) {
            mCurrentRefreshStatus = REFRESH_STATUS_PULL_DOWN_REFRESH;
        } else {
            mCurrentRefreshStatus = REFRESH_STATUS_LOOSEN_REFRESHING;
        }


    }

    /**
     * 添加头部的刷新View
     */
    private void addRefreshView() {
        RecyclerView.Adapter adapter = getAdapter();
        if (adapter != null && mRefreshCreator != null) {
            // 添加头部的刷新View
            View refreshView = mRefreshCreator.getRefreshView(getContext(), this);
            if (refreshView != null) {


                addHeaderView(refreshView);
                if (mRefreshView != null) {

                    ((WrapRecyclerAdapter) adapter).removeHeaderView(mRefreshView);
                }
                this.mRefreshView = refreshView;

                mRefreshViewHeight = mRefreshView.getMeasuredHeight();
                if (mRefreshViewHeight > 0) {
                    // 隐藏头部刷新的View  marginTop  多留出1px防止无法判断是不是滚动到头部问题
                    setRefreshViewMarginTop(-mRefreshViewHeight + 1);
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            if (mRefreshView != null && mRefreshViewHeight <= 0) {
                // 获取头部刷新View的高度
                mRefreshViewHeight = mRefreshView.getMeasuredHeight();
                if (mRefreshViewHeight > 0) {
                    // 隐藏头部刷新的View  marginTop  多留出1px防止无法判断是不是滚动到头部问题
                    setRefreshViewMarginTop(-mRefreshViewHeight + 1);
                }
            }
        }
    }

    /**
     * 设置刷新View的marginTop
     */
    private void setRefreshViewMarginTop(int marginTop) {
        if (mRefreshView == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mRefreshView.getLayoutParams();
        if (params == null) return;
        if (marginTop < -mRefreshViewHeight + 1) {
            marginTop = -mRefreshViewHeight + 1;
        }
        params.topMargin = marginTop;
        mRefreshView.setLayoutParams(params);
    }


    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     * 判断是不是滚动到了最顶部，这个是从SwipeRefreshLayout里面copy过来的源代码
     */
    public boolean canScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            return ViewCompat.canScrollVertically(this, -1) || this.getScrollY() > 0;
        } else {
            return ViewCompat.canScrollVertically(this, -1);
        }
    }

    /**
     * 停止刷新
     */
    public void stopRefresh() {
        mCurrentRefreshStatus = REFRESH_STATUS_NORMAL;
        restoreRefreshView();
        if (mRefreshCreator != null) {
            mRefreshCreator.onStopRefresh();
        }
        if (mListener != null) mListener.refreshEnd();
    }

    // 处理刷新回调监听
    private OnRefreshListener mListener;

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.mListener = listener;
    }

    public interface OnRefreshListener {
        //正在刷新
        void onRefresh();
        //刷新完成
        void refreshEnd();
    }

    /**
     *
     * @param needDefaultRefreshView 是否使用默认的下拉加载布局
     * @param refreshCreator 使用自己的下拉加载布局  extents RefreshViewCreator即可
     */
    public void setPullRefreshEnabled(boolean needDefaultRefreshView, RefreshViewCreator refreshCreator) {
        DefaultRefreshCreator defaultRefreshCreator = null;
        if (needDefaultRefreshView) {
            if (mRefreshCreator instanceof DefaultRefreshCreator)return;
            defaultRefreshCreator = new DefaultRefreshCreator();
            addRefreshViewCreator(defaultRefreshCreator);
        } else {
            if (getAdapter() != null && getAdapter() instanceof WrapRecyclerAdapter) {
                ((WrapRecyclerAdapter) getAdapter()).removeHeaderView(mRefreshView);
                mRefreshView = null;
                mRefreshCreator = null;
            } else {
                Toast.makeText(getContext(), "please set adapter first", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (refreshCreator != null) {
            addRefreshViewCreator(refreshCreator);
        }
    }
    public void setPullRefreshEnabled(boolean needDefaultRefreshView) {
        setPullRefreshEnabled(needDefaultRefreshView, null);
    }

    /**
     * 如果没有设置adapter 或者其他未知问题返回-1
     * @return 头的数量
     */
    public int getHeaderCount() {
        Adapter adapter = getAdapter();
        if (adapter != null && adapter instanceof WrapRecyclerAdapter) {
            int headerCount = ((WrapRecyclerAdapter) adapter).getHeaderCount();
            return headerCount;
        }
        return -1;
    }

    public View getRefreshView() {
        return mRefreshView;
    }

    public void setCanRefresh(boolean can) {
        canRefresh = can;
    }
}
