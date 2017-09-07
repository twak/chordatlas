package org.twak.viewTrace;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;

import org.apache.commons.math3.analysis.function.Gaussian;
import org.twak.tweed.Tweed;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.PaintThing;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.Graph2D;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.triangulate.EarCutTriangulator;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.utils.ui.WindowManager;

public class Slice extends JComponent {

	double sliceHeight;
	int majorAxis = 1; //z
	int[] flatAxis = new int[]{0,2};
	PanMouseAdaptor ma;
	
	ObjRead rawMesh, filteredMesh;
	
	boolean showGis = true, showCarnie = true, showSlice = true;
	Graph2D gis, carnieSoup;
	LoopL<Point2d> carnie;
	
	boolean showFit = true;
	LineSoup slice;
	FindLines foundLines;
	
	List<SupportPoint> supportPoints = new ArrayList();
	double supportMax;

	GBias gisBias;
	
	SliceParameters P;
	
	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;
		
		g2.setColor(Color.white);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

//		outline.paint( g2, ma);
		
		g2.setColor( new Color (200,100,100));
		g2.setStroke(new BasicStroke(5f));
		
		if (showFit && foundLines != null)
			PaintThing.paint ( foundLines.result, g2, ma );
		
		g2.setStroke(new BasicStroke(1f));

		if (showGis) {
			g2.setColor(Color.red);
			PaintThing.paint ( gis, g2, ma);
		}
		
		g2.setColor(Color.blue);
		if (carnie != null && showCarnie) 
			PaintThing.paint ( carnie, g2, ma);
		
//		if (carnieSoup != null && showCarnie) 
//			PaintThing.paint ( carnieSoup, g2, ma);
		
		g2.setColor(Color.black);
		
		if (slice != null && showSlice) {
			
//			AffineTransform af = g2.getTransform();
//			
//			AffineTransform p = new AffineTransform(af);
//			p.concatenate( AffineTransform.getTranslateInstance( 600, 0 ) );
//			
//			g2.setTransform( p );
			
			PaintThing.paint ( slice,g2,ma);
			
//			g2.setTransform( af );
		}

//		g2.setColor(Color.green);
		
		for (SupportPoint s : supportPoints) {
			g2.setColor ( Color.getHSBColor( (float)(0.7 * s.support/supportMax), 1f, 1f) );
			g2.fillOval(ma.toX(s.pt.x)-2, ma.toY(s.pt.y)-2, 4, 4);
		}
		
		PaintThing.paintDebug(g2, ma);
	}
	
	
	public void findSupportPoints() {
		
		supportPoints.clear();

		supportMax = 0;
		
//		Map<Line, Double> totalLineSupport = new HashedMap();
		
		double distSigma = 0.2;
		Gaussian distanceG = new Gaussian ( 0, distSigma), angleG = new Gaussian(0, 0.3);
		
		for (Line l : gis.allLines()) {
			double length = l.length();
			int noPoints = (int) Math.max ( 2, length / 0.1 );
			
			for ( int n = 0; n <= noPoints; n ++ ) {
				
				SupportPoint sp = new SupportPoint( l, l.fromPPram(n/ (double)noPoints) );
				
				sp.support = 0;//Double.MAX_VALUE;
				
				for (Line nearL : slice.getNear(sp.pt, distSigma * 3)) {
					
					double angle = nearL.absAngle(sp.line);
					
					double dist = nearL.distance(sp.pt, true);//Math.min  (sp.pt.distance(line.start), sp.pt.distance(line.end)) ;
					
					sp.support += distanceG.value(dist) * angleG.value(angle) * nearL.length();
				}

				supportPoints.add ( sp );

			}
		}
		
		foundLines = null;

		repaint();
	}


	final static int sliderScale = 10000;
	
	File toSlice;
	
	public Slice(File folder, double sliceScale) {
		this (new File (folder, "cropped.obj"), new File (folder, "gis.obj"), new SliceParameters(sliceScale), true );
	}
	
	public Slice(File toSlice, File footprint, SliceParameters p, boolean ui) {
		
		this.toSlice = toSlice;
		this.P = p;
		
		if (footprint.getName().endsWith(".obj")) {
			ObjRead gObj = new ObjRead(footprint);

			gis = new Graph2D();
			for (int[] face : gObj.faces)
			{
				for (int i = 0; i < face.length; i++) {
					
					double[] a = gObj.pts[face[i]],
							 b = gObj.pts[face[(i+1) % face.length]];
					
					gis.add(new Line(
							new Point2d( b[flatAxis[0]], b[flatAxis[1]] ),
							new Point2d( a[flatAxis[0]], a[flatAxis[1]] )
							));
				}
			}
			
		} else {
			
			gis = GMLReader.readGMLToGraph(footprint);
			gis.removeInnerEdges();

			AffineTransform af = null;
			if (footprint.getPath().contains("slice_building")) {

				af = AffineTransform.getScaleInstance(-0.102, 0.102);
				af.concatenate(AffineTransform.getRotateInstance(-0.036));
				af.concatenate(AffineTransform.getTranslateInstance(-3.5, 2.30));
				af.concatenate(AffineTransform.getTranslateInstance(-529422.5898700021, -182187.80314510738));
			} else if (footprint.getPath().contains("pollen")) {

				af = AffineTransform.getScaleInstance(-0.105, 0.105);
				af.concatenate(AffineTransform.getRotateInstance(-0.036));
				af.concatenate(AffineTransform.getTranslateInstance(-3.5, 2.30));
				af.concatenate(AffineTransform.getTranslateInstance(-529024.15, -181044.7));
			} else if (footprint.getPath().contains("tallis")) {

				af = AffineTransform.getScaleInstance(-0.105, 0.105);
				af.concatenate(AffineTransform.getRotateInstance(-0.036));
				af.concatenate(AffineTransform.getTranslateInstance(-3.5, 2.30));
				af.concatenate(AffineTransform.getTranslateInstance(-531420.45, -180961.5));
			
			} else if (footprint.getPath().contains("ludgate")) {

				af = AffineTransform.getScaleInstance(-0.105, 0.105);
				af.concatenate(AffineTransform.getRotateInstance(-0.036));
				af.concatenate(AffineTransform.getTranslateInstance(-3.5, 2.30));
				af.concatenate(AffineTransform.getTranslateInstance(-531661, -181216.45));
			}
			gis = gis.apply(af);
		}
		
		gisBias = new GBias(gis, 0.5);
		
		
		setupObj(new ObjRead(toSlice));
		
		sliceHeight = (max[majorAxis] - min[majorAxis]) / 2 + min[majorAxis];

		if (ui)
			buildUI();
	}
	
	private void buildUI() {
		
		ma = new PanMouseAdaptor(this);
		
		ma.center(new Point2d(
				(max[flatAxis[0]] - min[flatAxis[0]]) / 2 + min[flatAxis[0]],
				(max[flatAxis[1]] - min[flatAxis[1]]) / 2 + min[flatAxis[1]]
			));
		
		ma.setZoom(16);
		
		
		JFrame jf = new JFrame("slice");
		WindowManager.register( jf );
		
		jf.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
//				System.exit(0);
			}
		});
		
		jf.setLayout(new BorderLayout());
		this.setPreferredSize(new Dimension(600, 600));
		jf.add(this, BorderLayout.CENTER);


		final JSlider heightSlider = new JSlider( SwingConstants.VERTICAL, 
				(int)(min[majorAxis]*sliderScale), (int)( max[majorAxis] * sliderScale), 
				(int)((max[majorAxis] -min[majorAxis]) * 0.5 + min[majorAxis])*sliderScale );
		final JSlider lineSlider = new JSlider( SwingConstants.VERTICAL, -1,50, -1 ); 

		heightSlider.setPaintLabels(false);
		
		final ChangeListener cl;
		heightSlider.addChangeListener(cl = new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent arg0) {
				
				sliceHeight = heightSlider.getValue() / (double) sliderScale;
				
				LineSoup rawSoup = sliceMesh(rawMesh, sliceHeight);
				slice = sliceMesh(filteredMesh, sliceHeight);
				
				foundLines = new FindLines(slice, gisBias, lineSlider.getValue(), rawSoup.clique(P.CL_HIGH, P.CL_LOW), P);
				
				Concarnie cc = new Concarnie(foundLines.result, foundLines.cliques, P);
				
//				Concarnie cc = new Concarnie(slice, rawSoup.clique() );

				carnieSoup = cc.graph;
				carnie = cc.out;
				
				Slice.this.repaint();
			}
		});
		
		cl.stateChanged(null);
		
		lineSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				cl.stateChanged(null);
			}
		});
		
		final JSlider parameterScaleSlider = new JSlider( SwingConstants.VERTICAL, 0, 2000, (int)( P.getScale() * 100 ) );
		
		parameterScaleSlider.addChangeListener( new ChangeListener() {
			
			@Override
			public void stateChanged( ChangeEvent e ) {
				P.setScale( parameterScaleSlider.getValue() / 100. );
			}
		} );
		
		JButton go = new JButton("support");
		
		go.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				
				setupObj(new ObjRead(toSlice));
				
//				supportPoints.clear();
//				Slice.this.repaint();
//				SwingUtilities.invokeLater(new Runnable() {
//					@Override
//					public void run() {
//						PerpSupport ps = new PerpSupport(slice, gis);
//						Slice.this.supportMax = ps.supportMax;
//						Slice.this.supportPoints = ps.supportPoints;
//						Slice.this.repaint();
//					}
//				});
			}
		});
		
		JButton fs = new JButton("full support");
		fs.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				
				Set<Point2d> points = new HashSet();
				
				for (double v = min[majorAxis]; v < max[majorAxis]; v+= 0.01) {
					
					System.out.println("processing slice " + v +" / " + max[majorAxis]);
					
					slice = sliceMesh(filteredMesh, sliceHeight);
					for (SupportPoint sp : new PerpSupport(slice, gis).supportPoints)
						points.add(new Point2d ( sp.support, max[majorAxis]-v ) );
				}
				
				System.out.println("found " + points.size() +" pts");
				
				new Plot(points);
			}
		});
		
		JButton ge = new JButton("lines");
		
		ge.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				foundLines = new FindLines(slice, gisBias, lineSlider.getValue(), slice.clique(P.CL_HIGH, P.CL_LOW), P);
				
				Slice.this.repaint();
			}
		});
		
		JButton cc = new JButton("carnie");
		
		cc.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				LineSoup rawSoup = sliceMesh(rawMesh, sliceHeight);
				foundLines = new FindLines(slice, gisBias, lineSlider.getValue(), rawSoup.clique(P.CL_HIGH, P.CL_LOW), P);
				
				Concarnie cc = new Concarnie(foundLines.result, foundLines.cliques, P);
				
				carnieSoup = cc.graph;
				carnie = cc.out;
				
				Slice.this.repaint();
			}
		});

		JButton ob = new JButton("dump");
		ob.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				
				ObjDump out = new ObjDump();
				
				double delta = 2.5;

				for ( double d = min[majorAxis]; d <= max[majorAxis]; d+=delta )
				{
					System.out.println(d +" / " + max[majorAxis]);
					
					boolean error;
					int count = 0;
					do {
						error = false;
						try {
							
							LineSoup rawSoup = sliceMesh(rawMesh, d);
							slice = sliceMesh(filteredMesh, d);
							
							foundLines = new FindLines(slice, null, lineSlider.getValue(), rawSoup.clique(P.CL_HIGH, P.CL_LOW), P);
//							foundLines = new FindLines(slice, gisBias, lineSlider.getValue(), rawSoup.clique(P.CL_HIGH, P.CL_LOW), P);

							Concarnie c = new Concarnie(foundLines.result, foundLines.cliques, P);

//							
							extrude(out, c.out, d, d+delta);
//							capAtHeight (out, c.out, false, d );
							capAtHeight (out, c.out, true, d+delta );
							
							
						} catch (Throwable th) {
							error = true;
						}
					} while (error && count++ < 10);
					
				}
				
				out.dump(new File( Tweed.SCRATCH +"test2.obj"));
			}


		});
		
		JButton an = new JButton("anim");
		
		an.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				
				
				BufferedImage bi = new BufferedImage (Slice.this.getWidth(), Slice.this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
				
				for (File f : new File ( Tweed.SCRATCH +  "vid").listFiles())
					f.delete();
				
				int c = 0;
				
				for (int i = heightSlider.getMinimum(); i < heightSlider.getMaximum(); i+=100) {
					heightSlider.setValue( i );
					Slice.this.paintComponent( bi.getGraphics() );
					try {
						ImageIO.write( bi, "png", new File ( String.format ( Tweed.SCRATCH +  "vid/%05d.png", c++ ) ));
						
					} catch ( IOException e ) {
						e.printStackTrace();
					}
				}
			}
		});

		JButton sl = new JButton("global");
		
		sl.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				new SliceSolver(new File( Tweed.SCRATCH + "test2.obj" ), Slice.this, P);
			}
		});
		
		JButton pr = new JButton("profiles");
		
		pr.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				
				double delta = 0.5;

				
				BufferedImage out = new BufferedImage( Slice.this.getWidth(), Slice.this.getHeight(), BufferedImage.TYPE_4BYTE_ABGR );
				Graphics2D g2 = (Graphics2D) out.getGraphics();
				
				g2.setColor(Color.white);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				
				g2.setColor( new Color( 0, 0, 0, 50 ) );
				
				for ( double d = min[majorAxis]; d <= max[majorAxis]; d+=delta )
				{
					System.out.println(d +" / " + max[majorAxis]);
					
					boolean error;
					int count = 0;
					do {
						error = false;
						try {
							
							LineSoup rawSoup = sliceMesh(rawMesh, d);
							slice = sliceMesh(filteredMesh, d);
							
							FindLines fl = new FindLines(slice, gisBias, lineSlider.getValue(), rawSoup.clique(P.CL_HIGH, P.CL_LOW), P);
							
							PaintThing.paint( fl.result, g2, ma );

//							Concarnie c = new Concarnie(foundLines.result, foundLines.cliques, P);

						} catch (Throwable th) {
							error = true;
						}
					} while (error && count++ < 10);
				}
				
				g2.dispose();
				
				try {
					ImageIO.write( out, "png", new File (Tweed.SCRATCH +  "lines") );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				
			}
		});
		
		
		final JCheckBox sg = new JCheckBox("gis", true);
		sg.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showGis = sg.isSelected();
				Slice.this.repaint();
			}
		});
		
		final JCheckBox sf = new JCheckBox("fit", true);
		sf.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showFit = sf.isSelected();
				Slice.this.repaint();
			}
		});
		
		final JCheckBox sc = new JCheckBox("poly-out", true);
		sc.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showCarnie = sc.isSelected();
				Slice.this.repaint();
			}
		});
		
		final JCheckBox ss = new JCheckBox("slice", true);
		ss.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showSlice = ss.isSelected();
				Slice.this.repaint();
			}
		});
		
		JPanel buttons = new JPanel(new ListDownLayout());
		buttons.add(go);
		buttons.add(fs);
		buttons.add(ge);
		buttons.add(cc);
		buttons.add(an);
		buttons.add(ob);
		buttons.add(sl);
		buttons.add(pr);
		
		buttons.add(sg);
		buttons.add(sf);
		buttons.add(sc);
		buttons.add(ss);
		buttons.add(lineSlider);
		
		JPanel controls = new JPanel(new BorderLayout());
		controls.add(heightSlider, BorderLayout.CENTER);
		controls.add(parameterScaleSlider, BorderLayout.WEST);
		controls.add(buttons, BorderLayout.EAST);
		
		jf.add(controls, BorderLayout.EAST);
		jf.pack();
		jf.setVisible(true);
	}
	
	double[] min = new double[3], max = new double[3];
	
	private void setupObj(ObjRead obj) {
		
		rawMesh = obj;
		filterGE ( filteredMesh = new ObjRead ( obj ) );
		
		double[] minMax = obj.findExtent();
		
		min[0] = minMax[0];
		min[1] = minMax[2];
		min[2] = minMax[4];
		max[0] = minMax[1];
		max[1] = minMax[3];
		max[2] = minMax[5];
	}

	private void filterGE(ObjRead obj) {
		
		
//		double tol = 0.00001; //better tol, but misses some
		double tol = 0.0001;
		int count = 0;
		
		
//		CountPairs counter = new CountPairs();
		
//		for (int[] ind : obj.faces)
//			for ( int i = 0; i < ind.length; i++) 
//				counter.count(new Point3d(obj.pts[ind[i]]), new Point3d(obj.pts[ind[(i + 1) % ind.length]]));
		
		
//		for (int thin = 0; thin < 2; thin++)
			for (int face = 0; face < obj.faces.length; face++) {
			
				int[] ind = obj.faces[face];
			
				if (ind.length == 3) {
				
					Point3d 
					a = new Point3d ( obj.pts[ind[0]] ),
					b = new Point3d ( obj.pts[ind[1]] ),
					c = new Point3d ( obj.pts[ind[2]] );
				
					if ( 
//					   (counter.total(a,b) == 1 || 
//					    counter.total(b,c) == 1 || 
//					    counter.total(c,a) == 1   ) &&
					   Mathz.min(
							   a.distanceSquared(b),
							   b.distanceSquared(c),
							   c.distanceSquared(a)
							   ) < 0.05 &&
							
							
					   (Math.abs ( a.x - b.x ) < tol && Math.abs (b.x - c.x) < tol ||
						Math.abs ( a.y - b.y ) < tol && Math.abs (b.y - c.y) < tol ||
						Math.abs ( a.z - b.z ) < tol && Math.abs (b.z - c.z) < tol ) ) {
					
							obj.faces[face] = new int[0];
							count++;
//							counter.uncount(a, b);
//							counter.uncount(b, c);
//							counter.uncount(c, a);
					}
				}
			}
		
		
		System.out.println("GE tab filter removed " + count +" faces");
	}


	public static void extrude( ObjDump out, LoopL<Point2d> slice, double h1, double h2 ) {
		 
		for (Loop<Point2d> loop : slice) {

			for (Loopable<Point2d> pt : loop.loopableIterator()) {
				
				List<double[]> pts = new ArrayList<>(),
						norms = new ArrayList<>();
				
				Point2d a = pt.get(), b = pt.getNext().get();

				pts.add( new double[] { a.x, h1, a.y} );
				pts.add( new double[] { b.x, h1, b.y} );
				pts.add( new double[] { b.x, h2, b.y} );
				pts.add( new double[] { a.x, h2, a.y} );
				
				Vector2d d = new Vector2d( b);
				d.sub(a);
				d.normalize();
				
				double[] norm = new double[] {-d.y, 0, d.x};
//				double[] norm = new double[] {d.y, 0, -d.x};
				
				for (int i = 0; i < 4; i++)
					norms.add(norm);
				
				out.addFace(pts.toArray( new double[pts.size()][] ), null, norms.toArray( new double[norms.size()][] ));
			}
		}
	}
	
	public static void capAtHeight(ObjDump out, LoopL<Point2d> slice, boolean flipNormals, double height) {
		
		double[] normal = new double[] {0,flipNormals ? 1 : -1,0};
		final float[] up   = new float [] {0,1,0};
		
		for (Loop<Point2d> loop : slice) {
			
			
			
			List<Float> coords = new ArrayList();
			List<Integer> inds = new ArrayList();
			
			for (Point2d pt : loop) {

				inds.add( inds.size() );

				coords.add( (float) pt.x );
				coords.add( (float) height );
				coords.add( (float) pt.y );
			}
			
			float[] p = Arrayz.toFloatArray( coords );
			int[] i = Arrayz.toIntArray( inds ), ti = new int[i.length * 3];

			int tris = EarCutTriangulator.triangulateConcavePolygon(p,
	                0,
	                inds.size(),
	                i,
	                ti,
	                up );
			
			for (int t = 0; t < tris; t++) {
				List<double[]> 
						pts   = new ArrayList<>(),
						norms = new ArrayList<>();

				
				for ( int j : flipNormals ? new int[] {0,1,2} : new int[] {2,1,0} ) {
					
					int k = ti[t*3+j];
					
					pts.add( new double[] {
							p[k*3+0], 
							p[k*3+1],
							p[k*3+2] } );
					norms.add( normal );
				}
				
				out.addFace(pts.toArray( new double[pts.size()][] ), null, norms.toArray( new double[norms.size()][] ));
			}
			
//			for (Point2d pt : loop) { 
//				pts.add(new double[] {pt.x, height, pt.y } );
//				norms.add(up);
//			}
//			
//			if (!flipNormals)
//				Collections.reverse(pts);
			
		}
	}
	
	LineSoup sliceMesh(ObjRead mesh, double sliceHeight) {
		LineSoup out = new LineSoup ( ObjSlice.sliceTri(mesh, sliceHeight, majorAxis ) );
		
//		if (!filter)
//			return out;
//		Set<Line> togo = new HashSet();
//		for (Line l : out.all) // some models contained a pair of coincident lines that go nowhere....
//			if (out.all.contains( l.reverse() )) 
//				togo.add(l);
//		out.all.removeAll(togo);
		
		return new LineSoup (out.all);
	}
	
	public static void main(String[] args) {
		
		 try {
			UIManager.setLookAndFeel(
			            UIManager.getSystemLookAndFeelClassName());
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
//		new Slice(new File("/home/twak/data/mesh/for_profile.obj"), new File("/home/twak/data/mesh/slice_building.gml"));
//		new Slice(new File("/home/twak/data/mesh/whitefriars.obj"), new File("/home/twak/data/mesh/whitefriars_gis.obj"));
//		new Slice(new File("/home/twak/data/ludgate/ge.obj"), new File("/home/twak/data/ludgate/gis.obj"));
//		new Slice(new File("/home/twak/data/ludgate/ge.obj"), new File("/home/twak/data/ludgate/ludgate.gml"));
//		new Slice(new File("/home/twak/data/pollen/toslice.obj"), new File("/home/twak/data/pollen/gis.gml"));
//		new Slice(new File("/home/twak/data/tallis/to_slice.obj"), new File("/home/twak/data/tallis/tallis.gml"));
//		new Slice(new File("/home/twak/data/lerfosgade/lerfosgade.obj"), new File("/home/twak/data/lerfosgade/gis.obj"), new SliceParameters(), true);
//		new Slice(new File("/home/twak/data/waverley/waverley.obj"), new File("/home/twak/data/waverley/gis.obj"));
		new Slice( new File( Tweed.SCRATCH +  "meshes/194" ), 10 );
	}

//	private static boolean isGEHorizVert (Line l ) {
//	return  
//			l.lengthSquared() < 0.001 && 
//			( 
//			Math.abs( l.start.x - l.end.x ) < 0.00001 || 
//			Math.abs( l.start.y - l.end.y ) < 0.00001   );
//}

	
//	private class CountPairs extends CountThings<Pair<Point3d, Point3d>> {
//		
//		Point3dComparator comparator = new Point3dComparator();
//		
//		public void count(Point3d a, Point3d b) {
//			super.count( order(a,b) );
//		}
//		
//		public void uncount(Point3d a, Point3d b) {
//			super.uncount( order(a,b) );
//		}
//
//		public int total(Point3d a, Point3d b) {
//			return super.total(order(a,b));
//		}
//		
//		private Pair<Point3d, Point3d> order(Point3d a, Point3d b) {
//			
//			if (comparator.compare(a, b) < 0) 
//				return new Pair (b,a);
//			else
//				return new Pair (a,b);
//		}
//	}
//	
//	private void filterGE2(ObjRead obj) {
//		
//		double tol = 0.001;
//		
//		Cache<Double, Integer> count = new Cac
//		
//		for (int face = 0; face < obj.faces.length; face++) {
//			
//			int[] ind = obj.faces[face];
//		
//			if (ind.length == 3) {
//			
//				Point3d 
//				a = new Point3d ( obj.pts[ind[0]] ),
//				b = new Point3d ( obj.pts[ind[1]] ),
//				c = new Point3d ( obj.pts[ind[2]] );
//			
//				
//				if (Math.abs ( a.x - b.x ) < tol && Math.abs (b.x - c.x) < tol) {
//					
//				}
//				
//				if ( 
//				    Math.abs ( a.x - b.x ) < tol && Math.abs (b.x - c.x) < tol ||
//					Math.abs ( a.y - b.y ) < tol && Math.abs (b.y - c.y) < tol ||
//					Math.abs ( a.z - b.z ) < tol && Math.abs (b.z - c.z) < tol   ) {
//					
//				}
//			}
//		}
//	}
	
}
