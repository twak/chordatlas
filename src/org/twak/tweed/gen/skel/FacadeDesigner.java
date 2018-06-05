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
import org.twak.viewTrace.franken.FacadeLabelApp;
import org.twak.viewTrace.franken.App.AppMode;

public class FacadeDesigner {
	
	
	public FacadeDesigner( PlanSkeleton skel, SuperFace sf, SuperEdge se, SkelGen sg ) {

//		JToggleButton texture = new JToggleButton( "textured" );
//		texture.setSelected( se.toEdit != null && se.toEdit.app.texture != null );
		
		if ( se.toEdit == null ) {
			SkelGen.ensureMF( sf, se );
		}
		
		if ( se.toEdit.app.appMode == AppMode.Off )
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
							
							if (se.toEdit.app.appMode == AppMode.Net) // needs prior setSkel to compute visible windows.
							SkelGen.updateTexture( sf, new Runnable() {
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
