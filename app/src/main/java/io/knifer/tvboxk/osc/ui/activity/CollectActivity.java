package io.knifer.tvboxk.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import io.knifer.tvboxk.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.event.RefreshEvent;
import io.knifer.tvboxk.osc.ui.adapter.CollectAdapter;
import io.knifer.tvboxk.osc.ui.dialog.ConfirmClearDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {
    private ImageView tvDelete;
    private ImageView tvClear;
    private TextView tvDelTip;
    private TvRecyclerView mGridView;
    public static CollectAdapter collectAdapter;
    private boolean delMode = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_collect;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void toggleDelMode() {
    	HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        collectAdapter.notifyDataSetChanged();
        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDelete = findViewById(R.id.tvDelete);
        tvClear = findViewById(R.id.tvClear);
        tvDelTip = findViewById(R.id.tvDelTip);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, isBaseOnWidth() ? 5 : 6));
        collectAdapter = new CollectAdapter();
        mGridView.setAdapter(collectAdapter);
        tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfirmClearDialog dialog = new ConfirmClearDialog(mContext, "Collect");
                dialog.show();
            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvDelete.setFocusable(true);
                    tvClear.setFocusable(true);
                    tvDelete.requestFocus();
                }
                return false;
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        collectAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        collectAdapter.remove(position);
                        RoomDataManger.deleteVodCollect(vodInfo.getId());
                    } else {
                        if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                            Bundle bundle = new Bundle();
                            bundle.putString("id", vodInfo.vodId);
                            bundle.putString("sourceKey", vodInfo.sourceKey);
                            jumpActivity(DetailActivity.class, bundle);
                        } else {
                            Intent newIntent = new Intent(mContext, SearchActivity.class);
                            newIntent.putExtra("title", vodInfo.name);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }
                }
            }
        });
        collectAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
//                FastClickCheckUtil.check(view);
//                VodCollect vodInfo = collectAdapter.getData().get(position);
//                collectAdapter.remove(position);
//                RoomDataManger.deleteVodCollect(vodInfo.getId());
                tvDelete.setFocusable(true);
                toggleDelMode();
                return true;
            }
        });
    }

    private void initData() {
        List<VodCollect> allVodRecord = RoomDataManger.getAllVodCollect();
        List<VodCollect> vodInfoList = new ArrayList<>();
        for (VodCollect vodInfo : allVodRecord) {
            vodInfoList.add(vodInfo);
        }
        collectAdapter.setNewData(vodInfoList);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }
}