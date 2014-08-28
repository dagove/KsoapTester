package eggum.ksoaptester;

import java.util.ArrayList;
import java.util.List;

import org.ksoap2.HeaderProperty;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.SoapFault;
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
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.widget.Toast;

public class HelloService extends Service{
		
	private String HELLO_URL = "http://192.168.0.102:8081/proxy"; // Proxy
	private String HELLO_IPADDRESS = "192.168.0.102"; // Proxy
	private final String HELLO_NAMESPACE = "http://eggum/";
	private final String HELLO_METHOD_NAME = "hello";
	private final String HELLO_SOAP_ACTION = "http://eggum/hello"; // NAMESPACE + METHOD_NAME
	
	private int UDPPort;
	private String mqQueueName;
	
	private Intent batteryStatus;
	private int startBatteryPct;
	private int stopBatteryPct;
	
	private int rounds = 0; 
	private int tries = 0;
	private int faultyUploads = 0;
	private String compression = "";
	private String transport = "";
	private String ipAddress = "192.168.0.102";
	
	private SoapSerializationEnvelope helloEnvelope;
	private SoapObject request;
	private String Webresponse = "";
	
	private HTTPTransport helloHTTPTransport = null;
	private UDPTransport helloUDPTransport = null;
	private MQTransport helloMQTransport = null;	
	
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
		
		HELLO_IPADDRESS = ipAddress;
		HELLO_URL = "http://" + ipAddress + ":8081/proxy";
		
		messenger = (Messenger) intent.getParcelableExtra("messenger");
		message = Message.obtain(null, 3);
		
		if(compression.equalsIgnoreCase("noComp")) {UDPPort = 8085; mqQueueName = "helloNoComp";}
		else if(compression.equalsIgnoreCase("gzip")) {UDPPort = 8086; mqQueueName = "helloGzipComp";}
		else {UDPPort = 8087; mqQueueName = "helloExiComp";}
		
        headerList = new ArrayList<HeaderProperty>();      
        headerList.add(new HeaderProperty("Content-Encoding", compression));
        headerList.add(new HeaderProperty("Accept-Encoding", compression));
   
        if(transport.equalsIgnoreCase("http"))         {
        	helloHTTPTransport = new HTTPTransport(HELLO_URL);
            httpSending = true; }
        else if(transport.equalsIgnoreCase("udp")) {
        	helloUDPTransport = new UDPTransport(HELLO_IPADDRESS);
        	udpSending = true; }
        else{	// MQ      
        	helloMQTransport = new MQTransport(HELLO_IPADDRESS);
        	mqSending = true; }
        
  
        thread = new Thread() {
        public void run() {   			
        	while(shouldSend&&rounds>0&&tries<40){
  				createEnvelope();
        		try {
					if(httpSending) { helloHTTPTransport.call(HELLO_SOAP_ACTION, helloEnvelope, headerList); }
					else if(udpSending) { helloUDPTransport.call(HELLO_SOAP_ACTION, helloEnvelope, UDPPort, null); }
					else helloMQTransport.call(HELLO_SOAP_ACTION, helloEnvelope, mqQueueName, null);;
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
					response = (SoapPrimitive)helloEnvelope.getResponse();
					Webresponse = response.toString();
			 		System.out.println("Response: " + Webresponse);
				} catch (SoapFault e) {
					logError(e.toString());
					e.printStackTrace();
					continue;
				}
				
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
    	if(helloHTTPTransport != null)	
    	{ helloHTTPTransport.logComment(s);}
    	if(helloUDPTransport != null)	
    	{ helloUDPTransport.logComment(s);}
    	if(helloMQTransport != null)	
    	{ helloMQTransport.logComment(s);}
    }
    
    public void logError(String s)
    {
    	if(helloHTTPTransport != null)	
    	{ helloHTTPTransport.logError(s);}
    	if(helloUDPTransport != null)	
    	{ helloUDPTransport.logError(s);}
    	if(helloMQTransport != null)	
    	{ helloMQTransport.logError(s);}
    }
    
    public void closeLog()
    {
    	stopBatteryPct = getBatteryLevel();
		
    	if(helloHTTPTransport != null)	
    	{ helloHTTPTransport.stopLogging("HelloService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(helloUDPTransport != null)	
    	{ helloUDPTransport.stopLogging("HelloService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(helloMQTransport != null)	
    	{ helloMQTransport.stopLogging("HelloService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
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
    
    void createEnvelope()
    {
    	request = new SoapObject(HELLO_NAMESPACE, HELLO_METHOD_NAME);    	
        PropertyInfo fromProp = new PropertyInfo();
    	
        fromProp.setName("name");
        fromProp.setValue("Dag");
        fromProp.setType(String.class);        
        
        request.addProperty(fromProp);
       
        helloEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        helloEnvelope.implicitTypes = true;	// Remove i:type="d:string"
        helloEnvelope.setOutputSoapObject(request);

        if(!httpSending)
        {
        	helloEnvelope.headerOut = new Element[1];        
        	helloEnvelope.headerOut[0] = buildWsAddressingHeader();
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
    	Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
    }
}

