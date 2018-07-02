package com.auto.taiyijie;
import android.util.Log;
import org.cnbleu.serialport.SerialPort;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Xjr on 2017/6/11.
 */

public class Cash {
    protected SerialPort cashSerialPort;    //端口连接类
    private boolean isClose = true;        //状态
    public InputStream cashInputStream;     //读取
    public OutputStream cashOutStream;      //发送
    private Thread receiveThread;           //接受命令线程
    private Thread sendPollThread;          //发送命令线程
    private Thread startThread;             //开启机器线程
    public boolean stopCash = false;        //停止轮询
    public boolean stopRec = false;         //停止接收
    private boolean statue = false;
    private boolean isCheck_money = false;
    private boolean is_get_money = false;   //是否获得金额数据
    private int get_money = 0;              //获得金额面值

    public interface OnCashListening{
        void OnCashReceive(int num); //收到现金
        void OnCashConnectError(); //收银器连接失败
        void OnCashConnectSuccess(); //收银器连接成功
    }
    OnCashListening OnCashListening=null;
    public void setOnCashListening(OnCashListening e){
        OnCashListening=e;
    }

    public void start(String port){
        try{
            closePort();
        }catch (Exception e){}
        port = "ttyS4";
        stopCash = false;        //停止轮询
        stopRec = false;         //停止接收
        SerialPort coinSerialPort = new SerialPort();
        coinSerialPort.open(
                new File("/dev/" + port),
                SerialPort.BAUDRATE.B9600,
                SerialPort.STOPB.B2,
                SerialPort.DATAB.CS8,
                SerialPort.PARITY.NONE,
                SerialPort.FLOWCON.NONE
        );
        cashInputStream = coinSerialPort.getInputStream();
        cashOutStream = coinSerialPort.getOutputStream();
        startCash();//开启纸币器
    }

    private void startCash(){
        Log.i("money", "开启收银器_开始");
        if(OnCashListening != null){
            OnCashListening.OnCashConnectSuccess();
        }else{
            OnCashListening.OnCashConnectError();
        }
        startThread = new Thread(){
            @Override
            public void run() {
                try {
                    byte[] start_send = new byte[]{0x7F,(byte)0x80,0x01,0x11,0x65,(byte)0x82};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    start_send = new byte[]{0x7F,(byte)0x00,0x01,0x05,0x1E,(byte)0x08};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    start_send = new byte[]{0x7F,(byte)0x80,0x01,0x09,0x35,(byte)0x82};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    start_send = new byte[]{0x7F,(byte)0x00,0x01,0x07,0x11,(byte)0x88};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    start_send = new byte[]{0x7F,(byte)0x80,0x03,0x02,(byte)0xFF,(byte)0x00,0x27,(byte)0xA6};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    start_send = new byte[]{0x7F,(byte)0x00,0x01,0x0A,0x3C,(byte) 0x08};
                    cashOutStream.write(start_send);
                    Thread.sleep(200);
                    startPoll();
                    StartReceive();//开始接收数据
                }catch (Exception e){}
            }
        };
        startThread.start();
    }

    private void startPoll(){
        sendPollThread = new Thread(){
            @Override
            public void run() {
                while (!stopCash){
                    byte[] start_send;
                    if(!statue){
                        start_send = new byte[]{0x7F,(byte)0x80,0x01,0x07,0x12,(byte) 0x02};
                    }else{
                        start_send = new byte[]{0x7F,(byte)0x00,0x01,0x07,0x11,(byte) 0x88};
                    }
                    statue = !statue;
                    try{
                        cashOutStream.write(start_send);
                    }catch (Exception e){}
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {}
                }
            }
        };
        sendPollThread.start();
    }

    private void StartReceive(){
        Log.i("money", "receiveThread线程开始");
        receiveThread = new Thread() {
            @Override
            public void run() {
                while (!stopRec) {
                    byte[] buffer = new byte[50];
                    try {
                        if (cashInputStream.available() > 0) {
                            int size = cashInputStream.read(buffer);
                            if (size > 0) {
                                byte[] buffer_sure = new byte[size];
                                for (int i = 0; i < size; i++) {
                                    buffer_sure[i] = buffer[i];
                                }
                                Log.i("money",Command.Hex2String(buffer_sure));

                                if(buffer_sure[0]==0x7F && buffer_sure[4] == -17){
                                    isCheck_money = true;
                                }else if(buffer_sure[0]==0x7F && buffer_sure[4] == 0x20){
                                    isCheck_money = false;
                                    is_get_money = false;
                                    get_money = 0;
                                }else if(buffer_sure[0]==0x7F && buffer_sure[4] == 0x23){
                                    isCheck_money = false;
                                    is_get_money = false;
                                    get_money = 0;
                                }

                                if(isCheck_money){
                                    if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x01){
                                        get_money = 1;
                                        Log.i("getMoney","get_money:"+1);
                                    }else if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x02){
                                        get_money = 5;
                                        Log.i("getMoney","get_money:"+5);
                                    }else if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x03){
                                        get_money = 10;
                                        Log.i("getMoney","get_money:"+10);
                                    }else if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x04){
                                        get_money = 20;
                                        Log.i("getMoney","get_money:"+20);
                                    }else if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x05){
                                        get_money = 50;
                                        Log.i("getMoney","get_money:"+50);
                                    }else if(buffer_sure[0]==0x7F && buffer_sure[2] == 0x03 && buffer_sure[5] == 0x06){
                                        get_money = 100;
                                        Log.i("getMoney","get_money:"+100);
                                    }
                                }

                                //收到1元纸币 7F 80 03 F0 Ef 01 XX XX
                                if(buffer_sure[0]==0x7F && buffer_sure[4] == -18 && buffer_sure[5] == 0x01){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到1元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(1);
                                    }
                                }
                                //收到5元纸币 7F 80 03 F0 Ef 02 XX XX
                                else if(buffer_sure[0]==0x7F && buffer_sure[4]==-18 && buffer_sure[5] == 0x02){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到5元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(5);
                                    }
                                }
                                //收到10元纸币 7F 80 03 F0 Ef 03 XX XX
                                else if(buffer_sure[0]==0x7F && buffer_sure[4]==-18 && buffer_sure[5] == 0x03){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到10元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(10);
                                    }
                                }
                                //收到20元纸币 7F 80 03 F0 Ef 04 XX XX
                                else if(buffer_sure[0]==0x7F && buffer_sure[4]==-18 && buffer_sure[5] == 0x04){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到20元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(20);
                                    }
                                }
                                //收到50元纸币 7F 80 03 F0 Ef 05 XX XX
                                else if(buffer_sure[0]==0x7F && buffer_sure[4]==-18 && buffer_sure[5] == 0x05){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到50元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(50);
                                    }
                                }
                                //收到100元纸币 7F 80 03 F0 Ef 06 XX XX
                                else if(buffer_sure[0]==0x7F && buffer_sure[4]==-18 && buffer_sure[5] == 0x06){
                                    get_money = 0;
                                    is_get_money = true;
                                    Log.i("getMoney","收到100元纸币");
                                    if(OnCashListening != null){
                                        OnCashListening.OnCashReceive(100);
                                    }
                                }else if(!is_get_money && get_money > 0){
                                    for(int i=0;i<buffer_sure.length;i++){
                                        if(buffer_sure[i] == -18){
                                            Log.i("getMoney","特殊收到"+get_money+"元纸币");
                                            if(OnCashListening != null){
                                                OnCashListening.OnCashReceive(get_money);
                                            }
                                            get_money = 0;
                                            is_get_money = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }catch (Exception e){}
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {}
                }
            }
        };
        receiveThread.start();
    }

    public void closePort() {
        stopCash = true;        //停止轮询
        stopRec = true;         //停止接收
        try {
            byte[] start_send;
            if(!statue){
                start_send = new byte[]{0x7F,(byte)0x80,0x01,0x09,0x35,(byte) 0x82};
            }else{
                start_send = new byte[]{0x7F,(byte)0x00,0x01,0x09,0x36,(byte) 0x08};
            }
            cashOutStream.write(start_send);
        }catch (Exception e){}
        if (cashSerialPort != null) {
            cashSerialPort.close();
        }
        if (cashSerialPort != null) {
            try {
                cashSerialPort.close();
            } catch (Exception e) {

            }
        }
        if (cashSerialPort != null) {
            try {
                cashSerialPort.close();
            } catch (Exception e) {

            }
        }
        isClose = true;
    }
}
