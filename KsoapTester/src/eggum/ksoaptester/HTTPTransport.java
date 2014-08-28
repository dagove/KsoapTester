package eggum.ksoaptester;

import eggum.ksoaptester.Logger;

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
import java.net.Proxy;
import java.util.Date;
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

public class HTTPTransport extends Transport
{
  ExiJava exi = null;
  
  Logger logger = new Logger("H");
  
  long startTime = 0;
  long endTime = 0;
  long totalTime = 0;

  long marshallTime = 0;
  long unMarshallTime = 0;
  long roundtripTime = 0;

  long decompressingTime = 0;
  long parseTime = 0;
 
	
  public HTTPTransport(String url)
  {
    super(null, url);
  }

  public HTTPTransport(Proxy proxy, String url)
  {
    super(proxy, url);
  }

  public HTTPTransport(String url, int timeout)
  {
    super(url, timeout);
  }

  public HTTPTransport(Proxy proxy, String url, int timeout) {
    super(proxy, url, timeout);
  }

  public HTTPTransport(String url, int timeout, int contentLength)
  {
    super(url, timeout);
  }

  public HTTPTransport(Proxy proxy, String url, int timeout, int contentLength) {
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

  public List call(String soapAction, SoapEnvelope envelope, List headers, File outputFile)
    throws IOException, XmlPullParserException
  {
	startTime = System.nanoTime();
	
    if (soapAction == null) {
      soapAction = "\"\"";
    }
    boolean sendAsGzip = false;
    boolean sendAsExi = false;

    ServiceConnection connection = getServiceConnection();    
    
    if (headers != null) {
    	//System.out.println("T: headers.size(): " + headers.size());
      for (int i = 0; i < headers.size(); i++) {
        HeaderProperty hp = (HeaderProperty)headers.get(i);
        connection.setRequestProperty(hp.getKey(), hp.getValue());
        if((hp.getKey().equalsIgnoreCase("Content-Encoding")) 
        		&& (hp.getValue().equalsIgnoreCase("exi")))
        {
        	sendAsExi = true;
        }
        else if((hp.getKey().equalsIgnoreCase("Content-Encoding")) 
        		&& (hp.getValue().equalsIgnoreCase("gzip")))
        {
        	sendAsGzip = true;
        }
      }  }
    
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
        catch(Exception ex) {System.out.println(ex.toString());}
        requestData = byteStream.toByteArray();
        marshallTime = System.nanoTime() - m;
    }
    else
    {
    	long m = System.nanoTime();
    	requestData = createRequestData(envelope, "UTF-8");
    	System.out.println("REQUEST DATA LENGTH: " + requestData.length);
    	marshallTime = System.nanoTime() - m;
    }
    
    this.requestDump = (this.debug ? new String(requestData) : null);
    this.responseDump = null;

    connection.setRequestProperty("User-Agent", "ksoap2-android/2.6.0+");

    if (envelope.version != 120) {
      connection.setRequestProperty("SOAPAction", soapAction);
    }

    if (envelope.version == 120)
      connection.setRequestProperty("Content-Type", "application/soap+xml;charset=utf-8");
    else {
      connection.setRequestProperty("Content-Type", "text/xml;charset=utf-8");
    }
    
    connection.setRequestProperty("Connection", "close");
    //connection.setRequestProperty("Accept-Encoding", "gzip");

    connection.setRequestProperty("Content-Length", "" + requestData.length);
    connection.setFixedLengthStreamingMode(requestData.length);
    
    connection.setRequestMethod("POST");
    OutputStream os = connection.openOutputStream();
    
    os.write(requestData, 0, requestData.length);
    os.flush();
    os.close();    

    requestData = null;

    List retHeaders = null;
    byte[] buf = null;
    int contentLength = 8192;
    boolean gZippedContent = false;
    boolean exiContent = false;
    InputStream is = null;
    try 
    {
      int status = connection.getResponseCode();
      if (status != 200) {
        throw new IOException("HTTP request failed, HTTP status: " + status);
      }
      retHeaders = connection.getResponseProperties();
      for (int i = 0; i < retHeaders.size(); i++) {
        HeaderProperty hp = (HeaderProperty)retHeaders.get(i);

        if (null != hp.getKey())
        {
          if ((hp.getKey().equalsIgnoreCase("content-length")) && 
            (hp.getValue() != null)) {
            try {
              contentLength = Integer.parseInt(hp.getValue());
            } catch (NumberFormatException nfe) {
              contentLength = 8192;
            }

          }
          if ((hp.getKey().equalsIgnoreCase("Content-Encoding")) && (hp.getValue().equalsIgnoreCase("gzip")))
          {
            gZippedContent = true;
            break;
          }
          else if ((hp.getKey().equalsIgnoreCase("Content-Encoding")) && (hp.getValue().equalsIgnoreCase("exi")))
          {
        	exiContent = true;
            break;
          }
        	 
        }
      }

      is = new BufferedInputStream(connection.openInputStream(), contentLength);
      
      if (gZippedContent) {
          long x = System.nanoTime();
    	  is = new BufferedInputStream(getUnZippedInputStream(is));
	      decompressingTime = System.nanoTime()-x;

      }
      else if (exiContent) {
          long x = System.nanoTime();
    	  try {    		  	  
    		  is = new BufferedInputStream(exi.decompressInputStream(is)); 
			} catch (EfficientXMLException e) {
				e.printStackTrace();
				is = null;
			}
	      decompressingTime = System.nanoTime()-x;
      }
    }
    catch (IOException e)
    {
      if (gZippedContent) {
    	
        is = getUnZippedInputStream(new BufferedInputStream(connection.getErrorStream(), contentLength));
      }
      else {
        is = new BufferedInputStream(connection.getErrorStream(), contentLength);
      }

      if ((this.debug) && (is != null))
      {
        readDebug(is, contentLength, outputFile);
      }

      connection.disconnect();
      throw e;
    }

    if (this.debug) {
      is.close();
      is = readDebug(is, contentLength, outputFile);
    }
	
    long p = System.nanoTime();    
    parseResponse(envelope, is);
    is.close();
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
    
    os = null;
    buf = null;
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
    bos = null; // Endring
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
}