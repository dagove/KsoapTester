package eggum.ksoaptester;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Proxy;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.ksoap2.HeaderProperty;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.transport.ServiceConnection;
import org.ksoap2.transport.ServiceConnectionSE;
import org.ksoap2.transport.Transport;
import org.xmlpull.v1.XmlPullParserException;

import com.siemens.ct.exi.exceptions.EXIException;

import eggum.ksoaptester.EfficientXMLException;
import eggum.ksoaptester.ExiJava;

public class UDPTransport extends Transport
{
	  ExiJava exi = null;
	
	  Logger logger = new Logger("U");
	  
	  long startTime = 0;
	  long endTime = 0;
	  long totalTime = 0;

	  long marshallTime = 0;
	  long unMarshallTime = 0;
	  long roundtripTime = 0;

	  long decompressingTime = 0;
	  long parseTime = 0;
	
	  BufferedInputStream is2 = null;
	  InputStream is3 = null;
	
  public UDPTransport(String url)
  {
    super(null, url);
  }

  public UDPTransport(Proxy proxy, String url)
  {
    super(proxy, url);
  }

  public UDPTransport(String url, int timeout)
  {
    super(url, timeout);
  }

  public UDPTransport(Proxy proxy, String url, int timeout) {
    super(proxy, url, timeout);
  }

  public UDPTransport(String url, int timeout, int contentLength)
  {
    super(url, timeout);
  }

  public UDPTransport(Proxy proxy, String url, int timeout, int contentLength) {
    super(proxy, url, timeout);
  }

  public void call(String soapAction, SoapEnvelope envelope)
    throws IOException, XmlPullParserException
  {
    call(soapAction, envelope, null);
  }

  public List call(String soapAction, SoapEnvelope envelope, List headers) throws IOException, XmlPullParserException
  {
    return call(soapAction, envelope, headers, null);
  }
  
  public void logError(String s)
  {
	  logger.logError(s);
  }
  
  public void logComment(String s)
  {
	  logger.logComment(s);
  }
  
  public void stopLogging(String service, String compression, int startBatteryPct, int stopBatteryPct, int faultyUploads)
  {
	  logger.closeLogFile(service, compression, startBatteryPct, stopBatteryPct, faultyUploads);
  }

  public List call(String soapAction, SoapEnvelope envelope, int port, File outputFile)
    throws IOException, XmlPullParserException
  {
	startTime = System.nanoTime();
    
	if (soapAction == null) {
      soapAction = "\"\"";
    }
    
    boolean sendAsGzip = false;
    boolean sendAsExi = false;
    
    if( (port == 8083) || (port == 8086) || (port == 8089) ) {sendAsGzip = true;}
    if( (port == 8084) || (port == 8087) || (port == 8090) ) {sendAsExi = true;}
    
    byte[] requestData = null;
    if(sendAsExi)
    {
    	if(exi == null) {exi= new ExiJava();}
    	long m = System.nanoTime();
        try {
        	requestData = exi.compressByteArray(createRequestData(envelope, "UTF-8"));	
    	} catch (EfficientXMLException e) {
    		e.printStackTrace();
    		requestData = null;}
        marshallTime = System.nanoTime() - m;
    }    
    else if(sendAsGzip)
    {
    	System.out.println("Creating GZIP!");
    	long m = System.nanoTime();
    	byte[] b = createRequestData(envelope, "UTF-8");
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(b.length);
        GZIPOutputStream zipStream = new GZIPOutputStream(byteStream);
        try 
        {
        	zipStream.write(b);
        	zipStream.close();
        	byteStream.close();
        }
        catch(Exception ex) { System.out.println("GZIP: " + ex.toString());}
        requestData = byteStream.toByteArray();
        marshallTime = System.nanoTime() - m;
    }
    else
    {
    	System.out.println("Creating NOT GZIP!");
    	long m = System.nanoTime();
    	requestData = createRequestData(envelope, "UTF-8");
        marshallTime = System.nanoTime() - m;
    }
    
    
    this.requestDump = (this.debug ? new String(requestData) : null);
    this.responseDump = null;
    
    DatagramSocket clientSocket = new DatagramSocket();
    clientSocket.setSoTimeout(10000);
	InetAddress IPAddress = InetAddress.getByName(url);
	
	DatagramPacket sendPacket = new DatagramPacket(requestData, requestData.length, IPAddress, port);
	clientSocket.send(sendPacket);
    
	byte[] receiveData = new byte[81920];   
	DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);       
	clientSocket.receive(receivePacket);	
	
	ByteArrayInputStream is = new ByteArrayInputStream(receiveData);

	clientSocket.close();

    requestData = null;
    
    List retHeaders = null;
    
      if (sendAsGzip) {    	  
          long x = System.nanoTime();
    	  is2 = new BufferedInputStream(getUnZippedInputStream(new BufferedInputStream(is)));
	      decompressingTime = System.nanoTime()-x;
      }
      else if (sendAsExi) {
          long x = System.nanoTime();
    	  try {
    		  	is3 = exi.decompressInputStream(is);
			} catch (EfficientXMLException e) {
				e.printStackTrace();
				is = null;
			}
	      decompressingTime = System.nanoTime()-x;
      }
      
    long p = System.nanoTime();      
    if (sendAsGzip) { parseResponse(envelope, is2); is2.close();}
    else if (sendAsExi) { parseResponse(envelope, is3); is3.close();}
    else{ parseResponse(envelope, is); is.close(); }
    parseTime = (System.nanoTime() - p);
    
    endTime = System.nanoTime();
    
    totalTime = endTime - startTime;
    
    unMarshallTime = decompressingTime + parseTime;
    roundtripTime = totalTime - marshallTime - unMarshallTime;    
    
    logger.logMarshallTime(marshallTime);
	logger.logRoundtripTime(roundtripTime);
	logger.logDecompressingTime(decompressingTime);
	logger.logParseTime(parseTime);
	logger.logUnMarshallTime(unMarshallTime);
    logger.logTotalTime(endTime - startTime); 
    
    
    return retHeaders;
  }

  private InputStream readDebug(InputStream is, int contentLength, File outputFile)
    throws IOException
  {
    OutputStream bos;
    if (outputFile != null) {
      bos = new FileOutputStream(outputFile);
    }
    else {
      bos = new ByteArrayOutputStream(contentLength > 0 ? contentLength : 262144);
    }

    byte[] buf = new byte[256];
    while (true)
    {
      int rd = is.read(buf, 0, 256);
      if (rd == -1) {
        break;
      }
      bos.write(buf, 0, rd);
    }

    bos.flush();
    if ((bos instanceof ByteArrayOutputStream)) {
      buf = ((ByteArrayOutputStream)bos).toByteArray();
    }
    //OutputStream bos = null;
    bos = null;
    this.responseDump = new String(buf);
    is.close();
    return new ByteArrayInputStream(buf);
  }

  private InputStream getUnZippedInputStream(InputStream inputStream)
    throws IOException
  {
    try
    {
      return (GZIPInputStream)inputStream; } catch (ClassCastException e) { System.out.println(e.toString());
    }
    return new GZIPInputStream(inputStream);
  }

  public ServiceConnection getServiceConnection() throws IOException
  {
    return new ServiceConnectionSE(this.proxy, this.url, this.timeout);
  }

@Override
public List call(String arg0, SoapEnvelope arg1, List arg2, File arg3)
		throws IOException, XmlPullParserException {
	// TODO Auto-generated method stub
	return null;
}
}