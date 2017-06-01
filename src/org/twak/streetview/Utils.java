package org.twak.streetview;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class Utils {
	
	public static void main (String[] args) throws Throwable { // dumps downloaded panoramas as lat/long for qgis
		
//		joinPanoramas();
//		fileNamesToCSV();
	}
	
	private static void fileNamesToCSV() throws IOException {
		BufferedWriter out = new BufferedWriter( new FileWriter( new File("/home/twak/Desktop/tmp.csv") ) );
		
		for (File f : new File ("/home/twak/Downloads").listFiles()) {
			
			try {
			String[] vals = f.getName().split( "_" );
			
			out.write( vals[0] +","+vals[1]+"," + f.getName() +"\n");
			}
			catch (Throwable th ) {
				System.err.println(f);
				th.printStackTrace();
			}
		}
		
		out.close();
	}
}
