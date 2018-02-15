package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.Tweed;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Streamz;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.ui.Colour;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;

public class Pano {

	transient public BufferedImage pano, panoMedium;
	transient Geometry geom;
	transient private boolean dontLoadPano = false;

	public String name;

	public File orig;
	public Vector3d location = new Vector3d( -2.5, 1.2, -3.5 );
	Quaternion inverseGeomRot, geomRot;
	
	String smallFile, mediumFile;

	float rx /* pitch */, ry /* yaw */, rz /* roll */;
	public float oa1, oa2, oa3;


	public List<LinearForm3D> planes = null;

	public Pano( String name, Vector3d location, float a1, float a2, float a3 ) {

		this.name = name;
		this.location = location;
		this.orig = new File( name + ".jpg" );

		buildPlanes( new File( name + ".txt" ) );

		set( 	oa1 = a1 * FastMath.DEG_TO_RAD,
				oa2 = a2 * FastMath.DEG_TO_RAD, 
				oa3 = a3 * FastMath.DEG_TO_RAD );
	}

	public Pano( Pano o ) {
		this.name = o.name;
		this.location = new Vector3d(o.location );
		this.orig = o.orig;
		
		buildPlanes( new File( name + ".txt" ) );
		
		set( oa1 = o.oa1, oa2 = o.oa2, oa3 = o.oa3 );
		
	}

	private void buildPlanes( File file ) {

		if ( !file.exists() || planes != null )
			return;

		planes = new ArrayList<>();

		try {
			for ( String line : Files.readAllLines( file.toPath() ) ) {
				double[] params = Arrays.stream( line.split( "[,\\s]" ) ).mapToDouble( Double::parseDouble ).toArray();
				planes.add( new LinearForm3D( params[ 0 ], params[ 1 ], params[ 2 ], params[ 3 ] ) );
			}
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	public void set( float a1, float a2, float a3 ) {

		this.ry = (float) ( 3 * Math.PI - a1 ) % ( 2 * FastMath.PI ); // heading from north
		this.rx = ( a2 + FastMath.PI * 3 / 2 ) % ( 2 * FastMath.PI ); // normally about 90 (if not on a hill)
		this.rz = 2 * FastMath.PI + a3; // tilt: normally about 0 
		
		Quaternion q1 = new Quaternion(), q2 = new Quaternion(), q3 = new Quaternion();

		q2.fromAngleAxis( ry, new com.jme3.math.Vector3f( 0, 1, 0 ) );
		q3.fromAngleAxis( -rx, new com.jme3.math.Vector3f( 1, 0, 0 ) );
		q1.fromAngleAxis( rz, new com.jme3.math.Vector3f( 0, 0, 1 ) );

		geomRot = q2.mult( q3 ).mult( q1 );
		inverseGeomRot = geomRot.inverse();
	}

	void ensurePano() {

		if ( pano == null && !dontLoadPano ) {

			String name = orig.getName();

			convert( smallFile = name + "_small.png", 1024 );
			convert( mediumFile = name + "_medium.png", 2048 );

			try {
				pano = ImageIO.read( new File( Tweed.SCRATCH, smallFile ) );
				panoMedium = ImageIO.read( new File( Tweed.SCRATCH, mediumFile ) );
			} catch ( IOException e ) {
				dontLoadPano = true;
				e.printStackTrace();
			}
		}
	}

	private void convert( String f, int i ) {
		File downSampled = new File (  Tweed.SCRATCH, f );
		if ( !downSampled.exists() ) {
			try {

				System.out.println( "downscaling " + orig.getPath() + " to " + i + "x" + i );
				
				ProcessBuilder pb = new ProcessBuilder( "convert", Tweed.DATA + File.separator + "panos" + File.separator + orig.getPath(), "-resize", 
						i + "x" + i /*+"!" square images */, Tweed.SCRATCH + f  );
				
				Process p = pb.start();
				
				Streamz.inheritIO(p.getInputStream(), System.out);
				Streamz.inheritIO(p.getErrorStream(), System.err);
				
				System.out.println( "result " + p.waitFor() );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
	}

	static ThreadLocal<String> planeNameCache = new ThreadLocal<>();
	static ThreadLocal<BufferedImage> planeMaskCache = new ThreadLocal<>();
	
	public int castTo( float[] pos, BufferedImage image, Point3d worldHit, Vector3d worldNormal ) {

		com.jme3.math.Vector3f worldDir = new com.jme3.math.Vector3f( pos[ 0 ], pos[ 1 ], pos[ 2 ] );
		worldDir = worldDir.subtract( Jme3z.to(location) );

		com.jme3.math.Vector3f dir = inverseGeomRot.mult( worldDir );

		double angle = (( Math.atan2( -dir.x, dir.z ) + Math.PI ) % ( Math.PI * 2 )) / ( Math.PI * 2 );
		double elevation = (Math.atan2( -dir.y, Math.sqrt( dir.x * dir.x + dir.z * dir.z ) ) + Math.PI / 2) / Math.PI ;

		double[] rgb = new double[3];
		
		if (image != null)
		{
			double  x = angle * image.getWidth(), 
					y = elevation * image.getHeight(), 
					xF = x - Math.floor( x ), 
					yF = y - Math.floor( y );

			get( image, Math.floor( x ), Math.floor( y ), ( 1 - xF ) * ( 1 - yF ), rgb );
			get( image, Math.ceil ( x ), Math.floor( y ), xF * ( 1 - yF ), rgb );
			get( image, Math.ceil ( x ), Math.ceil ( y ), xF * yF, rgb );
			get( image, Math.floor( x ), Math.ceil ( y ), ( 1 - xF ) * yF, rgb );
		}
		
		if (worldHit != null) {
			
			worldHit.x = Double.NaN;
			
			if (planeNameCache.get() != name) {
				planeNameCache.set( name );
				planeMaskCache.set (getPlanePano() );
			}
			
			if ( planeMaskCache.get() != null ) {
				
				double  x = Mathz.clamp( (1-angle) * planeMaskCache.get().getWidth(), 0, planeMaskCache.get().getWidth()-1 ),
						y = elevation * planeMaskCache.get().getHeight();
				
				Color c = new Color ( planeMaskCache.get().getRGB( (int) x, (int) y ) );
				
				int planeNo = (c.getRed() + c.getGreen() + c.getBlue() ) / 3;
				
				if (planeNo < planes.size() && planeNo != 0) {
					
					LinearForm3D plane = new LinearForm3D ( planes.get( planeNo ) );
					
					{
						double tmp = plane.B;
						plane.B = plane.C;
						plane.C = tmp;
						plane.D = -plane.D;
						
						plane.A = -plane.A;
						
					}
					
					Point3d pt = plane.collide( new Point3d(), Jme3z.from( dir ) );
					
					{
						
						com.jme3.math.Vector3f ptm = Jme3z.to( pt );
						
						worldHit.set( Jme3z.from ( geomRot.mult( ptm ) ) );
						worldHit.add( location );

						worldNormal.set( Jme3z.from ( geomRot.mult( Jme3z.to(plane.normal()) ) ) );
					}
				}
			}
			 
			
		}
		
		return Colour.asInt( (int) rgb[ 0 ], (int) rgb[ 1 ], (int) rgb[ 2 ] );
	}

	private void get( BufferedImage image, double x, double y, double weight, double[] result ) {

		int ix = Mathz.clamp( (int) x, 0, image.getWidth() - 1 ), iy = Mathz.clamp( (int) y, 0, image.getHeight() - 1 );

		Color ac = new Color( image.getRGB( ix, iy ) );

		result[ 0 ] += ac.getRed() * weight;
		result[ 1 ] += ac.getGreen() * weight;
		result[ 2 ] += ac.getBlue() * weight;
	}
	
	public BufferedImage getRenderPano() {
		try {
			
			File toRead = orig;
			
			if (!toRead.isAbsolute())
				toRead = Tweed.toWorkspace( new File ( "panos" , toRead.getName() ) ) ; 
			
			System.out.println( "reading big " + orig.getName() );
			BufferedImage out = ImageIO.read( toRead );
			System.out.println( "done" );
			return out;
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		return null;
	}

	public BufferedImage getSmallPano() {
		ensurePano();
		return pano;
	}
	
	public BufferedImage getPlanePano() {
		try {
			System.out.println( "reading plane " + orig.getName() );
			BufferedImage out = ImageIO.read( new File(name+".png") );
			System.out.println( "done" );
			return out;
		} catch ( IOException e ) {
//			e.printStackTrace();
		}
		return null;
	}
}