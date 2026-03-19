package com.confa.apprfid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * App simple de lectura RFID UHF para Chanway C72.
 * Usa SDK RFIDWithUHFUART con lectura continua y sin duplicados (HashSet).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int MSG_ADD_EPC = 1;

    private RFIDWithUHFUART mReader;
    private boolean reading = false;

    private Button btnStart;
    private Button btnStop;
    private TextView tvTotal;
    private TextView tvUnique;
    private TextView tvEpcList;

    /** EPCs únicos leídos (LinkedHashSet = sin duplicados + orden de llegada). */
    private final Set<String> epcSet = new LinkedHashSet<>();
    /** Contador total de lecturas (incluye repeticiones). */
    private int totalReads = 0;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_ADD_EPC && msg.obj != null) {
                String epc = (String) msg.obj;
                if (epc.isEmpty()) return;
                totalReads++;
                boolean isNew = epcSet.add(epc);
                if (isNew) {
                    refreshEpcList();
                }
                tvTotal.setText(String.valueOf(totalReads));
                tvUnique.setText(String.valueOf(epcSet.size()));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvTotal = findViewById(R.id.tvTotal);
        tvUnique = findViewById(R.id.tvUnique);
        tvEpcList = findViewById(R.id.tvEpcList);

        try {
            // Intentamos obtener la instancia del lector
            mReader = RFIDWithUHFUART.getInstance();

            if (mReader != null) {
                if (!mReader.init(this)) {
                    Toast.makeText(this, "Error al inicializar el hardware RFID.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "RFID init failed");
                }
            }
        } catch (Exception e) {
            // Este bloque captura la 'ConfigurationException' que muestra tu imagen
            Log.e(TAG, "Error de configuración: " + e.getMessage());
            Toast.makeText(this, "Error de configuración del lector: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        btnStart.setOnClickListener(v -> startInventory());
        btnStop.setOnClickListener(v -> stopInventory());
    }

    private void startInventory() {
        if (mReader == null) return;
        if (reading) return;

        epcSet.clear();
        totalReads = 0;
        tvTotal.setText("0");
        tvUnique.setText("0");
        tvEpcList.setText("");

        mReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo uhftagInfo) {
                if (uhftagInfo == null) return;
                String epc = uhftagInfo.getEPC();
                if (epc != null && !epc.isEmpty()) {
                    Message msg = handler.obtainMessage(MSG_ADD_EPC);
                    msg.obj = epc;
                    handler.sendMessage(msg);
                }
            }
        });

        InventoryParameter param = new InventoryParameter();
        param.setResultData(new InventoryParameter.ResultData().setNeedPhase(false));

        if (mReader.startInventoryTag(param)) {
            reading = true;
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            Toast.makeText(this, "Lectura iniciada", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "startInventoryTag OK");
        } else {
            Toast.makeText(this, "Error al iniciar lectura", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "startInventoryTag failed");
        }
    }

    private void stopInventory() {
        if (mReader == null || !reading) return;

        if (mReader.stopInventory()) {
            reading = false;
            mReader.setInventoryCallback(null);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            Toast.makeText(this, "Lectura detenida", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "stopInventory OK");
        } else {
            Toast.makeText(this, "Error al detener lectura", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "stopInventory failed");
        }
    }

    private void refreshEpcList() {
        StringBuilder sb = new StringBuilder();
        for (String epc : epcSet) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(epc);
        }
        tvEpcList.setText(sb.length() > 0 ? sb.toString() : "");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (reading) {
            stopInventory();
        }
    }

    @Override
    protected void onDestroy() {
        if (reading) {
            stopInventory();
        }
        if (mReader != null) {
            mReader.setInventoryCallback(null);
            mReader.free();
        }
        super.onDestroy();
    }
}
