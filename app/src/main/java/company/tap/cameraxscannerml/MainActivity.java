package company.tap.cameraxscannerml;

import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.ml.vision.text.RecognizedLanguage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public TextView scannedNumber, scannedDate;
    PreviewView previewView;
    ImageAnalysis imageAnalysis;
    ExecutorService executorService;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    FirebaseVisionTextRecognizer detector;

    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scannedNumber = findViewById(R.id.scanned_number);
        scannedDate = findViewById(R.id.scanned_date);
        previewView = findViewById(R.id.preview_view);
        FirebaseApp.initializeApp(this);

        if (allPermissionsGranted()) {
            init();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void init() {
        CameraX.initialize(this, ((CameraXApplication)getApplication()).getCameraXConfig());
        detector = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        executorService = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executorService, new TapAnalyzer());
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
        initImageAnalysis();
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        preview.setSurfaceProvider(previewView.getPreviewSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    private void initImageAnalysis() {


    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                init();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private class TapAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (imageProxy.getImage() == null)
                return;
            Image mediaImage = imageProxy.getImage();
            int rotation = degreesToFirebaseRotation(imageProxy.getImageInfo().getRotationDegrees());
            FirebaseVisionImage image = FirebaseVisionImage.fromMediaImage(mediaImage, rotation);
            processImage(image);
            Log.d("TAP_ML", image.toString());
            imageProxy.close();
        }

        private int degreesToFirebaseRotation(int degrees) {
            switch (degrees) {
                case 0:
                    return FirebaseVisionImageMetadata.ROTATION_0;
                case 90:
                    return FirebaseVisionImageMetadata.ROTATION_90;
                case 180:
                    return FirebaseVisionImageMetadata.ROTATION_180;
                case 270:
                    return FirebaseVisionImageMetadata.ROTATION_270;
                default:
                    throw new IllegalArgumentException(
                            "Rotation must be 0, 90, 180, or 270.");
            }
        }
    }

    public void processImage(FirebaseVisionImage image){
        Task<FirebaseVisionText> result =
                detector.processImage(image)
                        .addOnSuccessListener(this::processText)
                        .addOnFailureListener(
                                e -> {
                                    // Task failed with an exception
                                    // ...
                                });
    }

    private void processText(FirebaseVisionText result) {
        List<FirebaseVisionText.TextBlock> blocks = result.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            String word = blocks.get(i).getText();

            if (word.matches("^(0[1-9]|1[0-2]|[1-9])/(1[4-9]|[2-9][0-9]|20[1-9][1-9])$"))
                scannedDate.setText(word);

            if (word.replace(" ", "").matches("^(?:4[0-9]{12}(?:[0-9]{3})?|[25][1-7][0-9]{14}|6(?:011|5[0-9][0-9])[0-9]{12}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|(?:2131|1800|35\\d{3})\\d{11})$"))
                scannedNumber.setText(word);
        }
    }
}
