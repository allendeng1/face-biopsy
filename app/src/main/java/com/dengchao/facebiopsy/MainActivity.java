package com.dengchao.facebiopsy;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private PreviewView previewView;

    private TextView lable;

    private ImageView pic;

    private ImageCapture imageCapture;

    private int actionNum = 2;

    private int actionPassNum;

    private Float curValue1;
    private Float curValue2;

    private int passNum;

    private boolean isTaked;

    private Bitmap facePic;

    private List<String> actions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.viewFinder);
        lable = findViewById(R.id.lable);
        pic = findViewById(R.id.pic);

        selectAction();

        if(!selfPermissionGranted(getApplicationContext(), Manifest.permission.CAMERA)){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }else{
            startCamera();
        }
    }

    private void selectAction(){
        actions.clear();
        List<String> as = new ArrayList<>();
        as.add("shake_head");
        as.add("open_mouth");
        as.add("blink_eyes");
        as.add("nod_head");
        Collections.shuffle(as);//打乱顺序
        Random r = new Random();
        while(actions.size() < actionNum){
            String action = as.get(r.nextInt(as.size()));
            if(actions.contains(action)){
                continue;
            }
            actions.add(action);
        }
    }

    public static boolean selfPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                && PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }

    private void startCamera() {

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageRotationEnabled(true)
                .build();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void analyze(@NonNull ImageProxy image) {
                analyzeImage(image);
            }
        });

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder()
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageCapture, imageAnalysis, preview);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("打开相机失败","打开相机失败", e);
            }
        }, ContextCompat.getMainExecutor(this));

    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy image){
        int imageHeight = image.getHeight();
        int imageWidth = image.getWidth();
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        //在检测人脸时更注重速度还是准确性。
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        //是否尝试识别面部“特征点”：眼睛、耳朵、鼻子、脸颊、嘴巴等。
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        //是否将人脸分为不同类别（例如“微笑”和“睁眼”）
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        //是否检测面部特征的轮廓。仅检测图片中最突出的人脸的轮廓。
                        //.setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();
        FaceDetector detector = FaceDetection.getClient(options);
        Task<List<Face>> result = detector.process(image.getImage(), image.getImageInfo().getRotationDegrees())
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                try {

                                    if(faces == null || faces.size() == 0){
                                        selectAction();
                                        lable.setText("未检测到人脸");
                                        actionPassNum = 0;
                                        curValue1 = null;
                                        curValue2 = null;
                                        passNum = 0;
                                        isTaked = false;
                                        if(facePic != null){
                                            facePic.recycle();
                                        }
                                        facePic = null;
                                        return;
                                    }
                                    Face face = faces.get(0);

                                    if(!isTaked){
                                        float angleZ = face.getHeadEulerAngleZ();
                                        if(angleZ < -2 || angleZ > 2){
                                            lable.setText("请调整相机角度");
                                            return;
                                        }
                                        FaceLandmark leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK);
                                        FaceLandmark rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK);

                                        float faceWidthScale = Math.abs(leftCheek.getPosition().x - rightCheek.getPosition().x) / imageWidth;

                                        if(faceWidthScale < 0.3){
                                            lable.setText("请靠近相机");
                                            return;
                                        }
                                        if(faceWidthScale > 0.5){
                                            lable.setText("请远离相机");
                                            return;
                                        }

                                        float faceXOffset = (rightCheek.getPosition().x + leftCheek.getPosition().x)/2 - imageWidth/2;
                                        if(faceXOffset < -100){
                                            lable.setText("请向右移动相机");
                                            return;
                                        }
                                        if(faceXOffset > 100){
                                            lable.setText("请向左移动相机");
                                            return;
                                        }

                                        if(leftCheek.getPosition().y > imageHeight - 180){
                                            lable.setText("请向下移动相机");
                                            return;
                                        }
                                        if(leftCheek.getPosition().y < 280){
                                            lable.setText("请向上移动相机");
                                            return;
                                        }

                                        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
                                        FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
                                        if(nose.getPosition().y - leftEye.getPosition().y < 60){
                                            lable.setText("请调整相机角度");
                                            return;
                                        }

                                        facePic = image.toBitmap();
                                        isTaked = true;
                                    }

                                    String action = actions.get(actionPassNum);
                                    if(action.equals("shake_head")){
                                        lable.setText("请重复摇头");
                                        float eulerAngleY = face.getHeadEulerAngleY();
                                        if (curValue1 == null) {
                                            curValue1 = eulerAngleY;
                                        } else {
                                            if ((curValue1 > 0 && eulerAngleY < 0) || (curValue1 < 0 && eulerAngleY > 0) && Math.abs(curValue1 - eulerAngleY) >= 10) {
                                                passNum++;
                                                curValue1 = eulerAngleY;
                                            }
                                        }
                                    }else if(action.equals("blink_eyes")){
                                        lable.setText("请重复眨眼");
                                        float leftEye = face.getLeftEyeOpenProbability();
                                        float rightEye = face.getRightEyeOpenProbability();
                                        if(curValue1 == null){
                                            if(leftEye >= 0.9 && rightEye >= 0.9){
                                                curValue1 = leftEye;
                                                curValue2 = rightEye;
                                            }
                                        }else{
                                            if(Math.abs(curValue1 - leftEye) >= 0.8 && Math.abs(curValue2 - rightEye) >= 0.8){
                                                passNum++;
                                            }
                                        }
                                    } else if (action.equals("nod_head")) {
                                        lable.setText("请重复点头");
                                        float eulerAngleX = face.getHeadEulerAngleX();
                                        if (curValue1 == null && eulerAngleX > 5) {
                                            curValue1 = eulerAngleX;
                                        } else {
                                            if (curValue1 > 0 && eulerAngleX < 0 && Math.abs(curValue1 - eulerAngleX) >= 10) {
                                                passNum++;
                                                curValue1 = null;
                                            }
                                        }

                                    }else if (action.equals("open_mouth")) {
                                        lable.setText("请重复张嘴");
                                        FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
                                        FaceLandmark mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);

                                        if (mouth != null) {
                                            PointF point = mouth.getPosition();
                                            PointF nosePoint = nose.getPosition();
                                            if(curValue1 == null){
                                                curValue1 = point.y;
                                                curValue2 = nosePoint.y;
                                            }else{
                                                if(curValue1 > point.y && curValue1 - point.y > 20 && Math.abs(curValue2 - nosePoint.y) < 10){
                                                    passNum++;
                                                }
                                                curValue1 = point.y;
                                                curValue2 = nosePoint.y;
                                            }
                                        }
                                    }
                                    if(passNum >= 3){
                                        actionPassNum++;
                                        curValue1 = null;
                                        curValue2 = null;
                                        passNum = 0;
                                        if(actionPassNum >= actionNum){
                                            Toast.makeText(getApplicationContext(), "成功", 5000);
                                            pic.setImageBitmap(facePic);
                                            selectAction();
                                            actionPassNum = 0;
                                            isTaked = false;
                                        }
                                    }
                                }catch (Exception e){
                                    Log.e("FACE错误","",e);
                                }finally {
                                    image.close();
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                lable.setText("未检测到人脸");
                                passNum = 0;
                                image.close();
                            }
                        });


    }
}