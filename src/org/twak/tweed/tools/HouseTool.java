package org.twak.tweed.tools;

import java.util.ArrayList;

import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.ui.AutoSpinner;
import org.twak.utils.ui.Colourz;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.App.AppMode;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class HouseTool extends Tool {

	public HouseTool( Tweed tweed ) {
		super( tweed );
	}
	
	public int num = 5;
	
	@Override
	public void clickedOn( Spatial target, Vector3f loc, Vector2f cursorPosition ) {

//		MegaFeatures mf = new MegaFeatures( new Line (0,0, 10,0) );//(Line) new XStream().fromXML( new File( "/home/twak/data/regent/March_30/congo/1/line.xml" ) ));
//		ImageFeatures imf = new ImageFeatures();// FeatureCache.readFeatures( new File( "/home/twak/data/regent/March_30/congo/1/0" ), mf );
//		imf.mega = mf;
		HalfMesh2.Builder builder = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		
		double accumWidth = 0;
		
		for ( int i = 0; i < num; i++ ) {
			
			double width = Math.random() * 4 + 6;
			double height = Math.random() * 6 + 6;
			
			double[] minMax = new double[] { 0, 5 + Math.random() *4, accumWidth, accumWidth + width };
			
			System.out.println( "start: "+accumWidth +" end: "+ (accumWidth + width ) );
			
			accumWidth += width + 0.5;
			
			builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z ) );
			builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z ) );
			builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z ) );
			builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z ) );
			
			HalfFace f = builder.newFace();

			Prof p1 = new Prof(), p2 = new Prof();

			p1.add( new Point2d( 0, 0 ) );
			p1.add( new Point2d( 0, height ) );
			p1.add( new Point2d( -5, height + 5 ) );

			p2.add( new Point2d( 0, 0 ) );
			p2.add( new Point2d( 0, height ) );

			Prof[] ps = new Prof[] { p1, p2 };

			int count = 0;

			for ( HalfEdge e : f ) {
				SuperEdge se = (SuperEdge) e;

				se.prof = ps[ count % ps.length ];

				MiniFacade mini = newMini( null, se.length() );

				if ( count == 0 ) {
					se.addMini( mini );

					se.toEdit = mini;

					if ( count == 0 )
						se.addMini( mini );
				}
				count++;
			}

			SuperFace sf = (SuperFace) f;
			sf.maxProfHeights = new ArrayList();
			sf.maxProfHeights.add( Double.valueOf( 100 ) );
			sf.height = 100;
		}

		HalfMesh2 mesh = builder.done();
		SkelGen sg = new SkelGen( mesh, tweed, null );
		tweed.frame.addGen( sg, true );
		
		 tweed.setTool( new TextureTool( tweed ) );
		
	}
	
	private MiniFacade newMini(ImageFeatures imf, double length) {
		
		MiniFacade mini = new MiniFacade();
		mini = new MiniFacade();
		mini.width = length;
		mini.height = 20;
		
		mini.featureGen.put( Feature.WINDOW, new FRect( Feature.WINDOW, Math.random() * mini.width - 3, 5, 3, 3, mini ) );
		mini.featureGen = new CGAMini( mini );

		mini.imageFeatures = imf;
		mini.app.appMode = AppMode.Off;//"tex.jpg";
		mini.app.color = Colourz.to4 ( GreebleSkel.BLANK_WALL );
//		mini.normal = "normal.jpg";
//		mini.spec = "spec.jpg";
		
		return mini;
	}
	
	@Override
	public String getName() {
		return "house tool";
	}
	

	@Override
	public void getUI( JPanel panel ) {

		panel.setLayout( new ListDownLayout() );
		
		panel.add( new AutoSpinner( this, "num", "number of houses", 1, 10 ));
	}

}
