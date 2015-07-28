package kr.usis.u_drone;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAV_CMD;
import org.mavlink.messages.MAV_FRAME;
import org.mavlink.messages.MAV_SET_MODE;
import org.mavlink.messages.ardupilotmega.msg_attitude;
import org.mavlink.messages.ardupilotmega.msg_command_long;
import org.mavlink.messages.ardupilotmega.msg_gps_raw_int;
import org.mavlink.messages.ardupilotmega.msg_mission_ack;
import org.mavlink.messages.ardupilotmega.msg_mission_count;
import org.mavlink.messages.ardupilotmega.msg_mission_item;
import org.mavlink.messages.ardupilotmega.msg_mission_request;
import org.mavlink.messages.ardupilotmega.msg_radio;
import org.mavlink.messages.ardupilotmega.msg_rc_channels_override;
import org.mavlink.messages.ardupilotmega.msg_set_mode;
import org.mavlink.messages.ardupilotmega.msg_sys_status;
import org.mavlink.messages.ardupilotmega.msg_vfr_hud;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.usis.serial.act.DeviceListActivity;
import kr.usis.serial.util.SerialInputManager;

public class MainActivity extends FragmentActivity {

    TextView[] textView = new TextView[30];

    private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(4096);
    boolean DisconnectedFlag = false;
    DeviceListActivity dla;
    BackThread mThread;
    HBReceive hbrThread;
    HBSend hbsThread;
    ThreadChSend chsendThread;


    long longitude;
    long latitude;
    long altitude;


    private SerialInputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    //Handler for getting start Input Thread. - Daniel
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                //when it's connected then call below function
                startIoManager();
            }
            if (msg.what == 1) {
                //heartbeat receiving error
                showErrorMsg("HEARTBEAT RECEIVING ERROR");
            }
            if (msg.what == 255 || msg.what == 200) {
                showErrorMsg("HEARTBEAT RECEIVING ERROR !!!!");
            }
        }

    };

    //handler for displaying recieved data. - Daniel (test module)
    private final SerialInputManager.Listener mListener =
            new SerialInputManager.Listener() {

                @Override
                public void onRunError(Exception e) {

                }

                @Override
                public void onNewData(final String data) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    //display input data(test module)
    private void updateReceivedData(String data) {
/*        textview.append(data);
        textview.append(" ");
        textview.invalidate();*/
    }

    public msg_rc_channels_override getChannelOvr() {
        msg_rc_channels_override msgovr = new msg_rc_channels_override(1, 1);
        msgovr.sequence = StateBuffer.increaseSequence();
        msgovr.target_component = 1;
        msgovr.target_system = 1;
        return msgovr;
    }

    public msg_mission_item getMissionItem(long latitude, long longitude, long altitude, int command) {
        msg_mission_item msg = new msg_mission_item(1, 1);
        msg.x = latitude;
        msg.y = longitude;
        msg.z = altitude;
        msg.seq = 0;
        msg.target_system = 1;
        msg.target_component = 1;
        msg.frame = (byte) MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;
        //  msg.frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;
        msg.current = 0;
        msg.autocontinue = 1;
        msg.command = command;
        msg.sequence = StateBuffer.increaseSequence();
        return msg;
    }

    private void startIoManager() {

        if (StateBuffer.CONNECTION != null) {

            //execute data receiving thread
            mSerialIoManager = new SerialInputManager(StateBuffer.CONNECTION, mListener);
            mExecutor.submit(mSerialIoManager);

            //execute heartbeat receiving thread
            hbrThread = new HBReceive(mHandler);
            hbrThread.setDaemon(true);
            hbrThread.start();

            //execute heartbeat sending thread
            hbsThread = new HBSend();
            hbsThread.start();

            //execute channel data sending thread
            chsendThread = new ThreadChSend();
            chsendThread.start();
        }
    }

    public void call_mission_accepted() throws Exception {
        msg_mission_ack message = new msg_mission_ack(1, 1);
        message.sequence = StateBuffer.increaseSequence();
        message.target_component = 1;
        message.target_system = 1;
        message.type = 0;
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    public void call_mission_count() throws Exception {
        msg_mission_count message = new msg_mission_count(1, 1);
        message.sequence = StateBuffer.increaseSequence();
        message.target_component = 1;
        message.target_system = 1;
        message.count = 0;
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    public void GetMsg_Waypoint() throws IOException {
        msg_mission_item message = getMissionItem(latitude, longitude, altitude, MAV_CMD.MAV_CMD_NAV_WAYPOINT);
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    public void GetMissionItem_LoiterUnli() throws Exception {
        msg_mission_item message = getMissionItem(latitude, longitude, altitude, MAV_CMD.MAV_CMD_NAV_LOITER_UNLIM);
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    public void takeoff_() throws Exception {
        msg_mission_item message = getMissionItem(0, 0, altitude, MAV_CMD.MAV_CMD_NAV_TAKEOFF);
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }


    public void TakeOffInit() throws IOException {
        msg_rc_channels_override msg = getChannelOvr();
        msg.chan1_raw = 65535;
        msg.chan2_raw = 65535;
        msg.chan3_raw = 1105;
        msg.chan4_raw = 65535;
        msg.chan5_raw = 65535;
        msg.chan6_raw = 65535;
        msg.chan7_raw = 65535;
        msg.chan8_raw = 65535;
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(msg.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    public void Arming() throws IOException {
        byte[] buff = null;
        msg_command_long msg = new msg_command_long(1, 1);
        msg.param1 = 1;
        msg.param2 = 0;
        msg.param3 = 0;
        msg.param4 = 0;
        msg.param5 = 0;
        msg.param6 = 0;
        msg.param7 = 0;

        msg.sequence = StateBuffer.increaseSequence();

        msg.target_system = 1;
        msg.target_component = 1;
        msg.command = MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
        //MAV_CMD_COMPONENT_CONTROL = 250;
        msg.confirmation = 0;

        mWriteBuffer.put(msg.encode());
        synchronized (mWriteBuffer) {
            int len = mWriteBuffer.position();
            if (len > 0) {
                buff = new byte[41];
                mWriteBuffer.rewind();
                mWriteBuffer.get(buff, 0, len);
                mWriteBuffer.clear();
            }
        }

        if (buff != null) {
            StateBuffer.CONNECTION.write(buff, 10000);
        }

    }

    //ARM Button event
    public void ARM(View v) throws IOException {
        SetMode(MAV_SET_MODE.STABILIZE);
        Arming();
        _Get_MissionReq();
        TakeOffInit();
    }

    // TAKEOFF Button event
    // Probably have to put sleep between functions.
    public void TakeOff(View v) throws Exception {
        call_mission_count();
        GetMsg_Waypoint();
        takeoff_();
        GetMissionItem_LoiterUnli();
        call_mission_accepted();
        SetMode(MAV_SET_MODE.AUTO);
        DuringTakingOff();
    }

    public void DuringTakingOff() throws Exception {
        msg_rc_channels_override msg = getChannelOvr();
        msg.chan1_raw = 1505;
        msg.chan2_raw = 1505;
        msg.chan3_raw = 1505;
        msg.chan4_raw = 65535;
        msg.chan5_raw = 65535;
        msg.chan6_raw = 65535;
        msg.chan7_raw = 65535;
        msg.chan8_raw = 65535;
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(msg.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }


    // Setting mode
    public void SetMode(int mode) throws IOException {
        byte[] buff = null;
        msg_set_mode message = new msg_set_mode(1, 1);
        message.target_system = mode;
        message.sequence = StateBuffer.increaseSequence();

        mWriteBuffer.put(message.encode());

        synchronized (mWriteBuffer) {
            int len = mWriteBuffer.position();
            if (len > 0) {
                buff = new byte[14];
                mWriteBuffer.rewind();
                mWriteBuffer.get(buff, 0, len);
                mWriteBuffer.clear();
            }
        }
        if (buff != null) {
            StateBuffer.CONNECTION.write(buff, 1000);
        }
    }

    //Mission Request
    public void _Get_MissionReq() throws IOException {
        msg_mission_request message = new msg_mission_request(1, 1);
        message.target_system = 1;
        message.target_component = 1;
        message.seq = 0;
        message.sequence = StateBuffer.increaseSequence();

        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(message.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }

    //DisArming
    public void DisARM(View v) throws IOException {
        byte[] buff = null;
        msg_command_long msg = new msg_command_long(1, 1);
        msg.param1 = 0;
        msg.param2 = 0;
        msg.param3 = 0;
        msg.param4 = 0;
        msg.param5 = 0;
        msg.param6 = 0;
        msg.param7 = 0;
        msg.sequence = StateBuffer.increaseSequence();

        msg.target_system = 1;
        msg.target_component = 1;
        msg.command = MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
        msg.confirmation = 0;

        mWriteBuffer.put(msg.encode());
        synchronized (mWriteBuffer) {
            int len = mWriteBuffer.position();
            if (len > 0) {
                buff = new byte[41];
                mWriteBuffer.rewind();
                mWriteBuffer.get(buff, 0, len);
                mWriteBuffer.clear();
            }
        }
        if (buff != null) {
            StateBuffer.CONNECTION.write(buff, 10000);
        }
    }


    public void YawToLeft(View v) throws IOException {
        // sending yaw
        byte[] buff = null;
        msg_rc_channels_override msg = getChannelOvr();
        msg.chan1_raw = 65535;  //roll
        msg.chan2_raw = 65535;  //pitch
        msg.chan3_raw = 65535;  //throttle
        msg.chan4_raw = 1400;   //yaw
        msg.chan5_raw = 65535;
        msg.chan6_raw = 65535;
        msg.chan7_raw = 65535;
        msg.chan8_raw = 65535;
        Toast.makeText(this, "Yaw button is pressed", Toast.LENGTH_SHORT).show();
        StateBuffer.flagThread_ch_send_Run = false;
        StateBuffer.BufferStorage.offer(msg.encode());
        StateBuffer.flagThread_ch_send_Run = true;
    }


    //to show up error message box
    public void showErrorMsg(String str) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle("Error Message Box")
                .setMessage(str)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //do some thing here which you need
                    }
                });
        //No Event here
        /*builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });*/
        AlertDialog alert = builder.create();
        alert.show();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        textView[0] = (TextView) findViewById(R.id.textView15);     //PITCH
        textView[1] = (TextView) findViewById(R.id.textView17);     //ROLL
        textView[2] = (TextView) findViewById(R.id.textView18);     //YAW
        textView[3] = (TextView) findViewById(R.id.textView11);     //rssi
        textView[4] = (TextView) findViewById(R.id.textView12);     //ramrssi
        textView[5] = (TextView) findViewById(R.id.textView23);     //voltage battery
        textView[6] = (TextView) findViewById(R.id.textView24);     //Altitude
        textView[7] = (TextView) findViewById(R.id.textView13);     //HDOP
        textView[8] = (TextView) findViewById(R.id.textView14);     //Number of satellites visible



        // Button listeners below
        final Button ConnectButton = (Button) findViewById(R.id.ConnectButton);

        ConnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connect();
            }
        });

        final Button DisconnectButton = (Button) findViewById(R.id.DisconnectButton);

        DisconnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                disconnect();
            }
        });

    }


    public void connect() {
        if (!DisconnectedFlag) {
            Toast.makeText(this, "Connection Established", Toast.LENGTH_SHORT).show();
            dla = new DeviceListActivity(this);
            dla.getUSBService(); //Get Telemetry device's Information
            dla.refreshDeviceList(); //Scan device and connect

            //call connection checking thread
            mThread = new BackThread(mHandler);
            mThread.setDaemon(true);
            mThread.start();

            //call Dequeue and Display Thread
            new Dequeue().execute();

            DisconnectedFlag = true;
        }
    }

    //disconnect button event
    public void disconnect() {
        Toast.makeText(this, "Connection Destroyed", Toast.LENGTH_SHORT).show();
        dla = null;
        DisconnectedFlag = false;
        mThread = null;
    }

    //Thread for checking received data and display
    public class Dequeue extends AsyncTask<Void, String, Void> {

        @Override
        protected void onProgressUpdate(String... strings) {
            for (int i = 0; i < 9; i++) {
                textView[i].setText(strings[i]);
            }
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            int counter = 0;

            String[] valuse = new String[10];
            while (true) {
                if (StateBuffer.RECEIEVEDATAQUEUE.isEmpty()) {
                    try {
                        counter++;
                        Thread.sleep(100);
                        // publishProgress("sleep");
                    } catch (InterruptedException e) {
                        ;
                    }
                } else {
                    counter = 0;
                    MAVLinkMessage msg = StateBuffer.RECEIEVEDATAQUEUE.poll();
                    //publishProgress("poll");
                    if (msg != null || !msg.equals(null)) {
                        switch (msg.messageType) {
                            case 30:
                                valuse[0] = Float.toString(((msg_attitude) msg).pitch);
                                valuse[1] = Float.toString(((msg_attitude) msg).roll);
                                valuse[2] = Float.toString(((msg_attitude) msg).yaw);
                                break;
                            case 166:
                                valuse[3] = Integer.toString(((msg_radio) msg).rssi);
                                valuse[4] = Integer.toString(((msg_radio) msg).remrssi);
                                break;
                            case 1:
                                float tmp1 = (((msg_sys_status) msg).voltage_battery) / 1000f;
                                String tmp2 = Float.toString(Math.round(tmp1 * 100f) / 100f);
                                valuse[5] = tmp2 + "V";
                                break;
                            case 74:
                                valuse[6] = Float.toString(Math.round((((msg_vfr_hud) msg).alt) * 100f) / 100f);
                                break;
                            case 24:
                                valuse[7] = Integer.toString(((msg_gps_raw_int) msg).eph);
                                valuse[8] = Integer.toString(((msg_gps_raw_int) msg).satellites_visible);
                                longitude = ((msg_gps_raw_int) msg).lon;
                                latitude = ((msg_gps_raw_int) msg).lat;
                                altitude = ((msg_gps_raw_int) msg).alt;
                                break;
                        }
                        publishProgress(valuse);

                    }

                }
                if (counter >= 1000) break;
            }
            return null;
        }
    }
}
