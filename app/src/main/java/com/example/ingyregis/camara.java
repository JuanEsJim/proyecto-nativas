package com.example.ingyregis;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Callback;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class camara extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private ImageView imageView; // Para mostrar la imagen

    private static final String BASE_URL = "http://localhost:8000/predict"; // Dirección de tu servidor FastAPI

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camara);


        Button takePhotoButton = findViewById(R.id.btn_take_photo);

        // Botón para capturar una imagen
        takePhotoButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(Camara.this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                ActivityCompat.requestPermissions(Camara.this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        });
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            // Obtener la imagen como un bitmap
            Bitmap photo = (Bitmap) data.getExtras().get("data");

            // Mostrar la imagen capturada en ImageView
            imageView.setImageBitmap(photo);

            // Guardar la imagen en un archivo temporal
            File photoFile = saveImageToTempFile(photo);

            // Enviar la imagen al servidor FastAPI
            if (photoFile != null) {
                sendImageToServer(photoFile);
            }
        }
    }

    // Guardar la imagen en un archivo temporal
    private File saveImageToTempFile(Bitmap bitmap) {
        File storageDir = getCacheDir();
        File tempFile = new File(storageDir, "captured_image.jpg");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Función para enviar la imagen al servidor FastAPI
    private void sendImageToServer(File photoFile) {
        // Crear RequestBody para el archivo de imagen
        RequestBody requestBody = RequestBody.create(photoFile, MediaType.parse("image/jpeg"));
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("file", photoFile.getName(), requestBody);

        // Crear Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Crear servicio API
        ApiService apiService = retrofit.create(ApiService.class);

        // Realizar la solicitud
        Call<PredictionResponse> call = apiService.uploadImage(imagePart);
        call.enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Obtener la predicción del modelo
                    String prediction = response.body().getPrediction();
                    Log.d("Camara", "Predicción: " + prediction);
                    // Mostrar la predicción en un Toast o actualizar la UI
                    Toast.makeText(Camara.this, "Predicción: " + prediction, Toast.LENGTH_SHORT).show();
                } else {
                    // Manejar error
                    Log.e("Camara", "Error en la respuesta: " + response.message());
                    Toast.makeText(Camara.this, "Error en la respuesta", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                // Manejar error de conexión
                Log.e("Camara", "Error en la solicitud: " + t.getMessage());
                Toast.makeText(Camara.this, "Error de conexión", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Interfaz para la API Retrofit
    public interface ApiService {
        @Multipart
        @POST("/predict")
        Call<PredictionResponse> uploadImage(@Part MultipartBody.Part image);
    }

    // Clase para manejar la respuesta del servidor (Predicción)
    public static class PredictionResponse {
        private String prediction;

        public String getPrediction() {
            return prediction;
        }

        public void setPrediction(String prediction) {
            this.prediction = prediction;
        }
    }
}
