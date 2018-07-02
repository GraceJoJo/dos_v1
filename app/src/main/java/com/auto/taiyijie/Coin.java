package com.auto.taiyijie;
import android.util.Log;
import org.cnbleu.serialport.SerialPort;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Xjr on 2017/6/10.
 */

public class Coin {
    protected SerialPort coinSerialPort;    //端口连接类
    private boolean isClose = false;        //状态
    private boolean action = false;
    public InputStream coinInputStream;     //读取
    public OutputStream coinOutStream;      //发送
    private Thread receiveThread;           //接受命令线程
    private Thread sendPollThread;          //发送命令线程
    private Thread checkPollThread;          //发送检查线程
    private Thread returnThread;          //发送检查线程
    public boolean stopCoin = false;        //停止轮询
    public boolean stopCheck = false;        //停止检查
    public boolean stopRec = false;         //停止接收
    public boolean stopReturn = false;         //停止找零
    public boolean return_success = false;    //找零成功状态
    public boolean return_type = false;     //是否要找零
    public int last_coin_num = 0;           //上次硬币数量
    public int return_coin_num = 0;           //找零数量

    public interface OnCoinListening{
        void OnCoinReceive(int num); //收到现金
        void OnCoinConnectError(); //收银器连接失败
        void OnCoinConnectSuccess(); //收银器连接成功
        void OnCoinLack();//缺少硬币
    }
    OnCoinListening OnCoinListening=null;
    public void setOnCoinListening(OnCoinListening e){
        OnCoinListening=e;
    }

    public void start(String port){
        try{
            closePort();
        }catch (Exception e){}
        port = "ttyS1";
        SerialPort coinSerialPort = new SerialPort();
        Log.i("Dos", "打开硬币端口_开始");
        coinSerialPort.open(
                new File("/dev/" + port),
                SerialPort.BAUDRATE.B9600,
                SerialPort.STOPB.B1,
                SerialPort.DATAB.CS8,
                SerialPort.PARITY.EVEN,
                SerialPort.FLOWCON.NONE
        );
        Log.i("Dos", "打开硬币端口_结束");
        coinInputStream = coinSerialPort.getInputStream();
        coinOutStream = coinSerialPort.getOutputStream();
        stopCoin = false;
        stopRec = false;
        stopCheck = false;
        isClose = false;
        StartReceive();//开始接收数据
        clean_coin();  //清零
    }

    private void startCoin(){
        Log.i("Dos", "sendPollThread线程开始");
        if(OnCoinListening != null){
            OnCoinListening.OnCoinConnectSuccess();
        }else{
            OnCoinListening.OnCoinConnectError();
        }
        sendPollThread = new Thread() {
            @Override
            public void run() {
                while (!stopCoin) {
                    byte[] start_send = new byte[]{0x05,0x10,0x00,0x40,0x00,0x55};
                    try{
//                        Log.i("Dos", "现金收银发送启动命令"+Command.Hex2String(start_send));
                        coinOutStream.write(start_send);
                    }catch (Exception e){}
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {}
                }
            }
        };
        sendPollThread.start();
    }

    private void checkCoin(){
        Log.i("Dos", "checkPollThread线程开始");
        Log.i("Dos", ""+stopCheck);
        checkPollThread = new Thread() {
            @Override
            public void run() {
                while (!stopCheck) {
                    byte[] start_send = new byte[]{0x05,0x10,0x00,0x11,0x00,0x26};
                    try{
                        Log.i("Dos", "现金收银发送检查命令"+Command.Hex2String(start_send));
                        coinOutStream.write(start_send);
                    }catch (Exception e){}
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {}
                }
            }
        };
        checkPollThread.start();
    }

    private void StartReceive(){
        Log.i("Dos", "receiveThread线程开始");
        receiveThread = new Thread(){
            @Override
            public void run() {
                while (!stopRec) {
                    byte[] buffer = new byte[50];
                    try {
                        if (coinInputStream.available() > 0) {
                            int size = coinInputStream.read(buffer);
                            if (size > 0) {
                                byte[] buffer_sure = new byte[size];
                                for (int i = 0; i < size; i++) {
                                    buffer_sure[i] = buffer[i];
                                }
                                Log.i("Dos",Command.Hex2String(buffer_sure));
                                //收到硬币 05 01 00 30 XX XX
//                                Log.i("coin", Command.Hex2String(buffer_sure));
                                if(buffer_sure[0]==0x05 && buffer_sure[1]==0x01 && buffer_sure[2]==0x00 && buffer_sure[3] == 0x30){
                                    Log.i("coin", "收到硬币"+ Command.HexInt(buffer_sure[4]) + "个");
                                    if(Command.HexInt(buffer_sure[4]) == 0){
                                        action = true;
                                    }
                                    if((Command.HexInt(buffer_sure[4]) - last_coin_num) == 1 && action){
                                        last_coin_num = Command.HexInt(buffer_sure[4]);
                                        if(OnCoinListening != null){
                                            OnCoinListening.OnCoinReceive(1);
                                        }
                                    }
                                }else if(buffer_sure[0]==0x05 && buffer_sure[1]==0x01 && buffer_sure[2]==0x00 && buffer_sure[3] == 0x31){
                                    Log.i("Dos", Command.Hex2String(buffer_sure));
                                    if(buffer_sure[5] == 0x36){
                                        Log.i("Dos","清零成功");
                                        last_coin_num = 0;
                                        if(!isClose) {
                                            checkCoin();
                                        }
                                    }else{
                                        Log.e("Dos","清零失败");
                                        clean_coin();
                                    }
                                }else if(buffer_sure[0]==0x05 && buffer_sure[1]==0x01 && buffer_sure[2]==0x00 && buffer_sure[3] == 0x04 && buffer_sure[4] == 0x02){
                                    Log.i("Dos", "找零硬币不足，请扫码支付");
                                    stopCheck = true;
                                    if(OnCoinListening != null){
                                        OnCoinListening.OnCoinLack();
                                    }
                                }else if(buffer_sure[0]==0x05 && buffer_sure[1]==0x01 && buffer_sure[2]==0x00 && buffer_sure[3] == 0x04 && buffer_sure[4] == 0x00){
                                    Log.i("Dos", "硬币充足");
                                    stopCheck = true;
                                    if(!isClose && !return_type){
                                        startCoin();
                                    }
                                }
                            }
                        }
                    }catch (Exception e){}
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {}
                }
            }
        };
        receiveThread.start();
    }

    //退币
    public void return_coin(int num){
        Log.i("Dos", "硬币退币数量:"+num);
        return_coin_num = num;
        returnThread = new Thread(){
            @Override
            public void run() {
                return_type = true;
                stopCoin = true;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                byte Hex_num = (byte)(return_coin_num & 0xff);
                byte[] buffer_coin = new byte[]{0x05,0x10,0x00,0x10,Hex_num,(byte)((0x05+0x10+0x00+0x10+Hex_num)& 0xff)};
                Log.i("Dos", "硬币退币开始:"+Command.Hex2String(buffer_coin)+Hex_num+"个");
                try{
                    coinOutStream.write(buffer_coin);
                }catch (Exception e){}
                closePort();
            }
        };
        returnThread.start();
    }

    //清零
    public void clean_coin(){
        byte[] buffer_coin = new byte[]{0x05,0x10,0x00,0x41,0x00,0x05+0x10+0x00+0x41+0x00};
        try{
            Log.i("Dos", "硬币清零");
            coinOutStream.write(buffer_coin);
        }catch (Exception e){}
    }

    public void closePort() {
        Log.i("Dos", "关闭端口");
        if (coinSerialPort != null) {
            coinSerialPort.close();
        }
        if (coinSerialPort != null) {
            try {
                coinSerialPort.close();
            } catch (Exception e) {

            }
        }
        if (coinSerialPort != null) {
            try {
                coinSerialPort.close();
            } catch (Exception e) {

            }
        }
        isClose = true;
        stopCoin = true;
        stopCheck = true;
        stopRec = true;
        stopReturn = true;
    }
}
