package eggum.ksoaptester;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.ksoap2.HeaderProperty;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.SoapFault;
import org.ksoap2.serialization.MarshalBase64;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.kxml2.kdom.Element;
import org.kxml2.kdom.Node;

import eggum.ksoaptester.HTTPTransport;
import eggum.ksoaptester.MQTransport;
import eggum.ksoaptester.UDPTransport;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.widget.Toast;

public class RawNFFIService extends Service{	
	
	private String RAW_IPADDRESS; // = "192.168.0.102"; // Proxy
	private String RAW_URL; // = "http://192.168.0.102:8081/proxy";
	private final String RAW_NAMESPACE = "http://service.nffi.org/";
	private final String RAW_METHOD_NAME = "operation";
	private final String RAW_SOAP_ACTION = "http://service.nffi.org/operation";
	
	private int UDPPort;
	private String mqQueueName;
	
	private String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
	
	private Intent batteryStatus;
	private int startBatteryPct;
	private int stopBatteryPct;
	private int counter = 1;
	private int rounds = 0; 
	private int tries = 0;
	private int faultyUploads = 0;
	private String compression = "";
	private String transport = "";
	private String ipAddress = "192.168.0.102";
	
	private SoapSerializationEnvelope rawEnvelope;
	private SoapObject request;
	
	private HTTPTransport rawHTTPTransport = null;
	private UDPTransport rawUDPTransport = null;
	private MQTransport rawMQTransport = null;	
	
	public Thread thread;
	public boolean shouldSend = false;
	
	public boolean httpSending = false;
	public boolean udpSending = false;
	public boolean mqSending = false;
	
	public List<HeaderProperty> headerList;
	
	public Messenger messenger;
	public Message message;
	
	PowerManager.WakeLock wakeLock;

	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
    public void onCreate() {
		shouldSend = true;

    }
	
	public int getBatteryLevel()
	{
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		batteryStatus = this.registerReceiver(null, ifilter);
		return batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	}

    @Override
    public void onStart(Intent intent, int startId) {
    	
    	PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    	wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakelockTag");
    	wakeLock.acquire();
		
		startBatteryPct = getBatteryLevel();	    
		Bundle extras = intent.getExtras();
		rounds = extras.getInt("rounds");
		compression = extras.getString("compression");
		transport = extras.getString("transport");
		ipAddress = extras.getString("ipAddress");
		
		RAW_IPADDRESS = ipAddress;
		RAW_URL = "http://" + ipAddress + ":8081/proxy";
		
		messenger = (Messenger) intent.getParcelableExtra("messenger");
		message = Message.obtain(null, 1);
		
		if(compression.equalsIgnoreCase("noComp")) {UDPPort = 8082; mqQueueName = "rawNoComp";}
		else if(compression.equalsIgnoreCase("gzip")) {UDPPort = 8083; mqQueueName = "rawGzipComp";}
		else {UDPPort = 8084; mqQueueName = "rawExiComp";}
		
        headerList = new ArrayList<HeaderProperty>();      
        headerList.add(new HeaderProperty("Content-Encoding", compression));
        headerList.add(new HeaderProperty("Accept-Encoding", compression));
   
        if(transport.equalsIgnoreCase("http"))         {
        	rawHTTPTransport = new HTTPTransport(RAW_URL);
            httpSending = true; }
        else if(transport.equalsIgnoreCase("udp")) {
        	rawUDPTransport = new UDPTransport(RAW_IPADDRESS);
        	udpSending = true; }
        else{	// MQ      
        	rawMQTransport = new MQTransport(RAW_IPADDRESS);
        	mqSending = true; }
           	
        thread = new Thread() {
        public void run() {
        	while(shouldSend&&rounds>0&&tries<40){
       			createEnvelope(getNextFile());
				try {
					if(httpSending) { rawHTTPTransport.call(RAW_SOAP_ACTION, rawEnvelope, headerList); }
					else if(udpSending) { rawUDPTransport.call(RAW_SOAP_ACTION, rawEnvelope, UDPPort, null); }
					else rawMQTransport.call(RAW_SOAP_ACTION, rawEnvelope, mqQueueName, null);;
				} catch (Exception e) {
					System.out.println("Exception");
		            e.printStackTrace();
					if(tries == 0) {logError(e.toString());}
					tries++;
					try {
						Thread.sleep(tries*2000);
						logError("Seconds slept " + 2*tries);
						System.out.println("Seconds slept " + 2*tries);
					} catch (InterruptedException e1) {
						logError("Woken up " + e1.toString());
						continue;
					}
					continue;
				}
				
				SoapPrimitive response = null;
				try {
					response = (SoapPrimitive)rawEnvelope.getResponse();
					if(!(response.toString()).startsWith("NFFI"))
					{
						faultyUploads++;
					}


				} catch (SoapFault e) {
					logError(e.toString());
					e.printStackTrace();
					continue;
				}
				counter++;
				if(counter == 21) { counter = 1;}
				tries = 0;
          		rounds--;
       			}
        		closeLog();
       			selfDestruct();
            }
        };
        	thread.start();
            Toast.makeText(this, " Service Started ", Toast.LENGTH_SHORT).show();        
    }
    
    public void logComment(String s)
    {
    	if(rawHTTPTransport != null)	
    	{ rawHTTPTransport.logComment(s);}
    	if(rawUDPTransport != null)	
    	{ rawUDPTransport.logComment(s);}
    	if(rawMQTransport != null)	
    	{ rawMQTransport.logComment(s);}
    }
    
    public void logError(String s)
    {
    	if(rawHTTPTransport != null)	
    	{ rawHTTPTransport.logError(s);}
    	if(rawUDPTransport != null)	
    	{ rawUDPTransport.logError(s);}
    	if(rawMQTransport != null)	
    	{ rawMQTransport.logError(s);}
    }
    
    public void closeLog()
    {
    	stopBatteryPct = getBatteryLevel();
		
    	if(rawHTTPTransport != null)	
    	{ rawHTTPTransport.stopLogging("RawService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(rawUDPTransport != null)	
    	{ rawUDPTransport.stopLogging("RawService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(rawMQTransport != null)	
    	{ rawMQTransport.stopLogging("RawService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    }
    
    public void selfDestruct()
    {	
    	wakeLock.release();
    	
		try {
            messenger.send(message);
        } catch (RemoteException exception) {
            exception.printStackTrace();
        }
    }
    	
    private byte[] getNextFile()
    {	 
		String fileDir = baseDir + "/NFFIfiles/" + counter + "raw.xml";

		URL resource = null;
		try {
			resource = new File(fileDir).toURI().toURL();
		} catch (MalformedURLException e) {e.printStackTrace();}
		
		InputStream in;
		ByteArrayOutputStream bos = null;
		try {
			
			in = resource.openStream();
			bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
		 
			for(int read; (read = in.read(buf)) != -1;) {
				 bos.write(buf, 0, read);
			}
			in.close();
			bos.close();
		} catch (IOException e) {e.printStackTrace();}
		 return bos.toByteArray();
    }
    
    void createEnvelope(byte[] data)
    {
    	request = new SoapObject(RAW_NAMESPACE, RAW_METHOD_NAME);    	
	   	
		PropertyInfo pi = new PropertyInfo();	         	
		pi.setName("nffiFile");
		pi.setValue(data);
		pi.setType(byte.class);
	       
		request.addProperty(pi);

		rawEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
		new MarshalBase64().register(rawEnvelope);   //enable serialization
		rawEnvelope.implicitTypes = true;	// Remove i:type="d:string"
		rawEnvelope.setOutputSoapObject(request);
	    
		if(!httpSending)
	    {
			rawEnvelope.headerOut = new Element[1];        
			rawEnvelope.headerOut[0] = buildWsAddressingHeader();
	    }
    }
    
    private Element buildWsAddressingHeader() {
	    Element h = new Element().createElement("wsa", "WSAddressing");
	    
	    Element messageID = new Element().createElement("wsa", "MessageID");
	    messageID.addChild(Node.TEXT, "FFFF-FFFFFFFF-FFFFFFFF-FFFF");
	    h.addChild(Node.ELEMENT, messageID);	   
	    
	    Element address = new Element().createElement("wsa", "Address");
	    address.addChild(Node.TEXT, "myAddress");
	    
	    Element element = new Element().createElement("wsa", "ReplyTo");
	    element.addChild(Node.ELEMENT, address);
	    h.addChild(Node.ELEMENT, element);
	    
	    Element to = new Element().createElement("wsa", "To");
	    to.addChild(Node.TEXT, "//eggum.example/testReceive");
	    h.addChild(Node.ELEMENT, to);	   
	    
	    Element action = new Element().createElement("wsa", "Action");
	    action.addChild(Node.TEXT, "//eggum.example/testAction");
	    h.addChild(Node.ELEMENT, action);		    

	    return h;
    }
    

    @Override
    public void onDestroy() {
    	shouldSend = false;
    	thread.interrupt();
    	Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
    }
}

