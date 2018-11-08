package com.example.app.scanlibrary.zxing;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Hashtable;

/**
 * Created by qtpay on 17/12/7.
 */

public class QrCodeUtils {

    /**
     * 生成二维码
     * @param string  二维码中包含的文本信息
     * @param mBitmap logo图片
     * @param format  编码格式
     *                Bitmap 生成bitmap显示
     * @throws WriterException
     */
    public static Bitmap haveLogoQrCode(String string,
                                        Bitmap mBitmap,
                                        BarcodeFormat format,
                                        int showQrWidth, int logoWidth) {
        Bitmap bitmap = null;
        try {
            Matrix m = new Matrix();//存放logo的矩阵
            float sx = (float) 2 * logoWidth / mBitmap.getWidth();
            float sy = (float) 2 * logoWidth
                    / mBitmap.getHeight();
            m.setScale(sx, sy);//设置缩放信息
            //将logo图片按martix设置的信息缩放
            mBitmap = Bitmap.createBitmap(mBitmap, 0, 0,
                    mBitmap.getWidth(), mBitmap.getHeight(), m, false);
            MultiFormatWriter writer = new MultiFormatWriter();
            Hashtable hst = new Hashtable();
            hst.put(EncodeHintType.CHARACTER_SET, "UTF-8");//设置字符编码
            hst.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);//设置二维码高容错，避免因图片遮挡而读不出内容

            BitMatrix matrix = writer.encode(string, format, showQrWidth, showQrWidth, hst);//生成二维码矩阵信息
            int width = matrix.getWidth();//矩阵高度
            int height = matrix.getHeight();//矩阵宽度
            int halfW = width / 2;
            int halfH = height / 2;
            int[] pixels = new int[width * height];//定义数组长度为矩阵高度*矩阵宽度，用于记录矩阵中像素信息
            for (int y = 0; y < height; y++) {//从行开始迭代矩阵
                for (int x = 0; x < width; x++) {//迭代列
                    if (x > halfW - logoWidth && x < halfW + logoWidth
                            && y > halfH - logoWidth
                            && y < halfH + logoWidth) {
                        //该位置用于存放图片信息记录图片每个像素信息
                        pixels[y * width + x] = mBitmap.getPixel(x - halfW
                                + logoWidth, y - halfH + logoWidth);
                    } else {
                        if (matrix.get(x, y)) {//如果有黑块点，记录信息
                            pixels[y * width + x] = 0xff000000;//记录黑块信息
                        }
                    }
                }
            }
            bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);
            // 通过像素数组生成bitmap
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * url转bitmap显示二维码
     *
     * @param qrURL
     * @param width
     * @return
     */
    public static Bitmap urlToQRCode(String qrURL, int width) {
        Bitmap qrcodeBitmap = null;
        if (null == qrURL || "".equals(qrURL)) {
            return null;
        }
        try {
            //生成二维码图片，第一个参数是二维码的内容，第二个参数是正方形图片的边长，单位是像素
            qrcodeBitmap = EncodingHandler.createQRCode(qrURL, width);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return qrcodeBitmap;
    }
}
