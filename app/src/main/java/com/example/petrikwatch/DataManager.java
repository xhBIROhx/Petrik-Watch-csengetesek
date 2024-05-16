package com.example.petrikwatch;
import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DataManager {

    private static final String DATA_FILE_NAME = "data.json";
    private static final String DATA_FOLDER_NAME = "petrik";

    // Function to read JSON data from the file
    public static JSONObject readData(Context context) {
        try {
            File file = new File(context.getFilesDir(), DATA_FOLDER_NAME + File.separator + DATA_FILE_NAME);
            if (!file.exists()) {
                return null;
            }

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            fis.close();

            String json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Function to write JSON data to the file
    public static void writeData(Context context, JSONObject data) {
        try {
            File folder = new File(context.getFilesDir(), DATA_FOLDER_NAME);
            if (!folder.exists()) {
                folder.mkdirs(); // Use mkdirs to create parent directories if they don't exist
            }

            File file = new File(folder, DATA_FILE_NAME);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
