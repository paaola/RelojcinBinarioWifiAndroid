package com.example.msi.relojcinbinario;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.support.design.widget.Snackbar;
import android.widget.Toolbar;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

public class MainActivity extends AppCompatActivity
{
    private static BroadcastReceiver tickReceiver;
    private boolean isRegistered = false;
    private boolean isReceiverRegistered = false;
    private boolean connectedToWifi = false;
    private boolean alarmActivated = false;

    private String ssid = "";
    private String pass = "";
    private String horas = "";
    private String minutos = "";
    private String alarmTime = "";

    private FloatingActionMenu fam;

    private Button hd1;
    private Button hd2;
    private Button hu1;
    private Button hu2;
    private Button hu3;
    private Button hu4;
    private Button md1;
    private Button md2;
    private Button md3;
    private Button mu1;
    private Button mu2;
    private Button mu3;
    private Button mu4;

    private Client.OnMessageReceived clientListener = new Client.OnMessageReceived()
    {
        @Override
        public void messageReceived(String message)
        {
            if (!message.contains("error"))
            {
                checkMessage(message);
            }
            else
            {
                Toast.makeText(MainActivity.this,
                        "Error de comunicacion",
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void errorMessage(String errorMessage)
        {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        }
    };

    public ProgressDialog ringProgressDialog;
    private Button buttons[][] = new Button[4][4];
    private Handler minuteHandler = new Handler();

    final Runnable minuteTimer = new Runnable()
    {
        @Override
        public void run()
        {
            if (!minutos.equals("59"))
            {
                minutos = String.valueOf(Integer.parseInt(minutos) + 1);
            }
            else
            {
                minutos = "00";
                if (!horas.equals("23"))
                {
                    horas = String.valueOf(Integer.parseInt(horas) + 1);
                }
                else
                {
                    horas = "00";
                }
            }

            if (connectedToWifi) sendTime();
            minuteHandler.postDelayed(minuteTimer, 60000 - (getSeconds() * 1000));
        }
    };

    private BroadcastReceiver myWifiReceiver = new BroadcastReceiver()
    {
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action))
            {
                NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (ConnectivityManager.TYPE_WIFI == netInfo.getType())
                {
                    if (netInfo.isConnected())
                    {
                        WifiManager wifiManager = (WifiManager) context.getApplicationContext().
                                getSystemService(context.WIFI_SERVICE);

                        if (wifiManager != null)
                        {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            String ssid = wifiInfo.getSSID();

                            if (ssid.contains("RELOJCIN_WIFI"))
                            {
                                connectedToWifi = true;
                            }
                            else
                            {
                                connectedToWifi = false;
                            }
                        }
                    }
                    else
                    {
                        connectedToWifi = false;
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        hd1 = (Button) findViewById(R.id.hd1);
        hd2 = (Button) findViewById(R.id.hd2);
        hu1 = (Button) findViewById(R.id.hu1);
        hu2 = (Button) findViewById(R.id.hu2);
        hu3 = (Button) findViewById(R.id.hu3);
        hu4 = (Button) findViewById(R.id.hu4);
        md1 = (Button) findViewById(R.id.md1);
        md2 = (Button) findViewById(R.id.md2);
        md3 = (Button) findViewById(R.id.md3);
        mu1 = (Button) findViewById(R.id.mu1);
        mu2 = (Button) findViewById(R.id.mu2);
        mu3 = (Button) findViewById(R.id.mu3);
        mu4 = (Button) findViewById(R.id.mu4);

        tickReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0)
                {
                    digitalToBinaryClock();
                    sendTime();
                }
            }
        };

        isRegistered = true;
        //Register the broadcast receiver to receive TIME_TICK
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        initButtonArray();
        digitalToBinaryClock();

        fam = (FloatingActionMenu) findViewById(R.id.clock_menu);
        fam.setVisibility(View.GONE);

        FloatingActionButton sync = (FloatingActionButton) findViewById(R.id.sync);
        sync.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Thread mThread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        syncWithFoundDevice();
                    }
                });

                mThread.start();
            }
        });

        FloatingActionButton netInfo = (FloatingActionButton) findViewById(R.id.config_network);
        netInfo.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent i = new Intent(MainActivity.this, MyPreferencesActivity.class);
                startActivityForResult(i, 1);
            }
        });

        FloatingActionButton alarmButton = (FloatingActionButton) findViewById(R.id.set_alarm);
        alarmButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                setAlarm();
            }
        });

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.clock_content_view);
        linearLayout.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (fam.getVisibility() == View.VISIBLE)
                {
                    fam.setVisibility(View.GONE);
                }
                else
                {
                    fam.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void syncWithFoundDevice()
    {
        DatagramSocket c;
        try
        {
            c = new DatagramSocket();
            c.setBroadcast(true);

            byte[] sendData = "EVERYTHING IS COPACETIC".getBytes();

            try
            {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                        InetAddress.getByName("255.255.255.255"), 2500);
                c.send(sendPacket);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            //Obtener respuestas de los dispositivos de la red
            while (true)
            {
                try
                {
                    byte[] recvBuf = new byte[256];
                    DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                    c.setSoTimeout(5000);
                    c.receive(receivePacket);

                    //Si hubo respuesta y no hubo timeout, obtener datos del dispositivo
                    String ipAdress = receivePacket.getAddress().getHostAddress();
                    //Check if the message is correct
                    String message = new String(receivePacket.getData()).trim();

                    Log.d("Relojcin", "IP -> " + ipAdress + "\nMessage -> " + message);
                }
                catch (IOException e)
                {
                    break;
                }
            }

            //Close the port!
            c.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void sendTime()
    {
        String paquete = "";

        paquete += "h" + horas + '\n';
        paquete += "m" + minutos + '\n';

        sendMessage(paquete);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.connection:
                if (connectedToWifi)
                {
                    sendTime();
                    Toast.makeText(MainActivity.this,
                            "Sincronizando",
                            Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(this, "Sin conexión Wifi", Toast.LENGTH_SHORT).show();
                }

                return true;

            case R.id.redInfo:
                Intent i = new Intent(this, MyPreferencesActivity.class);
                startActivityForResult(i, 1);
                return true;

            case R.id.alarm:
                setAlarm();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void setAlarm()
    {
        View checkBoxView = View.inflate(this, R.layout.alarmsettings, null);
        final CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        final EditText edittext1 = (EditText) checkBoxView.findViewById(R.id.hour);
        final EditText edittext2 = (EditText) checkBoxView.findViewById(R.id.minutes);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked)
                {
                    checkBox.setText("Activada");
                    checkBox.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.black));
                }
                else
                {
                    checkBox.setText("Desactivada");
                    checkBox.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.gray));
                }
            }
        });
        checkBox.setText("Alarma");

        if (alarmActivated)
        {
            checkBox.setChecked(true);
            edittext1.setText(alarmTime.substring(0, alarmTime.indexOf(":")));
            edittext2.setText(alarmTime.substring(alarmTime.indexOf(":") + 1));
        }
        else
        {
            checkBox.setChecked(false);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(" ALARMA");
        builder.setMessage(" Configuración (Formato 24 horas)")
                .setView(checkBoxView)
                .setCancelable(true)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        alarmTime = edittext1.getText().toString();
                        alarmTime += ":";
                        alarmTime += edittext2.getText().toString();

                        if (checkBox.isChecked())
                        {
                            alarmActivated = true;
                            sendMessage("a" + alarmTime + '\n');
                            Toast.makeText(getApplicationContext(), "Alarma configurada", Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            alarmActivated = false;
                            sendMessage("a0\n");
                        }
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case 1:
                String paquete = getInfo(); //obtener informacion de la red
                if (!paquete.equals(""))
                {
                    //mTcpClient.sendMessage(paquete);
                    sendMessage(paquete);
                    Toast.makeText(this, "Editando información", Toast.LENGTH_SHORT).show();
                }

                break;
        }
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if (isRegistered)
        {
            isRegistered = false;
            unregisterReceiver(tickReceiver);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        minuteHandler.removeCallbacks(minuteTimer);

        if (!isRegistered)
        {
            isRegistered = true;
            registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
            digitalToBinaryClock();
        }

        if (!isReceiverRegistered)
        {
            isReceiverRegistered = true;
            registerReceiver(myWifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)); // IntentFilter to wifi state change is "android.net.wifi.STATE_CHANGE"
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (isRegistered)
        {
            isRegistered = false;
            unregisterReceiver(tickReceiver);
        }

        if (isReceiverRegistered)
        {
            isReceiverRegistered = false;
            unregisterReceiver(myWifiReceiver);
        }
        /*
        if (connected)
        {
            minuteHandler.postDelayed(minuteTimer, 60000 - (getSeconds() * 1000));
        }
        */
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

    }

    private int getSeconds()
    {
        DateFormat df = new SimpleDateFormat("ss");
        String date = df.format(new Date());
        return Integer.parseInt(date);
    }

    private void initButtonArray()
    {
        buttons[0][0] = hd1;
        buttons[0][1] = hd2;
        buttons[1][0] = hu1;
        buttons[1][1] = hu2;
        buttons[1][2] = hu3;
        buttons[1][3] = hu4;
        buttons[2][0] = md1;
        buttons[2][1] = md2;
        buttons[2][2] = md3;
        buttons[3][0] = mu1;
        buttons[3][1] = mu2;
        buttons[3][2] = mu3;
        buttons[3][3] = mu4;
    }

    private void digitalToBinaryClock()
    {
        DateFormat df = new SimpleDateFormat("HH,mm");
        String date = df.format(new Date());

        horas = date.substring(0, 2);
        minutos = date.substring(3, 5);

        char dateChars[] = {date.charAt(0), date.charAt(1), date.charAt(3), date.charAt(4)};

        voidCircles();

        for (int charIdx = 0; charIdx < 4; charIdx++)
        {
            switch (dateChars[charIdx])
            {
                case '0':
                    break;

                case '1':
                    buttons[charIdx][0].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][0].setAlpha(1);
                    break;

                case '2':
                    buttons[charIdx][1].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][1].setAlpha(1);
                    break;

                case '3':
                    buttons[charIdx][0].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][0].setAlpha(1);
                    buttons[charIdx][1].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][1].setAlpha(1);
                    break;

                case '4':
                    buttons[charIdx][2].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][2].setAlpha(1);
                    break;

                case '5':
                    buttons[charIdx][0].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][0].setAlpha(1);
                    buttons[charIdx][2].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][2].setAlpha(1);
                    break;

                case '6':
                    buttons[charIdx][1].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][1].setAlpha(1);
                    buttons[charIdx][2].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][2].setAlpha(1);
                    break;

                case '7':
                    buttons[charIdx][0].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][0].setAlpha(1);
                    buttons[charIdx][1].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][1].setAlpha(1);
                    buttons[charIdx][2].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][2].setAlpha(1);
                    break;

                case '8':
                    buttons[charIdx][3].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][3].setAlpha(1);
                    break;

                case '9':
                    buttons[charIdx][0].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][0].setAlpha(1);
                    buttons[charIdx][3].setBackgroundDrawable(getResources().getDrawable(R.drawable.white_button));
                    buttons[charIdx][3].setAlpha(1);
                    break;
            }
        }
    }

    private void voidCircles()
    {
        for (int x = 0; x < 4; x++)
        {
            for (int y = 0; y < 4; y++)
            {
                if (buttons[x][y] != null)
                {
                    buttons[x][y].setBackgroundDrawable(getResources().getDrawable(R.drawable.blue_button));
                    buttons[x][y].setAlpha((float) 0.2);
                }
            }
        }
    }

    private String getInfo()
    {
        String paquete = "";

        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        String ssidToCompare = sharedPrefs.getString("ssid", "");
        String passToCompare = sharedPrefs.getString("pass", "");

        if (!ssid.equals(ssidToCompare)) paquete += "s" + ssidToCompare + '\n';
        if (!pass.equals(passToCompare)) paquete += "c" + passToCompare + '\n';

        return paquete;
    }

    private void checkMessage(String completeMessage)
    {
        String incomingMessage;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();

        while (completeMessage.contains("\n"))
        {
            incomingMessage = completeMessage;
            incomingMessage = incomingMessage.substring(0, incomingMessage.indexOf("\n"));
            completeMessage = completeMessage.substring(completeMessage.indexOf("\n") + 1, completeMessage.length());

            if (incomingMessage.startsWith("s"))
            {
                //SSID de red almacenada en el modulo
                ssid = incomingMessage.substring(1, incomingMessage.length());
                if (ssid.equals("0"))
                {
                    editor.putString("ssid", "").apply();
                }
                else
                {
                    editor.putString("ssid", ssid).apply();
                }

            }
            else if (incomingMessage.contains("c"))
            {
                //Contraseña de red almacenada en el modulo
                pass = incomingMessage.substring(1, incomingMessage.length());
                if (pass.equals("0"))
                {
                    editor.putString("pass", "").apply();
                }
                else
                {
                    editor.putString("pass", pass).apply();
                }
            }
            else if (incomingMessage.contains("a"))
            {
                //Alarma
                String alarmString = incomingMessage.substring(1, incomingMessage.length());
                if (alarmString.equals("0"))
                {
                    alarmActivated = false;
                }
                else
                {
                    alarmActivated = true;
                    alarmTime = alarmString;
                }
            }
        }
    }

    public void sendMessage(String mMessage)
    {
        if (connectedToWifi)
        {
            new Client(clientListener,
                    "192.168.4.1",
                    80).execute(mMessage);
        }
        else
        {
            View v = findViewById(android.R.id.content);
            Snackbar.make(v, "No está conectado al módulo",
                    Snackbar.LENGTH_LONG).show();
        }
    }
}
