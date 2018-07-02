package com.auto.taiyijie;

import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import com.xboot.stdcall.posix;
import org.wlf.filedownloader.FileDownloadConfiguration;
import org.wlf.filedownloader.FileDownloader;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.getExternalStoragePublicDirectory;

public class MainActivity extends AppCompatActivity {
    WebView webView;
    public Machine Machine = new Machine();
    public DmgcTiWen Tiwen; //体温对象
    public DmgcTemperature Temperature; //温湿度对象
    public Thread mDownloadThread;
    public Cash mCash;
    public Coin mCoin;
    public int setPowerOnOff(byte off_h, byte off_m, byte on_h, byte on_m, byte enable) {
        int fd, ret;
        fd = posix.open("/dev/McuCom", posix.O_RDWR, 0666);
        ret = posix.poweronoff(off_h, off_m, on_h, on_m, enable, fd);
        posix.close(fd);
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐去状态栏部分
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 隐藏底部操作栏
        closeBar();
        restartAndroid(4,0);
        setContentView(R.layout.webview);
        initWeb();
        String defaultPath = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).getPath();
        // 1、创建Builder
        FileDownloadConfiguration.Builder builder = new FileDownloadConfiguration.Builder(this);
        // 2.配置Builder
        // 配置下载文件保存的文件夹
        builder.configFileDownloadDir(defaultPath);
        // 配置同时下载任务数量，如果不配置默认为2
        builder.configDownloadTaskSize(1);
        // 配置失败时尝试重试的次数，如果不配置默认为0不尝试
        builder.configRetryDownloadTimes(5);
        // 开启调试模式，方便查看日志等调试相关，如果不配置默认不开启
        builder.configDebugMode(true);
        // 配置连接网络超时时间，如果不配置默认为10秒
        builder.configConnectTimeout(10000);// 10秒
        // 3、使用配置文件初始化FileDownloader
        FileDownloadConfiguration configuration = builder.build();
        FileDownloader.init(configuration);
        Tiwen = new DmgcTiWen();
        Tiwen.startTiwen("ttyS3");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("Dos","onResume");
        webView.loadUrl(String.format("javascript:get_prescription()"));
    }

    private void sendKeyCode(final int keyCode){
        new Thread(){
            public void run(){
                try{
                    Instrumentation inst = new Instrumentation();
                    inst.sendKeyDownUpSync(keyCode);
                }catch (Exception e){
                    Log.e("Dos",e.toString());
                }
            }
        }.start();
    }

    //定时重启开关
    private void restartAndroid(int resetTime,int addMin){
        final Calendar mCalendar=Calendar.getInstance();
        int resetHour,resetMinuts;
        long time = System.currentTimeMillis();
        mCalendar.setTimeInMillis(time);
        int mHour=mCalendar.get(Calendar.HOUR);
        int mMinuts=mCalendar.get(Calendar.MINUTE);
        int apm = mCalendar.get(Calendar.AM_PM);
        if(apm == 1){
            mHour += 12;
        }
        resetHour = 23 - mHour + resetTime;
        resetMinuts = 60 - mMinuts + addMin;
        if(resetMinuts >= 60){
            resetMinuts -= 60;
            resetHour += 1;
        }
        if(resetHour >= 24){
            resetHour -= 24;
        }
        Log.e("Dos",resetHour+"时"+resetMinuts+"分后自动重启");
        setPowerOnOff((byte)0,(byte)1,(byte)resetHour,(byte)resetMinuts,(byte)0);
    }

    private void initWeb(){
        webView = (WebView) findViewById(com.auto.taiyijie.R.id.web);
        webView.setVisibility(View.INVISIBLE);
        if(webView != null){
            webView.setWebChromeClient(new WebChromeClient(){
                public void onProgressChanged(WebView view, int progress) {
                if (progress == 100) {
                    Log.e("Dos","web加载完成");
                    webView.setVisibility(View.VISIBLE);
                    webView.loadUrl(String.format("javascript:apiready()"));
                }
                }
            });
            //webView.setWebContentsDebuggingEnabled(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);  //设置 缓存模式
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDefaultTextEncodingName("UTF-8");//设置字符编码
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false); //video标签
            webView.getSettings().setDomStorageEnabled(true);
            String appCachePath = getApplicationContext().getCacheDir().getAbsolutePath();
            webView.getSettings().setAppCachePath(appCachePath);
            webView.getSettings().setAllowFileAccess(true);
            webView.getSettings().setAppCacheEnabled(true);
            webView.addJavascriptInterface(new AndroidJavaScript(this),"Android");
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    final Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            Bundle b = msg.getData();
            switch (msg.what){
                case -3: //自检结果
                    webView.loadUrl(String.format("javascript:machine_msg(-3,'"+b.getString("msg")+"')"));
                    break;
                case -2: //自检通知
                    webView.loadUrl(String.format("javascript:machine_msg(-2,'"+b.getString("msg")+"')"));
                    break;
                case -1: //主板连接失败
                    webView.loadUrl(String.format("javascript:machine_msg(-1,'主板连接失败')"));
                    break;
                case 0: //主板连接成功
                    webView.loadUrl(String.format("javascript:machine_msg(0,'主板连接成功')"));
                    break;
                case 1://正在出货
                    String channel = b.getString("channel");
                    webView.loadUrl(String.format("javascript:machine_msg(1,'"+channel+"')"));
                    break;
                case 2://出货异常
                    String str = b.getString("msg");
                    int errorCode = b.getInt("errorCode");
                    webView.loadUrl(String.format("javascript:machine_msg(2,'"+str+"',"+errorCode+")"));
                    break;
                case 3: //是否能出货 code=0 不能 code=1可以出货
                    webView.loadUrl(String.format("javascript:machine_msg(3,'"+b.getString("msg")+"',"+b.getInt("code")+")"));
                    break;
                case 4: //出货结束
                    webView.loadUrl(String.format("javascript:machine_msg(4,'请取走货品')"));
                    break;
                case 5: //出货检测异常
                    webView.loadUrl(String.format("javascript:machine_msg(5,'出货检测异常')"));
                    break;
                case 6: //关闭端口
                    webView.loadUrl(String.format("javascript:machine_msg(6,'上一端口已关闭')"));
                    break;
                case 7://货道检测失败（调试用）
                    int errorChannelId = b.getInt("channelId");
                    webView.loadUrl(String.format("javascript:machine_msg(7,'调试货道发现错误','"+errorChannelId+"')"));
                    break;
                case 8://货道检测成功（调试用）
                    int successChannelId = b.getInt("channelId");
                    webView.loadUrl(String.format("javascript:machine_msg(8,'调试货道成功','"+successChannelId+"')"));
                    break;
                case 9://全货道检测完毕（调试用）
                    webView.loadUrl(String.format("javascript:machine_msg(9,'全货道检测完毕','')"));
                    break;
                case 100://收到现金收入消息
                    int num = b.getInt("num");
                    //Toast.makeText(getApplicationContext(), "收到现金"+num+"元", Toast.LENGTH_LONG).show();
                    webView.loadUrl(String.format("javascript:cash_msg(100,'收到现金','"+num+"')"));
                    break;
                case 101://现金收收银器连接失败
                    Toast.makeText(getApplicationContext(), "现金收银器连接失败", Toast.LENGTH_LONG).show();
                    webView.loadUrl(String.format("javascript:cash_msg(101,'现金收银器连接失败','')"));
                    break;
                case 102://现金收收银器连接失败
                    Toast.makeText(getApplicationContext(), "现金收银器连接成功", Toast.LENGTH_LONG).show();
                    break;
                case 103://硬币器缺少硬币
                    webView.loadUrl(String.format("javascript:machine_msg(103,'硬币器硬币不足')"));
                    break;
            }
        }
    };
    /**
     * 关闭Android导航栏，实现全屏
     */
    private void closeBar() {
        try {
            String command;
            command = "LD_LIBRARY_PATH=/vendor/lib:/system/lib service call activity 42 s16 com.android.systemui";
            ArrayList<String> envlist = new ArrayList<String>();
            Map<String, String> env = System.getenv();
            for (String envName : env.keySet()) {
                envlist.add(envName + "=" + env.get(envName));
            }
            String[] envp = envlist.toArray(new String[0]);
            Process proc = Runtime.getRuntime().exec(
                    new String[] { "su", "-c", command }, envp);
            proc.waitFor();
        } catch (Exception ex) {
            // Toast.makeText(getApplicationContext(), ex.getMessage(),
            // Toast.LENGTH_LONG).show();
        }
    }

    // 通过包名获取此APP详细信息，包括Activities、services、versioncode、name等等
    private void doStartApplicationWithPackageName(String packagename) {
        PackageInfo packageinfo = null;
        try {
            packageinfo = getPackageManager().getPackageInfo(packagename, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageinfo == null) {
            return;
        }

        // 创建一个类别为CATEGORY_LAUNCHER的该包名的Intent
        Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveIntent.setPackage(packageinfo.packageName);

        // 通过getPackageManager()的queryIntentActivities方法遍历
        List<ResolveInfo> resolveinfoList = getPackageManager()
                .queryIntentActivities(resolveIntent, 0);

        ResolveInfo resolveinfo = resolveinfoList.iterator().next();
        if (resolveinfo != null) {
            // packagename = 参数packname
            String packageName = resolveinfo.activityInfo.packageName;
            // 这个就是我们要找的该APP的LAUNCHER的Activity[组织形式：packagename.mainActivityname]
            String className = resolveinfo.activityInfo.name;
            // LAUNCHER Intent
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            // 设置ComponentName参数1:packagename参数2:MainActivity路径
            ComponentName cn = new ComponentName(packageName, className);
            intent.setComponent(cn);
            startActivity(intent);
        }
    }

    class AndroidJavaScript {
        private Context mContext;
        AndroidJavaScript(Context context){
            this.mContext = context;
        }

        @JavascriptInterface
        public void Log(String str){
            Log.e("Dos",str);
        }

        @JavascriptInterface
        public void cash_return(int num){
            mCoin.return_coin(num);
        }

        @JavascriptInterface
        public void play_count_down(){
            MediaPlayer mediaPlayer = MediaPlayer.create(this.mContext,R.raw.down_sec);
            mediaPlayer.setLooping(false);
            mediaPlayer.start();
        }

        @JavascriptInterface
        public void play_push_goods(){
            MediaPlayer mediaPlayer = MediaPlayer.create(this.mContext,R.raw.push_goods);
            mediaPlayer.setLooping(false);
            mediaPlayer.start();
        }

        @JavascriptInterface
        public void cash_start(String port) {
            mCash = new Cash();
            mCash.setOnCashListening(new Cash.OnCashListening(){
                @Override
                public void OnCashReceive(int num){
                    Message message = new Message();
                    message.what = 100;
                    Bundle b = new Bundle();
                    b.putInt("num", num);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnCashConnectError(){
                    Message message = new Message();
                    message.what = 101;
                    handler.sendMessage(message);
                }
                @Override
                public void OnCashConnectSuccess(){
                    Message message = new Message();
                    message.what = 102;
                    handler.sendMessage(message);
                }
            });
            mCash.start(port);
        }

        @JavascriptInterface
        public void coin_start(String port){
            mCoin = new Coin();
            mCoin.setOnCoinListening(new Coin.OnCoinListening(){
                @Override
                public void OnCoinReceive(int num){
                    Message message = new Message();
                    message.what = 100;
                    Bundle b = new Bundle();
                    b.putInt("num", num);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnCoinConnectError(){
                    Message message = new Message();
                    message.what = 101;
                    handler.sendMessage(message);
                }
                @Override
                public void OnCoinConnectSuccess(){
                    Message message = new Message();
                    message.what = 102;
                    handler.sendMessage(message);
                }

                @Override
                public void OnCoinLack() {
                    Message message = new Message();
                    message.what = 103;
                    handler.sendMessage(message);
                }
            });
            mCoin.start(port);
        }

        @JavascriptInterface
        public void cash_close(){
            mCash.closePort();
        }

        @JavascriptInterface
        public void coin_close(){
            mCoin.closePort();
        }

        @JavascriptInterface
        public void machine_open(String port,int machine_num) {
            Machine.setOnMachineListening(new Machine.OnMachineListening(){
                //机器自检完成回调
                @Override
                public void OnInitEnd(String msg) {
                    Message message = new Message();
                    message.what = -3;
                    Bundle b = new Bundle();
                    b.putString("msg", msg);
                    message.setData(b);
                    handler.sendMessage(message);
                }

                //机器自检回调
                @Override
                public void OnInitCheck(String msg) {
                    Message message = new Message();
                    message.what = -2;
                    Bundle b = new Bundle();
                    b.putString("msg", msg);
                    message.setData(b);
                    handler.sendMessage(message);
                }

                @Override
                public void OnOpenFailed() {
                    Message message = new Message();
                    message.what = -1;
                    handler.sendMessage(message);
                }
                @Override
                public void OnOpenSuccess() {
                    Message message = new Message();
                    message.what = 0;
                    handler.sendMessage(message);
                }
                @Override
                public void OnGoodIng(String channel) {
                    Log.e("Dos","出货中:"+channel);
                    Message message = new Message();
                    message.what = 1;
                    Bundle b = new Bundle();
                    b.putString("channel", channel);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnError(int errorCode,String msg) {
                    Log.e("Dos","出现异常:"+errorCode +":"+msg);
                    Message message = new Message();
                    message.what = 2;
                    Bundle b = new Bundle();
                    b.putInt("errorCode", errorCode);
                    b.putString("msg", msg);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnCheckSoldGood(int code,String msg) {
                    Message message = new Message();
                    message.what = 3;
                    Bundle b = new Bundle();
                    b.putInt("code", code);
                    b.putString("msg", msg);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnGoodEnd(String channel) {
                    Log.e("Dos","出货完成"+channel);
                    Message message = new Message();
                    message.what = 4;
                    handler.sendMessage(message);
                }
                @Override
                public void OnGoodError() {
                    Message message = new Message();
                    message.what = 5;
                    handler.sendMessage(message);
                }
                @Override
                public void OnClosePort() {
                    Message message = new Message();
                    message.what = 6;
                    handler.sendMessage(message);
                }
                @Override
                public void OnCheckChannelError(int channelId) {
                    Log.e("Dos","货道调试出现异常:"+channelId);
                    Message message = new Message();
                    message.what = 7;
                    Bundle b = new Bundle();
                    b.putInt("channelId", channelId);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnCheckChannelSuccess(int channelId) {
                    Log.e("Dos","货道调试正常:"+channelId);
                    Message message = new Message();
                    message.what = 8;
                    Bundle b = new Bundle();
                    b.putInt("channelId", channelId);
                    message.setData(b);
                    handler.sendMessage(message);
                }
                @Override
                public void OnCheckAllChannelEnd() {
                    Log.e("Dos","全货道调试完毕");
                    Message message = new Message();
                    message.what = 9;
                    handler.sendMessage(message);
                }

            });
            Machine.init(port,machine_num);
        }
        @JavascriptInterface
        public String get_machine_code(){
            String m_szAndroidID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            m_szAndroidID = stringToNum(m_szAndroidID);
            String machine_code = m_szAndroidID.substring(0,8).toUpperCase();
            //Log.e("Dos",machine_code);
            Toast.makeText(mContext, "CODE:"+machine_code, Toast.LENGTH_SHORT).show();
            return machine_code;
        }
        // JS检测是否可以出货
        @JavascriptInterface
        public void checkSoldGood(int machineId){
            byte machine = (byte)machineId;
            Machine.checkSoldGood(machine);
        }
        @JavascriptInterface
        public void machine_good(int machineId,int channelId){
            //Toast.makeText(mContext, "出货"+Integer.toString(machineId)+":"+Integer.toString(channelId), Toast.LENGTH_SHORT).show();
            byte machine = (byte)machineId;
            byte channel = (byte) channelId;
            Machine.soldGood(machine,channel);
        }

        @JavascriptInterface
        public void machine_close(){
            Machine.closePort();
        }

        @JavascriptInterface
        public int get_temp_value(){
            return getRandom(360,380);
        }

        @JavascriptInterface
        public void show_manager(){
            Toast.makeText(mContext, "进入管理员界面", Toast.LENGTH_SHORT).show();
        }
        //开始全货道检测
        @JavascriptInterface
        public void test_all_channel(final int machine_id){
            Toast.makeText(mContext, "开始全货道调试："+machine_id+"号机", Toast.LENGTH_SHORT).show();
            Machine.checkAllChannel((byte) machine_id);
        }

        //停止全货道检测
        @JavascriptInterface
        public void stopAllCheck(){
            Toast.makeText(mContext, "停止全货道检测", Toast.LENGTH_SHORT).show();
            Machine.stopAllCheck();
        }
        //单货道检测  （停用）
        @JavascriptInterface
        public void test_channel(int machine_id,int channel_id){
            Toast.makeText(mContext, "开始调试货道："+machine_id+"号机#"+machine_id+"货道", Toast.LENGTH_SHORT).show();
        }

        //读取体温数据
        @JavascriptInterface
        public double get_tiwen(){
            return Tiwen.num_tiwen;
        }

        //读取环境温湿度数据
        @JavascriptInterface
        public String get_temperature(){
            return Temperature.value;
        }

        //app重启
        @JavascriptInterface
        public void app_restart(){
            Intent intent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            PendingIntent restartIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
            AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, restartIntent); // 1秒钟后重启应用
            System.exit(0);//退出程序
        }

        @JavascriptInterface
        public void app_close(){
            System.exit(0);//退出程序
        }

        @JavascriptInterface
        public void open_live_app(){
            Toast.makeText(mContext, "正在启动APP", Toast.LENGTH_SHORT).show();
            Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage("com.cbk.ask");
            startActivity(LaunchIntent);
            //doStartApplicationWithPackageName("com.cbk.ask"); //打开视频APP
        }

        //下载文件
        @JavascriptInterface
        public void downloadfile(String url){
            doDownLoadFile(url);
        }

        //获取下载后的本地文件路径
        @JavascriptInterface
        public String getVideoFilePath(String url){
            return doGetVideoFilePath(url);
        }

        //清除无用的文件 （传进来有用的，除了传进来的，其他都删除）
        @JavascriptInterface
        public void clear_nouse_files(String files){
            doClearNouseFiles(files);
        }

        public int getRandom(int min,int max){
            return (int)(min+Math.random()*(max-min+1));
        }
        // 将字母转换成数字字符串
        public String stringToNum(String input) {
            char[] temp = input.toCharArray();
            String rs="";
            for (int i=0;i<temp.length;i++) {
                if(i<2){
                    rs += temp[i];
                }else{
                    rs += Integer.toString((int)temp[i]);
                }
            }
            return rs;
        }
    }

    public String doGetVideoFilePath(String url){
        String fileName = url.substring(url.lastIndexOf('/')+1);
        //Log.e("Dos","fileName==》"+fileName);
        if(fileIsExists(fileName)){
            return "file:///mnt/sdcard/Download/"+fileName;
        }else{
            return "none";
        }
    }

    public void doDownLoadFile(String url){
            //Log.e("Dos","downloadfile==》"+url);
            FileDownloader.start(url);
    }

    //删除无用的广告文件
    public void doClearNouseFiles(String files){
        String[] fileArray = files.split("\\|");
        String[] fileArrayName = new String[fileArray.length];
        if(fileArray.length>0){
            for(int i=0;i<fileArray.length;i++){
                String fileName = fileArray[i].substring(fileArray[i].lastIndexOf('/')+1);
                fileArrayName[i] = fileName;
                //Log.e("Dos","在播文件"+fileName);
            }

            String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Download";
            File downloadPath = new File(path);
            File[] fileList = downloadPath.listFiles();
            File[] fileDeleteList = new File[fileList.length];
            boolean isUseFull;
            for(int i=0;i<fileList.length;i++){
                if(fileList[i].isFile()){
                    //Log.e("Dos","已下载文件"+fileList[i].getName());
                    isUseFull = false;
                    for(int i2=0;i2<fileArrayName.length;i2++){
                        if(fileArrayName[i2].toString().equals(fileList[i].getName().toString()) || (fileArrayName[i2].toString()+".temp").equals(fileList[i].getName().toString())){
                            //Log.e("Dos","有用文件"+fileList[i].getName());
                            isUseFull = true;
                        }
                    }
                    if(!isUseFull){
                        fileDeleteList[i] = fileList[i];
                    }
                }
            }
            for(int i=0;i<fileDeleteList.length;i++){
                if(fileDeleteList[i].isFile()){
                    Log.e("Dos","删除无用文件"+fileDeleteList[i].getName());
                    fileDeleteList[i].delete();
                }
            }
        }
    }

    public boolean fileIsExists(String file){
        try{
            File f=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Download/"+file);
            if(!f.exists()){
                return false;
            }
        }catch (Exception e) {
            // TODO: handle exception
            return false;
        }
        return true;
    }
}
