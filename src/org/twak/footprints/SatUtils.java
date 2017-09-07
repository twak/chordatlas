package org.twak.footprints;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.twak.tweed.Tweed;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;

public class SatUtils {

	
	static MathTransform toLatLong;
	
	static {
		try {
			toLatLong = CRS.findMathTransform( DefaultGeocentricCRS.CARTESIAN, CRS.decode( "EPSG:4326" ), true );
		} catch ( FactoryException e ) {
			e.printStackTrace();
		}
	}

	public static void render( Tweed tweed, LoopL<Point3d> loopL ) {

		double[] range = Loopz.minMax( loopL );
		
		double jump = 30;
		int xMin = -1, xMax = 1, yMin = -1, yMax = 1;
		
		BufferedImage out = new BufferedImage( 640 * ( xMax-xMin+1), 640 * (yMax - yMin + 1), BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = out.createGraphics();
		
		int cenX = out.getWidth()/2, cenY = out.getHeight()/2;
		
		int removeBottom = 30;
		
		try {
			
			for (int x = xMin; x <= xMax; x++)
				for (int y = yMax; y >= yMin; y--) 
				{
			
				Point3d cen = new Point3d( 
						( range[ 0 ] + range[ 1 ] ) * 0.5 + x*jump, 
						0, 
						( range[ 4 ] + range[ 5 ] ) * 0.5 + y*jump );

				Point2d ll = worldToLLong( tweed, cen );

				System.out.println( "in EPSG:27700 " + cen );
				System.out.println( "lat long " + ll.x + " " + ll.y );

				URL url = new URL( "https://maps.googleapis.com/maps/api/staticmap?center=" + 
						ll.x + "," + ll.y + 
						"&zoom=20&size=640x640&maptype=satellite&format=png32&key=go_get_your_own" );

				URLConnection connection = url.openConnection();
				InputStream is = connection.getInputStream();

				BufferedImage image = ImageIO.read( is );

				g.drawImage( image, 
						cenX - image.getWidth() /2 - x * 323 + y * 8,
						cenY - image.getHeight()/2 - x * 8   - y * 323, 
						cenX + image.getWidth() /2 - x * 323 + y * 8,
						cenY + image.getHeight()/2 - x * 8   - y * 323 - removeBottom,
						
						0,0,image.getWidth(), image.getHeight() - removeBottom,
						
						 null );
				
				
				
			}

			g.dispose();
			ImageIO.write( out, "png", new File( Tweed.SCRATCH + "ssat.png" ) );
			
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
	}

	public static Point2d worldToLLong( Tweed tweed, Point3d cen ) {

		Point3d out = new Point3d( cen );
		tweed.fromOrigin.transform( out );

		try {
			double[] latLong = new double[3];

			toLatLong.transform( new double[] { out.x, out.y, out.z }, 0, latLong, 0, 1 );

			return new Point2d( latLong[ 0 ], latLong[ 1 ] );
		} catch ( TransformException e ) {
			e.printStackTrace();
		}
		return null;
	}

}
