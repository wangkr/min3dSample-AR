package min3d.sampleProject1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

public class CameraView extends SurfaceView implements Callback {
	public CameraHelper cameraHelper;
	public CameraView(Context context) {
		super(context);
	}

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void my_init(Handler handler, int displayWidth, int displayHeight) {
		getHolder().addCallback( this );
		getHolder().setType( SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS );
		cameraHelper = new CameraHelper(getContext(), getHolder(), handler, displayWidth, displayHeight);
	}

	public void surfaceCreated( SurfaceHolder holder ) {
		if(cameraHelper.cameraCount == 1) {
			cameraHelper.open();
		} else if(cameraHelper.cameraCount >=2){
			cameraHelper.open(Camera.CameraInfo.CAMERA_FACING_BACK);
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height ) {

	}

	public void surfaceDestroyed( SurfaceHolder holder ) {
		cameraHelper.stop();
	}
}