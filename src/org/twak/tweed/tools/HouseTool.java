package org.twak.tweed.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.mmg.MOgram;
import org.twak.mmg.media.GreebleMMG;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.LineGen3d;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.Loop;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.AutoSpinner;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.MMGFeatureGen;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.FacadeLabelApp;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class HouseTool extends Tool {

	public HouseTool( Tweed tweed ) {
		super( tweed );
	}
	
	public int numX = 1, numY = 1;
	
	static int seed = 0;
	
	@Override
	public void clickedOn( Spatial target, Vector3f loc, Vector2f cursorPosition ) {
		clickedOn2( target, loc, cursorPosition );
	}
	
	public SkelGen clickedOn2( Spatial target, Vector3f loc, Vector2f cursorPosition ) {

		Loop<Point3d> footprint = null;
		
		int numX_ = numX, numY_ = numY;
		
		if (target != null ) {
			
			Object[] val = target.getUserData( LineGen3d.class.getSimpleName() );
			if (val != null) { 
				footprint = (Loop<Point3d>)val[0];
				numX_ = 1;
				numY_ = 1;
			}
		}
		
		Random randy = new Random();
//		Random randy = new Random(18);
		
		System.out.println( "seed is "+ seed );
		seed++;
		
		HalfMesh2.Builder builder = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		
//		double accumWidth = 0;
		
		double spacing = 30;//14;
		
		for ( int x = 0; x < numX_; x++ ) {
			for ( int y = 0; y < numY_; y++ ) {

				double width = randy.nextDouble() * 4 + 6;
				double depth = randy.nextDouble() * 4 + 6;

				double height = randy.nextDouble() * 4 + 2;

				double[] minMax = new double[] { 
						x * spacing - depth / 2, 
						x * spacing + depth / 2, 
						y * spacing - width / 2, 
						y * spacing + width / 2 };

//				System.out.println( "start: " + accumWidth + " end: " + ( accumWidth + width ) );
				//			accumWidth += width + 0.5;

				if ( footprint == null ) {
					
					if (randy.nextDouble() < 0) {
						builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z ) );
						builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z ) );
						builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z ) );
						builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z ) );
					}
					else 
					{
						
						double wingLength = 8;
						
						double top    = randy.nextDouble() * wingLength * 2 - wingLength, 
							   left   = randy.nextDouble() * wingLength * 2 - wingLength,
							   bottom = randy.nextDouble() * wingLength * 2 - wingLength, 
							   right  = randy.nextDouble() * wingLength * 2 - wingLength;
						
						if (randy.nextDouble() < 0.2)
							top = bottom;
						if (randy.nextDouble() < 0.2)
							left= right;
						
						builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z ) );
						
						if (top > 0.1) {
							builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z + top ) );							
							builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z + top ) );
						}
						
						builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z ) );
						
						
						if (left > 0.1) {
							builder.newPoint( new Point2d( minMax[ 1 ] + loc.x + left, minMax[ 3 ] + loc.z ) );
							builder.newPoint( new Point2d( minMax[ 1 ] + loc.x + left, minMax[ 2 ] + loc.z ) );
						}
						
						builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z ) );
						
						if (bottom > 0.1) {
							builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z - bottom ) );
							builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z - bottom ) );
						}
						
						
						builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z ) );
						
						if (right > 0.1) {
							builder.newPoint( new Point2d( minMax[ 0 ] + loc.x - right, minMax[ 2 ] + loc.z ) );
							builder.newPoint( new Point2d( minMax[ 0 ] + loc.x - right, minMax[ 3 ] + loc.z ) );
						}
						
					}
				}
				else
				{
					for (Point3d pt : footprint) 
						builder.newPoint( new Point2d( pt.x, pt.z ) );
				}


				Prof p1 = new Prof(), p2 = new Prof();

				p1.add( new Point2d( 0, 0 ) );
				p1.add( new Point2d( 0, height ) );
				
				if (randy.nextDouble() > 0.8) {
					p1.add( new Point2d( -( 2 * randy.nextDouble() + 2), height + randy.nextDouble() * 2 + 0.5 ) );
					p1.add( new Point2d( -( 4.5 * randy.nextDouble() + 4), height + 3 ) );
				}
				else
					p1.add( new Point2d( -( 4.5 * randy.nextDouble() + 4), height + 5 ) );
				
				p2.add( new Point2d( 0, 0 ) );
				p2.add( new Point2d( 0, height ) );
				if (randy.nextDouble() > 0.5) 
					p2.add( new Point2d( -( 1.5 * randy.nextDouble() + 2), height + 5 ) );

				Prof[] ps = new Prof[] { p1, p2 };
				
				HalfFace f = builder.newFace();

				int count = 0;

				HalfEdge last = null;
				
				for ( HalfEdge e : f ) {
					
					SuperEdge se = (SuperEdge) e;

					se.prof = ps[ count % ps.length ];

					MiniFacade mini = newMini( null, se.length() );
					mini.height = height;

					se.addMini( mini );
					se.toEdit = mini;
					
					if (last == null || last.line().absAngle( e.line() ) > 0.1 )
						count++;
					last = e;
				}

				SuperFace sf = (SuperFace) f;
				sf.maxProfHeights = new ArrayList();
				sf.maxProfHeights.add( Double.valueOf( 100 ) );
				sf.height = 100;
			}
		}

		HalfMesh2 mesh = builder.done();
		SkelGen sg = new SkelGen( mesh, tweed, null );
		sg.name = "houses";
		tweed.frame.addGen( sg, true );
		
		MOgram mogram = GreebleMMG.createMOgram( null );
		for ( HalfFace hf : mesh )
			for ( HalfEdge he : hf ) {
				
				
				SuperEdge se = (SuperEdge)he;
				
				FacadeLabelApp mfa = se.toEdit.facadeLabelApp;
				
				se.toEdit = new MiniFacade();
				se.toEdit.featureGen = new MMGFeatureGen( mogram );
				
				mfa.appMode = TextureMode.MMG;
				mfa.mogram = mogram;
			}
		
		return sg;
	}
	
	private MiniFacade newMini(ImageFeatures imf, double length) {
		
		MiniFacade mini = new MiniFacade();
		mini = new MiniFacade();
		mini.width = length;
		mini.height = 10;
		
//		mini.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, randy.nextDouble() * mini.width - 3, 5, 3, 3, mini ) );
//		mini.featureGen = new CGAMini( mini );

		mini.imageFeatures = imf;
		
//		mini.normal = "normal.jpg";
//		mini.spec = "spec.jpg";
		
		return mini;
	}
	
	@Override
	public String getName() {
		return "house";
	}
	
	private void writeMany() {
		
		tweed.enqueue( new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
		
		for (int i = 0; i < 50000; i++) {
		
			System.out.println( " >>>>> "+ i );
			
			File f = new File("/home/twak/Desktop/tiny_buildings/"+i+".obj");
			if (!f.exists() || f.length() == 0)
				{
			
			SkelGen res = clickedOn2( null, new Vector3f(), new Vector2f() );

			res.calculate();
			
			ObjDump dump = new ObjDump();
			dump.REMOVE_DUPE_TEXTURES = true;
			res.dumpObj( dump );
			
			
			dump.dump( f, new File ( Tweed.DATA ) );
			
			if (f.length() == 0)
				i--;
			
			tweed.frame.removeGen( res );
				}
			
		}
		
			}
		} );
		
	}
	
	@Override
	public void getUI( JPanel panel ) {

		panel.setLayout( new ListDownLayout() );
		
		panel.add( new JLabel("click nothing to create grid:"));
		panel.add( new AutoSpinner( this, "numX", "number of houses deep", 1, 100 ));
		panel.add( new AutoSpinner( this, "numY", "number pf houses wide", 1, 100 ));
		JButton jb = new JButton("write many");
		jb.addActionListener( e -> writeMany() );
		panel.add( jb );
	}
}
