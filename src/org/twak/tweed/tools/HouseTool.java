package org.twak.tweed.tools;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;

import org.twak.siteplan.campskeleton.Profile;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.SkelGen;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.HalfMesh2;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.utils.HalfMesh2.HalfFace;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class HouseTool extends Tool {

	public HouseTool( Tweed tweed ) {
		super( tweed );
	}
	
	@Override
	public void clickedOn( Spatial target, Vector3f loc, Vector2f cursorPosition ) {

		MiniFacade mini;
		
//		{
//			MegaFeatures mf = new MegaFeatures((Line) new XStream().fromXML( new File( "/home/twak/data/regent/March_30/congo/1/line.xml" ) ));
//			ImageFeatures imf = FeatureGen.readFeatures( new File( "/home/twak/data/regent/March_30/congo/1/0" ), mf );
//			mini = imf.miniFacades.get( 2 );
//		}
		{
			mini = new MiniFacade();
			mini.width = 30;
			mini.height = 20;
			mini.rects.put( Feature.WINDOW, new FRect( 5, 5, 3, 3 ) );
		}
		
		double[] minMax = new double[] {0, 20, 0, 20};
		HalfMesh2.Builder builder = new HalfMesh2.Builder( SuperEdge.class, SuperFace.class );
		builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 3 ] + loc.z ) );
		builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 3 ] + loc.z ) );
		builder.newPoint( new Point2d( minMax[ 1 ] + loc.x, minMax[ 2 ] + loc.z ) );
		builder.newPoint( new Point2d( minMax[ 0 ] + loc.x, minMax[ 2 ] + loc.z ) );
		builder.newFace ();

		HalfMesh2 mesh = builder.done();

		
		Prof p = new Prof();
		
		p.add( new Point2d (0,0) );
		p.add( new Point2d (0,20) );
		p.add( new Point2d (-5,25) );
		
		
		boolean first = true;
		
		for (HalfFace f : mesh) {
			for (HalfEdge e : f) {
				SuperEdge se = (SuperEdge)e;
				
				se.prof = p;
				
				if ( first )
					se.addMini( mini );
				
				first = false;
				
			}

			SuperFace sf = (SuperFace)f;
			sf.maxProfHeights = new ArrayList();
			sf.maxProfHeights.add( Double.valueOf( 100 ) );
			sf.height = 100;
		}
		SkelGen sg = new SkelGen( mesh, tweed, null );
		tweed.frame.addGen( sg, true );
	}
	
	@Override
	public String getName() {
		return "house";
	}

}
