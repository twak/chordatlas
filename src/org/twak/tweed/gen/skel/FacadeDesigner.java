package org.twak.tweed.gen.skel;

import java.awt.Color;
import java.util.stream.Collectors;

import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacadePainter;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.FacadeGreebleApp;
import org.twak.viewTrace.franken.FacadeTexApp;

public class FacadeDesigner {
	
	static Plot p;
	
	public static void close() {
		p.closeLast();
	}
	
	public FacadeDesigner (MiniFacade mf,Runnable update) {

		FacadeGreebleApp fga = mf.facadeGreebleApp;
		fga.appMode = AppMode.Manual;
		
		FacadeTexApp ma = mf.facadeTexApp;
		
//		if ( ma.appMode == AppMode.Manual )
//			mf.groundFloorHeight = 2;
			
		Changed c = new Changed() {
			@Override
			public void changed() {
				update.run();
			}
		};
		
		DRectangle bounds = mf.getAsRect();
		bounds.y -= bounds.height;
		
		FacadeTexApp fta = mf.facadeTexApp;
		
		PaintThing.debug.clear();
		
		if (fta.postState != null) {
			PaintThing.debug( Color.lightGray, 1f, MiniFacadePainter.yFlip ( fta.postState.roofFaces ) );
			PaintThing.debug( Color.gray, 1f, MiniFacadePainter.yFlip ( fta.postState.wallFaces ) );
		}
		
		PaintThing.setBounds( bounds );
		p = new Plot( mf );
		
		p.addEditListener( c );
	}
	
	public FacadeDesigner( PlanSkeleton skel, SuperFace sf, SuperEdge se, SkelGen sg ) {

		if ( se.toEdit == null ) {
			sg.ensureMF( sf, se );
		}
		
		FacadeTexApp ma =se.toEdit.facadeTexApp;
		
		if ( ma.appMode == AppMode.Manual )
			se.toEdit.groundFloorHeight = 2;
		else {
			SkelGen.patchWallTag (skel, se, se.toEdit);
			se.toEdit.width = se.length();
		}
		
		if (se.toEdit.featureGen instanceof CGAMini) { // de-proecuralize before editing 
			((CGAMini) se.toEdit.featureGen).update();
			se.toEdit.featureGen = new FeatureGenerator( se.toEdit, se.toEdit.featureGen );
		}
		
		Changed c = new Changed() {

			@Override
			public void changed() {

				PaintThing.debug.clear();

				sg.tweed.enqueue( new Runnable() {
					@Override
					public void run() {
							
							sg.setSkel( skel, sf );
							sg.tweed.getRootNode().updateGeometricState();
							
							sg.block.faces.stream().map( x -> (SuperFace) x ).collect(Collectors.toSet() ).stream().
								forEach( x -> new GreebleSkel( null, x ).
										showSkeleton( x.skel.output, null, x.mr ) );
							
						if ( ma.appMode == AppMode.Net ) // needs prior setSkel to compute visible windows.
							sg.updateTexture( sf, new Runnable() {
								@Override
								public void run() {
									sg.tweed.enqueue( new Runnable() {
										@Override
										public void run() {
											sg.setSkel( skel, sf );
											sg.tweed.getRootNode().updateGeometricState();
										}
									} );

								}
							} );
					}
				} );
			}
		};
		
		p = new Plot( se.toEdit );
		
//		c.changed();
//		p.addEditListener( c );
	}
}
