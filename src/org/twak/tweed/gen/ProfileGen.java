package org.twak.tweed.gen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.VizSkelGen.Mode;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.FindLines;
import org.twak.viewTrace.GBias;
import org.twak.viewTrace.LineSoup;
import org.twak.viewTrace.ObjSlice;
import org.twak.viewTrace.SliceParameters;
import org.twak.viewTrace.SuperLine;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.thoughtworks.xstream.XStream;

public class ProfileGen extends Gen  implements IDumpObjs {

	public LoopL<Point2d> gis;

	final int majorAxis = 1;
	
	double[] extent;
	
	GBias gisBias;
	
	List<Line> footprint = new ArrayList();

	List<MegaFacade> faces;

	Map<Integer, MegaFacade> dbgProfileLookup = new HashMap<>();
	
	public BlockGen blockGen;
	public SkelGen skelGen;
	
	double totalPlanLineLength = 0;
	
	static double HORIZ_SAMPLE_DIST = 0.2; // sensible defaults, now overwritten by tweedSettings
	static double HEIGHT_DELTA = 0.5;
	
	public ProfileGen( BlockGen blockGen, LoopL<Point2d> gis, Tweed tweed ) {

		super( "profiles", tweed );

		HORIZ_SAMPLE_DIST = 1;//TweedSettings.settings.profileHSampleDist;
		HEIGHT_DELTA = 0.5;//TweedSettings.settings.profileVSampleDist;
		
		tweed.frame.removeGens( ProfileGen.class );
		tweed.frame.removeGens( JmeGen.class );
		
		this.blockGen = blockGen;
		
		this.gis = gis;
//		this.gis = Loopz.removeInnerEdges( gisIn );
		this.gis = Loopz.removeNegativeArea( this.gis, -1 );
		this.gis = Loopz.mergeAdjacentEdges( this.gis, 1, 0.05 );
		
		this.extent = blockGen.getCroppedMesh().findExtent();
		this.gisBias = new GBias ( Loopz.toGraph( this.gis ), 1 );
		
		new Thread(() -> faces = findMegaFaces( HEIGHT_DELTA ) ).start();
	}

	public double getHeight (int i) {
		return this.extent[2] + i * HEIGHT_DELTA;
	}
	
	private static class LineAtHeight {
		
		int height;
		Line line;
		double goodLength;

		
		public LineAtHeight ( int height, Line line ) {
			this.line = line;
			this.height = height;
		}
		
		public LineAtHeight ( int height, double delta, Line line, Set<Line> others) {

			this (height, line);
			
			this.goodLength = line.length();
			for (Line l2 : others ) { 
				double dist = line.distance( l2 ); // subtract from length lines moving in other direction
				if (line.absAngle( l2 ) > 0.7 * Math.PI && dist < 5 ) // fucking bus
					goodLength -= l2.length() *   ( 5-dist ) / 5;
			}
			
		}
		
		@Override
		public boolean equals( Object obj ) {
			try {
				LineAtHeight lah = (LineAtHeight) obj;
				return line.equals (lah.line) && height == lah.height;
			} catch (ClassCastException e){
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return line.hashCode() + new Double( height ).hashCode();
		}
	}
	
	public class LAHComparator implements Comparator<LineAtHeight> {

		@Override
		public int compare( LineAtHeight o1, LineAtHeight o2 ) {

			return Double.compare ( 
					( getHeight ( o2.height ) < 4 ? 0.1 : 1 ) * o2.goodLength,
					( getHeight ( o1.height ) < 4 ? 0.1 : 1 ) * o1.goodLength
					);
		}
		
	}
	
	private final static int MIN = 0, MAX = 1;
	public static class MegaFacade extends MultiMap<Integer,Line> implements Comparable<MegaFacade>  {

		double area = -1;
		
		LineAtHeight origin;

//		List<Prof> profiles;
		
		public Map<Integer, Integer[]> vRange = new HashMap(); // vertical (up/down) range per-profile
		public Map<Integer, Double[]> dRange  = new HashMap(); // depth    (towards/away from megafacade)
		public Map<Integer, Prof> profiles    = new HashMap(); 
		
		int hExtentMin = Integer.MAX_VALUE, hExtentMax = Integer.MIN_VALUE; // horizontal extent (left/right)
		
		double pLength, oLen;
		int pCount;
		
		Vector2d normal;

		public double minP = 0, maxP = 1;
		
		public MegaFacade (LineAtHeight origin) {
			
			setOrigin( origin );
		}

		private void setOrigin( LineAtHeight origin ) {
			
			this.origin = origin;
			this.oLen = origin.line.length();
			
			pCount = (int) Math.max (2, origin.line.length() / HORIZ_SAMPLE_DIST );
			pLength = oLen / pCount;
			
			normal = origin.line.dir();
			normal.set(normal.y, -normal.x);
			
			put (origin.height, origin.line);
		}
		
		private void ensureArea() {
			if (area < 0)
				area = values().stream().flatMap( x -> x.stream() ).mapToDouble( l -> l.length() ).sum();
		}
		
		public int max() {
			return keySet().stream().mapToInt( x->x ).max().getAsInt();
		}
		
		@Override
		public int compareTo( MegaFacade o ) {
			
			ensureArea();
			o.ensureArea();
			
			return Double.compare (o.area, area);
		}
		
		@Override
		public void put( Integer key, Line value ) {
			super.put( key, value );
			addLine (key, value);
		}
		
		private void addLine (int h, Line line) {
			
			double s = origin.line.findPPram( line.start ),
				   e = origin.line.findPPram( line.end );
			
			for ( int x = (int) ( s * oLen / pLength ); x <= (int) ( e * oLen / pLength ); x++ ) {
				
				double ppram = x * pLength / oLen;
				
				Point2d pt = origin.line.fromPPram( ppram );
				
				Point2d sect =  new LinearForm ( line ).intersect( new LinearForm( normal ).findC( pt ) );
				
				addXY( x, h, ( origin.line.isOnLeft( sect ) ? -1 : 1 ) * sect.distance( pt ) );
			}
		}
		
		private void addXY (int x, int h, double d) {
			
			hExtentMin = Math.min( hExtentMin, x );
			hExtentMax = Math.max( hExtentMax, x );

			{
				Integer[] xmm = vRange.get( x );

				if ( xmm == null )
					vRange.put( x, xmm = new Integer[] { Integer.MAX_VALUE, Integer.MIN_VALUE } );

				xmm[ MIN ] = Math.min( xmm[ MIN ], h );
				xmm[ MAX ] = Math.max( xmm[ MAX ], h );
			}

			{
				Double[] dmm = dRange.get( x );

				if ( dmm == null )
					dRange.put( x, dmm = new Double[] { Double.MAX_VALUE, -Double.MAX_VALUE } );

				dmm[ MIN ] = Math.min( dmm[ MIN ], d );
				dmm[ MAX ] = Math.max( dmm[ MAX ], d );
			}
		}

		public void computeProfiles(ProfileGen pg) {
			
			profiles = new HashMap<>();
			Line l2 = origin.line;
			
			for (int i = hExtentMin; i <= hExtentMax; i++) {

				Integer[] hExtent = vRange.get( i );
				
				if ( hExtent == null )
					continue;
//					hExtent = new Integer[] {-1000, 1000};
			
				int h = origin.height; // todo : should be outside loop?
				
				if ( h < hExtent[MIN] )
					h = hExtent[MIN];

				if ( h > hExtent[MAX] )
					h = hExtent[MAX];
				
				double hd = pg.getHeight(h);
				
				Line3d oLine = new Line3d( 
						l2.start.x, hd, l2.start.y, 
						l2.end  .x, hd, l2.end  .y ); // todo : should be outside loop?
				
				Point2d start2 = origin.line.fromPPram( i * pLength / oLen );
				Point3d start3 = new Point3d(start2.x, hd, start2.y);
				
				Prof prof = Prof.buildProfile( pg.blockGen.getCroppedMesh(), oLine, start3,  
						pg.getHeight( hExtent[MIN] ) - HEIGHT_DELTA, pg.getHeight ( hExtent[MAX] ) +HEIGHT_DELTA,
						dRange.get(i)[MIN], dRange.get(i)[MAX],
						pg.tweed, pg.gNode );
				
				if (prof.size() >= 2)
					profiles.put( i,  prof );
			}
		}

		public int getIndex( Point2d pt ) {
			return (int) (origin.line.findPPram( origin.line.project( pt, false ) ) * oLen / pLength );
		}

		public MegaFacade moveTo( SuperLine profileLine ) {
			
			MegaFacade out = new MegaFacade( origin );
			
			out.area = area;
			
			out.hExtentMin = hExtentMin;
			out.hExtentMax = hExtentMax;
			out.pLength = pLength;
			out.oLen = oLen;
			out.normal = normal;
			out.minP = minP;
			out.maxP = maxP;

			out.vRange = new HashMap(vRange); // vertical (up/down) range per-profile
			out.dRange  = new HashMap(dRange); // depth    (towards/away from megafacade)
			out.profiles  = new HashMap(); 
			
			for (int i : profiles.keySet()) 
				if (profiles.containsKey( i ))
					out.profiles.put ( i, Prof.retarget( profiles.get(i), profileLine ) );
			
			return out;
		}

		public List<Prof> getTween(Point2d start, Point2d end, double shorten) {
			
			List<Prof> out = new ArrayList();
			
			int s = getIndex( start ), e = getIndex( end ) + 1;
			
			int d = e-s;
			
			if ( d > 8 ) {
				s += d * shorten;
				e -= d * shorten;
			}
			
			for (int i = s; i <= e; i++)
			{
				Prof p = profiles.get(i);
				if (p != null)
					out.add (p);
			}
			
			return out;
		}
	}
	
	@Override
	public void calculate( ) {
		
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
		
		for (Node n : addOnJmeThread)
			gNode.attachChild( n );
		
		gNode.updateGeometricState();
		gNode.updateModelBound();
		
		super.calculate( );
	}

	public static double SLICE_SCALE = 2.8;
	
	private List<MegaFacade> findMegaFaces( double delta ) {
		SliceParameters P = new SliceParameters( SLICE_SCALE );
		
		List<LineAtHeight> lines = new ArrayList();
		Map<Integer, LineSoup> slices = new HashMap<>();
		
		for ( int hi = 0; getHeight ( hi ) < extent[3]; hi++ ) {
			
			int _i = hi;
			
			boolean error;
			int count = 0;
			
			do {
				error = false;
				try {
					LineSoup soup = new LineSoup ( ObjSlice.sliceTri(blockGen.getCroppedMesh(), getHeight( hi ), majorAxis ) );
					FindLines foundLines = new FindLines(soup, 
//								null,
									TweedSettings.settings.useGis ? gisBias : null,
							-1, null, P);
					foundLines.result.all.stream().forEach( x -> lines.add(new LineAtHeight( _i, delta, x, foundLines.result.all)) );
					slices.put( hi, foundLines.result );
					
				} catch (Throwable th) {
					th.printStackTrace(  );
					error = true;
				}
				
			} while (error && count++ < 10);
		}
		
		Collections.sort( lines, new LAHComparator() );
		
//		new Plot(lines.stream().map (lah -> lah.line).collect(Collectors.toList() ), gis);
		
		faces = new ArrayList<>();
		
		
		while ( !lines.isEmpty() ) {

			System.out.println("clustering megafacdes " + lines.size() );
			
			LineAtHeight start = lines.get( 0 ); // longest line

			lines.remove( start );

			MegaFacade face = new MegaFacade( start );
			
			Set<LineAtHeight> toProcess = new HashSet<>();
			toProcess.add(start);

			while (!toProcess.isEmpty()) {
				
				LineAtHeight lah = toProcess.iterator().next();
				toProcess.remove (lah);
				
				for ( int _pmDelta : new int[] {  -2, -1, 1, 2 } ) {

					int hi = lah.height + _pmDelta;

					LineSoup ls = slices.get( hi );

					if ( ls != null ) {
						for ( Line l : ls.all ) {

							double lps = start.line.findPPram( l.start ), lpe = start.line.findPPram( l.end );

							double overlap = 0;

							if ( lpe < lps )
								overlap = 0;
							else if ( lps > face.minP && lpe < face.maxP || lps < face.minP && lpe > face.maxP )
								overlap = 1;
							else if ( lps < face.minP && lpe > face.minP )
								overlap = ( lpe - face.minP ) / ( lpe - lps );
							else if ( lps < face.maxP && lpe > face.maxP )
								overlap = ( face.maxP - lps ) / ( lpe - lps );

							double angle = l.absAngle( start.line );

							if ( overlap > 0.8 && angle < 0.3 || overlap > 0.5 /* 0.1 for aggressive clustering */ && angle < 0.1 ) {
//								if (overlap > 0.1 && angle < 0.5 ) {
									if ( l.distance( lah.line ) < delta * TweedSettings.settings.megafacacadeClusterGradient ) {
										LineAtHeight toProc = new LineAtHeight( hi, l );
										
										if ( lines.contains( toProc ) ) {
											
											toProcess.add( toProc );
											face.put( hi, l );
											lines.remove( toProc );

											face.minP = Mathz.min( face.minP, lps ); // bit strange: depends on the processing order of the set
											face.maxP = Mathz.max( face.maxP, lpe );
										}
									}
								}
							}
						}
					}
				}
			
				if (face.values().stream().flatMap( x -> x.stream() ).count() > 5)
					faces.add(face);
				else 
					System.out.println("skipping small megafacade");
			
			}

		Collections.sort( faces );
		
		processMegaFaces();
		
		return faces;
	}
	
	List<Node> addOnJmeThread = new ArrayList<>();
	
	private void processMegaFaces() {

		addOnJmeThread.clear();
		
		if (faces == null || faces.isEmpty()) {
			JOptionPane.showMessageDialog( tweed.frame(), "Failed to cluster facades" );
			return;
		}
		
		Node mfNode = new Node();
//		Node cProfileNode = new Node();
		Random randy = new Random(2);
		dbgProfileLookup.clear();
		
		final boolean DBG = true;
		
		computeProfiles(faces);
		
		int i = 0;
		for (MegaFacade mf : faces)
		{
			
//			if (i == 4) 
			{
			
			System.out.println("building profiles over megafacade " + i +"/"+faces.size());
			
			ColorRGBA dispCol = new ColorRGBA( randy.nextFloat(), randy.nextFloat(), randy.nextFloat(), 1 );
			
			if ( DBG ) 
			{
				List<Line3d> dbg = new ArrayList();
				for ( int d : mf.keySet() ) {
					
					
					for ( Line l2 : mf.get( d ) ) {
						Line3d oLine = new Line3d( l2.start.x, getHeight( d ), l2.start.y, 
								l2.end.x, getHeight( d ), l2.end.y );

						dbg.add( oLine );
					}
				}

				mfNode.attachChild( Jme3z.lines( tweed.getAssetManager(), dbg, dispCol, 0.1f, true ) );
				
				Line l2 = mf.origin.line;
				Line3d oLine = new Line3d( 
						l2.start.x, getHeight( mf.origin.height ), l2.start.y, 
						l2.end.x, getHeight( mf.origin.height ), l2.end.y );
				
				mfNode.attachChild( Jme3z.lines( tweed.getAssetManager(), Collections.singletonList( oLine ), dispCol, 0.3f, true ) );
			}
		
//			if ( mf.area > TweedSettings.settings.megaFacadeAreaThreshold )
			{
				Line l3 = mf.origin.line;
				
//				if ( mf.profiles.values().stream().mapToDouble( p -> p.get( 0 ).y ).min().getAsDouble() > 8 )
//					continue;
				
				totalPlanLineLength += l3.length();
				


				
				Line3d oLine = new Line3d(
						l3.start.x, getHeight( mf.origin.height ), l3.start.y, 
						l3.end  .x, getHeight( mf.origin.height ), l3.end  .y );
				
				List<SuperLine> pLines = Prof.findProfileLines( mf.profiles.values(), oLine );
				
				for (int pi = 0; pi < pLines.size(); pi++) {

					SuperLine profileLine = pLines.get( pi );
					
//					 if ( distance ( gis, profileLine.start ) > 2 || distance ( gis, profileLine.end ) > 2 )
//						 continue;
					
					Node profileNode = new Node();
//					dispCol = new ColorRGBA( randy.nextFloat(), randy.nextFloat(), randy.nextFloat(), 1 );
					
					MegaFacade pMF = mf.moveTo(profileLine);
					
						if ( pi >= 1 ) {

							Line newOrigin = null;
							double bestDist = Double.MAX_VALUE;

							Point3d plCen = Pointz.to3( profileLine.fromPPram( 0.5 ), getHeight( mf.hExtentMin ) );

							for ( int li = mf.hExtentMin; li <= mf.hExtentMax; li++ )
								for ( Line l : mf.get( li ) ) {
									double dist = Pointz.to3( l.fromPPram( 0.5 ), getHeight( li ) ).distance( plCen );
									if ( dist < bestDist ) {
										newOrigin = l;
										bestDist = dist;
									}
								}

							if ( newOrigin != null ) {
								pMF.setOrigin( new LineAtHeight( pMF.hExtentMin, newOrigin ) );
								pMF.computeProfiles( ProfileGen.this );
							}
						}
					
					profileLine.setMega ( pMF );
					footprint.add( profileLine );

					dbgProfileLookup.put( i++, pMF );

					if ( DBG ) {
						

						profileNode.attachChild( Jme3z.lines( tweed.getAssetManager(), Collections.singletonList( 
								new Line3d( profileLine.start.x, 0, profileLine.start.y, profileLine.end.x, 0, profileLine.end.y ) ),
								dispCol, 0.3f, true ));
						
						render ( new ArrayList<>(pMF.profiles.values()), tweed, profileNode );
						
//						List<Prof> cleans = new ArrayList<>();
						
//						for ( Prof p : pMF.profiles.values() ) {
//							p.render( tweed, profileNode, dispCol, 1f );
//							cleans.add( new Prof( p ).parameterize() );
//							.render( tweed, cProfileNode, dispCol.add( ColorRGBA.Gray ), 1f );
//							
//						}
						
//						render ( cleans, tweed, cProfileNode );
						profileNode.attachChild( Jme3z.lines( tweed.getAssetManager(), Collections.singletonList( oLine ), dispCol, 0.3f, true ) );
						profileNode.setUserData( ProfileGen.class.getSimpleName(), i );
						addOnJmeThread.add( profileNode );
					}
				}
			}
			}
		}
		
		if (DBG) 
			tweed.frame.addGen( new JmeGen( "horizontal lines", tweed, mfNode  ), false );
//			tweed.frame.addGen( new JmeGen( "clean profiles", tweed, cProfileNode ), false );
		
		calculateOnJmeThread();
		
	}

	private void computeProfiles( List<MegaFacade> mfs ) {
		
		BlockingQueue<MegaFacade> togo = new ArrayBlockingQueue<>( mfs.size() ); // todo: replace with PArallel
		
		togo.addAll( mfs );
		
		int tCount = Runtime.getRuntime().availableProcessors();
		
		CountDownLatch cdl = new CountDownLatch( tCount );
		
		for (int i = 0; i < tCount; i ++ ) {
			new Thread() {
				
				@Override
				public void run() {
					
					try {
					while (true)  {
						MegaFacade mf = togo.poll();
						
						System.out.println("megafacades remaining: "+togo.size());
						
						if (mf == null)
							return;
						mf.computeProfiles( ProfileGen.this );
					}
					}
					finally {
						cdl.countDown();
					}
				}
			}.start();
		}
		
		try {
			cdl.await();
			System.out.println("megafacades found");
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}
	}

	protected double distance( LoopL<Point2d> gis2, Point2d start ) {

		double out = Double.MAX_VALUE;
		
		for (Loop<Point2d> p : gis2) {
			
			if (Loopz.inside( start, p ))
				return 0;
			
			for (Loopable<Point2d> ll : p.loopableIterator() ) 
				out = Math.min (out, new Line (ll.get(), ll.getNext().get()).distance( start ));
		}
		
		return out;
	}

	@Override
	public JComponent getUI() {
		
		JSlider slider = new JSlider( JSlider.HORIZONTAL, -1, gNode.getChildren().size(), -1 );

		slider.addChangeListener( new ChangeListener() {

			@Override
			public void stateChanged( ChangeEvent e ) {
				tweed.enqueue( new Runnable() {
					@Override
					public void run() {
						int value = slider.getValue();
						for ( Spatial s : gNode.getChildren() ) {
							if ( s instanceof Node ) {
								Integer i = s.getUserData( ProfileGen.class.getSimpleName() );

								if ( i != null && s instanceof Node )
									if ( value == -1 || i == value )
										s.setCullHint( CullHint.Inherit );
									else
										s.setCullHint( CullHint.Always );
							}
						}
						
						tweed.gainFocus();
					}
				} );
			}
		} );
		
		JButton skel = new JButton("optimize");
		skel.addActionListener( ae -> doSkel() );
		
		JComboBox<Mode> vizMode = new JComboBox<>(Mode.values());
		vizMode.addActionListener(e -> doViz( (Mode) vizMode.getSelectedItem() ) );
		
		JButton writeProfiles = new JButton("dump profs");
		writeProfiles.addActionListener( ae -> writeProfiles() );
		
		JButton stateBuilder = new JButton("preview results");
		stateBuilder.addActionListener( ae -> new SSBuilder(this, tweed.features) );
		
		JPanel out = new JPanel( new ListDownLayout() );
		out.add( new JLabel("profiles rendered:") );
		out.add( slider );
		out.add( skel );
		out.add( new JLabel("viz:") );
		out.add( vizMode );
		out.add( writeProfiles );
		out.add( stateBuilder );
		return out;
	}
	
	private void doSkel() {
		tweed.frame.removeGens(SkelGen.class);
		tweed.frame.addGen ( this.skelGen = new SkelGen( footprint, tweed, blockGen ), true );
	}
	
	private void doViz(Mode mode) {
		tweed.frame.removeGens(VizSkelGen.class);
		tweed.frame.addGen ( new VizSkelGen( footprint, tweed, blockGen, mode ), true );
	}

	private void writeProfiles() {
		List<Prof> out = new ArrayList();
		for (MegaFacade mf : faces) {
			if (mf.area > 30) {
				out.addAll(mf.profiles.values());
			}
		}
		
		try {
			new XStream().toXML( out, new FileOutputStream( new File ( Tweed.SCRATCH+"profiles.xml" ) ) );
			
		} catch ( FileNotFoundException e ) {
			e.printStackTrace();
		}
	}
	
	public static void render( List<Prof> ofs, Tweed tweed, Node n ) {

		Random randy = new Random(ofs.hashCode());
		
		Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
		
		ColorRGBA col = new ColorRGBA(
				randy.nextFloat(), 
				0.2f+ 0.5f * randy.nextFloat(), 
				0.5f+ 0.5f * randy.nextFloat(), 1);
		
		mat.setColor( "Diffuse", col );
		mat.setColor( "Ambient", col.mult( 0.5f ) );
		mat.setBoolean( "UseMaterialColors", true );
		
		for (Prof p : ofs) {
			Geometry g = new Geometry();
			g.setMesh( p.renderStrip( HORIZ_SAMPLE_DIST/2, null ) );
			g.setMaterial( mat );
			n.attachChild( g );
		}
	}

	@Override
	public void dumpObj( ObjDump dump ) {
		Jme3z.dump(dump, gNode, 0);
	}
}
