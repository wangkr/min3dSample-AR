package min3d.sampleProject1;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.text.BoringLayout;
import android.view.Display;
import android.view.WindowManager;

import min3d.core.RendererActivity;

/**
 * Created by Kairong on 2016/6/12.
 * mail:wangkrhust@gmail.com
 */
public class min3dApp extends Application {
    private static Context _context;
    private static Resources _resource;
    @Override
    public void onCreate() {
        super.onCreate();

        _context = getApplicationContext();
        _resource = getResources();

        initDisplayInfo();
    }

    private void initDisplayInfo(){
        RendererActivity.initDisplayInfo(_context);
    }
}
