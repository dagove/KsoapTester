package eggum.ksoaptester;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
import android.util.Base64;
import android.widget.Toast;

public class PictureService extends Service{
	
	private String PICTURE_IPADDRESS = "192.168.0.102"; // Proxy
	private String PICTURE_URL = "http://192.168.0.102:8081/proxy"; // Proxy
	private final String PICTURE_NAMESPACE = "http://service.picture.org/";
	private final String PICTURE_METHOD_NAME = "exchangePicture";
	private final String PICTURE_SOAP_ACTION = "http://service.picture.org/" + PICTURE_METHOD_NAME;
	
	private int UDPPort;
	private String mqQueueName;
	
	private String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
	
	private Intent batteryStatus;
	private int startBatteryPct;
	private int stopBatteryPct;

	private int rounds = 0; 
	private int tries = 0;
	private int faultyUploads = 0;
	private String compression = "";
	private String transport = "";
	private String ipAddress = "192.168.0.102";
	
	private SoapSerializationEnvelope pictureEnvelope;
	private SoapObject request;

	
	private HTTPTransport pictureHTTPTransport = null;
	private UDPTransport pictureUDPTransport = null;
	private MQTransport pictureMQTransport = null;
	
	public Thread thread;
	public boolean shouldSend = false;

	public boolean httpSending = false;
	public boolean udpSending = false;
	public boolean mqSending = false;
	
	public List<HeaderProperty> headerList;
	
	public Messenger messenger;
	public Message message;
	
	PowerManager.WakeLock wakeLock;
	
	public byte[] newPicture = null; 
	
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
		
		PICTURE_IPADDRESS = ipAddress;
		PICTURE_URL = "http://" + ipAddress + ":8081/proxy";
		
		messenger = (Messenger) intent.getParcelableExtra("messenger");
		message = Message.obtain(null, 2);
		
		if(compression.equalsIgnoreCase("noComp")) {UDPPort = 8088; mqQueueName = "uploadNoComp";}
		else if(compression.equalsIgnoreCase("gzip")) {UDPPort = 8089; mqQueueName = "uploadGzipComp";}
		else {UDPPort = 8090; mqQueueName = "uploadExiComp";}
		
        headerList = new ArrayList<HeaderProperty>();      
        headerList.add(new HeaderProperty("Content-Encoding", compression));
        headerList.add(new HeaderProperty("Accept-Encoding", compression));
        
        if(transport.equalsIgnoreCase("http"))         {
        	pictureHTTPTransport = new HTTPTransport(PICTURE_URL);
            httpSending = true; }
        else if(transport.equalsIgnoreCase("udp")) {
        	pictureUDPTransport = new UDPTransport(PICTURE_IPADDRESS);
        	udpSending = true; }
        else{	// MQ      
        	pictureMQTransport = new MQTransport(PICTURE_IPADDRESS);
        	mqSending = true; }
        
        thread = new Thread() {
        public void run() {
        	while(shouldSend&&rounds>0&&tries<40){
  				createEnvelope(getPictureFile());
				try {
					if(httpSending) { pictureHTTPTransport.call(PICTURE_SOAP_ACTION, pictureEnvelope, headerList); }
					else if(udpSending) { pictureUDPTransport.call(PICTURE_SOAP_ACTION, pictureEnvelope, UDPPort, null); }
					else pictureMQTransport.call(PICTURE_SOAP_ACTION, pictureEnvelope, mqQueueName, null);;
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
					response = (SoapPrimitive)pictureEnvelope.getResponse();
				} catch (SoapFault e) {

					logError(e.toString());
					e.printStackTrace();
					continue;
				}
				try {
				storePictureFile(response.toString());
				} catch (Exception e) {
					faultyUploads++;
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
    	if(pictureHTTPTransport != null)	
    	{ pictureHTTPTransport.logComment(s);}
    	if(pictureUDPTransport != null)	
    	{ pictureUDPTransport.logComment(s);}
    	if(pictureMQTransport != null)	
    	{ pictureMQTransport.logComment(s);}
    }
    
    public void logError(String s)
    {
    	if(pictureHTTPTransport != null)	
    	{ pictureHTTPTransport.logError(s);}
    	if(pictureUDPTransport != null)	
    	{ pictureUDPTransport.logError(s);}
    	if(pictureMQTransport != null)	
    	{ pictureMQTransport.logError(s);}
    }
    
    public void closeLog()
    {
    	stopBatteryPct = getBatteryLevel();
		
    	if(pictureHTTPTransport != null)	
    	{ pictureHTTPTransport.stopLogging("PictureService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(pictureUDPTransport != null)	
    	{ pictureUDPTransport.stopLogging("PictureService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
    	if(pictureMQTransport != null)	
    	{ pictureMQTransport.stopLogging("PictureService", compression, startBatteryPct, stopBatteryPct, faultyUploads); }
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
    
    private void storePictureFile(String dataString)
    {
    	System.out.println("Storing String length: " + dataString.length());
    	storePictureFile(Base64.decode(dataString, Base64.DEFAULT));   
    }
    
    private void storePictureFile(byte[] data)
    {
    	System.out.println("Storing bytes length: " + data.length);
        File myDir = new File(baseDir + "/Files");    
        
        String fname = "testpicturecopy.jpg";
        File file = new File (myDir, fname);
        if(file.exists()) { file.delete();}
        try {
        	FileOutputStream out = new FileOutputStream(file);
        	out.write(data);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        if (file.exists()) 
        {
        	System.out.println("File created");
        }
        else { System.out.println("File not created"); }
    }
    	
    private byte[] getPictureFile()
    {	 
		String fileDir = baseDir + "/Files/testpicture.jpg";

		URL resource = null;
		 try {
			 resource = new File(fileDir).toURI().toURL();
		 } catch (MalformedURLException e) {
			 e.printStackTrace();
		 }
		 InputStream in;
		 ByteArrayOutputStream bos = null;
		 try {
			 in = resource.openStream();
			 bos = new ByteArrayOutputStream();
			 byte[] buf = new byte[1024];
		 
			 for(int read; (read = in.read(buf)) != -1;) {
				 bos.write(buf, 0, read);
			 }
		 } catch (IOException e) {
			 e.printStackTrace();
		 }
	 	 return bos.toByteArray();
    }
    
    void createEnvelope(byte[] data)
    {
    	request = new SoapObject(PICTURE_NAMESPACE, PICTURE_METHOD_NAME);    	
       	
   	 	PropertyInfo pi = new PropertyInfo();	         	
   	 	pi.setName("picture");
   	 	pi.setValue(data);
   	 	pi.setType(byte.class);
           
   	 	request.addProperty(pi);
           				        
   	 	pi = new PropertyInfo();	         	
   	 	pi.setName("pictureName");
   	 	pi.setValue("Android");
   	 	pi.setType(String.class);
          
   	 	request.addProperty(pi);

   	 	pictureEnvelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
   	 	new MarshalBase64().register(pictureEnvelope);   //enable serialization
   	 	pictureEnvelope.implicitTypes = true;	// Remove i:type="d:string"
   	 	pictureEnvelope.setOutputSoapObject(request);
	    
   	 	if(!httpSending)
   	 	{
   	 		pictureEnvelope.headerOut = new Element[1];        			
   	 		pictureEnvelope.headerOut[0] = buildWsAddressingHeader();
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
