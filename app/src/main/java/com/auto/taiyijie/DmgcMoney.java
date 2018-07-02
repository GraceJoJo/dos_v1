/**
 * Created by Dr on 2016/11/28.
 */
package com.auto.taiyijie;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.cnbleu.serialport.SerialPort;


public class DmgcMoney {
    protected SerialPort tempSerialPort;  //串口通讯对象
    protected int baudrate = 9600;
    public InputStream tempInputStream;  //输入流
    public OutputStream tempOutputStream; //输出流
    private Thread receiveThread; //接收数据线程
    private boolean isClose = false;

    DmgcMoney(String port){
        try{
            this.closePort();
        }catch (Exception e){}
        isClose = false;
        try{
            Log.i("Dos","开始连接收银器:"+port);
            int stopbit = 1; //停止位
            int databit = 8; //数据位
            int parity = 0; //校验位
            int flowCon = 0; //流控
            tempSerialPort = new SerialPort();
            tempSerialPort.open(
                    new File("/dev/"+port),
                    SerialPort.BAUDRATE.B9600,
                    SerialPort.STOPB.B1,
                    SerialPort.DATAB.CS8,
                    SerialPort.PARITY.NONE,
                    SerialPort.FLOWCON.NONE
            );
            tempInputStream = tempSerialPort.getInputStream();
            tempOutputStream = tempSerialPort.getOutputStream();
            startReceive();//开始接收数据
            Log.i("Dos","收银器连接成功");
        }catch (Exception error){
            Log.e("Dos","收银器连接失败");
        }
    }
    //接收温湿度数据
    private void startReceive(){
        receiveThread = new Thread(){
            @Override
            public void run(){
                while (!isClose){
                    try{
                        //读取数据
                        byte[] buffer = new byte[100];
                        if (tempInputStream.available()>0) {
                            int size = tempInputStream.read(buffer);
                            if (size == 16) {
                                byte[] buffer_sure = new byte[size];
                                for (int i = 0; i < size; i++) {
                                    buffer_sure[i] = buffer[i];
                                }
                            }
                        }
                        try {
                            //Log.i("Dos", "温湿度接收等待800ms");
                            Thread.sleep(800);
                        } catch (InterruptedException e) {}

                    }catch (IOException e){
                        Log.e("Dos","收银器读取失败");
                    }
                }
            }
        };
        receiveThread.start();
    }

    /**
     * 关闭串口
     */
    public void closePort() {
        if (tempSerialPort != null) {
            tempSerialPort.close();
        }
        if (tempInputStream != null) {
            try {
                tempInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (tempOutputStream != null) {
            try {
                tempOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        isClose = true;
    }

    //字节转字ASCII字符串
    public static String bytesToAsciiString(byte[] bytes){
        char[] tChars=new char[bytes.length];
        String a="";
        for(int i=0;i<bytes.length;i++) {
            tChars[i] = (char) bytes[i];
            a += tChars[i];
        }
        //Log.i("Dos","温湿度结果："+a);
        return  a;
    }

}
