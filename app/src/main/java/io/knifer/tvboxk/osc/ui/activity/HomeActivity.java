package io.knifer.tvboxk.osc.ui.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.viewpager.widget.ViewPager;

import io.knifer.tvboxk.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import io.knifer.tvboxk.osc.ui.adapter.HomePageAdapter;
import io.knifer.tvboxk.osc.ui.adapter.SelectDialogAdapter;
import io.knifer.tvboxk.osc.ui.adapter.SortAdapter;
import io.knifer.tvboxk.osc.ui.dialog.SelectDialog;
import io.knifer.tvboxk.osc.ui.dialog.TipDialog;
import io.knifer.tvboxk.osc.ui.fragment.GridFragment;
import io.knifer.tvboxk.osc.ui.fragment.UserFragment;
import io.knifer.tvboxk.osc.ui.tv.widget.DefaultTransformer;
import io.knifer.tvboxk.osc.ui.tv.widget.FixedSpeedScroller;
import io.knifer.tvboxk.osc.ui.tv.widget.NoScrollViewPager;
import io.knifer.tvboxk.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.knifer.freebox.websocket.WSHelper;
import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeActivity extends BaseActivity {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private Handler mHandler = new Handler();
    private long mExitTime = 0;
    private Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        initView();
        initViewModel();
        useCacheConfig = false;
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            useCacheConfig = bundle.getBoolean("useCache", false);
        }
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.mGridView.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 10.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }

                        public View v = view;
                        public int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    HomeActivity.this.currentView = view;
                    HomeActivity.this.isDownOrUp = false;
                    HomeActivity.this.sortChange = true;
                    view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(HomeActivity.this.getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    HomeActivity.this.sortFocusView = view;
                    HomeActivity.this.sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    } else if (baseLazyFragment instanceof UserFragment) {
                        showSiteSwitch();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public final boolean onInBorderKeyEvent(int direction, View view) {
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                isDownOrUp = true;
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                if (!((GridFragment) baseLazyFragment).isLoad()) {
                    return true;
                }
                return false;
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(dataInitOk && jarInitOk){
                    showSiteSwitch();
                }else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(dataInitOk && jarInitOk){
                    Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("useCache", true);
                    intent.putExtras(bundle);
                    HomeActivity.this.startActivity(intent);
                }else {
                    jumpActivity(SettingActivity.class);
                }
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                showSuccess();
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true));
                } else {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
                }
                initViewPager(absXml);
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    private void initData() {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null && home.getName() != null && !home.getName().isEmpty())
            tvName.setText(home.getName());
        if (dataInitOk && jarInitOk) {
            showLoading();
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            return;
        }
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!useCacheConfig)
                                    Toast.makeText(HomeActivity.this, "自定义jar加载成功", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void retry() {

                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(HomeActivity.this, "jar加载失败", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        });
                    }
                });
            }
            return;
        }
        String ANDROID_ID =
                Settings.System.getString(getContentResolver(), Settings.System.ANDROID_ID);
        ApiConfig.get().loadConfig(ANDROID_ID, useCacheConfig, new ApiConfig.LoadConfigCallback() {
            TipDialog dialog = null;

            @Override
            public void retry() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog == null)
                            dialog = new TipDialog(HomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            initData();
                                            dialog.hide();
                                        }
                                    });
                                }
                            });
                        if (!dialog.isShowing())
                            dialog.show();
                    }
                });
            }
        }, this);

        // FreeBox WebSocket初始化
        if (WSHelper.init(ANDROID_ID)) {
            mHandler.postDelayed(() -> {
                if (WSHelper.isOpen()) {
                    Toast.makeText(mContext, "FreeBox配对成功", Toast.LENGTH_SHORT).show();
                }
            }, 3000);
        }
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
            try {
                Field field = ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    @Override
    public void onBackPressed() {

        // takagen99: Add check for VOD Delete Mode
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
        } else {
            int i;
            if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || (i = this.sortFocused) < 0) {
                exit();
                return;
            }
            BaseLazyFragment baseLazyFragment = this.fragments.get(i);
            if (baseLazyFragment instanceof GridFragment) {
                View view = this.sortFocusView;
                GridFragment grid = (GridFragment) baseLazyFragment;
                if (grid.restoreView()) {
                    return;
                }// 还原上次保存的UI内容
                if (view != null && !view.isFocused()) {
                    this.sortFocusView.requestFocus();
                } else if (this.sortFocused != 0) {
                    this.mGridView.setSelection(0);
                } else {
                    exit();
                }
            } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList1.canScrollVertically(-1)) {
                UserFragment.tvHotList1.scrollToPosition(0);
                this.mGridView.setSelection(0);
            } else {
                exit();
            }
        }
    }

    private void exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            //这一段借鉴来自 q群老哥 IDCardWeb
            EventBus.getDefault().unregister(this);
            AppManager.getInstance().appExit(0);
            ControlManager.get().stopServer();
            finish();
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mRunnable);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                HomeActivity.this.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    if (sortFocused == 0) {
                        changeTop(false);
                    } else {
                        changeTop(true);
                    }
                }
            }
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showSiteSwitch();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {

        }
        return super.dispatchKeyEvent(event);
    }

    byte topHide = 0;

    private void changeTop(boolean hide) {
        ViewObj viewObj = new ViewObj(topLayout, (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                topHide = (byte) (hide ? 1 : 0);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        if (hide && topHide == 0) {
            animatorSet.playTogether(new Animator[]{
                    ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 10.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f))
                            }),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f))
                            }),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", new float[]{1.0f, 0.0f})});
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
        if (!hide && topHide == 1) {
            animatorSet.playTogether(new Animator[]{
                    ObjectAnimator.ofObject(viewObj, "marginTop", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 0.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 10.0f))
                            }),
                    ObjectAnimator.ofObject(viewObj, "height", new IntEvaluator(),
                            new Object[]{
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 1.0f)),
                                    Integer.valueOf(AutoSizeUtils.mm2px(this.mContext, 50.0f))
                            }),
                    ObjectAnimator.ofFloat(this.topLayout, "alpha", new float[]{0.0f, 1.0f})});
            animatorSet.setDuration(200);
            animatorSet.start();
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
    }

    void showSiteSwitch() {
        List<SourceBean> sites = ApiConfig.get().getSourceBeanList();
        if (sites.size() > 0) {
            SelectDialog<SourceBean> dialog = new SelectDialog<>(HomeActivity.this);
            TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
            int spanCount;
            spanCount = (int)Math.floor(sites.size()/60);
            spanCount = Math.min(spanCount, 2);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(dialog.getContext(), spanCount+1));
            ConstraintLayout cl_root = dialog.findViewById(R.id.cl_root);
            ViewGroup.LayoutParams clp = cl_root.getLayoutParams();
            clp.width = AutoSizeUtils.mm2px(dialog.getContext(), 380+200*spanCount);
            dialog.setTip("请选择首页数据源");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    ApiConfig.get().setSourceBean(value);
                    Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("useCache", true);
                    intent.putExtras(bundle);
                    HomeActivity.this.startActivity(intent);
                }

                @Override
                public String getDisplay(SourceBean val) {
                    return val.getName();
                }
            }, new DiffUtil.ItemCallback<SourceBean>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }
            }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
            dialog.show();
        }
    }
}
