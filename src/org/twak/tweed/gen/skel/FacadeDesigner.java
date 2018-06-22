package org.twak.tweed.gen.skel;

import java.util.stream.Collectors;

import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.FacadeTexApp;

public class FacadeDesigner {
	
	public FacadeDesigner (AppStore ac,MiniFacade mf, Runnable update) {

		FacadeTexApp ma = ac.get (FacadeTexApp.class, mf);
		
		if ( ma.appMode == AppMode.Off )
			mf.groundFloorHeight = 2;
			
		Changed c = new Changed() {
			@Override
			public void changed() {
				update.run();
			}
		};
		
		Plot p = new Plot( mf );
		p.addEditListener( c );
	}
	
	public FacadeDesigner( AppStore ass, PlanSkeleton skel, SuperFace sf, SuperEdge se, SkelGen sg ) {

		if ( se.toEdit == null ) {
			sg.ensureMF( sf, se );
		}
		
		FacadeTexApp ma = ass.get (FacadeTexApp.class, se.toEdit);
		
		if ( ma.appMode == AppMode.Off )
			se.toEdit.groundFloorHeight = 2;
		else {
			SkelGen.patchWallTag (skel, se, se.toEdit);
			se.toEdit.width = se.length();
		}
		
		if (se.toEdit.featureGen instanceof CGAMini) { // de-proecuralize before editing 
			((CGAMini) se.toEdit.featureGen).update(ass);
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
								forEach( x -> new GreebleSkel( null, ass, x ).
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
		
		Plot p = new Plot( se.toEdit );
		
//		c.changed();
//		p.addEditListener( c );
	}
}
