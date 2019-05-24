package org.twak.readTrace;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.vecmath.Matrix4d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjDump.Face;
import org.twak.utils.geom.ObjDump.Material;
import org.twak.utils.streams.InaxPoint3dCollector;
import org.twak.utils.ui.auto.Auto;

import com.jme3.math.Matrix4f;
import com.thoughtworks.xstream.XStream;

public class MiniTransform {

	public final static String OBJ = "model.obj", INDEX = "index.xml";
	public Map<Integer, Matrix4d> index = new HashMap(); // folder name to offset
	public Matrix4f offset = new Matrix4f(); // additional offset applied to all via ui

	public enum Orientation {
		
		X_UP (new Matrix4d(
				0, 0, 1, 0,
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 0, 1
				)),
		
		Y_UP (new Matrix4d(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				0, 0, 0, 1
				)), 
		
		Z_UP (new Matrix4d(
				0, 1, 0, 0,
				0, 0, 1, 0,
				1, 0, 0, 0,
				0, 0, 0, 1
				));
		
		Matrix4d m;
		
		Orientation(Matrix4d m) {
			this.m = m;
		}
	}
	
	public static class MiniOptions {
		
		public Orientation orientation = Orientation.Y_UP;
		public boolean importAllInDirectory = true;
		protected File output;
		
		public MiniOptions( File output ) {
			this.output = output;
		}
	}
	
	public static void convertToMini( File oneOfManyObj, File outputDir, Runnable onDone ) {
		
		MiniOptions options =  new MiniOptions(outputDir);
				
		new Auto( options, false ) {
			public void apply() {
				
				super.apply();
				
				List<File> files = new ArrayList();

				if ( options.importAllInDirectory ) {
					for ( File f : oneOfManyObj.getParentFile().listFiles() )
						if ( f.getName().toLowerCase().endsWith( ".obj" ) )
							files.add( f );
				} else {
					files.add( oneOfManyObj );
				}
				
				convertToMini( files, options.output, options.orientation.m );
				
				onDone.run();
			}
			
			public void updateOkayCancel() {
				okay.setEnabled( true );
			}
			
		}.frame();
	}

	public static void convertToMini( Iterable<File> bigObj, File outfile, Matrix4d transform ) {

		outfile.mkdirs();

		ObjDump src = new ObjDump( bigObj );//.iterator().next() );
		
		src.centerVerts();
		src.transform (transform);
		
		long count = src.material2Face.entrySet().stream().mapToInt( x -> x.getValue().size() ).sum();
		double[] bounds = src.orderVert.stream().collect( new InaxPoint3dCollector() );
		long targetCount = 5000;

		double volume = ( bounds[ 1 ] - bounds[ 0 ] ) * ( bounds[ 3 ] - bounds[ 2 ] ) * ( bounds[ 5 ] - bounds[ 4 ] );

		double edgeLength = Math.pow( volume / ( (double) count / targetCount ), 0.3333 );

		int 
				xc = (int) Math.ceil( ( bounds[ 1 ] - bounds[ 0 ] ) / edgeLength ), 
				yc = (int) Math.ceil( ( bounds[ 3 ] - bounds[ 2 ] ) / edgeLength ), 
				zc = (int) Math.ceil( ( bounds[ 5 ] - bounds[ 4 ] ) / edgeLength );

		Set<Face>[][][] faces = new Set[xc][yc][zc];

		for ( Entry<Material, List<Face>> e : src.material2Face.entrySet() )
			for ( Face f : e.getValue() ) {

				Tuple3d vt = src.orderVert.get( f.vtIndexes.get( 0 ) );

				int ix = (int) ( ( vt.x - bounds[ 0 ] ) / edgeLength );
				int iy = (int) ( ( vt.y - bounds[ 2 ] ) / edgeLength );
				int iz = (int) ( ( vt.z - bounds[ 4 ] ) / edgeLength );

				if ( faces[ ix ][ iy ][ iz ] == null )
					faces[ ix ][ iy ][ iz ] = new HashSet();

				faces[ ix ][ iy ][ iz ].add( f );
			}

		int dir = 0;
		MiniTransform mt = new MiniTransform();

		for ( int x = 0; x < xc; x++ )
			for ( int y = 0; y < yc; y++ )
				for ( int z = 0; z < zc; z++ ) {

					Set<Face> miniF = faces[ x ][ y ][ z ];
					if ( miniF == null )
						continue;

					Matrix4d trans = new Matrix4d();
					trans.setIdentity();
					trans.setScale( edgeLength / 255 );
					trans.setTranslation( new Vector3d(
							x * edgeLength + bounds[0],
							y * edgeLength + bounds[2],
							z * edgeLength + bounds[4]
							) );
					
					Matrix4d pack = new Matrix4d( trans );
					pack.invert();
					
					ObjDump mini = new ObjDump();
					miniF.stream().forEach( f -> mini.addFaceFrom( f, src ) );
					mini.transform( pack );
					
					mini.dump( new File( new File( outfile, "" + dir ), OBJ ) );
					mt.index.put( dir, trans );

					dir++;
				}

		try {
			new XStream().toXML( mt, new FileWriter( new File( outfile, INDEX ) ) );
		} catch ( IOException e1 ) {
			e1.printStackTrace();
		}

		System.out.println( "wrote " + count + " faces to " + dir + " meshes" );

	}
}
