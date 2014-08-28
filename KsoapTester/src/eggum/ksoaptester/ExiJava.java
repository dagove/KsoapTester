package eggum.ksoaptester;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.api.sax.EXIResult;
import com.siemens.ct.exi.api.sax.EXISource;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

class ExiJava
{
	private boolean initialized = false;
    EXIFactory exiFactory = null;
    EXISource saxSource = null;
    XMLReader decodingXmlReader = null;
    
    TransformerFactory tf = null;
    Transformer transformer = null;
    
    XMLReader encodingXmlReader = null;
    
	EXIResult exiResult = null;
    
    byte[] buffer = null;
	
	private void initExiFactory() throws EfficientXMLException
	{

	    exiFactory = DefaultEXIFactory.newInstance();
	    exiFactory.setFidelityOptions(FidelityOptions.createStrict());
	    exiFactory.setCodingMode(CodingMode.COMPRESSION);
		
	    try {
			saxSource = new EXISource(exiFactory);
		} catch (EXIException e) {
			throw new EfficientXMLException("ExiLib: error decompressing", e);
		}
	    decodingXmlReader = saxSource.getXMLReader();
	    
	    tf = TransformerFactory.newInstance();
	    try {
			transformer = tf.newTransformer();
		} catch (TransformerConfigurationException e) 
		{throw new EfficientXMLException("ExiLib: error creating transformer", e);}
	    
	    try {
			encodingXmlReader = XMLReaderFactory.createXMLReader();
		} catch (SAXException e) 
		{throw new EfficientXMLException("ExiLib: error trying createXMLReader", e);}
	    
		try {
			exiResult = new EXIResult(exiFactory);
		} catch (EXIException e) 
		{throw new EfficientXMLException("ExiLib: error creating new EXIResult", e);}
	    
		initialized = true;
		System.out.println("EXIFactory initialized");
	}
	
	
  private byte[] extractBytes(InputStream inputStream) throws IOException {	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();				
		buffer = new byte[1024];
		int read = 0;
		while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
			baos.write(buffer, 0, read);
		}		
		buffer = null;
		baos.flush();		
		return  baos.toByteArray();
	}
	
  public InputStream decompressInputStream(InputStream inputStreamData)
		    throws EfficientXMLException
		  {
	  		if(!initialized) {initExiFactory();}
	  		
			byte[] data = null;
			try {
				data = extractBytes(inputStreamData);
			} catch (IOException e) {
				throw new EfficientXMLException("ExiLib: error extracting bytes", e);
			}
			
			InputStream is;
		    try
		    {
		      is = new ByteArrayInputStream(decodeByteArray(decodingXmlReader, data));
		    } catch (Exception e) {
		      throw new EfficientXMLException("ExiLib: error decompressing", e);
		    }
		    data = null;
		    return is;
		  }
	
  public byte[] decompressByteArray(byte[] data)
    throws EfficientXMLException
  {
	  if(!initialized) {initExiFactory();}
	  
	  byte[] b;

	  try
	  {
		  b = decodeByteArray(decodingXmlReader, data);
	  } catch (Exception e) {
		  throw new EfficientXMLException("ExiLib: error decompressing", e);
	  }

	  return b;
  }

  private byte[] decodeByteArray(XMLReader exiReader, byte[] data)
    throws SAXException, IOException, TransformerException
  {
    InputStream exiIS = new ByteArrayInputStream(data);
    SAXSource exiSource = new SAXSource(new InputSource(exiIS));
    exiSource.setXMLReader(exiReader);

    OutputStream os = new ByteArrayOutputStream();
    transformer.transform(exiSource, new StreamResult(os));
    ByteArrayOutputStream bos = (ByteArrayOutputStream)os;
    byte[] xml = bos.toByteArray();
    
    exiIS.close();
    exiSource = null;
    os.close();

    return xml;
  }

  private void encodeByteArray(ContentHandler ch, byte[] data) throws SAXException, IOException {
    
	encodingXmlReader.setContentHandler(ch);
    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    encodingXmlReader.parse(new InputSource(bais));
  }

  public byte[] compressByteArray(byte[] data) throws EfficientXMLException
  {
	  if(!initialized) {initExiFactory();}
	  byte[] c;
	  try
	  {
		  OutputStream exiOS = new ByteArrayOutputStream();
		  exiResult.setOutputStream(exiOS);
		  SAXResult s = (SAXResult)exiResult;
		  encodeByteArray(s.getHandler(), data);
		  ByteArrayOutputStream bos = (ByteArrayOutputStream)exiOS;
		  c = bos.toByteArray();
		  exiOS.close();
	
	  } catch (Exception e) {
		  throw new EfficientXMLException("ExiLib: error compressing", e);
	  }

	  return c;
  }
  
  public InputStream compressInputStream(InputStream InputStreamData) throws EfficientXMLException
  {
	  if(!initialized) {initExiFactory();}
	  InputStream is;
	  byte[] data = null;
	  try {
		  data = extractBytes(InputStreamData);
	  } catch (IOException e1) {
		  e1.printStackTrace();
	  }
	  byte[] b;
		
	  try
	  {
		  OutputStream exiOS = new ByteArrayOutputStream();

		  exiResult.setOutputStream(exiOS);
		  SAXResult s = (SAXResult)exiResult;

		  encodeByteArray(s.getHandler(), data);
		  ByteArrayOutputStream bos = (ByteArrayOutputStream)exiOS;
		  b = bos.toByteArray();
		  is = new ByteArrayInputStream(b);
		  exiOS.close();
	  } catch (Exception e) {
		  throw new EfficientXMLException("ExiLib: error compressing", e);
	  }

	  return is;
  	}
}