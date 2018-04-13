package org.twak.viewTrace;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.twak.readTrace.Mosaic;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Pano;
import org.twak.tweed.gen.PanoGen;

public class SVLatLongQuery {
	
	double[] ll;
	
	public interface Score {
		public double score (Pano p);
	}
	
	public Pano query(Score score) {

		URL url;
		BufferedReader in = null;
		try {
			url = new URL( "https://maps.googleapis.com/maps/api/js/GeoPhotoService.SingleImageSearch?pb=" + 
					"!1m5!1sapiv3!5sUS!11m2!1m1!1b0!2m4!1m2!3d" + ll[0] 
							+ "!4d" + ll[1] + "!2d50!3m10!2m2!1sen!2sGB!9m1!1e2!11m4!1m3!1e2!2b1!3e2!4m10!1e1!1e2!1e3!1e4!1e8!1e6!5m1!1e2!6m1!1e2&callback=_xdc_._v2mub5" );
			
			URLConnection urlConnection = url.openConnection();
			urlConnection.setDoInput( true );
			//		urlConnection.setRequestProperty("cookies",cookies);
			urlConnection.connect();
			in = new BufferedReader( new InputStreamReader( urlConnection.getInputStream() ) );
			String inputLine;

			StringBuffer sb = new StringBuffer();
			
			while ( ( inputLine = in.readLine() ) != null )
				sb.append( inputLine );
//				for (String s : inputLine.split( "," ))
//					System.out.println( s+"," );
			
			Pattern pat = Pattern.compile( "(\\[\\[2,\\\".*?\\]\\])" );
			
			Matcher m = pat.matcher( sb.toString() );
			
			File panoLoc = new File(Tweed.DATA+File.separator + "panos") ;
			double bestScore = -Double.MAX_VALUE;
			Pano bestPano = null;
			
			while (m.find()) {
				
				String meta = m.group( 0 );
				meta = meta.replaceAll( "\\[\\[2,", " ");
				meta = meta.replaceAll( "null,", " " );
				meta = meta.replaceAll( "[\\\"\\[\\],]" , " ");
				
				
				String[] parts = meta.trim().split( "\\s+" );
				
				String name = "";
				for (int i = 1; i < parts.length; i++) {
					name += parts[i]+"_";
				}
				name += parts[0];
				
				if (parts[0].length() > 25) {
					System.out.println( "something strange here" );
					continue;
				}
				
//				meta = meta.trim().replaceAll( "\\s+", "_" );
				
				
				Pano pano = PanoGen.createPanoGen( new File(panoLoc, name+".jpg") , "EPSG:4326" );
				double s = score.score( pano );
				if (s > bestScore) {
					bestScore = s;
					bestPano = pano;
				}
			}
			
			if (bestPano == null)
				return null;
			
//			new Mosaic( Collections.singletonList( bestPano.name ), panoLoc );
			
			if ( !new File(panoLoc, bestPano.name +".jpg").exists() )
				return null;
			
			return bestPano;
			
		} catch ( Throwable th ) {
			th.printStackTrace();
		}
		finally {
			if (in != null)
				try {
					in.close();
				} catch ( IOException e ) {
					e.printStackTrace();
				}
		}
		return null;
	}

	public SVLatLongQuery( double[] worldToLatLong ) {
		this.ll = worldToLatLong;
	}
}
