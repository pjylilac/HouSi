package io.github.mcxinyu.housi.fragment;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.florent37.viewanimator.ViewAnimator;

import org.jsoup.Jsoup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.github.mcxinyu.housi.R;
import io.github.mcxinyu.housi.activity.ReadEditHostsActivity;
import io.github.mcxinyu.housi.util.ObservableRichEditor;
import io.github.mcxinyu.housi.util.ScreenUtils;
import io.github.mcxinyu.housi.util.StaticValues;

/**
 * Created by huangyuefeng on 2017/9/20.
 * Contact me : mcxinyu@gmail.com
 */
public class ReadEditHostsFragment extends ABaseFragment {
    private static final String TAG = ReadEditHostsFragment.class.getSimpleName();
    private static final String ARGS_EDIT = "args_edit";
    private static final int WHAT_RESET = 1024;

    private static final String TEMP_HTML = "file:///android_asset/editor.html";

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout mSwipeRefresh;
    @BindView(R.id.parent_view)
    CoordinatorLayout mParentView;
    @BindView(R.id.rich_editor)
    ObservableRichEditor mRichEditor;
    @BindView(R.id.toolbar_title)
    TextView mToolbarTitle;
    @BindView(R.id.toolbar_spinner)
    AppCompatSpinner mToolbarSpinner;
    @BindView(R.id.relative_layout_title)
    RelativeLayout mRelativeLayoutTitle;
    // @BindView(R.id.scroll_view)
    // NestedScrollView mScrollView;
    @BindView(R.id.button_left)
    Button mButtonLeft;
    @BindView(R.id.edit_text_index)
    EditText mEditTextIndex;
    @BindView(R.id.button_right)
    Button mButtonRight;
    @BindView(R.id.linear_layout_control)
    LinearLayout mLinearLayoutControl;
    private Unbinder unbinder;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case WHAT_RESET:
                    if (mLinearLayoutControl != null) {
                        if (mStartAnimate != null) {
                            mStartAnimate.cancel();
                            mStartAnimate = null;
                        }
                        mResetAnimate = ViewAnimator.animate(mLinearLayoutControl)
                                .alpha(mLinearLayoutControl.getAlpha(), 1)
                                .duration(300)
                                .accelerate()
                                .start();
                    }
                    break;
            }
        }
    };

    private boolean mEditStatus;
    private String mHostsHtml;
    private List<String> mPageList = new ArrayList<>();
    private ViewAnimator mStartAnimate;
    private ViewAnimator mResetAnimate;
    private int mCapacityPage;
    private int mPage;
    private boolean padding;

    public static ReadEditHostsFragment newInstance(boolean edit) {

        Bundle args = new Bundle();
        args.putBoolean(ARGS_EDIT, edit);
        ReadEditHostsFragment fragment = new ReadEditHostsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            mEditStatus = getArguments().getBoolean(ARGS_EDIT, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit, container, false);
        unbinder = ButterKnife.bind(this, view);
        mSwipeRefresh.setColorSchemeResources(R.color.colorAccent);
        mSwipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                readHosts();
            }
        });
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initToolbar();
        readHosts();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initToolbar() {
        if (mToolbar != null) {
            if (mEditStatus) {
                mToolbar.setTitle("");
                mRelativeLayoutTitle.setVisibility(View.VISIBLE);
                mToolbarTitle.setText("编辑 hosts");
            } else {
                mRelativeLayoutTitle.setVisibility(View.GONE);
            }
        }
        final String[] lineOptional = new String[]{"100行/页", "200行/页", "300行/页"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), R.layout.simple_spinner_item_theme_color, lineOptional);
        adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item_theme_color);

        mToolbarSpinner.setAdapter(adapter);
        mToolbarSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mEditStatus) {
                    mCapacityPage = (position + 1) * 100;

                    dealHostsString();

                    mEditTextIndex.setText(String.valueOf(mPage));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mButtonLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePage();
                mPage--;
                if (mPage < 0)
                    mPage = 0;
                mEditTextIndex.setText(String.valueOf(mPage));
            }
        });
        mButtonRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePage();
                mPage++;
                if (mPage >= mPageList.size())
                    mPage = mPageList.size() - 1;
                mEditTextIndex.setText(String.valueOf(mPage));
            }
        });
        mEditTextIndex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    setPage(Integer.parseInt(s.toString()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        if (mEditStatus) {
            mSwipeRefresh.setRefreshing(false);
            mSwipeRefresh.setEnabled(false);
            mLinearLayoutControl.setVisibility(View.VISIBLE);
        } else {
            mLinearLayoutControl.setVisibility(View.GONE);
        }
        mRichEditor.setOnScrollChangedCallbacks(new ObservableRichEditor.OnScrollChangedCallbacks() {
            @Override
            public void onScroll(int x, int y, int oldX, int oldY) {
                if (mLinearLayoutControl.getAlpha() == 1) {
                    if (mStartAnimate == null) {
                        mStartAnimate = ViewAnimator.animate(mLinearLayoutControl)
                                .alpha(1, 0.2f)
                                .duration(300)
                                .accelerate()
                                .start();
                    }
                }
                mHandler.removeMessages(WHAT_RESET);
                mHandler.sendEmptyMessageDelayed(WHAT_RESET, 500);
            }
        });
    }

    private void savePage() {
        String html = mRichEditor.getHtml();
        mPageList.set(mPage, html);
    }

    private void dealHostsString() {
        if (!TextUtils.isEmpty(ReadEditHostsActivity.sHostsString)) {
            String[] split = ReadEditHostsActivity.sHostsString.split("\n");
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < split.length; i++) {
                if (i % mCapacityPage == 0) {
                    if (i == 0) {
                        stringBuilder.append(split[i]);
                        stringBuilder.append("</br>");
                    } else {
                        mPageList.add(stringBuilder.toString());
                        stringBuilder.delete(0, stringBuilder.length());
                        stringBuilder.append(split[i]);
                        stringBuilder.append("</br>");
                    }
                } else {
                    stringBuilder.append(split[i]);
                    stringBuilder.append("</br>");
                }
            }
        }
    }

    private void setPage(int page) {
        if (page > mPageList.size()) {
            return;
        }
        if (mPageList.size() > 0) {
            mRichEditor.setHtml(mPageList.get(page));
            if (!padding) {
                padding = true;
                mLinearLayoutControl.post(new Runnable() {
                    @Override
                    public void run() {
                        mRichEditor.setPadding(0, 0, 0,
                                ScreenUtils.px2dp(getContext(), mLinearLayoutControl.getMeasuredHeight()));
                    }
                });
            }
        }
    }

    @Override
    protected Toolbar getToolBar() {
        return mToolbar;
    }

    @Override
    protected int getNavMenuItemId() {
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
        if (mStartAnimate != null) {
            mStartAnimate.cancel();
            mStartAnimate = null;
        }
        if (mResetAnimate != null) {
            mResetAnimate.cancel();
            mResetAnimate = null;
        }
        unbinder.unbind();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void readHosts() {
        if (mEditStatus) {
            // dealHostsString();
        } else {
            mRichEditor.getSettings().setJavaScriptEnabled(true);
            mRichEditor.addJavascriptInterface(new HostsJavaScriptInterFace(), "hosts_script");
            mRichEditor.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    if (!mSwipeRefresh.isRefreshing())
                        mSwipeRefresh.setRefreshing(true);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    view.loadUrl("javascript:window.hosts_script.getSource(document.documentElement.outerHTML);void(0)");
                    if (mToolbar != null && url != null) {
                        mToolbar.setSubtitle(url);
                    }
                    mSwipeRefresh.setRefreshing(false);
                    mSwipeRefresh.setEnabled(false);
                }
            });

            File hostsFile = new File(StaticValues.SYSTEM_HOSTS_FILE_PATH);
            mRichEditor.loadUrl(Uri.fromFile(hostsFile).toString());
        }
    }

    public final class HostsJavaScriptInterFace {

        @JavascriptInterface
        public void getSource(String html) {
            if (!mEditStatus) {
                mHostsHtml = Jsoup.parse(html).text();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mEditStatus) {
            inflater.inflate(R.menu.edit_menu, menu);
        } else {
            inflater.inflate(R.menu.read_menu, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit_hosts:
                ReadEditHostsActivity.sHostsString = mHostsHtml;
                startActivity(ReadEditHostsActivity.newIntent(getContext(), true));
                return true;
            case R.id.action_save_hosts:
                // TODO: 2019/1/8 save hosts
                Toast.makeText(getContext(), "do save", Toast.LENGTH_SHORT).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}