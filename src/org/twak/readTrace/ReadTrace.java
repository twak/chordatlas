package org.twak.readTrace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4d;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.geotools.referencing.operation.matrix.Matrix4;
import org.twak.utils.BitTwiddle;
import org.twak.utils.CountThings;
import org.twak.utils.MUtils;
import org.twak.utils.ObjDump;

import com.thoughtworks.xstream.XStream;

public class ReadTrace {

	private static File CACHE_FILE =  new File("/home/twak/Desktop/frames.xml");
	
	File folder;
	
	Map<Long, String > bufferToBin = new HashMap<>();
	
	private static class Tex {
		int width, height;
		String filename;
		public Tex (int width, int height, String filename) {
			this.width = width;
			this.height = height;
			this.filename = filename;
		}
	}
	Map<Long, Tex> bufferToTex = new HashMap<>();
	
	Pattern bindBufferRE  =  Pattern.compile(".*glBindBuffer\\(target\\ =\\ GL_([^,]*).*buffer\\ =\\ (\\d*).*");
	Pattern glBufferDataRE = Pattern.compile(".*glBufferData\\(target\\ =\\ GL_([^,]*).*\\\"(.*)\\\".*" );
	
	Pattern bindTextureRE =  Pattern.compile(".*glBindTexture.*texture\\ =\\ (\\d*).*");
	Pattern glCompressedTexture =  Pattern.compile(".*glCompressedTexImage2D.*width\\ =\\ (\\d*).*height\\ =\\ (\\d*).*\\\"(.*)\\\".*");
	Pattern glActiveTexture = Pattern.compile(".*glActiveTexture\\(texture\\ =\\ GL_TEXTURE([^\\)]*)\\)");
	
	long currentFrame = 0, block = 0;
	
	int[] tUnitsTextures;// new int[] {0,0,0,0,0,0,0,0};
	
	Map <String, Long> boundBuffer = new HashMap();
	
	static class Frame extends ArrayList <FrameGeometry> {};
	
	Frame frame = new Frame();
	List<Frame> frames = new ArrayList<>();
	
	double[] magic8 = new double[8];
	
	File miniMesh = null;
	
	public static void main(String[] args) throws Throwable {
		String dataDir = "/home/twak/dump/blah";
		
//		new ReadTrace( dataDir );
		new ReadTrace( dataDir, CACHE_FILE );
	}
	
	public ReadTrace(String dumpF, File cacheFile) throws Throwable {
		
		miniMesh = new File ("/home/twak/Desktop/minimesh/");
		
		File dump = new File(dumpF);
		assert dump.exists();
		folder = dump.getParentFile();
		
		frames = (List) new XStream().fromXML( cacheFile );
		
		postProcessFrames();
	}
	
	public ReadTrace(String dumpF) throws Throwable {

		Arrays.fill(tUnitsTextures = new int[32], 0);
		
		File dump = new File(dumpF);
		assert dump.exists();
		folder = dump.getParentFile();

		System.out.println( "reading " + dumpF );
		
		BufferedReader br = new BufferedReader(new FileReader(dump), 1024 * 1024 * 10);

		String line;

		boolean inBlock = false;

		
//		ObjDump out = new ObjDump();
		
		boolean endOfBlock = false;
	
		// need to distinguish gl_element_array_buffer and gl_array_buffer
		
		while ((line = br.readLine()) != null) {

			// keep track of which bin is in which buffer/texture
			
			Matcher m = bindBufferRE.matcher(line);
			if ( m.matches() ) {
				boundBuffer.put(m.group(1), Long.parseLong(m.group(2)));
			}
			
			m = glBufferDataRE.matcher(line);
			if ( m.matches() )
			{
				bufferToBin.put( boundBuffer.get(m.group(1)), m.group(2));
			}
			
			m = glActiveTexture.matcher(line);
			if ( m.matches() ) {
				try{
					iVals[ACTIVE_TUNIT] = Integer.parseInt(m.group(1) );
				}
				catch (NumberFormatException th ) {}
			}
			
			m = bindTextureRE.matcher(line);
			if (m.matches())
				tUnitsTextures[(int)iVals[ACTIVE_TUNIT]] = Integer.parseInt( m.group(1) );
			
			m = glCompressedTexture.matcher(line);
			if (m.matches()) 
				bufferToTex.put( (long) tUnitsTextures[ (int) iVals[ACTIVE_TUNIT]], new Tex( Integer.parseInt( m.group(1) ), Integer.parseInt( m.group(2) ), m.group(3) ) );
				
			if (line.indexOf("glXSwapBuffers") >= 0) {
				currentFrame++;

				matrixForAntiWibble = null;
				
				if (!frame.isEmpty())
					frames.add(frame);
				frame = new Frame();
				
				endOfBlock = false;
			}
			else if ( currentFrame == 58 ) //  
			{
				if (line.indexOf("glDisableVertexAttribArray") > -1)
					endOfBlock = true;
				
				if (!endOfBlock && 
					(
							line.indexOf("glUniform1fv(location = 9, count = 8, value = ") > -1 ||  /*first useful data starts with alpha mask 8 element mystery*/
							line.indexOf("glUniform1fv(location = 1, count = 8, value = ") > -1 ||  /*first useful data starts with alpha mask 8 element mystery*/
							( block >= 1 && line.indexOf("glBindTexture(target = GL_TEXTURE_2D") /*some dont set the alpha mask */ > -1 )) ) {
					
					inBlock = true;
				}

				System.out.println( line );
				
				processLine( line );

				if (inBlock && line.indexOf("glDrawElements") > -1) {
					inBlock = false;
					
					processBlock();
					
					block++;
					
					System.out.println("> " + block);
				}

			}
		}

		if (!frame.isEmpty())
			frames.add(frame);
		
//		out.validateIndicies();
		
//		out = new TextureAtlas(folder, out).done;
		
//		out.allDone(new File (folder, "model.obj"));
		br.close();
		
		System.out.println("done");
		
		new XStream().toXML( frames,new FileOutputStream( CACHE_FILE ) );

		postProcessFrames();
	}
	
	private final static String ELEMENT_ARRAY_BUFFER = "ELEMENT_ARRAY_BUFFER", ARRAY_BUFFER = "ARRAY_BUFFER";
	
	static class FrameGeometry {
		
		Matrix4d viewMatrix;
		String loc, ind, uvs;
		long[] iVals;
		float[] fVals;
		Tex tex;
		double[] magic8;
		
		public FrameGeometry( Matrix4d viewMatrix, long[] iVals, float[] fVals, double[] magic8, String ind, String loc, String uvs, Tex tex ) {
			this.viewMatrix = new Matrix4d(viewMatrix);
			this.iVals = Arrays.copyOf( iVals, iVals.length );
			this.fVals = Arrays.copyOf( fVals, fVals.length );
			this.magic8 = Arrays.copyOf( magic8, magic8.length );
			this.ind = ind;
			this.loc = loc;
			this.uvs = uvs;
			this.tex = tex;
		}
	}
	
	private void processBlock() throws Throwable {

		int texBuffer = tUnitsTextures[(int)iVals[ACTIVE_TUNIT]];
		
		String uvBuffer = bufferToBin.get( (long) iVals[UV_BUFFER]),
				indBuffer = bufferToBin.get( (long) boundBuffer.get(ELEMENT_ARRAY_BUFFER).intValue() ),
				vertBuffer = bufferToBin.get( (long) iVals[VERTEX_BUFFER]);
		
		if (uvBuffer != null && indBuffer != null && vertBuffer != null &&
				new File( folder, vertBuffer ).exists()
				)
		frame.add( new FrameGeometry(
					viewMatrix,
					iVals,
					fVals,
					magic8,
					indBuffer,
					vertBuffer,
					uvBuffer,
					bufferToTex.get( (long) texBuffer)
				) );
		else
			System.err.println("missing buffer " + uvBuffer + " "+indBuffer + " "+vertBuffer);
	}
			
	private void postProcessFrames() throws Throwable {
		
		ObjDump out = new ObjDump();
		
		Collections.reverse( frames ); // start with data most likely to be loaded
		
		double[] tmp = new double[4];

//		Frame ff = frames.get(frames.size()-1);
//		frames.clear();
//		frames.add(ff);
		
		for (Frame f : frames) { // filter out large tiles

			CountThings<Double> lengths = new CountThings<>();
			
			for (FrameGeometry fg : f) {
				
				for (int i = 0; i < 3; i ++) { 
					fg.viewMatrix.getColumn( i, tmp );
					
					lengths.count( (double) Math.round ( new Vector4d(tmp).length() * 20 )/20 );
				}
			}
			
			double targetLength = lengths.getMax().first(); // we assume the most popular tile size is the smallest (and that they've loaded)
			double t = 0.01/targetLength;
			
			Matrix4d scale = new Matrix4d( 
					t, 0, 0, 0,
					0, t, 0, 0,
					0, 0, t, 0,
					0, 0, 0, t );
			
			Iterator<FrameGeometry> fig = f.iterator();
			
			fig:
			while (fig.hasNext()) {

				FrameGeometry fg = fig.next();
				
				for (int i = 0; i < 3; i ++) { 
					
					fg.viewMatrix.getColumn( i, tmp );
					
					if ( new Vector4d(tmp).lengthSquared() > (targetLength * targetLength * 1.01) ) { // remove non-smallest tile size
						fig.remove();
						continue fig;
					}
				}
						
				fg.viewMatrix.mul(scale);
				
				swapRows( fg.viewMatrix, 0, 1 ); // y-up!
				swapRows( fg.viewMatrix, 0, 2 );
				
				fg.viewMatrix.m33 = 1;
			}
			
		}

		Matrix4d frameTransform = new Matrix4d();
		frameTransform.setIdentity();
		Map<String, Matrix4d> knownVerts = new HashMap<>();
		Iterator<Frame> fit = frames.iterator();

		while(fit.hasNext())
			if (fit.next().size() < 50) {
				System.out.println("warning: removing small frame");
				fit.remove();
			}
		
		fit = frames.iterator();
		
		while(fit.hasNext()) {
			Frame f = fit.next();
			
			Set<Matrix4d> mats = new HashSet<>();
			
			for ( FrameGeometry fg : f ) { // search for known geometry
				
				if ( knownVerts.containsKey( fg.loc ) ) {

						Matrix4d toFrame = new Matrix4d( fg.viewMatrix );
						toFrame.invert();
						toFrame.mul( knownVerts.get( fg.loc ) );
						
						mats.add(toFrame);
						
//						frameTransform = new Matrix4d( fg.viewMatrix );
//						frameTransform.invert();
//						frameTransform.mul( knownVerts.get( fg.loc ) );
//
//						break;
//						frameTransform = toFrame;
				}
					
			}
			
			if (!mats.isEmpty())
				frameTransform = average(mats);
			
			if (frameTransform != null ) { // apply found transform to all within same frame

				Iterator<FrameGeometry> fig = f.iterator();
				while (fig.hasNext()) {
					FrameGeometry fg = fig.next();

					if ( knownVerts.containsKey( fg.loc )) {
						fig.remove();
					}
					else {
						
						fg.viewMatrix.mul( frameTransform );
						
						knownVerts.put( fg.loc, fg.viewMatrix );
					}
				}
			}
			else {
				System.out.println("failed to find origin for frame");
				fit.remove();
			}
			
			frameTransform = null;
		}
		
		int count = 0, c2 = 0;
		
		MiniTransform miniTransform = new MiniTransform();
		
		for ( Frame f : frames ) { // write out the frames into a single file
			
			System.out.println( "post-processing " + count++ + "/" + frames.size() );
			
			for ( FrameGeometry fg : f ) {
				
				File miniFrameFolder = null;
				File outFolder = folder;
				Matrix4d meshTransform = fg.viewMatrix;
				
				if (miniMesh != null) { // if we're writing out in the miniMesh format
					out = new ObjDump();
					
					miniFrameFolder = new File (miniMesh, c2+"");
					miniFrameFolder.mkdirs();
					miniTransform.index.put( c2, fg.viewMatrix );
					outFolder = miniFrameFolder;
					
					meshTransform = new Matrix4d();
					meshTransform.setIdentity();
				}
				
				
				int[] ind = BitTwiddle.byteToUShort( getBytes( fg.ind ) );

				if ( ind == null ) {
					System.out.println( "*** missing index buffer " + fg.ind );
					continue;
				}

				byte[] vtLocBytes = getBytes( fg.loc );

				if ( vtLocBytes == null )
					continue;

				int[][] vtLoc = BitTwiddle.byteToUByte( vtLocBytes, 4, 
						(int) fg.iVals[ VERTEX_BUFFER_OFFSET ], (int) fg.iVals[ VERTEX_BUFFER_STRIDE ] );

				byte[] uvBytes = Files.readAllBytes( new File( folder, fg.uvs ).toPath() );
				int[][] uvLoc = BitTwiddle.byteToUShort( uvBytes, 2, 
						(int) fg.iVals[ UV_BUFFER_OFFSET ], (int) fg.iVals[ UV_BUFFER_STRIDE ] );

				out.setCurrentTexture( ""+c2++, 512, 512 );
				
				Tex tex = fg.tex;
				
				File texFile = null;
				
				if ( tex != null && ( texFile = new File( folder, tex.filename )).exists() ) {
					
					File mat = DTX1.toPNG( Files.readAllBytes( texFile.toPath() ), outFolder, tex.width, tex.height );
					out.setCurrentTexture( mat.getName(), tex.width, tex.height );
				} else {
					out.setCurrentTexture( "missing_" + c2, 1, 1 );
					System.err.println( "missing texture!" + c2 );
				}

				for ( int i = 2; i < fg.iVals[ TRI_COUNT ]; i++ ) { // GL_TRIANGLES

					int a = ind[ i ], 
						b = ind[ i - ( i % 2 == 0 ? 2 : 1 ) ], 
						c = ind[ i - ( i % 2 == 0 ? 1 : 2 ) ];
					
					if (
							a != b && b != c && c != a &&
							
							isMagic8Visible (fg.magic8, vtLoc[ a ][3]) && // google viewer magically hides some verts
							isMagic8Visible (fg.magic8, vtLoc[ b ][3]) && 
							isMagic8Visible (fg.magic8, vtLoc[ c ][3]) &&
							!isTab ( vtLoc[a], vtLoc[b], vtLoc[c] )       // removes the tabs from the edges and centers of tiles (much easier before transform)
							)  {
						
						
						float[][] pos = new float[][] { 
							locTrans( vtLoc[ a ], meshTransform ),  
							locTrans( vtLoc[ b ], meshTransform ), 
							locTrans( vtLoc[ c ], meshTransform ),
							};
						
						out.addFace( 
							pos, 
							new float[][] { 
							uvMunge( uvLoc[ a ][ 0 ], uvLoc[ a ][ 1 ], tex, fg.fVals ), 
							uvMunge( uvLoc[ b ][ 0 ], uvLoc[ b ][ 1 ], tex, fg.fVals ), 
							uvMunge( uvLoc[ c ][ 0 ], uvLoc[ c ][ 1 ], tex, fg.fVals ) },
							findNormals(pos) );
					}
				}
				
				if (miniMesh != null) {
//					out.writeMtlFile = false;
					out.dump( new File( miniFrameFolder, "model.obj" ) );
				}
			}
		}

		
		if (miniMesh == null)
			out.dump( new File( folder, "model.obj" ) );
		else
		{
			new XStream().toXML( miniTransform, new FileOutputStream( new File (miniMesh, "index.xml") ) );
		}
	}
	
	private float[][] findNormals (float[][] pos) {
	
		Vector3f 
			a = new Vector3f(pos[0]),
			b = new Vector3f(pos[1]),
			c = new Vector3f(pos[2]);
		
		b.sub(a);
		c.sub(a);
		
		b.normalize();
		c.normalize();
		
		a.cross(b,c);
		
		float[] out = new float[3];
		a.get( out );
		
		if (Float.isNaN( a.x ) || Float.isNaN( a.y ) || Float.isNaN( a.z ))
			a.set(0,0,0);
		
		return new float[][] { out, out, out };
		
	}
	
	private Matrix4d average( Set<Matrix4d> mats ) {
		
		class LocationWrapper implements Clusterable {
			
		    double[] points;
		    Matrix4d d;

		    public LocationWrapper(Matrix4d d) {
		        this.d = d;
		        
//		        this.points = new double[16];
//		        d.set( points );
		        
		        this.points = new double[] { d.m03, d.m13, d.m23 };
		    }

		    public double[] getPoint() {
		        return points;
		    }
		}
		
		List<LocationWrapper> lrs = new ArrayList();
		
		{
			double[] a = new double[4], b = new double[4], c = new double[4];

			double small = 0.001;

			for ( Matrix4d m : mats ) {

				m.getColumn( 0, a );
				m.getColumn( 1, b );
				m.getColumn( 2, c );

				double la = new Vector3d( a ).length(), lb = new Vector3d( b ).length(), lc = new Vector3d( c ).length();

				if ( la < small || lb < small || lc < small ) // ill formed transforms
					continue;

				if ( Math.abs( la - lb ) > small || Math.abs( lb - lc ) > small ) // ill formed transforms (also check perpendicular?)
					continue;

				lrs.add( new LocationWrapper( m ) );
			}
		}
		
//		mats.stream().map( x -> new LocationWrapper( x ) ).collect( Collectors.toList() );
		
		if (lrs.isEmpty())
			return null;
		
		KMeansPlusPlusClusterer<LocationWrapper> clusterer = new KMeansPlusPlusClusterer<LocationWrapper>( Math.min (lrs.size(), 10), 10000);
		CentroidCluster<LocationWrapper> biggest = clusterer.cluster(lrs).stream().max( (a,b) -> Double.compare (a.getPoints().size(), b.getPoints().size()) ).get();
		
		if (biggest.getPoints().size() < 5)
			return null;
		
		return biggest.getPoints().get( 0 ).d;
		
//		for (Matrix4d m : mats)
//		        
//		
//		Matrix4d avg = new Matrix4d();
//		
//		
//		for (Matrix4d m : mats)
//			for (int x = 0; x < 4; x++)
//				for (int y = 0; y < 4; y++)
//					avg.setElement( x, y, avg.getElement(x,y) + m.getElement( x, y ) );
//		
//		for (int x = 0; x < 4; x++)
//			for (int y = 0; y < 4; y++)
//				avg.setElement( x, y, avg.getElement(x,y) /mats.size() );
//		return avg;
	}

	private static boolean isTab( int[] a, int[] b, int[] c ) {
		for (int i = 0; i < 3; i++) 
			if (  a[i] == b[i] && b[i] == c[i])
				if ( 
						a[i] == 127 || // tab locations are strange...but predictable?! per dataset?
						( i == 0 && ( a[i] == 9  || a[i] == 245 ) ) ||  
						( i == 1 && ( a[i] == 33 || a[i] == 221 ) ) ||  
						( i == 2 && ( a[i] == 3  || a[i] == 251 ) ) ) 

//					( i == 0 && ( a[i] == 32 || a[i] == 222 ) ) ||  
//					( i == 1 && ( a[i] == 3  || a[i] == 251 ) ) ||  
//					( i == 2 && ( a[i] == 27 || a[i] == 227 ) ) ) 
					
//					( i == 0 && ( a[i] == 21 || a[i] == 233 ) ) ||  
//					( i == 1 && ( a[i] == 3  || a[i] == 251 ) ) ||  
//					( i == 2 && ( a[i] == 16 || a[i] == 238 ) ) ) 
					return true;
		
		return false;
	}

	private void swapRows( Matrix4d m, int a, int b ) {
		double[] tmpA = new double[4], tmpB = new double[4];
		m.getRow( a, tmpA );
		m.getRow( b, tmpB );
		
		m.setRow( a, tmpB );
		m.setRow( b, tmpA );
	}

	private boolean isMagic8Visible( double[] magic8, int i ) {
		return magic8 [ (int) MUtils.clamp  ( (double)((int)Math.floor ( i + 0.5 )), 0, 7) ] > 0;
	}

	private float[] locTrans(int[] fs, Matrix4d viewMatrix) {
		
		Vector4d loc =new Vector4d(
				Math.floor( ( fs[0] ) + 0.5),
				Math.floor( ( fs[1] ) + 0.5), 
				Math.floor( ( fs[2] ) + 0.5),
				1); 
		
		viewMatrix.transform(loc);
		
		return new float[] { (float) loc.x, (float) loc.y , (float) loc.z};
	}

	private byte[] getBytes(String filename) throws Throwable {
		
		File file = new File ( folder, filename );
		
		if (filename == null || !file.exists()) {
			System.out.println("*** warning! missing buffer! " + filename );
			return null;
		}
		
		return Files.readAllBytes( file.toPath() );
	}
	private byte[] getBytes(int buffer) throws Throwable {
		
		return getBytes (  bufferToBin.get( (long) buffer ) );
	}

	private static float[] uvMunge (int x /*uShort*/, int y /*uShort*/, Tex tex, float[] frameFVals ) {
		
		float xx = (float)( Math.floor ( (  x * 0xff ) + 0.5 ) + frameFVals[UV_PARAM_X] ) * frameFVals[UV_PARAM_Z];
		float yy = (float)( Math.floor ( ( -y * 0xff ) + 0.5 ) + frameFVals[UV_PARAM_Y] ) * frameFVals[UV_PARAM_W];
		
		if (tex != null) {
			xx += tex.width == 256 ? 0.5f : +0.25f;
			switch (tex.height) {
			case 256:
				yy += -0.5f;
				break;
			case 512:
				yy += -0.75f;
				break;
			case 1024:
				yy += -0.875f; // gave 1 at -0.75f
				break;
			}
		}

		return new float[] { xx/255 , yy/255 }; 
	}
	
	private long [] iVals = new long[11];
	private static int TRI_COUNT = 0, // INDEX_BUFFER = 1, CURRENT_BUFFER = 2,  
		VERTEX_BUFFER = 3, VERTEX_BUFFER_OFFSET = 4, VERTEX_BUFFER_STRIDE = 5,
		UV_BUFFER = 6, UV_BUFFER_OFFSET = 7, UV_BUFFER_STRIDE = 8, CURRENT_TEXTURE = 9,
		ACTIVE_TUNIT = 10;
	
	private float[] fVals = new float[10];	
	private static int	UV_PARAM_X = 0, UV_PARAM_Y = 1, UV_PARAM_Z = 2, UV_PARAM_W = 3;
	
	Matrix4d viewMatrix = new Matrix4d(), matrixForAntiWibble = null;

	Pattern glDrawElementsRE = Pattern.compile(".*glDrawElements.*count\\ =\\ (\\d*),.*");
	Pattern glVertexAttrib = Pattern.compile( ".*glVertexAttribPointer.*GL_UNSIGNED_([^,]*).*stride\\ =\\ ([\\d]*),\\ pointer\\ =\\ ([a-f0-9xNUL]*).*" );
	
	Pattern uvParams = Pattern.compile(".*glUniform4fv\\(location\\ =\\ 1.*\\{" +
//	Pattern uvParams = Pattern.compile(".*glUniform4fv\\(location\\ =\\ 9.*\\{" +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^\\}]*).*");

	Pattern viewMatrixPattern = Pattern.compile(".*glUniformMatrix4fv\\(location\\ =\\ 2.*\\{" +
//	Pattern viewMatrixPattern = Pattern.compile(".*glUniformMatrix4fv\\(location\\ =\\ 0.*\\{" +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^\\}]*).*");
	
	Pattern magic8Pattern = Pattern.compile(".*glUniform1fv\\(location\\ =\\ 9,\\ count\\ =\\ 8,\\ value\\ =\\ \\{" +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^,]*),\\ " +
			"([^\\}]*).*");
	
	private void processLine(String line) throws Throwable {

		set(glDrawElementsRE, line, TRI_COUNT);
		
		Matcher m = glVertexAttrib.matcher(line);
		
		if (m.matches())
		{
			if (m.group(1).equals("BYTE") ) {
				iVals[VERTEX_BUFFER] = boundBuffer.get(ARRAY_BUFFER);
				iVals[VERTEX_BUFFER_OFFSET] = m.group(3).equals("NULL") ? 0 : Integer.parseUnsignedInt( m.group(3 ).substring(2), 16 );
				iVals[VERTEX_BUFFER_STRIDE] = Integer.parseInt( m.group(2 ) );
			}
			if (m.group(1).equals("SHORT")) {
				iVals[UV_BUFFER] = boundBuffer.get(ARRAY_BUFFER);
				iVals[UV_BUFFER_OFFSET] = m.group(3).equals("NULL") ? 0 : Integer.parseInt( m.group(3 ).substring(2), 16 );
				iVals[UV_BUFFER_STRIDE] = Integer.parseInt( m.group(2 ) );
			}
		}
		
		m = magic8Pattern.matcher( line );
		if (m.matches())
			for (int i = 1; i < 9; i++)
				magic8[i-1] = Double.parseDouble( m.group(i) );
		
		m = viewMatrixPattern.matcher(line);
		if (m.matches()) {
			for (int xi = 0; xi < 4; xi++)
				for (int yi = 0; yi < 4; yi++) 
					viewMatrix.setElement(xi, yi, Double.parseDouble( m.group( xi + yi * 4 + 1) ) );
			
			if (matrixForAntiWibble == null) {
				matrixForAntiWibble = new Matrix4d( viewMatrix );
				try {
					matrixForAntiWibble.invert();
				}
				catch (Throwable th ) {
					matrixForAntiWibble = null;
				}
			}
			
			if (matrixForAntiWibble != null)
				viewMatrix.mul(matrixForAntiWibble, viewMatrix);
		}
	
		m = uvParams.matcher(line);
		if (m.matches()) 
			for (int i = 0; i < 4; i++)
				fVals[UV_PARAM_X+i] = Float.parseFloat( m.group(1+i) );
		
	}

	private void set(Pattern p, String line, int index) {
		Matcher m = p.matcher(line);

		if (m.matches())
			iVals[index] = Long.parseLong(m.group(1));

	}

	
	
	/**
	 * Old, unused to decode manually extraced tile buffers
	 */
//	protected static void decodeBuffer() throws Throwable { 
//		int[] ind = BitTwiddle.byteToUShort ( Files.readAllBytes(new File("/home/twak/vertex_ind.bin").toPath()) );
//		
//		byte[] vertData = Files.readAllBytes(new File("/home/twak/vertex_data.bin").toPath()) ;
//		
//		float[][] vtLoc = BitTwiddle.byteToFloat (vertData, 3, 0 , 28);
//		float[][] uvLoc = BitTwiddle.byteToFloat (vertData, 2, 12, 28);
//		
//		ObjDump out = new ObjDump();
//		
//		
//		for (int i = 0; i < ind.length/3; i++ )
//			out.addFace( 
//					new float[][] { vtLoc[ind[i*3]], vtLoc[ind[i*3+1]], vtLoc[ind[i*3+2]] },
//					new float[][] { uvLoc[ind[i*3]], uvLoc[ind[i*3+1]], uvLoc[ind[i*3+2]] }
//				);
//		
//		out.allDone(new File ( "/home/twak/building.obj") );
//		System.out.println("obj written");
//	}
}
