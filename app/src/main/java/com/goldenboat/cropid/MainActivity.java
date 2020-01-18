package com.goldenboat.cropid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button imgCap,imgload;
    ImageView imageView;
    TextView predicted_crop;
    private static final int PIXEL_WIDTH = 28;
    int SELECT_PICTURE=1001,CAMERA_REQUEST =1002;
    private static final int MY_CAMERA_PERMISSION_CODE = 100;
    private List<Classifier> mClassifiers ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imgCap = findViewById(R.id.cam_button);
        imgload = findViewById(R.id.load_button);
        imageView = findViewById(R.id.image_view);
        predicted_crop = findViewById(R.id.predicted_crop);
        mClassifiers = new ArrayList<>();
        imgload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent();
                i.setType("image/*");
                i.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(i, "Select Picture"), SELECT_PICTURE);
            }
        });
        imgCap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_PERMISSION_CODE);
                } else {
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                }
            }
        });
        loadModel();
    }

    private void loadModel() {
        //The Runnable interface is another way in which you can implement multi-threading other than extending the
        // //Thread class due to the fact that Java allows you to extend only one class. Runnable is just an interface,
        // //which provides the method run.
        // //Threads are implementations and use Runnable to call the method run().
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //add 2 classifiers to our classifier arraylist
                    //the tensorflow classifier and the keras classifier
                    mClassifiers.add(
                            TensorFlowClassifier.create(getAssets(), "TensorFlow",
                                    "opt_mnist_convnet-tf.pb", "labels.txt", PIXEL_WIDTH,
                                    "input", "output", true));

                } catch (final Exception e) {
                    //if they aren't found, throw an error!
                    throw new RuntimeException("Error initializing classifiers!", e);
                }
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE)
        {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
            else
            {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == RESULT_OK && requestCode == SELECT_PICTURE) {
            //if(data.getData() == null) return;
            Bitmap bm = null;
            Uri image = data.getData();
            try {
                bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), image);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            imageView.setImageURI(image);
            DecodeImage(bm);
        }
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK)
        {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(photo);
            //DecodeImage(photo);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void DecodeImage(Bitmap bm){

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        bm.recycle();

        float pixels[] = new float[PIXEL_WIDTH*PIXEL_WIDTH];
        byte arr[] = stream.toByteArray();
        for(int i=0;i<PIXEL_WIDTH*PIXEL_WIDTH;i++){
            if(arr[i] == 1) pixels[i] = 1;
            else pixels[i] = 0;

        }
        //init an empty string to fill with the classification output
        String text = "";
        //for each classifier in our array
        for (Classifier classifier : mClassifiers) {
            //perform classification on the image
            final Classification res = classifier.recognize(pixels);
            //if it can't classify, output a question mark
            if (res.getLabel() == null) {
                text += classifier.name() + ": ?\n";
            } else {
                //else output its name
                text += String.format("%s : %f\n", res.getLabel(),
                        res.getConf());
            }
        }
        predicted_crop.setText(text);
    }
}
