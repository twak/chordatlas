package org.twak.viewTrace.facades;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.twak.tweed.gen.FeatureCache;

public class FixPython {

	
	public static void main (String[] args) throws Throwable {

		FileUtils.iterateFiles( new File ("/home/twak/data/regent/May_4/cock"), new IOFileFilter() {

			@Override
			public boolean accept( File arg0 ) {
				if ( arg0.getName().equals( FeatureCache.PARAMETERS_YML) ) {
					try {
						doit( arg0 );
					} catch ( Throwable e ) {
						e.printStackTrace();
					}
				}
				return true;
			}

			@Override
			public boolean accept( File arg0, String arg1 ) {
				return accept (new File (arg0, arg1));
			}
			
		}, TrueFileFilter.TRUE );
	}
	
	public static void doit(File inputFile) throws Throwable {
		
		File tempFile = new File( inputFile.getParentFile().getAbsolutePath(), inputFile.getName()+"_tmp");

		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

		String currentLine;

//		boolean ignore = false;
		
		while((currentLine = reader.readLine()) != null) {
		    // trim newline when comparing with lineToRemove
		    String trimmedLine = currentLine.trim();
//		    if( trimmedLine.contains( "win_h_margin" ) )
//		    	ignore = true;
//		    		
//		    if (trimmedLine.contains( "- door_line") || trimmedLine.contains( "homography" ))
//		    	ignore = false;
//		    
//		    if (ignore)
//		    	continue;
		    
		    currentLine = currentLine.replaceAll( "facade_right", "facade-right" );
		    currentLine = currentLine.replaceAll( "door_line", "door-line" );
		    currentLine = currentLine.replaceAll( "sky_line", "sky-line" );
		    
		    writer.write(currentLine + System.getProperty("line.separator"));
		}
		writer.close(); 
		reader.close(); 
		boolean successful = tempFile.renameTo(inputFile);
	}
}
