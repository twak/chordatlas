package org.twak.tweed.tools;

import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.LineGen3d;
import org.twak.tweed.gen.MMGSkelGen;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.Loop;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.ui.AutoSpinner;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.FacadeTexApp;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class HouseTool extends Tool {

	public HouseTool( Tweed tweed ) {
		super( tweed );
	}
	
	public int numX = 1, numY = 1;
	
	@Override
	public void clickedOn( Spatial target, Vector3f loc, Vector2f cursorPosition ) {

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
		
		HalfMesh2.Builder builder = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		
		double accumWidth = 0;
		
		double spacing = 14;
		
		for ( int x = 0; x < numX_; x++ ) {
			for ( int y = 0; y < numY_; y++ ) {

				double width = Math.random() * 4 + 6;
				double depth = Math.random() * 4 + 6;

				double height = Math.random() * 4 + 4;

				double[] minMax = new double[] { x * spacing - depth / 2, x * spacing + depth / 2, y * spacing - width / 2, y * spacing + width / 2 };

				System.out.println( "start: " + accumWidth + " end: " + ( accumWidth + width ) );

				//			accumWidth += width + 0.5;

				if ( footprint == null ) {
					builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z ) );
					builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z ) );
					builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z ) );
					builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z ) );
				}
				else
				{
					for (Point3d pt : footprint) 
						builder.newPoint( new Point2d( pt.x, pt.z ) );
				}

				HalfFace f = builder.newFace();

				Prof p1 = new Prof(), p2 = new Prof();

				p1.add( new Point2d( 0, 0 ) );
				p1.add( new Point2d( 0, height ) );
				p1.add( new Point2d( -( 4.5 * Math.random() + 4), height + 5 ) );

				p2.add( new Point2d( 0, 0 ) );
				p2.add( new Point2d( 0, height ) );
				if (Math.random() > 0.5)
					p2.add( new Point2d( -( 1.5 * Math.random() + 2), height + 5 ) );

				Prof[] ps = new Prof[] { p1, p2 };

				int count = 0;

				for ( HalfEdge e : f ) {
					SuperEdge se = (SuperEdge) e;

					se.prof = ps[ count % ps.length ];

					MiniFacade mini = newMini( null, se.length() );
					mini.height = height;

					if ( count >= 0 ) {
						se.addMini( mini );

						se.toEdit = mini;
					}
					count++;
				}

				SuperFace sf = (SuperFace) f;
				sf.maxProfHeights = new ArrayList();
				sf.maxProfHeights.add( Double.valueOf( 100 ) );
				sf.height = 100;
			}
		}

		HalfMesh2 mesh = builder.done();
		SkelGen sg = new MMGSkelGen( mesh, tweed, null );
		sg.name = "houses";
		tweed.frame.addGen( sg, true );
		
		for (HalfFace hf : mesh)
		for (HalfEdge he : hf) {
			FacadeTexApp mfa =  ((SuperEdge)he).toEdit.facadeTexApp;

			mfa.appMode = TextureMode.Off;//"tex.jpg";
		}
	}
	
	private MiniFacade newMini(ImageFeatures imf, double length) {
		
		MiniFacade mini = new MiniFacade();
		mini = new MiniFacade();
		mini.width = length;
		mini.height = 10;
		
//		mini.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, Math.random() * mini.width - 3, 5, 3, 3, mini ) );
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
	

	@Override
	public void getUI( JPanel panel ) {

		panel.setLayout( new ListDownLayout() );
		
		panel.add( new JLabel("click nothing to create grid:"));
		panel.add( new AutoSpinner( this, "numX", "number of houses deep", 1, 50 ));
		panel.add( new AutoSpinner( this, "numY", "number pf houses wide", 1, 50 ));
	}

}
