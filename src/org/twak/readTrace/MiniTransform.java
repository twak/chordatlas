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

import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjDump.Face;
import org.twak.utils.geom.ObjDump.Material;
import org.twak.utils.streams.InaxPoint3dCollector;

import com.jme3.math.Matrix4f;
import com.thoughtworks.xstream.XStream;

public class MiniTransform {

	public final static String OBJ = "model.obj", INDEX = "index.xml";
	public Map<Integer, Matrix4d> index = new HashMap(); // folder name to offset
	public Matrix4f offset = new Matrix4f(); // additional offset applied to all via ui

	public static void importTo( File oneOfManyObj, File outputDir ) {

		List<File> files = new ArrayList();

		for ( File f : oneOfManyObj.getParentFile().listFiles() )
			if ( f.getName().toLowerCase().endsWith( ".obj" ) )
				files.add( f );

		importTo( files, outputDir );
	}

	public static void importTo( Iterable<File> bigObj, File outputDir ) {

		outputDir.mkdirs();

		ObjDump src = new ObjDump( bigObj );

		//		ObjDump src = new ObjDump( new File( "/home/twak/Downloads/bath_andy_hoskins/Bath_2017_Sample/OBJ/Tile-3-2-1-1.obj" ) );

		long count = src.material2Face.entrySet().stream().mapToInt( x -> x.getValue().size() ).sum();
		double[] bounds = src.orderVert.stream().collect( new InaxPoint3dCollector() );
		long targetCount = 5000;

		double volume = ( bounds[ 1 ] - bounds[ 0 ] ) * ( bounds[ 3 ] - bounds[ 2 ] ) * ( bounds[ 5 ] - bounds[ 4 ] );

		double edgeLength = Math.pow( volume / ( count / targetCount ), 0.3333 );

		int xc = (int) Math.ceil( ( bounds[ 1 ] - bounds[ 0 ] ) / edgeLength ), yc = (int) Math.ceil( ( bounds[ 3 ] - bounds[ 2 ] ) / edgeLength ), zc = (int) Math.ceil( ( bounds[ 5 ] - bounds[ 4 ] ) / edgeLength );

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

		File outfile = new File( "/home/twak/Desktop/minimesh" );

		int dir = 0;
		MiniTransform mt = new MiniTransform();

		for ( int x = 0; x < xc; x++ )
			for ( int y = 0; y < yc; y++ )
				for ( int z = 0; z < zc; z++ ) {

					Set<Face> miniF = faces[ x ][ y ][ z ];
					if ( miniF == null )
						continue;

					Matrix4d loc = new Matrix4d();
					loc.setIdentity();

					mt.index.put( dir, loc );

					ObjDump mini = new ObjDump();

					miniF.stream().forEach( f -> mini.addFaceFrom( f, src ) );
					mini.dump( new File( new File( outfile, "" + dir ), OBJ ) );

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
