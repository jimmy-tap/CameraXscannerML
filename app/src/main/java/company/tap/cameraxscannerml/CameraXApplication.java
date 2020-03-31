package company.tap.cameraxscannerml;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;

import com.google.firebase.FirebaseApp;

/**
 * Created by Mario Gamal on 3/30/20
 * Copyright © 2020 Tap Payments. All rights reserved.
 */
public class CameraXApplication extends Application implements CameraXConfig.Provider {

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }
}
