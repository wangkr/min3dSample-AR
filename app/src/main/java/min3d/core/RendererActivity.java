package min3d.core;

import min3d.Shared;
import min3d.interfaces.ISceneController;
import min3d.sampleProject1.CameraHelper;
import min3d.sampleProject1.CameraView;
import min3d.sampleProject1.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Extend this class when creating your min3d-based Activity. 
 * Then, override initScene() and updateScene() for your main
 * 3D logic.
 * 
 * Override onCreateSetContentView() to change layout, if desired.
 * 
 * To update 3d scene-related variables from within the the main UI thread,  
 * override onUpdateScene() and onUpdateScene() as needed.
 */
public class RendererActivity extends Activity implements ISceneController
{
	public static int displayWidth,displayHeight;
	public Scene scene;
	protected GLSurfaceView _glSurfaceView;
	protected CameraView _cameraView;
	protected Button _captureBtn;
	
	protected Handler _initSceneHander;
	protected Handler _updateSceneHander;

	protected final Object lock = new Object();
    private boolean _renderContinuously;
	protected volatile State save_picture_state = State.NONE;
	protected String saved_picture;


	// temp value
	private boolean continue_tag2 = false;
	private boolean continue_tag1 = false;
	private float xpos, ypos, xpos2, ypos2;
	private volatile float move_x;
	private volatile float move_z;
	private volatile float rotate_z;
	private volatile float rotate_x;


	private volatile boolean capture = false;

	ProgressDialog progressDialog;

	public enum State{NONE, SAVING, ERROR, DONE}


	// init in min3dApp
	public static void initDisplayInfo(Context context){
		WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		Display display = windowManager.getDefaultDisplay();
		RendererActivity.displayWidth = display.getWidth();
		RendererActivity.displayHeight = display.getHeight();
	}


	static class TakePhotoHandler extends Handler {
		private static WeakReference<RendererActivity> activityWeakReference;
		public TakePhotoHandler(RendererActivity activity) {
			activityWeakReference = new WeakReference<RendererActivity>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			RendererActivity activity = activityWeakReference.get();
			switch (msg.what){
				case CameraHelper.SAVE_PICTURE_DONE:
					synchronized (activity.lock) {
						String savedPath = (String)msg.obj;
						activity._cameraView.cameraHelper.restartPreview();
						if(savedPath==null) {
							Toast.makeText(activity,"保存图片出错!",Toast.LENGTH_SHORT).show();
							activity.save_picture_state = State.DONE;
							activity.lock.notifyAll();
							break;
						}
						activity.save_picture_state = State.DONE;
						activity.saved_picture = savedPath;
						activity.lock.notifyAll();
					}
					break;
				case CameraHelper.SAVED_ERROR:
					synchronized (activity.lock) {
						activity.save_picture_state = State.ERROR;
						activity.lock.notifyAll();
					}
					activity._cameraView.cameraHelper.restartPreview();
					Toast.makeText(activity,"发生错误,请重新拍照!",Toast.LENGTH_SHORT).show();
					break;
				case CameraHelper.MSG_TAKE_PICTURE:
					activity._cameraView.cameraHelper.takePhoto();
					break;
				case CameraHelper.MSG_EXIT_APP:
					activity.finish();
					synchronized (activity.lock) {
						activity.save_picture_state = State.ERROR;
						activity.lock.notifyAll();
					}
					break;
			}
		}
	}
	protected final Handler _takePhotohandler = new TakePhotoHandler(this);

	final Runnable _initSceneRunnable = new Runnable() 
	{
        public void run() {
            onInitScene();
        }
    };
    
	final Runnable _updateSceneRunnable = new Runnable() 
    {
        public void run() {
            onUpdateScene();
        }
    };
    

    @Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.exampleload3ds_layout);

		_initSceneHander = new Handler();
		_updateSceneHander = new Handler();

		
		//
		// These 4 lines are important.
		//
		Shared.context(this);
		scene = new Scene(this);
		Renderer r = new Renderer(scene);
		Shared.renderer(r);
		
		_glSurfaceView = (GLSurfaceView)findViewById(R.id.gl_surfaceview);
		_cameraView = (CameraView)findViewById(R.id.surfaceview);
		_captureBtn = (Button)findViewById(R.id.capture_btn);

		progressDialog = new ProgressDialog(RendererActivity.this);
		progressDialog.setIndeterminate(true);

        glSurfaceViewConfig1();
		_glSurfaceView.setRenderer(r);
		_glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

		_cameraView.my_init(_takePhotohandler, displayWidth, displayHeight);
		_captureBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				capture = true;
			}
		});
	}


	public void capture(final int[] bitmapBuffer){
		_takePhotohandler.post(new Runnable() {
			@Override
			public void run() {
				progressDialog.setCanceledOnTouchOutside(false);
				progressDialog.show();
			}
		});

		new Thread(){
			@Override
			public void run() {
				_takePhotohandler.obtainMessage(CameraHelper.MSG_TAKE_PICTURE).sendToTarget();

				synchronized (lock) {
					save_picture_state = RendererActivity.State.SAVING;
				}

				Bitmap bitmap = null;
				try {
					bitmap = createBitmapFromGLSurface(displayWidth, displayHeight, bitmapBuffer);
					synchronized (lock) {
						while (save_picture_state == RendererActivity.State.SAVING) {
							try {
								lock.wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}

						if (save_picture_state == RendererActivity.State.DONE && saved_picture != null) {
							Bitmap newb = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888);
							Canvas canvas = new Canvas(newb);

							Bitmap picture = BitmapFactory.decodeFile(saved_picture);
							canvas.drawBitmap(picture, 0, 0, null);
							canvas.drawBitmap(bitmap, 0, 0, null);
							final String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + System.currentTimeMillis() + ".png";
							File bitFile = new File(filePath);
							FileOutputStream out = new FileOutputStream(bitFile);
							newb.compress(Bitmap.CompressFormat.PNG, 90, out);
							out.close();
							newb.recycle();
							picture.recycle();
							bitmap.recycle();
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (progressDialog.isShowing()) {
										progressDialog.dismiss();
									}
									Toast.makeText(RendererActivity.this, "保存成功:"+filePath, Toast.LENGTH_SHORT).show();
								}
							});
						}
					}

				} catch (OutOfMemoryError | NullPointerException | IOException error){
					error.printStackTrace();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							progressDialog.dismiss();
							Toast.makeText(RendererActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
						}
					});
				} finally {
					if (bitmap != null && !bitmap.isRecycled()) {
						bitmap.recycle();
					}
				}
			}
		}.start();

	}

	private Bitmap createBitmapFromGLSurface(int w, int h, int[] bitmapBuffer){
		int bitmapSource[] = new int[1280*720];

		int offset1, offset2;
		for(int i = 0;i < h;++i){
			offset1 = i*w;
			offset2 = (h - i - 1)*w;
			for(int j = 0;j < w;++j){
				int texturePixel = bitmapBuffer[offset1 + j];
				int blue = (texturePixel >> 16) & 0xff;
				int red = (texturePixel << 16) & 0x00ff0000;
				int pixel = (texturePixel & 0xff00ff00) | red | blue;
				bitmapSource[offset2+j] = pixel;
			}
		}

		return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
	}

	@Override
	public boolean onTouchEvent(MotionEvent me) {

		if (me.getAction() == MotionEvent.ACTION_DOWN || me.getAction() == MotionEvent.ACTION_UP) {
			continue_tag2 = false;
			continue_tag1 = false;
			return true;
		}

		if (me.getAction() == MotionEvent.ACTION_MOVE) {
			if (me.getPointerCount() == 1) {
				continue_tag2 = false;
				if (!continue_tag1) {
					xpos = me.getX();
					ypos = me.getY();
					continue_tag1 = true;
				} else {
					float xd = me.getX() - xpos;
					float yd = me.getY() - ypos;

					xpos = me.getX();
					ypos = me.getY();

					move_x = xd / 100f;
					move_z = yd / 100f;
				}
			}

			if (me.getPointerCount() == 2) {
				continue_tag1 = false;
				if (!continue_tag2) {
					xpos2 = (me.getX(0) + me.getX(1)) / 2;
					ypos2 = (me.getY(0) + me.getY(1)) / 2;
					continue_tag2 = true;
				} else {
					float xd = (me.getX(0) + me.getX(1)) / 2 - xpos2;
					float yd = (me.getY(0) + me.getY(1)) / 2 - ypos2;

					xpos2 = (me.getX(0) + me.getX(1)) / 2;
					ypos2 = (me.getY(0) + me.getY(1)) / 2;

					rotate_z = xd / 15f;
					rotate_x = yd / 15f;
				}
			}
			return true;
		}

		try {
			Thread.sleep(10);
		} catch (Exception e) {
			// No need for this...
		}

		return super.onTouchEvent(me);
	}

	public void onAddCameraView(){

	}
    /**
     * Any GlSurfaceView settings that needs to be executed before 
     * GLSurfaceView.setRenderer() can be done by overriding this method. 
     * A couple examples are included in comments below.
     */
    protected void glSurfaceViewConfig()
    {
	    // Example which makes glSurfaceView transparent (along with setting scene.backgroundColor to 0x0)
	    // _glSurfaceView.setEGLConfigChooser(8,8,8,8, 16, 0);
	    // _glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		// Example of enabling logging of GL operations 
		// _glSurfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR | GLSurfaceView.DEBUG_LOG_GL_CALLS);
    }

	protected void glSurfaceViewConfig1()
	{
		// !important
		_glSurfaceView.setEGLConfigChooser(8,8,8,8, 16, 0);
		_glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

	}
	
	protected GLSurfaceView glSurfaceView()
	{
		return _glSurfaceView;
	}
	
	/**
	 * Separated out for easier overriding...
	 */
	protected void onCreateSetContentView()
	{
		setContentView(_glSurfaceView);
	}
	
	@Override
	protected void onResume() 
	{
		super.onResume();
		_glSurfaceView.onResume();
	}
	
	@Override
	protected void onPause() 
	{
		super.onPause();
		_glSurfaceView.onPause();
	}

	/**
	 * Instantiation of Object3D's, setting their properties, and adding Object3D's 
	 * to the scene should be done here. Or any point thereafter.
	 * 
	 * Note that this method is always called after GLCanvas is created, which occurs
	 * not only on Activity.onCreate(), but on Activity.onResume() as well.
	 * It is the user's responsibility to build the logic to restore state on-resume.
	 */
	public void initScene()
	{
	}

	/**
	 * All manipulation of scene and Object3D instance properties should go here.
	 * Gets called on every frame, right before rendering.   
	 */
	public void updateScene()
	{
		for (int i = 0;i < scene.numChildren();++i) {
			Object3d object3d = scene.getChildAt(i);
			object3d.position().x += move_x;
			object3d.position().z += move_z;

			object3d.rotation().z += -rotate_z;
			object3d.rotation().x += rotate_x;
		}

		rotate_x = 0;
		rotate_z = 0;
		move_x = 0;
		move_z = 0;

		if(capture) {
			capture = false;
			int bitmapBuffer[] = new int[1280*720];

			IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
			intBuffer.position(0);

			try{
				Shared.renderer().gl().glReadPixels(0, 0, displayWidth, displayHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
				Log.d("TEST", "in "+Thread.currentThread().getName()+" glReadPixels");
			} catch (GLException e){
				e.printStackTrace();
				return;
			}
			capture(bitmapBuffer);

		}
	}
	
    /**
     * Called _after_ scene init (ie, after initScene).
     * Unlike initScene(), gets called from the UI thread.
     */
    public void onInitScene()
    {
    }
    
    /**
     * Called _after_ updateScene()
     * Unlike initScene(), gets called from the UI thread.
     */
    public void onUpdateScene()
    {

    }
    
    /**
     * Setting this to false stops the render loop, and initScene() and onInitScene() will no longer fire.
     * Setting this to true resumes it. 
     */
    public void renderContinuously(boolean $b)
    {
    	_renderContinuously = $b;
    	if (_renderContinuously)
    		_glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    	
    	else
    		_glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }
    
	public Handler getInitSceneHandler()
	{
		return _initSceneHander;
	}
	
	public Handler getUpdateSceneHandler()
	{
		return _updateSceneHander;
	}

    public Runnable getInitSceneRunnable()
    {
    	return _initSceneRunnable;
    }
	
    public Runnable getUpdateSceneRunnable()
    {
    	return _updateSceneRunnable;
    }
}
