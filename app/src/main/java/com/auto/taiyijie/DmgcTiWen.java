/**
 * Created by Dr on 2016/11/28.
 */
package com.auto.taiyijie;

import android.util.Log;

import org.cnbleu.serialport.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;


public class DmgcTiWen {
    protected SerialPort tempSerialPort;  //串口通讯对象
    public InputStream tempInputStream;  //输入流
    public OutputStream tempOutputStream; //输出流
    private Thread receiveThread; //接收数据线程
    private Thread sendPollThread; //轮询线程
    private boolean isClose = false;
    public boolean receive = false;
    public double num_tiwen = 0;

    DmgcTiWen() {
    }

    public void startTiwen(String port) {
        try {
            this.closePort();
        } catch (Exception e) {
        }
        isClose = false;
        try {
            Log.i("Dos", "开始连接体温计:" + port);
            port = "ttyS3";

            tempSerialPort = new SerialPort();
            tempSerialPort.open(
                    new File("/dev/" + port),
                    SerialPort.BAUDRATE.B9600,
                    SerialPort.STOPB.B1,
                    SerialPort.DATAB.CS8,
                    SerialPort.PARITY.NONE,
                    SerialPort.FLOWCON.NONE
            );
            tempInputStream = tempSerialPort.getInputStream();
            tempOutputStream = tempSerialPort.getOutputStream();
//            startPoll();//开始POLL数据
            startReceive();//开始接收数据
            Log.i("Dos", "体温计连接成功");
        } catch (Exception error) {
            Log.e("Dos", "体温计连接失败");
        }
    }

    //发送温湿度数据 2秒发送一次
    private void startPoll() {
        sendPollThread = new Thread() {
            @Override
            public void run() {
                while (!isClose) {
                    try {
                        byte[] command = getPollCommand();
                        //Log.i("Dos", "poll温度"+Hex2String(command));
                        tempOutputStream.write(command);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                        }
                    } catch (IOException e) {
                        Log.e("Dos", "体温指令发送失败");
                    }
                }
            }
        };
        sendPollThread.start();
    }

    //接收温湿度数据
    private void startReceive() {
        receiveThread = new Thread() {
            @Override
            public void run() {
                while (!isClose) {
                    try {
                        //读取数据
                        byte[] buffer = new byte[100];
                        if (tempInputStream != null && tempInputStream.available() > 0) {
                            int size = tempInputStream.read(buffer);
                            //Log.i("Dos", "体温数据111:" + Hex2String(buffer));
                            if (size > 0) {
                                byte[] buffer_sure = new byte[size];
                                for (int i = 0; i < size; i++) {
                                    buffer_sure[i] = buffer[i];
                                }

                                try {
                                    byte[] tiwen = new byte[]{buffer_sure[4], buffer_sure[5]};  //体温
                                    BigInteger bigNum1 = new BigInteger(tiwen);
                                    double Num1 = bigNum1.intValue() / 10;
                                    num_tiwen = Num1;
                                    //Log.i("Dos", "体温数据:" + num_tiwen);

                                    byte[] huanjing = new byte[]{buffer_sure[6], buffer_sure[7]}; //环境温度
                                    BigInteger bigNum2 = new BigInteger(huanjing);
                                    double Num2 = bigNum2.intValue() / 10;

                                    //Log.i("Dos", "体温：" + Num1 + " # 环境温度" + Num2);
                                } catch (Exception e) {
                                }

                            }
                        }
                    } catch (IOException e) {
                        Log.e("Dos", "体温读取失败");
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
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


    //组合要POLL的指令，并对其添加CRC校验
    public byte[] getPollCommand() {
        byte[] buff = new byte[4];
        buff[0] = 0x66; //地址
        buff[1] = 0x66; //功能码 设置地址
        buff[2] = 0x02; //数据位高位
        buff[3] = 0x56; //数据位低位
        return buff;
    }

    //CRC校验
    public static int CRC16_Check(byte pcMess[]) {
        int[] table = {
                0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
                0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
                0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
                0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
                0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
                0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
                0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
                0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
                0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
                0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
                0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
                0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
                0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
                0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
                0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
                0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
                0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
                0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
                0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
                0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
                0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
                0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
                0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
                0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
                0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
                0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
                0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
                0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
                0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
                0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
                0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
                0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
        };

        int Index;
        int nCRCData = 0xffff;
        int i = 0;
        int wLen = pcMess.length;
        while (wLen-- != 0) {
            Index = nCRCData >> 8;
            Index = Index ^ (pcMess[i++] & 0x00ff);
            nCRCData = (nCRCData ^ table[Index]) & 0x00ff;
            nCRCData = (nCRCData << 8) | (table[Index] >> 8);
        }
        return nCRCData >> 8 | nCRCData << 8;
    }

    //字节转字符串
    public static String Hex2String(byte[] b) {
        String temp = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            temp += hex.toUpperCase() + " ";
        }
        return temp;
    }

    //CRC合法性校验
    public boolean checkCrc(byte[] buff) {
        int len = buff.length;
        if (len >= 4) {
            boolean crc_check = false;
            byte[] temp = new byte[len - 2];//新的数组
            for (int i = 0; i < len - 2; i++) {
                temp[i] = buff[i];
            }
            int my_crc = CRC16_Check(temp);
            int low_crc = buff[len - 2];
            int height_crc = buff[len - 1];
            if (low_crc == (byte) (my_crc & 0xff) && height_crc == (byte) (my_crc >> 8)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
