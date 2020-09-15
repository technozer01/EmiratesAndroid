package com.accurascan.accuraemirates.sdk;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.accurascan.accuraemirates.sdk.model.OcrData;
import com.docrecog.scan.ImageOpencv;
import com.docrecog.scan.RecogResult;
import com.inet.facelock.callback.FaceCallback;
import com.inet.facelock.callback.FaceDetectionResult;
import com.inet.facelock.callback.FaceLockHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OcrResultActivity extends AppCompatActivity implements View.OnClickListener, FaceCallback {

    private final String TAG = "OcrResultActivity";
    FaceDetectionResult leftResult = null;
    FaceDetectionResult rightResult = null;
    float match_score = 0.0f;
    Bitmap face2 = null; // Selfi camera face
    Bitmap face1; // Document Face
    final private int CAPTURE_IMAGE = 2;
    private ImageView ivUserProfile;
    private ImageView ivUserProfile2;
    private TextView tvFM, tvCancel,  tvFaceMatchScore1;
    private LinearLayout llFaceMatchScore;

    //custom
    TableLayout mrz_table_layout, front_table_layout, back_table_layout;
    OcrData ocrData;
    RecogResult g_recogResult;
    ImageView iv_frontside, iv_backside;
    LinearLayout ly_back, ly_front;
    View ly_mrz_container, ly_front_container, ly_back_container;
    String[][] Frontdata;
    String[][] Backdata;
    private boolean isEngineInitialize = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_data_temp); //memory leak

        try {
            initUI();
            ocrData = (OcrData) getIntent().getSerializableExtra("ocrData");
            if (ocrData!=null) {
                g_recogResult = ocrData.getMrzData();
            }
            setDocumentData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setDocumentData() {

        Frontdata = ocrData.getFrontData();
        Backdata = ocrData.getBackData();
        LayoutInflater inflater = LayoutInflater.from(OcrResultActivity.this);
        if (Frontdata != null && Backdata != null) {
            ly_front_container.setVisibility(View.GONE);
            ly_back_container.setVisibility(View.VISIBLE);
        }

        if (Frontdata != null) {
            final Bitmap frontBitmap = ocrData.getFrontimage();
            for (int i = 0; i < Frontdata.length; i++) {
                final String[] array = Frontdata[i];
                if (array != null && array.length != 0) {
                    final String key = array[0];
                    Log.e(TAG, "run: key" + key);
                    final String value = array[1];
                    final View layout = (View) inflater.inflate(R.layout.table_row, null);
                    //            TableRow tableRow = layout.findViewById(R.id.table_row);
                    final TextView tv_key = layout.findViewById(R.id.tv_key);
                    final TextView tv_value = layout.findViewById(R.id.tv_value);
                    ImageView imageView = layout.findViewById(R.id.iv_image);

                    tv_key.setTextSize(16);
                    tv_key.setTextColor(getResources().getColor(R.color.black));
                    if (!value.equalsIgnoreCase("") && !value.equalsIgnoreCase(" ")) {

                        tv_key.setText(key.replace("_img", "") + ":");
                        // if key contains "_img", then its image data else it is simple text data
                        if (!key.contains("_img")) {
                            tv_value.setText(value);
                            imageView.setVisibility(View.GONE);
                            front_table_layout.addView(layout);

                        } else {
                            try {
                                // if key contains "face" then its Scanned document face
                                if (key.toLowerCase().contains("face")) {
                                    try {
                                        if (face1 == null) {
                                            face1 = ImageOpencv.getImage(value);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    tv_value.setVisibility(View.GONE);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        }
                    }
                if (frontBitmap != null && !frontBitmap.isRecycled()) {
                    try {
                        iv_frontside.setImageBitmap(frontBitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        } else {
            ly_front.setVisibility(View.GONE);
        }

        if (Backdata != null) {
            final Bitmap BackImage = ocrData.getBackimage();
        for (int i = 0; i < Backdata.length; i++) {
            View layout = (View) inflater.inflate(R.layout.table_row, null);
            TextView tv_key = layout.findViewById(R.id.tv_key);
            TextView tv_value = layout.findViewById(R.id.tv_value);
            ImageView imageView = layout.findViewById(R.id.iv_image);
            String[] array = Backdata[i];

            if (array != null && array.length != 0) {
                String key = array[0];
                String value = array[1];
                if (!key.equalsIgnoreCase("mrz")) {
                    if (!value.equalsIgnoreCase("") && !value.equalsIgnoreCase(" ")) {
                        tv_key.setTextSize(16);
                        tv_key.setTextColor(getResources().getColor(R.color.black));
                        // if key contains "_img", then its image data else it is simple text data
                        if (!key.contains("_img")) {
                            tv_value.setText(value);
                            imageView.setVisibility(View.GONE);
                            back_table_layout.addView(layout);
                        } else {
                            try {
                                Bitmap bitmap = ImageOpencv.getImage(value);
                                if (bitmap != null) {
                                    imageView.setImageBitmap(bitmap); //memory leak
                                    imageView.setVisibility(View.VISIBLE);
                                }
                                tv_value.setVisibility(View.GONE);
                                back_table_layout.addView(layout);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        tv_key.setText(key.replace("_img", "") + ":");
                    }
                } else if (key.toLowerCase().contains("mrz")) {
                    ly_mrz_container.setVisibility(View.VISIBLE);
                    setMRZData();// to display MRZ data
                }
            }
        }
            if (BackImage != null && !BackImage.isRecycled()) {
                try {
                    iv_backside.setImageBitmap(BackImage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            ly_back.setVisibility(View.GONE);
        }

        setData(); //set the result data
    }

    private void setMRZData() {
        //display MRZ data
        addMRZRaw("MRZ", g_recogResult.lines);
        addMRZRaw("Document", g_recogResult.docType);
        addMRZRaw("Last Name", g_recogResult.surname);
        addMRZRaw("First Name", g_recogResult.givenname);
        addMRZRaw("Document No", g_recogResult.docnumber);
        addMRZRaw("Document Check Number", g_recogResult.docchecksum);
        addMRZRaw("Country", g_recogResult.country);
        addMRZRaw("Nationality", g_recogResult.nationality);
        addMRZRaw("Sex", g_recogResult.sex.equalsIgnoreCase("F")?"Female":"Male");
        DateFormat date = new SimpleDateFormat("yymmdd", Locale.getDefault());
        SimpleDateFormat newDateFormat = new SimpleDateFormat("dd-mm-yy", Locale.getDefault());
        try {
            Date birthDate = date.parse(g_recogResult.birth.replace("<", ""));
            addMRZRaw("Date of Birth", newDateFormat.format(birthDate));
            addMRZRaw("Birth Check Number", g_recogResult.birthchecksum);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        try {
            Date expirationDate = date.parse(g_recogResult.expirationdate.replace("<", ""));
            addMRZRaw("Date Of Expiry", newDateFormat.format(expirationDate));
            addMRZRaw("Expiration Check Number", g_recogResult.expirationchecksum);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        addMRZRaw("OTHER ID", g_recogResult.otherid);
        addMRZRaw("Other ID Check", g_recogResult.otheridchecksum);
        addMRZRaw("Second Row Check Number", g_recogResult.secondrowchecksum);
    }

    private void addMRZRaw(String key, String value) {
        if (!TextUtils.isEmpty(value)) {
            View view = (View) LayoutInflater.from(OcrResultActivity.this).inflate(R.layout.table_row, null);
            TextView tv_key = view.findViewById(R.id.tv_key);
            TextView tv_value = view.findViewById(R.id.tv_value);
            tv_key.setText(String.format("%s:", key));
            String str = key + ":" + value + "<br/>";
            tv_value.setText(value);
            mrz_table_layout.addView(view);

        }
    }

    private void initUI() {
        //initialize the UI
        ivUserProfile = findViewById(R.id.ivUserProfile);
        ivUserProfile2 = findViewById(R.id.ivUserProfile2);
        tvFM = findViewById(R.id.tvFM);
        tvFaceMatchScore1 = findViewById(R.id.tvFaceMatchScore1);
        tvCancel = findViewById(R.id.tvCancel);

        llFaceMatchScore = findViewById(R.id.llFaceMatchScore);

        tvCancel.setOnClickListener(this);
        tvFM.setOnClickListener(this);

        tvFM.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.VISIBLE);
        //custom
        ly_back = findViewById(R.id.ly_back);
        ly_front = findViewById(R.id.ly_front);

        iv_frontside = findViewById(R.id.iv_frontside);
        iv_backside = findViewById(R.id.iv_backside);

        mrz_table_layout = findViewById(R.id.mrz_table_layout);
        front_table_layout = findViewById(R.id.front_table_layout);
        back_table_layout = findViewById(R.id.back_table_layout);

        ly_mrz_container = findViewById(R.id.ly_mrz_container);
        ly_front_container = findViewById(R.id.ly_front_container);
        ly_back_container = findViewById(R.id.ly_back_container);


    }

    private void setData() {
        try {
            if (face1 != null && !face1.isRecycled()) {
                ivUserProfile.setImageBitmap(face1); //memory leak
                ivUserProfile.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //handle click of different view
    @Override
    public void onClick(View view) {
//        isFaceMatch = false;
        switch (view.getId()) {
            case R.id.tvFM: // handle click of FaceMatch
                //Start Facematch
                if (initEngine()) {
//                isFaceMatch = true;
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File f = new File(Environment.getExternalStorageDirectory(), "temp.jpg");
                    Uri uriForFile = FileProvider.getUriForFile(
                            OcrResultActivity.this,
                            getPackageName() + ".provider",
                            f
                    );
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uriForFile);
                    startActivityForResult(intent, CAPTURE_IMAGE);
                }
                break;
            case R.id.tvCancel:
                tvCancel.setClickable(false);
                tvCancel.setFocusable(false);
                onBackPressed();
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // make sure the result was returned correctly
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (resultCode == RESULT_OK) {
                if (requestCode == CAPTURE_IMAGE) {
                    File f = new File(Environment.getExternalStorageDirectory().toString());
                    File ttt = null;
                    for (File temp : f.listFiles()) {
                        if (temp.getName().equals("temp.jpg")) {
                            ttt = temp;
                            break;
                        }
                    }
                    if (ttt == null)
                        return;
                    Bitmap bmp = rotateImage(ttt.getAbsolutePath());
                    ttt.delete();
                    if (bmp != null) {
                        face2 = bmp.copy(Bitmap.Config.ARGB_8888, true);

                        //if (g_recogResult.faceBitmap != null) {
                        if (face1 != null && !face1.isRecycled()) {
                            //Bitmap nBmp = g_recogResult.faceBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            Bitmap nBmp = face1.copy(Bitmap.Config.ARGB_8888, true);
                            int w = nBmp.getWidth();
                            int h = nBmp.getHeight();
                            int s = (w * 32 + 31) / 32 * 4;
                            ByteBuffer buff = ByteBuffer.allocate(s * h);
                            nBmp.copyPixelsToBuffer(buff);
                            FaceLockHelper.DetectLeftFace(buff.array(), w, h);
                        }

                        if (face2 != null && !face2.isRecycled()) {
                            Bitmap nBmp = face2;
                            int w = nBmp.getWidth();
                            int h = nBmp.getHeight();
                            int s = (w * 32 + 31) / 32 * 4;
                            ByteBuffer buff = ByteBuffer.allocate(s * h);
                            nBmp.copyPixelsToBuffer(buff);
                            FaceLockHelper.DetectRightFace(buff.array(), w, h, null);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //used for rotate image of given path
    //parameter to pass : String path
    // return bitmap
    private Bitmap rotateImage(final String path) {

        Bitmap b = decodeFileFromPath(path);

        if (b != null) {
            try {
                ExifInterface ei = new ExifInterface(path);
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);

                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                        break;
                    default:
                        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
                        //b.copyPixelsFromBuffer(ByteBuffer.)
                        break;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return b;
    }

    //Get bitmap image form path
    //parameter to pass : String path
    // return Bitmap
    private Bitmap decodeFileFromPath(String path) {
        Uri uri = getImageUri(path);
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);

            //Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;

            BitmapFactory.decodeStream(in, null, o);
            in.close();

            int scale = 1;
            int inSampleSize = 1024;
            if (o.outHeight > inSampleSize || o.outWidth > inSampleSize) {
                scale = (int) Math.pow(2, (int) Math.round(Math.log(inSampleSize / (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
            }

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            in = getContentResolver().openInputStream(uri);
            // Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            int MAXCAP_SIZE = 512;
            Bitmap b = getResizedBitmap(BitmapFactory.decodeStream(in, null, o2), MAXCAP_SIZE);
            in.close();

            return b;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //get image uwi from given string path
    //parameter to pass : String path
    // return Uri
    private Uri getImageUri(String path) {
        return Uri.fromFile(new File(path));
    }

    //Used for resizing bitmap
    //parameter to pass : Bitmap image, int maxSize
    // return bitmap
    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 0) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    //checked for user permission
    private boolean checkReadWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(OcrResultActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 111);
                return false;
            }
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 111) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED || grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                //resume tasks needing this permission
                checkReadWritePermission();
            }
        }
    }

    //initialize the engine
    private boolean initEngine() {

        if (isEngineInitialize)
            return isEngineInitialize;

        //call Sdk  method InitEngine
        // parameter to pass : FaceCallback callback, int fmin, int fmax, float resizeRate, String modelpath, String weightpath, AssetManager assets
        // this method will return the integer value
        //  the return value by initEngine used the identify the particular error
        // -1 - No key found
        // -2 - Invalid Key
        // -3 - Invalid Platform
        // -4 - Invalid License

        writeFileToPrivateStorage(R.raw.model, "model.prototxt"); //write file to private storage
        File modelFile = getApplicationContext().getFileStreamPath("model.prototxt");
        String pathModel = modelFile.getPath();
        writeFileToPrivateStorage(R.raw.weight, "weight.dat");
        File weightFile = getApplicationContext().getFileStreamPath("weight.dat");
        String pathWeight = weightFile.getPath();

        int nRet = FaceLockHelper.InitEngine(this, 30, 800, 1.18f, pathModel, pathWeight, this.getAssets());
        Log.i("OcrResultActivity", "InitEngine: " + nRet);
        if (nRet < 0) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            if (nRet == -1) {
                builder1.setMessage("No Key Found");
            } else if (nRet == -2) {
                builder1.setMessage("Invalid Key");
            } else if (nRet == -3) {
                builder1.setMessage("Invalid Platform");
            } else if (nRet == -4) {
                builder1.setMessage("Invalid License");
            }

            builder1.setCancelable(true);

            builder1.setPositiveButton(
                    "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();

                        }
                    });

            AlertDialog alert11 = builder1.create();
            try {
                if (!isFinishing()) {
                    alert11.show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            isEngineInitialize = false;
        } else {
            isEngineInitialize = true;
        }
        return isEngineInitialize;
    }

    @Override
    public void onInitEngine(int ret) {
    }

    // call if face detect
    @Override
    public void onLeftDetect(FaceDetectionResult faceResult) {
        leftResult = null;
        if (faceResult != null) {
            leftResult = faceResult;

            if (face2 != null && !face2.isRecycled()) {
                Bitmap nBmp = face2.copy(Bitmap.Config.ARGB_8888, true);
                if (nBmp != null && !nBmp.isRecycled()) {
                    int w = nBmp.getWidth();
                    int h = nBmp.getHeight();
                    int s = (w * 32 + 31) / 32 * 4;
                    ByteBuffer buff = ByteBuffer.allocate(s * h);
                    nBmp.copyPixelsToBuffer(buff);
                    if (leftResult != null) {
                        FaceLockHelper.DetectRightFace(buff.array(), w, h, leftResult.getFeature());
                    } else {
                        FaceLockHelper.DetectRightFace(buff.array(), w, h, null);
                    }
                }
            }
        } else {
            if (face2 != null && !face2.isRecycled()) {
                Bitmap nBmp = face2.copy(Bitmap.Config.ARGB_8888, true);
                if (nBmp != null && !nBmp.isRecycled()) {
                    int w = nBmp.getWidth();
                    int h = nBmp.getHeight();
                    int s = (w * 32 + 31) / 32 * 4;
                    ByteBuffer buff = ByteBuffer.allocate(s * h);
                    nBmp.copyPixelsToBuffer(buff);
                    if (leftResult != null) {
                        FaceLockHelper.DetectRightFace(buff.array(), w, h, leftResult.getFeature());
                    } else {
                        FaceLockHelper.DetectRightFace(buff.array(), w, h, null);
                    }
                }
            }
        }
        calcMatch();
    }

    //call if face detect
    @Override
    public void onRightDetect(FaceDetectionResult faceResult) {
        if (faceResult != null) {
            rightResult = faceResult;
            try {
//                Bitmap face2 = BitmapUtil.createFromARGB(faceResult.getNewImg(), faceResult.getNewWidth(), faceResult.getNewHeight());
                ivUserProfile2.setImageBitmap(faceResult.getFaceImage(face2));
                ivUserProfile2.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            rightResult = null;
        }
        calcMatch();
    }

    @Override
    public void onExtractInit(int ret) {
    }

    //Calculate Pan and Adhare facematch score
    public void calcMatch() {
        if (leftResult == null || rightResult == null) {
            match_score = 0.0f;
        } else {
            match_score = FaceLockHelper.Similarity(leftResult.getFeature(), rightResult.getFeature(), rightResult.getFeature().length);
            match_score *= 100.0f;
        }
        tvFaceMatchScore1.setText(String.valueOf(match_score));
        llFaceMatchScore.setVisibility(View.VISIBLE);
    }

    //Copy file to privateStorage
    public void writeFileToPrivateStorage(int fromFile, String toFile) {
        InputStream is = getApplicationContext().getResources().openRawResource(fromFile);
        int bytes_read;
        byte[] buffer = new byte[4096];
        try {
            FileOutputStream fos = getApplicationContext().openFileOutput(toFile, Context.MODE_PRIVATE);

            while ((bytes_read = is.read(buffer)) != -1)
                fos.write(buffer, 0, bytes_read); // write

            fos.close();
            is.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Runtime.getRuntime().gc();
    }

    public boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        super.onBackPressed();
    }
}
