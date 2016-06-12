package min3d.sampleProject1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Kairong on 2015/8/24.
 * mail:wangkrhust@gmail.com
 */
public class CameraHelper {
    private Context mContext;
    private Camera camera;
    private SurfaceHolder holder;
    private Camera.Parameters photoParameters;
    private Handler handler;
    private CameraUtil cameraUtil;

    public volatile String savedPhotoPath;
    public int displayWidth;
    public int displayHeight;
    public int cameraCount;
    public int camera_position;
    public int save_photo_state;
    public int mMaxZoom;
    public boolean isPreviewing;
    public boolean focuseState;
    public boolean focusing;


    private boolean isAutoFocus = true;
    private boolean iftakepicture = false;
    private int orientation;
    private int barrier_height;

    /*照片保存状态*/
    public final static int SAVING_PHOTO = 3323;
    public final static int SAVED_PHOTO = 3324;
    public final static int SAVED_ERROR = 3325;
    public final static int SAVE_PICTURE_DONE = 3326;
    /*显示对焦状态*/
    public final static int MSG_FOCUSING = 3234;
    public final static int MSG_FOCUSED = 3235;
    public final static int MSG_FOCUS_FAILED = 3236;
    /*主线程拍照消息*/
    public final static int MSG_TAKE_PICTURE = 3237;
    /*终止主线程*/
    public final static int MSG_EXIT_APP = 3238;


    public int flashLightMode;
    /*闪光灯状态*/
    public final static int FLIGHT_OFF = 3241;
    public final static int FLIGHT_ON = 3240;
    public final static int FLIGHT_AUTO = 3242;
    public final static int FLIGHT_NONE = 3243;

    public final static String IMAGE_SAVE = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)+"/Camera";
    public static final float MAX_ZOOM_GESTURE_SIZE = 2.5f; // This affects the pinch to zoom gesture

    public CameraHelper(Context mContext, SurfaceHolder holder, Handler handler, int displayWidth, int displayHeight) {
        this.handler = handler;
        this.mContext = mContext;
        this.holder = holder;
        this.displayHeight = displayHeight;
        this.displayWidth = displayWidth;
        this.isPreviewing = false;
        this.focuseState = false;
        this.cameraCount = Camera.getNumberOfCameras();//得到摄像头的个数
        cameraUtil = CameraUtil.getCameraUtil();
    }
    public void open(){
        // 默认打开后置摄像头
        if(camera!=null){
            camera.release();
            camera = null;
        }

        camera = Camera.open();

        try {
            // 设置摄像头参数
            setParameters(Camera.CameraInfo.CAMERA_FACING_BACK);
            camera.setPreviewDisplay(holder);// 通过surfaceview显示取景画面
            camera.setDisplayOrientation(90);
            camera.startPreview();// 开始预览
            camera_position = Camera.CameraInfo.CAMERA_FACING_BACK;
            isPreviewing = true;
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    public void open(int position){
        if(camera!=null){
            camera.release();
            camera = null;
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        if(cameraCount == 0){
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "系统没有检测到摄像头", Toast.LENGTH_LONG).show();
                }
            });
            handler.sendEmptyMessage(MSG_EXIT_APP);
            return;
        } else if(cameraCount == 1){
            camera = Camera.open();
        } else if(cameraCount >= 2) {
            for (int i = 0; i < cameraCount; i++) {
                Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
                if (position == cameraInfo.facing) {
                    camera = Camera.open(i);
                    break;
                }
            }
        }

        try {
            // 设置摄像头参数
            setParameters(position);
            camera.setPreviewDisplay(holder);// 通过surfaceview显示取景画面
            camera.setDisplayOrientation(90);
            camera.startPreview();      // 开始预览
            if(position== Camera.CameraInfo.CAMERA_FACING_BACK)
                autoFocus(true,false); // 自动对焦
            isPreviewing = true;
            camera_position = position;
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    /**
     * 设置摄像头参数
     * @param camera_position 摄像头位置
     */
    private void setParameters(int camera_position){
        photoParameters = camera.getParameters();

        // 初始化最大缩放参数
        if (photoParameters.isZoomSupported()) {
            mMaxZoom = photoParameters.getMaxZoom();
        } else {
            mMaxZoom = 0;
        }

        try {
            // 预览和图片分辨率都设置为屏幕分辨率
            photoParameters.setPreviewSize(displayHeight, displayWidth);
            Camera.Size picSize = cameraUtil.getPicSizeOfScreen(camera_position, displayWidth, displayHeight);
            photoParameters.setPictureSize(picSize.width,picSize.height);
        }catch (NullPointerException e){
            e.printStackTrace();
            String msg = "抱歉，您的摄像头不支持!";
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
        }

        camera.setParameters(photoParameters);
    }
    
    public void stop(){
        // 置预览回调为空，再关闭预览
        if(camera!=null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            isPreviewing = false;
        }
    }

    /**
     * 调整焦距
     * @param zoomScaleFactor
     */
    public void setCameraZoom(float zoomScaleFactor) {
        // Convert gesture to camera zoom value
        int zoom = (int) ((zoomScaleFactor - 1) * mMaxZoom);

        // Sanity check for zoom level
        if (zoom > mMaxZoom) {
            zoom = mMaxZoom;
        } else if (zoom < 0) {
            zoom = 0;
        }

        // Update the camera with the new zoom if it is supported
        if (photoParameters.isZoomSupported()) {
            photoParameters.setZoom(zoom);
            camera.setParameters(photoParameters);
        }
    }

    /**
     *切换前置或者后置摄像头
     */
    public void switchCamera(){
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        if(cameraCount <= 1){
            Toast.makeText(mContext, "系统只检测到一个摄像头", Toast.LENGTH_LONG).show();
            return;
        }
        for(int i = 0; i < cameraCount;i++) {
            Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
            if(camera_position == Camera.CameraInfo.CAMERA_FACING_BACK) {
                //现在是后置，变更为前置
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表摄像头的方位，CAMERA_FACING_FRONT前置  CAMERA_FACING_BACK后置
                    camera_position = Camera.CameraInfo.CAMERA_FACING_FRONT;
                    camera.stopPreview();   //停掉原来摄像头的预览
                    camera.release();       //释放资源
                    camera = null;          //取消原来摄像头
                    camera = Camera.open(i);//打开当前选中的摄像头

                    // 设置摄像头参数
                    setParameters(Camera.CameraInfo.CAMERA_FACING_FRONT);

                    try {
                        camera.setPreviewDisplay(holder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    camera.setDisplayOrientation(90);
                    camera.startPreview();//开始预览
                    break;
                }
            }
            if(camera_position == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //现在是前置， 变更为后置
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_BACK) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    camera.stopPreview();//停掉原来摄像头的预览
                    camera.release();//释放资源
                    camera = null;//取消原来摄像头
                    camera = Camera.open(i);//打开当前选中的摄像头

                    try {
                        camera.setPreviewDisplay(holder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    // 设置摄像头参数
                    setParameters(Camera.CameraInfo.CAMERA_FACING_BACK);

                    camera.setDisplayOrientation(90);
                    camera.startPreview();//开始预览
                    camera_position = Camera.CameraInfo.CAMERA_FACING_BACK;
                    break;
                }
            }

        }
    }

    /**
     * 自动对焦回调函数
     */
    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback(){

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if(success){
                if(!isAutoFocus) {
                    handler.sendEmptyMessage(MSG_FOCUSED);
                } else if(iftakepicture){
                    handler.sendEmptyMessage(MSG_TAKE_PICTURE);
                    iftakepicture = false;
                }
            } else {
                if(!isAutoFocus)
                    handler.sendEmptyMessage(MSG_FOCUS_FAILED);
            }
            focuseState = success;
            focusing = false;
        }
    };

    /**
     * 自动对焦函数
     * @param isAutoFocus 是否自动对焦
     * @param iftakepicture 是否对焦成功后拍照
     */
    public synchronized void autoFocus(boolean isAutoFocus,boolean iftakepicture){
        if(focusing){
            return;
        }
        this.iftakepicture = iftakepicture;
        focuseState = false;
        focusing = true;
        this.isAutoFocus = isAutoFocus;
        if(!isAutoFocus)
            handler.sendEmptyMessage(MSG_FOCUSING);
        camera.autoFocus(autoFocusCallback);
    }

    public void restartPreview(){
        camera.setDisplayOrientation(90);
        camera.startPreview();
        isPreviewing = true;
        camera.autoFocus(autoFocusCallback);
    }

    // 创建jpeg图片回调数据对象,对图片进行旋转和初步裁剪
    private Camera.PictureCallback jpeg = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, final Camera camera) {
            isPreviewing = false;
            new Thread(){
                @Override
                public void run() {
                    camera.stopPreview();
                    Bitmap saved_photo = BitmapFactory.decodeByteArray(data, 0, data.length);
                    save_photo_state = SAVING_PHOTO;
                    savePictureToCache(saved_photo);
                }
            }.start();
        }
    };

    /**
     * 拍照回调函数
     */
    public void takePhoto(){
        // 每次拍照前设置一下闪光灯
        setFlashLightMode();
        camera.takePicture(null,null,jpeg);
    }
    /**
     * 切换闪光灯的状态
     */
    public void switchFlashLightMode(){
        switch (flashLightMode){
            case FLIGHT_OFF:
                this.flashLightMode = FLIGHT_ON;
                setFlashLightMode();
                break;
            case FLIGHT_ON:
                this.flashLightMode = FLIGHT_AUTO;
                setFlashLightMode();
                break;
            case FLIGHT_AUTO:
                this.flashLightMode = FLIGHT_OFF;
                setFlashLightMode();
                break;
            default:
                break;
        }
    }

    /**
     * 设置闪光灯状态
     */
    private void setFlashLightMode(){
        // 目前只支持后置闪关灯
        if(camera_position == Camera.CameraInfo.CAMERA_FACING_FRONT){
            return;
        }
        switch (flashLightMode)
        {
            case FLIGHT_AUTO:
                photoParameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
            case FLIGHT_OFF:
                photoParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                break;
            case FLIGHT_ON:
                photoParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                break;
            default:
                break;
        }
        camera.setParameters(photoParameters);

    }
    private void savePictureToCache(Bitmap saved_photo) {
        // 旋转90度
        Matrix mRotate = new Matrix();
        if(camera_position == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mRotate.setRotate(90);
        } else if(camera_position == Camera.CameraInfo.CAMERA_FACING_FRONT){
            mRotate.setRotate(-90);
        }

        saved_photo = Bitmap.createBitmap(saved_photo, 0, 0, saved_photo.getWidth(), saved_photo.getHeight(),  mRotate, true);

        String filename = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(new Date());
        File file = new File(mContext.getCacheDir(), filename);
        savedPhotoPath = file.getPath();

        /*保存成临时文件*/
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            saved_photo.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();    // 刷新此缓冲区的输出流
            bos.close();    // 关闭此输出流并释放与此流有关的所有系统资源
            save_photo_state = SAVED_PHOTO;
            // 向MainActivity发送图片保存完成消息
            sendSavedMsg();
        } catch (IOException | NullPointerException e){
            e.printStackTrace();
            save_photo_state = SAVED_ERROR;
            handler.sendEmptyMessage(SAVED_ERROR);
        } finally {
            // 释放内存
            if(saved_photo!=null&&!saved_photo.isRecycled()){
                saved_photo.recycle();
                System.gc();
            }
        }
    }

    /**
     * 将保存的图片路径发送给MainActivity主线程处理
     */
    private void sendSavedMsg(){
        Message.obtain(handler, SAVE_PICTURE_DONE, savedPhotoPath).sendToTarget();
    }
}
