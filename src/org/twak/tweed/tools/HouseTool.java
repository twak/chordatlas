package org.twak.tweed.tools;

import java.util.ArrayList;

import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.tweed.gen.Prof;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.Line;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
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

		MegaFeatures mf = new MegaFeatures( new Line (0,0, 10,0) );//(Line) new XStream().fromXML( new File( "/home/twak/data/regent/March_30/congo/1/line.xml" ) ));
		ImageFeatures imf = new ImageFeatures();// FeatureCache.readFeatures( new File( "/home/twak/data/regent/March_30/congo/1/0" ), mf );
		imf.mega = mf;	
		
		double[] minMax = new double[] {0, 15, 0, 25};
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
				MiniFacade mini = newMini(imf, se.length());
				
				if ( true ) {
					se.addMini( mini );
					se.proceduralFacade = mf;

					se.toEdit = mini;

					if ( first )
						se.addMini( mini );
				}
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
	
	private MiniFacade newMini(ImageFeatures imf, double length) {
		
		MiniFacade mini = new MiniFacade();
		mini = new MiniFacade();
		mini.width = length;
		mini.height = 20;
		mini.rects.put( Feature.WINDOW, new FRect( Feature.WINDOW, Math.random() * mini.width - 3, 5, 3, 3 ) );
		mini.color = new double[] {0.8,0.8,0.3,1};
		mini.imageFeatures = imf;
		mini.texture = null;//"tex.jpg";
//		mini.normal = "normal.jpg";
//		mini.spec = "spec.jpg";
		return mini;
	}
	
	@Override
	public String getName() {
		return "proc-ex house";
	}

}
