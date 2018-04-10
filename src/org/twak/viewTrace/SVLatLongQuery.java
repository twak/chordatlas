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
import org.twak.tweed.TweedFrame;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.Pano;
import org.twak.tweed.gen.PanoGen;

public class SVLatLongQuery {
	
	double[] ll;
	
	public Pano query() {

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
				
//				meta = meta.trim().replaceAll( "\\s+", "_" );
				
				File panos = new File(Tweed.DATA+File.separator + "panos") ;
				new Mosaic( Collections.singletonList( name ), panos );
				
				return PanoGen.createPanoGen( new File(panos, name+".jpg") , "EPSG:4326" );
				
			}
			
//			System.out.println( ">>>> "+ll[0]+", "+ll[1] );
			
			//		Log.d("Response",s.hasNext() ? s.next() : "");
			//		https://maps.googleapis.com/maps/api/js/GeoPhotoService.SingleImageSearch?pb=!1m5!1sapiv3!5sUS!11m2!1m1!1b0!2m4!1m2!3d"+origin.lat+"!4d"+origin.lng+"!2d50!3m10!2m2!1sen!2sGB!9m1!1e2!11m4!1m3!1e2!2b1!3e2!4m10!1e1!1e2!1e3!1e4!1e8!1e6!5m1!1e2!6m1!1e2&callback=_xdc_._v2mub5
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
