package eggum.ksoaptester;

import java.text.SimpleDateFormat;
import java.util.Date;

import eggum.ksoaptester.RawNFFIService;
import eggum.ksoaptester.R;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class KsoapMainActivity extends Activity {
	
	private Button noCompressionButton;
	private Button gzipCompressionButton;
	private Button exiCompressionButton;
	private Button startScriptButton;
	private Button stopScriptButton;
	private Button httpButton;
	private Button udpButton;
	private Button mqButton;
	private Button startRawButton;
	private Button startPictureButton;
	private Button startHelloButton;
	private Button stopRawButton;
	private Button stopPictureButton;
	private Button stopHelloButton;
	
	private Button publicIPButton;
	private Button privateIPButton;
	private String ipAddress = "192.168.0.102";
	
	private EditText roundsField;
	private TextView textField;
	
	private String compression = "noComp";	
	private String transport = "http";
	
	private Messenger messenger;
	
	private Handler handler;
	
	private int testCounter;
	private boolean testsRunning = false;
	PowerManager.WakeLock wakeLock;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ksoap_main);
		
		noCompressionButton = (Button) findViewById(R.id.noCompressionButton);
		gzipCompressionButton = (Button) findViewById(R.id.gzipCompressionButton);
		exiCompressionButton = (Button) findViewById(R.id.exiCompressionButton);
		startScriptButton = (Button) findViewById(R.id.startScriptButton);
		stopScriptButton = (Button) findViewById(R.id.stopScriptButton);
		httpButton = (Button) findViewById(R.id.httpButton);
		udpButton = (Button) findViewById(R.id.udpButton);
		mqButton = (Button) findViewById(R.id.mqButton);
		startRawButton = (Button) findViewById(R.id.startRawButton);
		startPictureButton = (Button) findViewById(R.id.startPictureButton);
		startHelloButton = (Button) findViewById(R.id.startHelloButton);
		stopRawButton = (Button) findViewById(R.id.stopRawButton);
		stopPictureButton = (Button) findViewById(R.id.stopPictureButton);
		stopHelloButton = (Button) findViewById(R.id.stopHelloButton);
		
		publicIPButton  = (Button) findViewById(R.id.publicIPButton);
		privateIPButton  = (Button) findViewById(R.id.privateIPButton);
		
		roundsField = (EditText) findViewById(R.id.roundsField);
		textField = (TextView) findViewById(R.id.textField);
		
		publicIPButton.setOnClickListener(IPListener);
		privateIPButton.setOnClickListener(IPListener);
		
		noCompressionButton.setOnClickListener(compressionListener);
		gzipCompressionButton.setOnClickListener(compressionListener);
		exiCompressionButton.setOnClickListener(compressionListener);
		httpButton.setOnClickListener(transportListener);
		udpButton.setOnClickListener(transportListener);
		mqButton.setOnClickListener(transportListener);
		
		startRawButton.setOnClickListener(startTestListener);
		startPictureButton.setOnClickListener(startTestListener);
		startHelloButton.setOnClickListener(startTestListener);
		stopRawButton.setOnClickListener(stopTestListener);
		stopPictureButton.setOnClickListener(stopTestListener);
		stopHelloButton.setOnClickListener(stopTestListener);
		
		startScriptButton.setOnClickListener(scriptListener);
		stopScriptButton.setOnClickListener(scriptListener);
		
		privateIPButton.setEnabled(false);
		noCompressionButton.setEnabled(false);
		httpButton.setEnabled(false);
		
		stopRawButton.setEnabled(false);
		stopPictureButton.setEnabled(false);
		stopHelloButton.setEnabled(false);
		
		handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                System.out.println("Magic Message: " + msg.what);
            	if(msg.what==1) {
    				startRawButton.setEnabled(true);
    				startPictureButton.setEnabled(true);
    				startHelloButton.setEnabled(true);
    				stopRawButton.setEnabled(false);
    				stopService(RawNFFIService.class);
            	}
            	if(msg.what==2) {
    				startRawButton.setEnabled(true);
    				startPictureButton.setEnabled(true);
    				startHelloButton.setEnabled(true);
    				stopPictureButton.setEnabled(false);
    				stopService(PictureService.class);          		
            	}
            	if(msg.what==3) {
    				startRawButton.setEnabled(true);
    				startPictureButton.setEnabled(true);
    				startHelloButton.setEnabled(true);
    				stopHelloButton.setEnabled(false);
    				stopService(HelloService.class);         		
            	}
            	makeSound();
            	updateTextField();
            	if (testsRunning) {startNextTest();}
            }
        };
		
		System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");		  
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		System.out.println("baseDir = " + Environment.getExternalStorageDirectory().getAbsolutePath());
		System.out.println("baseDir = " + Environment.getExternalStorageDirectory());
	}
	
	private void updateTextField()
	{
		String time = new SimpleDateFormat("HH.mm").format(new Date());
		textField.append("\nTest" + testCounter + " done" + time);
	}
	
	private void startNextTest()
	{
		testCounter++;

			 if(testCounter == 1) { transport = "http"; compression = "noComp"; }
		else if(testCounter == 2) { transport = "http"; compression = "gzip"; }
		else if(testCounter == 3) { transport = "http"; compression = "exi"; }
		else if(testCounter == 4) { transport = "mq"; compression = "noComp"; }
		else if(testCounter == 5) { transport = "mq"; compression = "gzip"; }
		else if(testCounter == 6) { transport = "mq"; compression = "exi"; }
		else if(testCounter == 7) { transport = "udp"; compression = "noComp"; }
		else if(testCounter == 8) { transport = "udp"; compression = "gzip"; }
		else if(testCounter == 9) { transport = "udp"; compression = "exi"; }

			 
		if(testCounter < 10)
		{
			testsRunning = true;
			//startService(RawNFFIService.class);
			startService(PictureService.class);
		}
		else
		{
			testsRunning = false;	
			wakeLock.release();
		}
		
	}
	
	private OnClickListener scriptListener= new OnClickListener() {
		@Override
		public void onClick(View vee) {
			switch(vee.getId())
			{
			case R.id.startScriptButton:
				
		    	PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		    	wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
		    	wakeLock.acquire();
				
				testCounter = 0;
				startNextTest();		
			break;
			case R.id.stopScriptButton:
				if(testsRunning)
				{
					testCounter = 10;
					//stopService(RawNFFIService.class);
					startService(PictureService.class);
					testsRunning = false;
					
					wakeLock.release();
				}
			}
		}
	};
	
	private void makeSound()
	{
		ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 200);
		toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000); // 1000 is duration in ms
	}

	private OnClickListener IPListener = new OnClickListener() {		
		public void onClick(View v) {
			switch(v.getId())
			{
			case R.id.publicIPButton:
				privateIPButton.setEnabled(true);
				publicIPButton.setEnabled(false);
				ipAddress = "84.202.118.69";
			break;
			case R.id.privateIPButton:
				privateIPButton.setEnabled(false);
				publicIPButton.setEnabled(true);
				ipAddress = "192.168.0.102";	//Bergen
			break;
			}
		}	
	};
	
	private OnClickListener stopTestListener = new OnClickListener() {

		public void onClick(View v) {
			switch(v.getId())
			{
			case R.id.stopRawButton:
				startRawButton.setEnabled(true);
				startPictureButton.setEnabled(true);
				startHelloButton.setEnabled(true);
				stopRawButton.setEnabled(false);
				stopService(RawNFFIService.class);
			break;
			case R.id.stopPictureButton:
				startRawButton.setEnabled(true);
				startPictureButton.setEnabled(true);
				startHelloButton.setEnabled(true);
				stopPictureButton.setEnabled(false);
				stopService(PictureService.class); 
			break;
			case R.id.stopHelloButton:
				startRawButton.setEnabled(true);
				startPictureButton.setEnabled(true);
				startHelloButton.setEnabled(true);
				stopHelloButton.setEnabled(false);
				stopService(HelloService.class);
			break;
		}
		}
	};
	
	// Start the  service
	public void startService(Class c) {
		Intent i = new Intent(this, c);
		i.putExtra("rounds", Integer.parseInt(roundsField.getText().toString()));
		i.putExtra("compression", compression);
		i.putExtra("transport", transport);	
		i.putExtra("ipAddress", ipAddress);
		
		messenger = new Messenger(handler);
		i.putExtra("messenger", messenger);
		startService(i);
		System.out.println("StartIntent Sent");
	}
	
	// Stop the  service
	public void stopService(Class c) {
		stopService(new Intent(this, c));
		System.out.println("StopIntent Sent");
	}
	
	private OnClickListener startTestListener = new OnClickListener() {

		public void onClick(View v) {
			switch(v.getId())
			{
			case R.id.startRawButton:
				startRawButton.setEnabled(false);
				startPictureButton.setEnabled(false);
				startHelloButton.setEnabled(false);
				stopRawButton.setEnabled(true);
				startService(RawNFFIService.class);
			break;
			case R.id.startPictureButton:
				startRawButton.setEnabled(false);
				startPictureButton.setEnabled(false);
				startHelloButton.setEnabled(false);
				stopPictureButton.setEnabled(true);
				startService(PictureService.class);
			break;
			case R.id.startHelloButton:
				startRawButton.setEnabled(false);
				startPictureButton.setEnabled(false);
				startHelloButton.setEnabled(false);
				stopHelloButton.setEnabled(true);
				startService(HelloService.class);				
			break;

		}
		}
	};
	
	private OnClickListener transportListener = new OnClickListener() {

		public void onClick(View v) {
			switch(v.getId())
			{
			case R.id.httpButton:
				transport = "http";
				httpButton.setEnabled(false);
				udpButton.setEnabled(true);
				mqButton.setEnabled(true);
			break;
			case R.id.udpButton:
				transport = "udp";
				httpButton.setEnabled(true);
				udpButton.setEnabled(false);
				mqButton.setEnabled(true);
			break;
			case R.id.mqButton:
				transport = "mq";
				httpButton.setEnabled(true);
				udpButton.setEnabled(true);
				mqButton.setEnabled(false);
			break;
			}
		}
	};
	
	private OnClickListener compressionListener = new OnClickListener() {

		public void onClick(View v) {
			switch(v.getId())
			{
			case R.id.noCompressionButton:
				compression = "noComp";
				noCompressionButton.setEnabled(false);
				gzipCompressionButton.setEnabled(true);
				exiCompressionButton.setEnabled(true);
			break;
			case R.id.gzipCompressionButton:
				compression = "gzip";	
				noCompressionButton.setEnabled(true);
				gzipCompressionButton.setEnabled(false);
				exiCompressionButton.setEnabled(true);
							
			break;
			case R.id.exiCompressionButton:
				compression = "exi";
				noCompressionButton.setEnabled(true);
				gzipCompressionButton.setEnabled(true);
				exiCompressionButton.setEnabled(false);
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.ksoap_main, menu);
		return true;
	}

}
