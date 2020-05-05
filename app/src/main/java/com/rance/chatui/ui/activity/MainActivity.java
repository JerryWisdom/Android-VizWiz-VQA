package com.rance.chatui.ui.activity;

import android.Manifest;
import android.app.ActionBar;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.RecognizerIntent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.iflytek.cloud.SpeechUtility;
import com.jude.easyrecyclerview.EasyRecyclerView;
import com.rance.chatui.R;
import com.rance.chatui.adapter.ChatAdapter;
import com.rance.chatui.adapter.CommonFragmentPagerAdapter;
import com.rance.chatui.enity.FullImageInfo;
import com.rance.chatui.enity.MessageInfo;
import com.rance.chatui.ui.fragment.ChatEmotionFragment;
import com.rance.chatui.ui.fragment.ChatFunctionFragment;
import com.rance.chatui.util.Constants;
import com.rance.chatui.util.GlobalOnItemClickManagerUtils;
import com.rance.chatui.util.MediaManager;
import com.rance.chatui.widget.EmotionInputDetector;
import com.rance.chatui.widget.NoScrollViewPager;
import com.rance.chatui.widget.StateButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
//import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

import android.os.Build;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.rance.chatui.ui.fragment.TtsSettings.PREFER_NAME;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener{  //使用兼容版的actionbar
    /*
        自动生成findViewById无需再实例化控件
     */
    @Bind(R.id.chat_list)
    EasyRecyclerView chatList;
    @Bind(R.id.emotion_voice)
    ImageView emotionVoice;
    @Bind(R.id.edit_text)
    EditText editText;
    @Bind(R.id.voice_text)
    TextView voiceText;
    @Bind(R.id.emotion_button)
    ImageView emotionButton;
    @Bind(R.id.emotion_add)
    ImageView emotionAdd;
    @Bind(R.id.emotion_send)
    StateButton emotionSend;
    @Bind(R.id.viewpager)
    NoScrollViewPager viewpager;
    @Bind(R.id.emotion_layout)
    RelativeLayout emotionLayout;

    private static final String TAG = "MainActivity";
    private EmotionInputDetector mDetector;
    private ArrayList<Fragment> fragments;
    private ChatEmotionFragment chatEmotionFragment;
    private ChatFunctionFragment chatFunctionFragment;
    private CommonFragmentPagerAdapter adapter;

    private ChatAdapter chatAdapter;
    private LinearLayoutManager layoutManager;
    private List<MessageInfo> messageInfos;

    //录音、后台通信相关
    public String ans = "";
    public String ip = "";
    int animationRes = 0;
    int res = 0;
    int send_cnt = 0;
    String voice_translate = "";
    String ques_translate = "";
    AnimationDrawable animationDrawable = null;
    private ImageView animView;

    //长按消息框选择功能-语音转换
    int voice_longClickPosition;
    int longClickPosition;
    private PopupWindow popupWindow;
    private List<Message> messages=new ArrayList<Message>();
    private ListView listView;
    private TextToSpeech textToSpeech;

    private static Request.Builder builder = new Request.Builder();
    OkHttpClient client1 = new OkHttpClient();
    OkHttpClient client2 = new OkHttpClient.Builder()  //因为与服务器连接一般良好，且预测回答迅速
                                .connectTimeout(5,TimeUnit.SECONDS)  //连接超时时间暂时不需要设置
                                .readTimeout(200,TimeUnit.SECONDS)  //读取超时时间
                                .build();

    private final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private final MediaType MEDIA_TYPE_AMR = MediaType.parse("audio/amr");
    private File file;
    Bitmap bitmap;
    private final int URL_REQUEST_CODE = 0X001;
    private ImageView imageView;
    private Dialog dialog;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) { //在窗口显示前设置窗口的属性如风格、位置颜色等
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //解决安卓版本兼容问题，例如打不开相机
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        mscInit(null);  //采用sdk默认url
        ButterKnife.bind(this);
        EventBus.getDefault().register(this); //注册消息总线
        initWidget();   //加载界面Fragment，增加屏幕滚动和点击响应事件
        textToSpeech = new TextToSpeech(this, this);
    }

    private void initWidget() {
        fragments = new ArrayList<>();
        chatEmotionFragment = new ChatEmotionFragment();
        fragments.add(chatEmotionFragment);
        chatFunctionFragment = new ChatFunctionFragment();
        fragments.add(chatFunctionFragment);
        adapter = new CommonFragmentPagerAdapter(getSupportFragmentManager(), fragments);
        viewpager.setAdapter(adapter);
        viewpager.setCurrentItem(0);

        mDetector = EmotionInputDetector.with(this)
                .setEmotionView(emotionLayout)
                .setViewPager(viewpager)
                .bindToContent(chatList)
                .bindToEditText(editText)
                .bindToEmotionButton(emotionButton)
                .bindToAddButton(emotionAdd)
                .bindToSendButton(emotionSend)
                .bindToVoiceButton(emotionVoice)
                .bindToVoiceText(voiceText)
                .build();
        GlobalOnItemClickManagerUtils globalOnItemClickListener = GlobalOnItemClickManagerUtils.getInstance(this);
        globalOnItemClickListener.attachToEditText(editText);

        chatAdapter = new ChatAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(chatAdapter);
        chatList.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                        chatAdapter.handler.removeCallbacksAndMessages(null);
                        chatAdapter.notifyDataSetChanged();
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        chatAdapter.handler.removeCallbacksAndMessages(null);
                        mDetector.hideEmotionLayout(false);
                        mDetector.hideSoftInput();
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
        chatAdapter.addItemClickListener(itemClickListener);
        LoadData();
    }

    /*
    TODO:
    1、盲人输入语音，搞清安卓里语音的数据形式，Python后端调用百度api将其转化为文字，再进行处理
    2、盲人得到文字回答，长按消息框，将TextView内容转化为语音输出播放，或者在后台同时转为语音返回
    3、功能完善：在界面某处固定按钮实现语音转换，最好用语音唤醒，语音命令客户端朗读返回结果（语音/文字）
    */
    private void mscInit (String serverUrl){
        StringBuffer bf = new StringBuffer();
        bf.append("appid="+getString(R.string.app_id));
        bf.append(",");
        if (!TextUtils.isEmpty(serverUrl)) {
            bf.append("server_url="+serverUrl);
            bf.append(",");
        }
        SpeechUtility.createUtility(this.getApplicationContext(), bf.toString());
    }
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.ENGLISH);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "数据丢失或不支持", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        textToSpeech.stop(); // 不管是否正在朗读TTS都被打断，textToSpeech.shutdown(); // 关闭，释放资源
    }


    //如果将onLongClick返回false，那么执行完长按事件后，还有执行单击事件
    View.OnClickListener clickListener1 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.delete:
                    messageInfos.remove(longClickPosition);
                    chatAdapter.remove(longClickPosition);   //已实现删除消息
                    break;
                case R.id.translation:
                        translateFromServer(messageInfos.get(longClickPosition).getContent());
                    break;
//                      popupWindow.dismiss();
                case R.id.word_to_speech:
                    if (textToSpeech != null && !textToSpeech.isSpeaking() && messageInfos.get(longClickPosition).getVoiceTime()==0) {
                        Log.d(TAG, "文字转语音 " + longClickPosition + " " + messageInfos.get(longClickPosition).getContent());
                        //设置语调和语速
                        textToSpeech.setPitch(1.0f);
                        textToSpeech.setSpeechRate(0.8f);
                        textToSpeech.speak(messageInfos.get(longClickPosition).getContent(), TextToSpeech.QUEUE_FLUSH, null);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    View.OnClickListener clickListener2 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.delete_voice:
                    messageInfos.remove(voice_longClickPosition);
                    chatAdapter.remove(voice_longClickPosition);   //已实现删除消息
                    break;
                case R.id.voice_to_text:
                    MessageInfo message = new MessageInfo();
                    if(voice_translate!=""){
                        message.setContent(voice_translate);    //服务器返回语音识别结果
                        message.setType(Constants.CHAT_ITEM_TYPE_RIGHT);
                        message.setHeader("https://img.52z.com/upload/news/image/20180213/20180213062641_35687.jpg");
                        messageInfos.add(message);
                        chatAdapter.add(message);
                        chatList.scrollToPosition(chatAdapter.getCount() - 1);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * item点击事件，type=1表示回复者的消息，type=2表示发送消息的用户
     */
    //显示悬浮按钮列表
    private void showDialog1(View view) {
        final View popView1 = LayoutInflater.from(this).inflate(R.layout.item_func_select, null);
        PopupWindow popupWindow1 = new PopupWindow(popView1, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow1.setAnimationStyle(R.anim.anim_pop);  //设置加载动画
        int deleteHeight=popView1.findViewById(R.id.delete).getHeight()==0?145:popView1.findViewById(R.id.delete).getHeight();
        popupWindow1.setFocusable(true);
        popupWindow1.setOutsideTouchable(true);
        popupWindow1.setBackgroundDrawable(new BitmapDrawable());
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        popupWindow1.showAtLocation(view, Gravity.NO_GRAVITY, location[0], location[1]-popupWindow1.getHeight()-deleteHeight);
        Log.d(TAG, "showDialog1 Click 111");

        popView1.findViewById(R.id.delete).setOnClickListener(clickListener1);
        popView1.findViewById(R.id.translation).setOnClickListener(clickListener1);
        popView1.findViewById(R.id.word_to_speech).setOnClickListener(clickListener1);
    }

    private void showDialog2(View v) {
        View popView2 = LayoutInflater.from(this).inflate(R.layout.voice_func_select, null);
        PopupWindow popupWindow2 = new PopupWindow(popView2, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow2.setAnimationStyle(R.anim.anim_pop);  //设置加载动画
        int deleteHeight=popView2.findViewById(R.id.delete_voice).getHeight()==0?145:popView2.findViewById(R.id.delete_voice).getHeight();
        popupWindow2.setFocusable(true);
        popupWindow2.setOutsideTouchable(true);
        popupWindow2.setBackgroundDrawable(new BitmapDrawable());
        int[] location = new int[2];
        v.getLocationOnScreen(location);
        popupWindow2.showAtLocation(v, Gravity.NO_GRAVITY, location[0], location[1]-popupWindow2.getHeight()-deleteHeight);

        popView2.findViewById(R.id.delete_voice).setOnClickListener(clickListener2);
        popView2.findViewById(R.id.voice_to_text).setOnClickListener(clickListener2);
    }


    private ChatAdapter.onItemClickListener itemClickListener;
    {
        itemClickListener = new ChatAdapter.onItemClickListener() {
            @Override
            public void onHeaderClick(int position) {
                Toast.makeText(MainActivity.this, "onHeaderClick", Toast.LENGTH_SHORT).show();
            }
//            @Override
            public void onTextClick(View v, final int position) {
                longClickPosition = position;
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        longClickPosition = position;
                        showDialog1(v);
                        return true;  //如果将onLongClick返回false，那么执行完长按事件后，还有执行单击事件。
                    }
                });
            }
            @Override
            public void onImageClick(View view, int position) {  //界面点击图片显示
                int location[] = new int[2];    //location用来记录图片的XY坐标
                view.getLocationOnScreen(location);
                Log.d(TAG, "OnImageClick");
                FullImageInfo fullImageInfo = new FullImageInfo();
                fullImageInfo.setLocationX(location[0]);
                fullImageInfo.setLocationY(location[1]);
                fullImageInfo.setWidth(view.getWidth());
                fullImageInfo.setHeight(view.getHeight());
                fullImageInfo.setImageUrl(messageInfos.get(position).getImageUrl());
                EventBus.getDefault().postSticky(fullImageInfo);
                startActivity(new Intent(MainActivity.this, FullImageActivity.class));
                overridePendingTransition(0, 0);
            }
            //点击语音消息，播放
            @Override
            public void onVoiceClick(final ImageView imageView, final int position) {
                if (animView != null) {
                    animView.setImageResource(res);
                    animView = null;
                }
                voice_longClickPosition = position;
                Log.d(TAG, "VOICE LONG CLICK " + voice_longClickPosition);
                imageView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showDialog2(v);
                        return true;
                    }
                });
                switch (messageInfos.get(position).getType()) {
                    case 1:
                        animationRes = R.drawable.voice_left;
                        res = R.mipmap.icon_voice_left3;
                        break;
                    case 2:
                        animationRes = R.drawable.voice_right;
                        res = R.mipmap.icon_voice_right3;
                        break;
                }
                animView = imageView;
                animView.setImageResource(animationRes);
                animationDrawable = (AnimationDrawable) imageView.getDrawable();
                animationDrawable.start();
                MediaManager.playSound(messageInfos.get(position).getFilepath(), new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        animView.setImageResource(res);
                    }
                });
//                MediaManager.pause();
            }
        };
    }

    /**
     * 构造、初始化聊天数据
     */
    private void LoadData() {
        GetPublicNetIp();
        messageInfos = new ArrayList<>();
        MessageInfo messageInfo = new MessageInfo();
        messageInfo.setContent("Please first upload an image then ask questions!");
        messageInfo.setType(Constants.CHAT_ITEM_TYPE_LEFT);
        messageInfo.setHeader("https://i.loli.net/2019/11/08/dcxpUF2qI9Szl8W.jpg");
        messageInfos.add(messageInfo);
        chatAdapter.addAll(messageInfos);
    }


    /*
    与后台交互的通信模块，采用OKHTTP框架，以下为三种交互形式
     */
    private String intToIp(int i) {
        return (i & 0xFF ) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16 ) & 0xFF) + "." +
                ( i >> 24 & 0xFF) ;
    }

    public void GetPublicNetIp() {
        String ipUrlAddress = "http://pv.sohu.com/cityjson?ie=utf-8";
        Request request = new Request.Builder().url(ipUrlAddress).get().build();
        Call call = client2.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "HTTP Connection Failed");
                                Toast.makeText(MainActivity.this, "查询IP地址出错", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String res = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (res.equals("0")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "无效，请先上传图片", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new Handler().postDelayed(new Runnable() {
                                        public void run() {
                                            int satrtIndex = res.indexOf("{");
                                            int endIndex = res.indexOf("}");
                                            String js = res.substring(satrtIndex, endIndex + 1);
                                            com.alibaba.fastjson.JSONObject jo = com.alibaba.fastjson.JSONObject.parseObject(js);
                                            ip = jo.getString("cip");
                                        }
                                    }, 3000);
                                }
                            });
                        }
                    }
                });
            }
        });
    }
    public String getRealPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if(cursor.moveToFirst()){;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }

    /*
    发送图片给服务器，视觉问答项目的路由为register（图片）和question
    */
    private void sendImgToServer_1(String imguri) {
        send_cnt = 0;
        Log.d(TAG,  "send image  " + messageInfos.get(longClickPosition).getContent());
        String urlAddress_1 = "https://hducsrao.xyz/register/";
        String image_path = imguri;
        file = new File(image_path);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "img" + "_" + System.currentTimeMillis() + ".jpg",
                        RequestBody.create(MEDIA_TYPE_PNG, file));
        Request request = new Request.Builder().url(urlAddress_1).post(builder.build()).build();

        Call call = client1.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                            textToSpeech.setPitch(1.0f);
                            textToSpeech.setSpeechRate(0.8f);
                            textToSpeech.speak("图片上传成功", TextToSpeech.QUEUE_FLUSH, null);
                        }
                        Toast.makeText(MainActivity.this, "图片上传成功！", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /*
       发送字符串给服务器  messageInfos.get(position).getFilepath();
       有个问题是不同局域网ip不同，接受到的字符串不同，会报错，后期完善考虑：多个用户同时使用的IP和多线程问题，观察服务器上的稳定性
    */
    private void sendQuesToServer(String ques) {
        Log.d(TAG, "IP = "+ip);
        String urlAddress = "https://hducsrao.xyz/question/";
        send_cnt = send_cnt + 1;
        String userip = ip.split("\\.")[0] + ip.split("\\.")[1] +ip.split("\\.")[2]+ip.split("\\.")[3];
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add("question"+userip+String.valueOf(send_cnt), ques);
        // formBuilder.add("question", ques);
        Log.d(TAG, "question"+userip+String.valueOf(send_cnt));

        Request request = new Request.Builder().url(urlAddress).post(formBuilder.build()).build();
        Call call = client2.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "服务器错误", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String res = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (res.equals("0") || res.length() > 20) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                                        textToSpeech.setPitch(1.0f);
                                        textToSpeech.setSpeechRate(0.8f);
                                        textToSpeech.speak("问题无效, 请先上传图片", TextToSpeech.QUEUE_FLUSH, null);
                                    }
                                    Toast.makeText(MainActivity.this, "问题无效, 请先上传图片", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new Handler().postDelayed(new Runnable() {
                                        public void run() {
                                            MessageInfo message = new MessageInfo();
                                            message.setContent(res);
                                            if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                                                textToSpeech.setPitch(1.0f);
                                                textToSpeech.setSpeechRate(0.8f);
                                                textToSpeech.speak(res, TextToSpeech.QUEUE_FLUSH, null);
                                            }
                                            message.setType(Constants.CHAT_ITEM_TYPE_LEFT);
                                            message.setHeader("https://i.loli.net/2019/11/08/dcxpUF2qI9Szl8W.jpg");
                                            messageInfos.add(message);
                                            chatAdapter.add(message);
                                            chatList.scrollToPosition(chatAdapter.getCount() - 1);
                                        }
                                    }, 3000);
                                }
                            });
                        }
                    }
                });
            }
        });
    }


    // 发送语音给服务器，关键问题是语音的文件形式和messageInfo.getContent()的关系,
    // okhttp中MultipartBody是否能传语音，服务器接收到的字段叫 voice_ques
    private void sendVoiceToServer(MessageInfo voice) {
        send_cnt = send_cnt + 1;
        String urlAddress_1 = "https://hducsrao.xyz/voiceQues/";
        String voice_path = voice.getFilepath();
        String userip = ip.split("\\.")[0] + ip.split("\\.")[1] +ip.split("\\.")[2]+ip.split("\\.")[3];
        Log.d(TAG, voice_path);
        file = new File(voice_path);

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("voice_ques"+userip, file.getName(),
                        RequestBody.create(MEDIA_TYPE_AMR, file));
        Request request = new Request.Builder().url(urlAddress_1).post(builder.build()).build();
        Call call = client2.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "HTTP Connection Failed");
                                Toast.makeText(MainActivity.this, "服务器错误", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String res = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (res.equals("0")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                                        textToSpeech.setPitch(1.0f);
                                        textToSpeech.setSpeechRate(0.8f);
                                        textToSpeech.speak("问题无效, 请先上传图片", TextToSpeech.QUEUE_FLUSH, null);
                                    }
                                    Toast.makeText(MainActivity.this, "问题无效, 请先上传图片", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new Handler().postDelayed(new Runnable() {
                                        public void run() {
                                            MessageInfo message = new MessageInfo();
                                            message.setContent(res.split("\n")[2]);
                                            voice_translate = "Chinese: "+ res.split("\n")[0] + "\nEnglish: "+res.split("\n")[1];
                                            Log.d(TAG, "RESULT: " + res);
                                            if (textToSpeech != null && !textToSpeech.isSpeaking()) {
                                                textToSpeech.setPitch(1.0f);
                                                textToSpeech.setSpeechRate(0.8f);
                                                textToSpeech.speak(res.split("\n")[2], TextToSpeech.QUEUE_FLUSH, null);
                                            }
                                            message.setType(Constants.CHAT_ITEM_TYPE_LEFT);
                                            message.setHeader("https://i.loli.net/2019/11/08/dcxpUF2qI9Szl8W.jpg");
                                            messageInfos.add(message);
                                            chatAdapter.add(message);
                                            chatList.scrollToPosition(chatAdapter.getCount() - 1);
                                        }
                                    }, 3000);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void translateFromServer(String quesInfo) {
        Log.d(TAG, quesInfo);
        String userip = ip.split("\\.")[0] + ip.split("\\.")[1] +ip.split("\\.")[2]+ip.split("\\.")[3];
        String urlAddress_2 = "https://hducsrao.xyz/translate/";
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add("question"+userip, quesInfo);
        Log.d(TAG, "question"+userip);

        Request request = new Request.Builder().url(urlAddress_2).post(formBuilder.build()).build();
        Call call = client2.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "HTTP Connection Failed");
                                Toast.makeText(MainActivity.this, "服务器错误", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final String res = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (res.equals("0")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "无效", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new Handler().postDelayed(new Runnable() {
                                        public void run() {
                                            MessageInfo message = new MessageInfo();
                                            message.setContent("翻译: "+res);   //服务器返回英文问题翻译
                                            message.setType(Constants.CHAT_ITEM_TYPE_RIGHT);
                                            message.setHeader("https://img.52z.com/upload/news/image/20180213/20180213062641_35687.jpg");
                                            messageInfos.add(message);
                                            chatAdapter.add(message);
                                            chatList.scrollToPosition(chatAdapter.getCount() - 1);
                                        }
                                    }, 1000);
                                }
                            });
                        }
                    }
                });
            }
        });
    }


    @Subscribe(threadMode = ThreadMode.MAIN)  //在ui线程执行
    public void MessageEventBus(final MessageInfo messageInfo) {  //final变量不能被修改
        messageInfo.setHeader("https://img.52z.com/upload/news/image/20180213/20180213062641_35687.jpg");
        messageInfo.setType(Constants.CHAT_ITEM_TYPE_RIGHT);
//        messageInfo.setSendState(Constants.CHAT_ITEM_SENDING);  Constants.CHAT_ITEM_SEND_SUCCESS
        messageInfos.add(messageInfo);
        chatAdapter.add(messageInfo);
        chatList.scrollToPosition(chatAdapter.getCount() - 1);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                chatAdapter.notifyDataSetChanged();
            }
        }, 2000);

        // 判断发送给服务器的是否为问题字符串(仅在视觉问答中使用），否则为图片，但也可能是语音消息 !!!
        if(messageInfo.getContent() instanceof String && messageInfo.getContent().length()<50){
            sendQuesToServer(messageInfo.getContent());
        }
        else if(messageInfo.getVoiceTime() != 0) {
            //发送语音，通过打印日志中 MessageInfo 的 voicetime 字段区别消息类型，单位是毫秒，ms
            sendVoiceToServer(messageInfo);
        }
        else {
            sendImgToServer_1(messageInfo.getImageUrl());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (URL_REQUEST_CODE == requestCode) {
            try{
                SharedPreferences pref = getSharedPreferences(PREFER_NAME, MODE_PRIVATE);
                String server_url = pref.getString("url_preference","");
                String domain = pref.getString("url_edit","");
                if (!TextUtils.isEmpty(domain)) {
                    server_url = "http://"+domain+"/msp.do";
                }
                new Thread().sleep(40);
                mscInit(server_url);
            }catch (Exception e) {
//                showTip("reset url failed");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!mDetector.interceptBackPress()) {
            super.onBackPressed();
        }
    }//Android程序当你按下手机的back键时系统会默认调用程序栈中最上层Activity的Destroy()方法来销毁当前Activity

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ButterKnife.unbind(this);
        EventBus.getDefault().removeStickyEvent(this);
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onResume() {
        // 开放统计 移动数据统计分析
		/*FlowerCollector.onResume(MainActivity.this);
		FlowerCollector.onPageStart(TAG);*/
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void requestPermissions(){
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                int permission = ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if(permission!= PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,new String[]
                            {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.LOCATION_HARDWARE,Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.WRITE_SETTINGS,Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS},0x0010);
                }

                if(permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,new String[] {
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},0x0010);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void mscUninit() {
        if (SpeechUtility.getUtility()!= null) {
            SpeechUtility.getUtility().destroy();
            try{
                new Thread().sleep(40);
            }catch (InterruptedException e) {
            }
        }
    }

}
