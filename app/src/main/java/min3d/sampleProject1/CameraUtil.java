package min3d.sampleProject1;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.provider.MediaStore;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Kairong on 2015/6/12.
 * mail:wangkrhust@gmail.com
 */
public final class CameraUtil {
    // 摄像头个数
    private int cameraCount = 0;
    // 前置摄像头相机支持的预览尺寸列表
    private static List<Map<String,Object>> supportedPreviewSizesFront = null;
    // 前置相机支持的图片尺寸列表
    private static List<Map<String,Object>> supportedPictureSizesFront = null;
    // 后置摄像头相机支持的预览尺寸列表
    private static List<Map<String,Object>> supportedPreviewSizesBack = null;
    // 后置相机支持的图片尺寸列表
    private static List<Map<String,Object>> supportedPictureSizesBack = null;


    private static final String KEY_VALUE = "value";
    private static final String KEY_RATIO = "ratio";

    private static CameraUtil cameraUtil = null;

    private CameraUtil(){
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        // 对分辨率进行排序
        Comparator comp = new Comparator() {
            @Override
            public int compare(Object lhs, Object rhs) {
                Size sl = (Size)lhs;
                Size sr = (Size)rhs;
                long resoll = ((Size) lhs).height*((Size) lhs).width;
                long resolr = ((Size) rhs).height*((Size) rhs).width;
                if(resoll>resolr)
                    return -1;
                else if(resoll==resolr)
                    return 0;
                else if(resoll<resolr)
                    return 1;
                return 0;
            }
        };
        for(int i = 0;i < cameraCount;i++){
            Camera camera = Camera.open(i);
            Camera.getCameraInfo(i, cameraInfo);
            List<Size> prewSizes = null,picSizes = null;
            if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                supportedPreviewSizesFront = new ArrayList<Map<String,Object>>();
                supportedPictureSizesFront = new ArrayList<Map<String,Object>>();
                Camera.Parameters parameters = camera.getParameters();
                prewSizes = parameters.getSupportedPreviewSizes();
                picSizes = parameters.getSupportedPictureSizes();
                Collections.sort(picSizes, comp);
                Collections.sort(prewSizes, comp);
                Map<String,Object> map = null;
                for(int j = 0;j < picSizes.size();j++){
                    map = new HashMap<String, Object>();
                    map.put(KEY_VALUE,picSizes.get(j));
                    map.put(KEY_RATIO,getWHratioString(picSizes.get(j).width,picSizes.get(j).height));
                    supportedPictureSizesFront.add(map);
                }
                for(int j1 = 0;j1 < prewSizes.size();j1++){
                    map = new HashMap<String, Object>();
                    map.put(KEY_VALUE,prewSizes.get(j1));
                    map.put(KEY_RATIO,getWHratioString(prewSizes.get(j1).width,prewSizes.get(j1).height));
                    supportedPreviewSizesFront.add(map);
                }
            }
            else if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                supportedPreviewSizesBack = new ArrayList<Map<String,Object>>();
                supportedPictureSizesBack = new ArrayList<Map<String,Object>>();
                Camera.Parameters parameters = camera.getParameters();
                prewSizes = parameters.getSupportedPreviewSizes();
                picSizes = parameters.getSupportedPictureSizes();
                Collections.sort(picSizes, comp);
                Collections.sort(prewSizes, comp);
                Map<String,Object> map = null;
                for(int j = 0;j < picSizes.size();j++){
                    map = new HashMap<String, Object>();
                    map.put(KEY_VALUE,picSizes.get(j));
                    map.put(KEY_RATIO,getWHratioString(picSizes.get(j).width,picSizes.get(j).height));
                    supportedPictureSizesBack.add(map);
                }
                for(int j1 = 0;j1 < prewSizes.size();j1++){
                    map = new HashMap<String, Object>();
                    map.put(KEY_VALUE,prewSizes.get(j1));
                    map.put(KEY_RATIO,getWHratioString(prewSizes.get(j1).width,prewSizes.get(j1).height));
                    supportedPreviewSizesBack.add(map);
                }
            }
            camera.release();
        }
    }
    public static CameraUtil getCameraUtil(){
        if(cameraUtil == null){
            cameraUtil = new CameraUtil();
        }
        return cameraUtil;
    }

    /**
     * 获取对应摄像头的最大预览图片分辨率
     * @param cameraPosition:摄像头位置
     * @return
     */
    public Size getMaxPreviewSize(int cameraPosition){
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition)
        {
            return (Size)supportedPreviewSizesFront.get(0).get(KEY_VALUE);
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition)
        {
            return (Size)supportedPreviewSizesBack.get(0).get(KEY_VALUE);
        }
        return null;
    }

    public Size getPicSizeOfScreen(int cameraPosition,int displayWidth, int displayHeight) {
        int picSizeOfSrnWidth = displayHeight;
        int picSizeOfSrnHeight = displayWidth;
        List<Map<String,Object>> supportedSizes;
        if(cameraPosition == Camera.CameraInfo.CAMERA_FACING_BACK){
            supportedSizes = supportedPictureSizesBack;
        } else {
            supportedSizes = supportedPictureSizesFront;
        }
        Size size;
        int i = 0;
        // 首先找支持屏幕分辨率的的图片分辨率
        for(;i < supportedSizes.size();i++){
            size = (Size)supportedSizes.get(i).get(CameraUtil.KEY_VALUE);
            if(size.width == picSizeOfSrnWidth && size.height == picSizeOfSrnHeight){
                return size;
            }
        }
        // 没找到屏幕分辨率的照片分辨率，则找第一个(从大到小)和屏幕高近似的分辨率
        for (i = 0; i < supportedSizes.size(); i++) {
            size = (Size) supportedSizes.get(i).get(CameraUtil.KEY_VALUE);
            float rate = (float) size.width / picSizeOfSrnWidth;
            if ( rate <= 1.5 && rate >= 0.9) {
                return size;
            }
        }
        // 没有近似的则最大的(极端情况)
        return getMaxPictureSize(cameraPosition);
    }

    /**
     * 获取对应摄像头的最小预览图片分辨率
     * @param cameraPosition:摄像头位置
     * @return
     */
    public Size getMinPreviewSize(int cameraPosition){
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition){
            return (Size)supportedPreviewSizesFront.get(supportedPreviewSizesFront.size()-1).get(KEY_VALUE);
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition){
            return (Size)supportedPreviewSizesBack.get(supportedPreviewSizesBack.size()-1).get(KEY_VALUE);
        }
        return null;
    }

    /**
     * 获取对应摄像头的最大图片分辨率
     * @param cameraPosition:摄像头位置
     * @return
     */
    public Size getMaxPictureSize(int cameraPosition){
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition){
            return (Size)supportedPictureSizesFront.get(0).get(KEY_VALUE);
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition){
            return (Size)supportedPictureSizesBack.get(0).get(KEY_VALUE);
        }
        return null;
    }

    /**
     * 获取对应摄像头的最小图片分辨率
     * @param cameraPosition:摄像头位置
     * @return
     */
    public Size getMinPictureSize(int cameraPosition){
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition)
        {
            return (Size)supportedPictureSizesFront.get(supportedPictureSizesFront.size()-1).get(KEY_VALUE);
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition)
        {
            return (Size)supportedPictureSizesBack.get(supportedPictureSizesBack.size()-1).get(KEY_VALUE);
        }
        return null;
    }

    /**
     * 根据图片宽高比例来查找对应摄像头的预览图片分辨率
     * @param cameraPosition:相机位置
     * @param ratio:图片宽高比例{@code null}
     * @return
     */
    public Size getMaxPreviewSizeOfRatio(int cameraPosition, String ratio){
        int idx = 0;
        Map<String,Object> map = null;
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition){
            int size = 0;
            for(int i = size;i < supportedPreviewSizesFront.size();i++){
                map = supportedPreviewSizesFront.get(i);
                if(((String)map.get(KEY_RATIO)).contains(ratio))return (Size)map.get(KEY_VALUE);
            }
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition){
            int size = 0;
            for(int i = size;i < supportedPreviewSizesBack.size();i++){
                map = supportedPreviewSizesBack.get(i);
                if(((String)map.get(KEY_RATIO)).contains(ratio))return (Size)map.get(KEY_VALUE);
            }
        }
        return null;
    }

    public List<Size> getAllPictureSizeOfRatio(int cameraPosition, String ratio){
        List<Size> list = new ArrayList<>();
        if(cameraPosition == Camera.CameraInfo.CAMERA_FACING_BACK) {
            for (int i = 0; i < supportedPictureSizesBack.size(); i++) {
                if (supportedPictureSizesBack.get(i).get(KEY_RATIO).equals(ratio)) {
                    list.add((Size) supportedPictureSizesBack.get(i).get(KEY_VALUE));
                }
            }
        }
        return list;
    }
    /**
     * 根据图片的宽高比来查找对应摄像头的存储图片分辨率:默认从高到低查询
     * @param cameraPosition:相机位置
     * @param ratio:图片宽高比例{@code null}
     * @return
     */
    public Size getMaxPictureSizeOfRatio(int cameraPosition, String ratio){
        int idx = 0;
        Map<String,Object> map = null;
        if(Camera.CameraInfo.CAMERA_FACING_FRONT == cameraPosition){
            int size = 0;
            for(int i = size;i < supportedPictureSizesFront.size() ;i++){
                map = supportedPictureSizesFront.get(i);
                if(((String)map.get(KEY_RATIO)).contains(ratio))return (Size)map.get(KEY_VALUE);
            }
        }
        if(Camera.CameraInfo.CAMERA_FACING_BACK == cameraPosition){
            int size = 0;
            for(int i = size;i < supportedPictureSizesBack.size() ;i++){
                map = supportedPictureSizesBack.get(i);
                if(((String)map.get(KEY_RATIO)).contains(ratio))return (Size)map.get(KEY_VALUE);
            }
        }
        return null;
    }

    /**
     * 从系统相册获取图片，并返回图片路径
     * @param activity：目标Activity
     * @param data：相册数据
     * @return
     */
    public static String getImageFromSysGallery(Activity activity, Intent data){
        Uri uri = data.getData();
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = activity.managedQuery(uri, proj, null, null, null);
        // 获得图片索引值
        int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        // 将光标移至开头
        cursor.moveToFirst();
        // 最后根据索引值获取图片路径
        String filepath = cursor.getString(index);
        return filepath;
    }
    /**
     * 返回图片分辨率的比例
     */
    public static String getWHratioString(int width, int height){
        int gcd = gcd(width,height);
        int w = width/gcd;int h = height/gcd;
        return w+":"+h;
    }
    public static int gcd(int a,int b){
        int min = a;
        int max = b;
        if (a > b) {
            min = b;
            max = a;
        }
        if (min == 0)
            return max;
        else
            return gcd(min, max - min);
    }
}
