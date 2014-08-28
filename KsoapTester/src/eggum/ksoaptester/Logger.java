package eggum.ksoaptester;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Environment;

public class Logger {
	
	private String name;
	private File logFile;
	private String logFileName;
	private FileWriter writer;
	
	private int connectionErrors= 0;
	
	private long marshallTime = 0;
	private long marshallCounter = 0;
	
	private long roundtripTime = 0;
	private long roundtripCounter = 0;
	
	private long decompressingTime = 0;
	private long decompressingCounter = 0;
	
	private long parseTime = 0;
	private long parseCounter = 0;
	
	private long unMarshallTime = 0;
	private long unMarshallCounter = 0;
	
	private long totalTime = 0;
	private long totalCounter = 0;
	
	private long meanMarshallTime = 0;
	private long meanRoundtripTime = 0;
	private long meanDecompressingTime = 0;
	private long meanParseTime = 0;
	private long meanUnmarshallTime = 0;
	private long meanTotalTime = 0;
	
	private String startTime;
	private String stopTime;
	
	public Logger(String n)
	{
		name = n;
		startTime = new SimpleDateFormat("dd-HH.mm.ss").format(new Date());
		
		File root = new File(Environment.getExternalStorageDirectory(), "Logfiles Testing");
        if (!root.exists()) {
            root.mkdirs();
        }
        logFileName = name + "-" + startTime + ".txt";
        logFile = new File(root, logFileName);
        
        try {
			writer = new FileWriter(logFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
        System.out.println("Logfile created: " + logFileName);
	}
	
	public void logComment(String s)
	{
		try {
			writer.append("Comment: " + new SimpleDateFormat("HH.mm.ss").format(new Date()) + " " + s);
			writer.append("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void logError(String s)
	{
		connectionErrors++;
		try {
			writer.append("ERROR: " + s);
			writer.append("\n");
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	
	public void closeLogFile(String service, String compression, int startBatteryPct, int stopBatteryPct, int faultyUploads)
	{
		stopTime = new SimpleDateFormat("dd-HH.mm.ss").format(new Date());
		if(totalCounter>0)
		{			
			meanMarshallTime = (marshallTime/marshallCounter)/1000000;
			meanRoundtripTime = (roundtripTime/roundtripCounter)/1000000;
			meanDecompressingTime = (decompressingTime/decompressingCounter)/1000000;
			meanParseTime = (parseTime/parseCounter)/1000000;
			meanUnmarshallTime = (unMarshallTime/unMarshallCounter)/1000000;
			
			meanTotalTime = (totalTime/totalCounter)/1000000;
		
			try
			{	
				writer.append(name);
				writer.append("\n");
				writer.append("Service "); writer.append("\t");
				writer.append(service); writer.append("\n");
				writer.append("Compression "); writer.append("\t");
				writer.append(compression); writer.append("\n"); writer.append("\n");
				
				writer.append("Start Time "); writer.append("\t");
				writer.append(startTime); writer.append("\n");
				writer.append("Stop Time "); writer.append("\t");
				writer.append(stopTime); writer.append("\n"); writer.append("\n");
				
				writer.append("Start Battery "); writer.append("\t");
				writer.append(startBatteryPct +"%"); writer.append("\n");
				writer.append("Stop Battery "); writer.append("\t");
				writer.append(stopBatteryPct +"%"); writer.append("\n\n"); 

				writer.append("Mean Marshall Time "); writer.append("\t"); 
	        	writer.append(String.valueOf(meanMarshallTime)); writer.append(" milliseconds\n");
				writer.append("Mean Roundtrip Time "); writer.append("\t");
	        	writer.append(String.valueOf(meanRoundtripTime)); writer.append(" milliseconds\n");
				writer.append("Mean Unmarshall Time "); writer.append("\t"); 
	        	writer.append(String.valueOf(meanUnmarshallTime)); writer.append(" milliseconds\n");
	        	
	        	writer.append("Mean Total Time "); writer.append("\t");
	        	writer.append(String.valueOf(meanTotalTime)); writer.append(" milliseconds\n\n");
	        	
				writer.append("Mean Decompressing Time "); writer.append("\t");
	        	writer.append(String.valueOf(meanDecompressingTime)); writer.append(" milliseconds\n");
				writer.append("Mean Parse Time "); writer.append("\t");
	        	writer.append(String.valueOf(meanParseTime)); writer.append(" milliseconds\n\n");
	        	
	        	writer.append("Number of Rounds "); writer.append("\t");
	        	writer.append(String.valueOf(totalCounter)); writer.append("\n");
	        	writer.append("Faulty Uploads "); writer.append("\t");
	        	writer.append(String.valueOf(faultyUploads)); writer.append("\n");
	        	writer.append("Connection Errors: "); writer.append("\t");
	        	writer.append(String.valueOf(connectionErrors)); writer.append("\n");
	        
	        	writer.flush();
	        	writer.close();
			}
			catch(IOException e) {e.printStackTrace();}
		}
		else
		{
			try
			{
				writer.append("No tests were run");
	        	writer.flush();
	        	writer.close();
			}
			catch(IOException e) {e.printStackTrace();}
		}
	}
	
	public void logMarshallTime(long value){
		marshallTime = marshallTime + value;
		marshallCounter++;
	}
	
	public void logRoundtripTime(long value){
		roundtripTime = roundtripTime + value;
		roundtripCounter++;
	}
	
	public void logDecompressingTime(long value){
		decompressingTime = decompressingTime + value;
		decompressingCounter++;
	}
	
	public void logParseTime(long value){
		parseTime = parseTime + value;
		parseCounter++;
	}
	
	public void logUnMarshallTime(long value){
		unMarshallTime = unMarshallTime + value;
		unMarshallCounter++;
	}

	public void logTotalTime(long value){
		totalTime = totalTime + value;
		totalCounter++;
	}
	
	public void logCompressionType(String compressionType)
	{
	    try
	    {
		writer.append("\n");
        writer.append("Compression Type"); writer.append("\t");
        writer.append(compressionType); writer.append("\n");
	    }
	    catch(IOException e) {e.printStackTrace();}
	}
}
