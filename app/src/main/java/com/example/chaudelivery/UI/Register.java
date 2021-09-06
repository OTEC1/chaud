package com.example.chaudelivery.UI;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.chaudelivery.R;
import com.example.chaudelivery.utils.Find;
import com.example.chaudelivery.utils.User;
import com.example.chaudelivery.utils.utils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;

import static com.example.chaudelivery.utils.Constant.PICK_IMAGE;

public class Register extends AppCompatActivity {

    private EditText name, email, phone, pass, confirm_pass, delivery_details;
    private Button Register, choose_file;
    private ProgressBar progressBar;
    private Uri imgUri;


    private boolean confirm_img_pick = false, call_started = false;
    private String TAG = "Register";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        name = (EditText) findViewById(R.id.name);
        email = (EditText) findViewById(R.id.input_email);
        phone = (EditText) findViewById(R.id.Phone);
        pass = (EditText) findViewById(R.id.input_password);
        confirm_pass = (EditText) findViewById(R.id.input_confirm_password);
        choose_file = (Button) findViewById(R.id.photo_selector);
        Register = (Button) findViewById(R.id.btn_register);
        delivery_details = (EditText) findViewById(R.id.delivery_guy__details);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);


        choose_file.setOnClickListener(this::file_picker);


        Register.setOnClickListener(i -> {

            if (!new utils().init(getApplicationContext()).getBoolean(getString(R.string.DEVICE_REG_TOKEN), false))
                new utils().message("Device not Registered Pls Relaunch or Reinstall App.", getApplicationContext());
            else if (new utils().verify(name, email, phone, pass, confirm_pass, delivery_details, getApplicationContext()))
                make_post_on_location(name.getText().toString(), email.getText().toString(), phone.getText().toString(), pass.getText().toString());

        });
    }


    private void file_picker(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        intent.setDataAndType(imgUri, "image/*");
        startActivityForResult(intent, PICK_IMAGE);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            assert data != null;
            imgUri = data.getData();
            assert imgUri != null;
            if (imgUri.toString().contains("image")) {
                choose_file.setText("Image Added Proceed to Registration");
                confirm_img_pick = true;
            } else
                new utils().message2("No Image Selected.", this);
        }
    }


    private void make_post_on_location(String name, String email, String phone, String pass) {

        if (confirm_img_pick) {
            String p = generate_name();
            if (getFile_extension(imgUri).equals("png") | getFile_extension(imgUri).equals("jpg") | getFile_extension(imgUri).equals("jpeg") | getFile_extension(imgUri).equals("webp")) {
                progressBar.setVisibility(View.VISIBLE);
                call_started = true;
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
                        .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {

                                if (task.isSuccessful()) {
                                    User user = new User();
                                    user.setName(name);
                                    user.setEmail(email);
                                    user.setUsername(email.substring(0, email.indexOf("@")));
                                    user.setPhone(phone);
                                    user.setUser_id(FirebaseAuth.getInstance().getUid());
                                    user.setMember_T(" I agree to Terms and Condition");
                                    user.setToken("");
                                    user.setImg_url(p);
                                    user.setDelivery_details(delivery_details.getText().toString());

                                    DocumentReference new_member = FirebaseFirestore.getInstance().collection(getString(R.string.DELIVERY_REG)).document(Objects.requireNonNull(FirebaseAuth.getInstance().getUid()));
                                    new_member.set(user).addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {

                                            if (!task.isSuccessful()) {
                                                if (Objects.requireNonNull(task.getException()).toString().contains("The email address is already in use by another account.")) {
                                                    new utils().message("Bad Request" + task.getException(), getApplicationContext());
                                                    progressBar.setVisibility(View.INVISIBLE);
                                                }
                                            } else if (task.isSuccessful()) {
                                                new utils().message("Registered User ", getApplicationContext());
                                                credentials(p);
                                            } else {
                                                new utils().message("Something went wrong.", getApplicationContext());
                                                progressBar.setVisibility(View.INVISIBLE);
                                            }
                                        }
                                    });

                                } else {
                                    new utils().message("Error " + task.getException(), getApplicationContext());
                                    progressBar.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
            } else
                new utils().message("Pls select an image file", getApplicationContext());
        } else
            new utils().message2("Pls Select an image file", this);
    }


    //-----------------------------------------------S3 Query------------------------------------//
    private void credentials(String img_url) {

        DocumentReference user = FirebaseFirestore.getInstance().collection("east").document("lab");
        user.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                try {
                    send_data_to_s3(task.getResult().getString("p1"), task.getResult().getString("p2"), task.getResult().getString("p3"), img_url);
                } catch (Exception e) {
                    new utils().message(e.getLocalizedMessage(), getApplicationContext());
                    Log.d(TAG, e.toString());
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }


    private String getFile_extension(Uri uri) {
        MimeTypeMap mine = MimeTypeMap.getSingleton();
        return mine.getExtensionFromMimeType(this.getContentResolver().getType(uri));
    }


    public String generate_name() {
        long x = System.currentTimeMillis();
        long q = System.nanoTime();
        return String.valueOf(x).concat(String.valueOf(q));
    }


    private void send_data_to_s3(String p1, String p2, String p3, String url) throws URISyntaxException {


        new utils().message2("Uploading Image...", this);
        AWSCredentials credentials = new BasicAWSCredentials(p1, p2);
        AmazonS3 s3 = new AmazonS3Client(credentials);
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");
        s3.setRegion(Region.getRegion(Regions.EU_WEST_3));
        //s3.setObjectAcl("", ".png", CannedAccessControlList.PublicRead);
        TransferUtility transferUtility = new TransferUtility(s3, getApplicationContext());
        String d = Find.get_file_selected_path(imgUri, getApplicationContext());
        TransferObserver trans = transferUtility.upload(p3, url, new File(d));
        trans.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {

            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                float percentDone = ((float) bytesCurrent / (float) bytesTotal) * 100;
                int percentDo = (int) percentDone;


                if (percentDo == 100) {
                    progressBar.setVisibility(View.INVISIBLE);
                    startActivity(new Intent(getApplicationContext(), Login.class));
                    FirebaseAuth.getInstance().signOut();
                    Register.setEnabled(true);
                }


            }

            @Override
            public void onError(int id, Exception ex) {
                new utils().message(ex.getLocalizedMessage(), getApplicationContext());
                Log.d(TAG, ex.getLocalizedMessage());
                progressBar.setVisibility(View.GONE);
                Register.setEnabled(true);


            }

        });


    }
    //-----------------------------------------------End S3 Query------------------------------------//


    @Override
    public void onBackPressed() {
        if (call_started)
            new utils().message2("Pls wait Registration in progress", this);
        else
            super.onBackPressed();
    }
}