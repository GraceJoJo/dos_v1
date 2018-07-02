package com.auto.taiyijie;

/**
 * Created by Dr on 2016/11/23.
 */
import android.util.Log;

import org.cnbleu.serialport.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class Machine {
    private byte machine =1; //默认机器为1号机
    private int machine_num = 1; //默认只有一台机器

    protected SerialPort mSerialPort;  //串口通讯对象
    public InputStream mInputStream;  //输入流
    public OutputStream mOutputStream; //输出流
    public Command mCommand = new Command(); //指令对象

    private Thread receiveThread; //接收数据线程
    private Thread sendThread; //发送线程
    private Thread sendPollThread; //主板轮询线程
    private Thread sendEndThread; //发送确认指令线程
    private Thread checkAllChannelThread; //全货道检测线程
    private boolean checkAllChannelThread_Stop = false; //全货道检测线程 是否停止


    private boolean checkMachineFlag = false;//自检标识符 false检测通过 true检测未通过
    private int[] checkMachineRs;
    private boolean is_check = false;
    boolean isGoodIng = false; //当前是否在出货
    boolean isSureEnd = false; //是否完成上次的指令

    public interface OnMachineListening{
        void OnInitCheck(String msg);//机器自检
        void OnInitEnd(String msg);//机器自检结束
        void OnOpenSuccess();
        void OnOpenFailed();
        void OnCheckSoldGood(int code, String msg);//0不能出货  1可以出货
        void OnGoodIng(String channel);
        void OnError(int errorCode, String msg);
        void OnGoodEnd(String channel);
        void OnGoodError();
        void OnClosePort();
        void OnCheckChannelError(int channelId); //货道检测失败（调试用）
        void OnCheckChannelSuccess(int channelId); //货道检测成功（调试用）
        void OnCheckAllChannelEnd(); //货道全部检测成功（调试用）
    }
    OnMachineListening OnMachineListening=null;
    public void setOnMachineListening(OnMachineListening e){
        OnMachineListening=e;
    }

    public void init(String port,int _machine_num){
        try{
            this.closePort();
        }catch (Exception e){}
        try{
            port = "ttyXRM0";

            mSerialPort = new SerialPort();
            mSerialPort.open(
                    new File("/dev/"+port),
                    SerialPort.BAUDRATE.B9600,
                    SerialPort.STOPB.B1,
                    SerialPort.DATAB.CS8,
                    SerialPort.PARITY.NONE,
                    SerialPort.FLOWCON.NONE
            );
            mInputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();
            machine_num = _machine_num;

            //触发主板连接完成事件
            if(OnMachineListening != null){
                OnMachineListening.OnOpenSuccess();
            }

            checkMachine();//开始自检
        }catch (Exception error){
            Log.i("Dos","通讯端口连接失败");
            checkMachineFlag = false;
            //触发主板连接完成事件
            if(OnMachineListening != null){
                OnMachineListening.OnOpenFailed();
            }
        }
    }

    /**
     * 关闭串口
     */
    public void closePort() {
        if (mSerialPort != null) {
            mSerialPort.close();
        }
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mOutputStream != null) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //触发关闭端口事件
        if(OnMachineListening != null){
            OnMachineListening.OnClosePort();
        }
    }

    //机器自检
    public void checkMachine(){
        checkMachineRs = new int[machine_num+1];

        sendThread = new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[100];
                byte[] command;
                byte[] buffer_sure;
                for(int i=1;i<=machine_num;i++) {
                    if(is_check){
                        continue;
                    }else{
                        is_check = true;
                    }
                    Log.i("Dos", "自检:" + i + "号机器");
                    machine = (byte) i;
                    mCommand.setMachine(machine);
                    try {
                        //触发自检完成事件
                        if(OnMachineListening != null){
                            OnMachineListening.OnInitCheck("检测主板："+i);
                        }

                        command = mCommand.commandEnd();
                        for(int z = 0;z<5;z++){
                            Log.i("Dos", "自检发送数据"+Command.Hex2String(command));
                            mOutputStream.write(command);
                            Thread.sleep(200);
                        }

                        try {
                            Log.i("Dos", "接收等待1000ms");
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {}

                        //读取数据
                        buffer = new byte[100];
                        if (mInputStream.available()>0){
                            int size = mInputStream.read(buffer);
                            Log.e("Dos",Command.Hex2String(buffer));
                            if (size > 0) {
                                buffer_sure = new byte[4];
                                int len = 0;
                                boolean set = false;
                                for (int i2 = 0; i2 < size; i2++) {
                                    if(buffer[i2] == 0x00){
                                        set = true;
                                    }
                                    if(set){
                                        buffer_sure[i2] = buffer[i2];
                                        len+=1;
                                    }
                                    if(set && len == 4){
                                        break;
                                    }
                                }
                                Log.e("Dos", "自检接收数据"+Command.Hex2String(buffer_sure));
                                if(mCommand.checkCrc(buffer_sure)){
                                    //校验发送的为确认指令，所以接收也应该为确认指令6
                                    if(buffer_sure[1] == 0x06){
                                        checkMachineRs[i] = 1; //检测通过
                                        Log.i("Dos", "检测通过 检测通过 检测通过 检测通过");
                                    }else if(checkMachineRs[i]!=1){
                                        checkMachineRs[i] = 0; //检测失败
                                        Log.i("Dos", "检测没有通过 1");
                                    }
                                }else{
                                    Log.i("Dos", "自检接收数据 checkCrc 失败");
                                    checkMachineRs[i] = 0; //检测失败
                                }
                            }else{
                                checkMachineRs[i] = 0; //检测失败
                                Log.i("Dos", "检测没有通过 2");
                            }
                        }else{
                            checkMachineRs[i] = 0; //检测失败
                            Log.i("Dos", "检测没有通过 3");
                        }
                    } catch (Exception e) {
                        checkMachineRs[i] = 0; //检测失败
                        Log.i("Dos", "检测没有通过 4");
                    }

                    try {
                        command = mCommand.commandPoll();
                        mOutputStream.write(command);
                        //等待500ms  获取指令反馈
                        Log.i("Dos", "POLL指令发送，并等待500ms");
                        Thread.sleep(800);
                        mInputStream.read(buffer);
                        Log.i("Dos", "POLL指令接收，并再等待500ms:"+Command.Hex2String(buffer));
                        Thread.sleep(800);
                    } catch (Exception e) {}
                    is_check = false;
                }
                String msg = "";
                int failedNum = 0;
                for(int i=1;i<=machine_num;i++) {
                    Log.i("Dos", "检查结果："+i+"->"+checkMachineRs[i]);
                    if(checkMachineRs[i] == 0){
                        String seprate = "";
                        if(i!=machine_num){
                            seprate = "|";
                        }else{
                            seprate = "";
                        }
                        msg += i+ seprate;
                        failedNum++;
                    }
                }
                if(failedNum>0){
                    checkMachineFlag = false;
                    msg = "机器自检失败:"+msg;
                }else{
                    checkMachineFlag = true;
                    isSureEnd = true;
                    msg = "自检通过";
                }
                Log.i("Dos", "自检结果:" + msg);
                //触发自检完成事件
                if(OnMachineListening != null){
                    if(!is_check){
                        OnMachineListening.OnInitEnd(msg);
                    }
                }
            }
        };
        sendThread.start();
    }

    //检测是否可以出货
    public void checkSoldGood(byte machine){
        //检查该机器是否通过自检
        try{
            if(checkMachineRs[machine]==0){
                if(OnMachineListening != null){
                    OnMachineListening.OnError(1,"主板连接异常");
                }
                isSureEnd = true;
                return;
            }
        }catch (Exception e){
            if(OnMachineListening != null){
                OnMachineListening.OnError(1,"主板连接异常");
            }
            isSureEnd = true;
            return;
        }

        //通过自检的机器开始检测是否可以出货
        mCommand.setMachine(machine);
        sendThread = new Thread() {
            @Override
            public void run() {
                boolean isCanGood = false;
                try{
                    byte[] command = mCommand.commandPoll();
                    mOutputStream.write(command);
                    try {
                        Log.i("Dos", "1接收等待1500ms");
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {}

                    //读取数据
                    byte[] buffer = new byte[100];
                    if (mInputStream.available()>0){
                        int size = mInputStream.read(buffer);
                        if (size > 0) {
                            byte[] buffer_sure = new byte[size];
                            for (int i2 = 0; i2 < size; i2++) {
                                buffer_sure[i2] = buffer[i2];
                            }
                            Log.i("Dos", "1接收："+Command.Hex2String(buffer_sure));
                            if(mCommand.checkCrc(buffer_sure)){
                                //校验发送的为POLL指令，所以接收也应该为确认指令3 且poll结果为货道闲置状态
                                if(buffer_sure[1] == 3 && buffer_sure[2] == 0){
                                    isCanGood = true;
                                }else{
                                    isCanGood = false;
                                }
                            }else{
                                isCanGood = false;
                            }
                        }else{
                            isCanGood = false;
                        }
                    }else{
                        isCanGood = false;
                    }
                }catch (Exception e){
                    isCanGood = false;
                }

                if(isCanGood){
                    if(OnMachineListening != null){
                        OnMachineListening.OnCheckSoldGood(1,"可以出货");
                    }
                }else{
                    //触发出货异常事件 机器初始化失败不能出货
                    if(OnMachineListening != null){
                        OnMachineListening.OnCheckSoldGood(0,"稍后出货");
                    }
                }
            }
        };
        sendThread.start();


    }

    //出货
    public void soldGood(byte machine,byte channel){
        //检查该机器是否通过自检
        try{
            if(checkMachineRs[machine]==0){
                if(OnMachineListening != null){
                    OnMachineListening.OnError(1,"主板连接异常");
                }
                isSureEnd = true;
                return;
            }
        }catch (Exception e){
            if(OnMachineListening != null){
                OnMachineListening.OnError(1,"主板连接异常");
            }
            isSureEnd = true;
            return;
        }

        //通过自检的机器开始出货
        mCommand.setMachine(machine);
        mCommand.setChannel(channel);
        sendThread = new Thread() {
            @Override
            public void run() {
                byte[] command_good = mCommand.commandGood();
                byte[] command_poll = mCommand.commandPoll();
                byte[] command_end = mCommand.commandEnd();
                byte[] buffer = new byte[100];
                byte[] buffer_sure = new byte[100];
                int size;
                boolean isFailed = false;
                //1.先发确认指令
                try{
                    Log.i("Dos", "发送确认指令"+Command.Hex2String(command_end));
                    mOutputStream.write(command_end);

                    //等待500ms  获取指令反馈
                    Thread.sleep(800);
                    mInputStream.read(buffer);
                    Log.i("Dos", "确认指令接收:"+Command.Hex2String(buffer));

                    //2.发出货指令
                    Log.i("Dos", "发出货指令"+Command.Hex2String(command_good));
                    mOutputStream.write(command_good);

                    //等待500ms 获取指令反馈
                    Thread.sleep(800);
                    mInputStream.read(buffer);
                    Log.i("Dos", "出货指令接收:"+Command.Hex2String(buffer));

                    if(OnMachineListening != null){
                        OnMachineListening.OnGoodIng(Integer.toString(mCommand.getChannel()));
                    }

                    //等待2000ms  等待电机转动
                    Log.i("Dos", "接收等待3200ms");
                    Thread.sleep(3200);

                    //3.检测出货结果
                    Log.i("Dos", "发POLL结果"+Command.Hex2String(command_poll));
                    mOutputStream.write(command_poll);

                    //等待500ms 获取指令反馈
                    Thread.sleep(500);

                    //读取出货指令数据
                    buffer = new byte[100];
                    size = mInputStream.read(buffer);
                    buffer_sure = new byte[size];
                    for (int i2 = 0; i2 < size; i2++) {
                        buffer_sure[i2] = buffer[i2];
                    }
                    Log.i("Dos", "出货指令接收:"+Command.Hex2String(buffer_sure));

                    //检查出货结果
                    if(!checkGoodResult(buffer_sure)){
                        isFailed = true;
                    }

                    //4 确认指令完成
                    Log.i("Dos", "确认指令完成"+Command.Hex2String(command_end));
                    mOutputStream.write(command_end);
                    //等待500ms 获取指令反馈
                    Thread.sleep(500);
                    mInputStream.read(buffer);
                    Log.i("Dos", "确认指令接收:"+Command.Hex2String(buffer));


                }catch (Exception e){
                    isFailed = true;
                }

                if(isFailed && OnMachineListening != null){
                    Log.i("Dos", "出货失败");
                    OnMachineListening.OnCheckChannelError(buffer_sure[3]);
                }else{
                    OnMachineListening.OnCheckChannelSuccess(buffer_sure[3]);
                }

                try {
                    Log.i("Dos", "发POLL结果"+Command.Hex2String(command_poll));
                    mOutputStream.write(command_poll);
                    //等待500ms  获取指令反馈
                    Thread.sleep(600);
                    mInputStream.read(buffer);
                    Log.i("Dos", "接收POLL结果:"+Command.Hex2String(buffer));
                }catch (Exception e){}

                isSureEnd = true;
                return;
            }
        };
        sendThread.start();
    }

    //检查出货结果
    private boolean checkGoodResult(byte[] buffer_sure){
        boolean isFailed = false;
        if(mCommand.checkCrc(buffer_sure)){
            if(buffer_sure[1] == 3) {
                switch (buffer_sure[2]) {
                    case 0:
                        isGoodIng = false;
                        break;
                    case 1:
                        switch (buffer_sure[4]) {
                            case 0:
                                //触发出货事件
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnGoodIng(Integer.toString(buffer_sure[3]));
                                }
                                break;
                            case 3:
                                //触发出货异常事件 电机无停止信号
                                isFailed = true;
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnError(4, Integer.toString(buffer_sure[3]) + "电机无停止信号");
                                }
                                break;
                        }
                        break;
                    case 2:
                        switch (buffer_sure[4]) {
                            case 0:
                                //触发出货完成事件
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnGoodEnd(Integer.toString(buffer_sure[3]));
                                }
                                break;
                            case 1:
                                //触发出货异常事件 电机过流
                                isFailed = true;
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnError(2, Integer.toString(buffer_sure[3]) + "电机过流");
                                }
                                break;
                            case 2:
                                //触发出货异常事件 电机断线
                                isFailed = true;
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnError(3, Integer.toString(buffer_sure[3]) + "电机断线");
                                }
                                break;
                            case 3:
                                //触发出货异常事件 电机无停止信号
                                isFailed = true;
                                if (OnMachineListening != null) {
                                    OnMachineListening.OnError(4, Integer.toString(buffer_sure[3]) + "电机无停止信号.");
                                }
                                break;
                        }
                        break;
                }
            }

            if(isFailed){
                return false;
            }else{
                return true;
            }
        }else{
            return false;
        }
    }

    public void checkAllChannel(byte machine){
        checkAllChannelThread_Stop = false;
        checkAllChannelThread sendThread = new checkAllChannelThread();
        sendThread.setMachine(machine);
        sendThread.start();
    }
    public class checkAllChannelThread extends Thread {
        private byte machine;
        public void setMachine(byte temp) {
            this.machine = temp;
        }
        @Override
        public void run(){
            if(checkMachineFlag){
                for(int i=0;i<100;i++){
                    Log.e("Dos", "检测货道:"+this.machine+"|"+i);
                    soldGood(this.machine,(byte) i);
                    isSureEnd = false;
                    while (!isSureEnd){
                        try {
                            Log.i("Dos", "等待100ms");
                            Thread.sleep(100);
                            if(checkAllChannelThread_Stop){
                                isSureEnd = true;
                            }
                        } catch (InterruptedException e) {}
                    }
                    if(checkAllChannelThread_Stop){
                        i=999;
                    }
                }
                Log.e("Dos", "主板全部检测完成");
                //触发全货到测试完成事件
                if(OnMachineListening != null){
                    OnMachineListening.OnCheckAllChannelEnd();
                }
            }else{
                Log.e("Dos", "主板初未始化成功");
                //触发出货异常事件 机器初始化失败不能出货
                if(OnMachineListening != null){
                    OnMachineListening.OnError(1,"主板连接异常");
                }
            }
        }
    }

    //停止全货道检测
    public void stopAllCheck(){
        checkAllChannelThread_Stop = true;
    }
}