package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.utils.Line;
import org.twak.utils.MUtils;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public class PlaneGen extends Gen implements IDumpObjs {

	public Vector3f a, b;
	float heightMax, heightMin;
	Collection<Pano> nearby = new ArrayList();
	public Vector3f u01, v01;

	transient List<Window> windows = new ArrayList();

	private class Window {
		float x, y, w, h; 
		
		public Window( double t, double l, double b, double r ) {
			this.x = (float) l;
			this.y = (float) b;
			this.w = (float) (r-l);
			this.h = (float) (b-t);
		}

	}

	public PlaneGen( Tweed tweed, float x1, float y1, float x2, float y2, float heightMin, float heightMax, Collection<Pano> nearby ) {
		super( "plane gen", tweed );
		
		this.a = new Vector3f( x1, heightMin, y1 ); // a is the coord-system origin
		this.b = new Vector3f( x2, heightMin, y2 );
		this.heightMax = heightMax;
		this.heightMin = heightMin;
		this.nearby = nearby;

		calcUV();
	}
	
	public PlaneGen( PlaneGen planeGen ) {
		super( planeGen.name, planeGen.tweed );
		
		this.a = new Vector3f(planeGen.a);
		this.b = new Vector3f(planeGen.b);
		this.heightMax = planeGen.heightMax;
		this.heightMin = planeGen.heightMin;
		this.nearby = new ArrayList<>(planeGen.nearby );
		
		calcUV();
	}

	private void calcUV() {
		u01 = new Vector3f( b.x - a.x, 0, b.z - a.z );
		v01 = new Vector3f( 0, heightMax-heightMin, 0 );
	}

	@Override
	public void calculate() {

		for ( Spatial s : gNode.getChildren() )
			s.removeFromParent();

		Mesh mesh1 = new Mesh();
		{
			Vector3f[] vertices = new Vector3f[4];
			vertices[ 0 ] = new Vector3f( a.x, heightMin, a.z );
			vertices[ 1 ] = new Vector3f( a.x, heightMax, a.z );
			vertices[ 2 ] = new Vector3f( b.x, heightMin, b.z );
			vertices[ 3 ] = new Vector3f( b.x, heightMax, b.z );

			Vector3f[] texCoord = new Vector3f[4];
			texCoord[ 0 ] = new Vector3f( 0, 0, 0 );
			texCoord[ 1 ] = new Vector3f( 1, 0, 0 );
			texCoord[ 2 ] = new Vector3f( 0, 1, 0 );
			texCoord[ 3 ] = new Vector3f( 1, 1, 0 );

			int[] indexes = { 0, 1, 3, 3, 2, 0, 3, 1, 0, 0, 2, 3 };

			mesh1.setBuffer( VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer( vertices ) );

			mesh1.setBuffer( VertexBuffer.Type.TexCoord, 3, BufferUtils.createFloatBuffer( texCoord ) );
			mesh1.setBuffer( VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer( indexes ) );
			mesh1.updateBound();
		}
		Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
		mat.getAdditionalRenderState().setBlendMode( BlendMode.Alpha );
		mat.setColor( "Color", new ColorRGBA( color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 0.5f ) );

		//		Material mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");

		Geometry spat = new Geometry( "Box", mesh1 );
		spat.setMaterial( mat );

		gNode.attachChild( spat );

		{
			Vector3f dir = new Vector3f( b );
			dir.subtractLocal( a );
			dir.normalizeLocal();
			Vector3f norm = new Vector3f( -dir.z, dir.y, dir.x );
			Vector3f up = new Vector3f(0,1,0);
			
			Material mat2 = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
			mat2.getAdditionalRenderState().setBlendMode( BlendMode.Alpha );
			mat2.setColor( "Color", new ColorRGBA( 1f, 0.3f, 0.3f, 1f ) );

			
			int[] indexes = {
					0,3,1, // back
					3,2,1,
					
					4,5,7, // front
					7,5,6,

					0,1,4, // left
					4,1,5,
					
					3,6,2, // right
					6,3,7,

					1,2,5, // bottom
					5,2,6,
					
					0,4,3, // top
					4,7,3,
					
			};
		
			for ( Window w : windows ) {

				Vector3f corner = dir.mult( w.x ).add( a );
				
				corner.addLocal( 0, w.y, 0 );

				Vector3f[] vertices = new Vector3f[8];
				vertices[ 0 ] = corner;
				vertices[ 1 ] = corner.add( up.mult( w.h ) );
				vertices[ 2 ] = vertices[ 1 ].add( dir.mult( w.w ) );
				vertices[ 3 ] = corner.add( dir.mult( w.w ) );

				Vector3f normD = new Vector3f(norm).mult( 3 );
				vertices[ 4 ] = vertices[0].add( normD );
				vertices[ 5 ] = vertices[1].add( normD );
				vertices[ 6 ] = vertices[2].add( normD );
				vertices[ 7 ] = vertices[3].add( normD );
				
				Mesh mesh = new Mesh();
				
				mesh.setBuffer( VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer( vertices ) );
				mesh.setBuffer( VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer( indexes ) );
				mesh.updateBound();
				
				Geometry window = new Geometry( "Window", mesh );
				window.setMaterial( mat2 );
				gNode.attachChild( window );
			}
		}

		super.calculate();
	}
	
	private float[] uvToWorld( float u, float v ) {

		return new float[] { u01.x * u + a.x, v01.y * v + a.y, u01.z * u + a.z };
	}

	public BufferedImage renderDepth( File fout, float scale, Pano pano, Spatial scene, double minDepth, double maxDepth ) {
		
		BufferedImage out = new BufferedImage( (int) ( a.distance( b ) * scale ), (int) ( ( heightMax- heightMin) * scale ), BufferedImage.TYPE_3BYTE_BGR );
		
		Vector3f o = Jme3z.to ( pano.location );
		
		for ( int x = 0; x < out.getWidth(); x++ ) {
			for ( int y = 0; y < out.getHeight(); y++ ) {

				CollisionResults results = new CollisionResults();

				float[] target = uvToWorld( x / (float) out.getWidth(), 1 - ( y / (float) out.getHeight() ) );
				Vector3f ray = new Vector3f (target[0], target[1], target[2]);
				ray.subtractLocal( o );
				
				scene.collideWith(new Ray( o, ray), results);
				
				CollisionResult cr = results.getClosestCollision();
				double dist = maxDepth;
				
				if (cr != null) 
					dist = Math.min (maxDepth, cr.getDistance() );
				
				dist = MUtils.clamp ( ( dist - minDepth ) / ( maxDepth - minDepth ), 0, 1);
				
				int d = (int)(dist * 256);
				double r = dist*256 - d;
				
				Color c = new Color( 
						MUtils.clamp ( d + (r > 0.25 ? 1 : 0), 0, 255),
						MUtils.clamp ( d + (r > 0.50 ? 1 : 0), 0, 255),
						MUtils.clamp ( d + (r > 0.75 ? 1 : 0), 0, 255) );
				
				out.setRGB( x, y, c.getRGB() );
			}
			System.out.println( x + " / " + out.getWidth() );
		}
		
		if ( fout != null )
			try {
				ImageIO.write( out, "png", fout );
			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		return out;
	}
	
	public PlaneGen rotateByAngle(double deltaAngle, double distance) {
		
		Line  nLine = new Line ( new Point2d(a.x, a.z), new Point2d(b.x, b.z ) );
		Point2d cen = nLine.fromPPram( 0.5 );
		
 		double angle = nLine.aTan2() + deltaAngle; 
		double len = nLine.length() / 2;
		
		Vector2d dir = new Vector2d( 
				-Math.cos(angle) * len, 
				-Math.sin(angle) * len );
		
		Point2d start = new Point2d(cen), 
				end = new Point2d(cen);
		
		start.add(dir);
		end.sub(dir);
		
		PlaneGen out = new PlaneGen(this);
		
		out.a.set( (float) start.x, a.y, (float) start.y);
		out.b.set( (float) end  .x, b.y, (float) end  .y);
		
		out.calcUV();
		
		return out;
	}
	
	public void  fudgeToDepth( float scale, Pano pano, List<Double> deltas ) {
		
		Point3d worldPos = new Point3d();
		Vector3d worldNormal = new Vector3d(), planeNormal = new Vector3d( Jme3z.from(b) );
		planeNormal.sub( Jme3z.from ( a  ) );
		planeNormal.normalize();
		planeNormal.set(planeNormal.z, 0, -planeNormal.x);
		
		int width = (int)(a.distance( b ) * scale);
		
//		List<Double> deltas = new ArrayList();
			
		for ( int x = width / 3; x < 2 *width / 3; x++ ) {
				
				float[] planeWorld = uvToWorld( x / (float) width, 0.2f );
				Point3d requested = new Point3d(planeWorld[0], planeWorld[1], planeWorld[2]);
				
				pano.castTo(
								planeWorld,
								null ,
								worldPos, 
								worldNormal );
				
				if (! Double.isNaN( worldPos.x ) &&
					  requested.distanceSquared( worldPos ) < 30 ) { 
					
					double delta = MUtils.signedAngle( 
							new Vector2d ( worldNormal.x, worldNormal.z ), 
							new Vector2d ( planeNormal.x, planeNormal.z ) ) ;
					
					if (Math.abs (delta) < 0.6)
						deltas.add( delta );
				}
		}
			
//		PlaneGen out = this;
//		
//		if ( deltas.size() > 20 ) {
//			Collections.sort( deltas );
//
//			double delta = deltas.get( deltas.size() / 3 );
//
//			out = rotateByAngle( delta, 0 );
//				
//			System.out.println(" delta is " + delta );
//		}
//			
//		calculateOnJmeThread();
//		
//		return out;
	}
	
	public BufferedImage render( File folder, float scale, Pano pano, Line mega, String filename ) {

		Point3d worldPos = new Point3d();
		Vector3d worldNormal = new Vector3d(), planeNormal = new Vector3d( Jme3z.from(b) );
		planeNormal.sub( Jme3z.from ( a  ) );
		planeNormal.normalize();
		planeNormal.set(planeNormal.z, 0, -planeNormal.x);
		
		BufferedImage out = new BufferedImage( (int) ( a.distance( b ) * scale ), (int) ( ( heightMax- heightMin) * scale ), BufferedImage.TYPE_3BYTE_BGR );
		BufferedImage outClip = new BufferedImage( out.getWidth(), out.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
		

		BufferedImage source = pano.getRenderPano();
		
//		List<float[]> cubes = new ArrayList();
		
		for ( int x = 0; x < out.getWidth(); x++ ) {
			for ( int y = 0; y < out.getHeight(); y++ ) {

				float[] planeWorld = uvToWorld( x / (float) out.getWidth(), 1 - ( y / (float) out.getHeight() ) );
				
				Color c = new Color( 
						pano.castTo( 
								planeWorld, 
								source , 
								worldPos, 
								worldNormal ) );
				
				out.setRGB( x, y, c.getRGB() );
				
				Point3d requested = new Point3d(planeWorld[0], planeWorld[1], planeWorld[2]);
				
				Color d = Color.white;
				
				if ( 
						Double.isNaN( worldPos.x) || 
//						requested.distanceSquared( worldPos ) > 30 ||
//						planeNormal.angle(worldNormal) > 0.5 || 
						(mega != null && mega.distance( new Point2d(worldPos.x, worldPos.z), true )  > 5 ) 
					)
						d = Color.black;
				
				outClip.setRGB( x, y, d.getRGB() );
				
//				if (! Double.isNaN( worldPos.x) )
//					if ( x % 20 == 0 && y % 20 == 0 )
//						cubes.add( new float[] { (float) worldPos.x, (float) worldPos.y, (float) worldPos.z } );
				
			}
			System.out.println( x + " / " + out.getWidth() );
		}
		System.out.println( "render complete" );
		
//		addCubes( cubes );

		if ( folder != null )
			try {
				ImageIO.write( out, "png", new File ( folder, filename+".png" ) );
				ImageIO.write( outClip, "png", new File ( folder, "mask.png" ) );
			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		return out;
	}
	
//	static Node cubeDebug;
//	private void addCubes (List<float[]> cubes) {
//		tweed.enqueue( new Runnable() {
//			public void run() {
//				
//				if (cubeDebug != null) {
//					cubeDebug.removeFromParent();
//				}
//				cubeDebug = new Node();
//				
//				Material mat1 = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
//				mat1.setColor( "Color", ColorRGBA.Blue );
//				
//				for (float[] a : cubes) {
//				
//					Box box1 = new Box( 0.1f, 0.1f, 0.1f );
//					Geometry blue = new Geometry( "Box", box1 );
//					blue.setMaterial( mat1 );
//					blue.setLocalTranslation( a[ 0 ], a[ 1 ], a[ 2 ] );
//					blue.updateGeometricState();
//					cubeDebug.attachChild( blue );
//				}
//				
//				tweed.getRootNode().attachChild( cubeDebug );
//			}
//		} );
//	}
	

	static File lastLocation; 
	
	@Override
	public JComponent getUI() {
		JPanel out = new JPanel( new ListDownLayout() );

		JButton render = new JButton( "render" );

		render.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {

				int c = 0;
				for ( Pano pg : nearby )
					render( new File( Tweed.CONFIG + "facades/0/" + ( c++ ) ), 40f, pg, null, "orthographic" );
			}

		} );

		out.add( render );

		JButton load = new JButton( "load" );

		load.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				JFileChooser fileChooser = new JFileChooser(lastLocation == null ? 
						new File ( (System.getProperty( "user.home" )+"/Desktop") ) : lastLocation );
				
				fileChooser.setFileFilter( new FileFilter() {

					@Override
					public String getDescription() {
						return "csv";
					}

					@Override
					public boolean accept( File f ) {
						return f.isDirectory() || f.getName().toLowerCase().endsWith( ".csv" );
					}
				} );

				if ( fileChooser.showOpenDialog( out ) == JFileChooser.APPROVE_OPTION ) {

					
					windows.clear();
					File csv = fileChooser.getSelectedFile();
					lastLocation = csv.getParentFile();

					try ( Stream<String> stream = Files.lines( csv.toPath() ) ) {

						stream.forEach( new Consumer<String>() {
							@Override
							public void accept( String line ) {

								if (line.startsWith( "top" ))
									return;
								
								double ppm = 40;
								
								String[] vals = line.split( "," );
								try{
									windows.add (new Window(
											30 - Double.parseDouble( vals[0] ) / ppm,
											Double.parseDouble( vals[1] ) / ppm,
											30 - Double.parseDouble( vals[2] ) / ppm,
											Double.parseDouble( vals[3] ) / ppm
										));
								}
								catch (Throwable th) {
									System.err.println("while parsing " + line +" :");
									th.printStackTrace();
								}

							}
						} );
						
						
					} catch ( Throwable th ) {
						th.printStackTrace();
					}
				}
				calculateOnJmeThread();
			}
		});

		out.add( load );

		return out;
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		
		dump.setCurrentMaterial( Color.orange, 0.5);
		
		Jme3z.dump( dump, gNode, 0 );
		
//		int c = 0;
//		for (Spatial s : gNode.getChildren() ) {
//			if (c++ == 0)
//				continue; // skip plane itself
//			
//			if (s instanceof Geometry) {
//				Mesh m = ((Geometry)s).getMesh();
//				Jme3z.toObj (m, dump, ((Geometry)s).getLocalTransform());
//			}
//		}
		
	}
	
	@Override
	public String toString() {
		return a.x+" " + a.z + " " + b.x +" " + b.z + " " + heightMin + " " + heightMax;
	}

}
