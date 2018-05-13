package org.twak.tweed.gen.skel;

import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.franken.App.AppMode;

public class FacadeDesigner {
	
	
	public FacadeDesigner( PlanSkeleton skel, SuperFace sf, SuperEdge se, SkelGen sg ) {

//		JToggleButton texture = new JToggleButton( "textured" );
//		texture.setSelected( se.toEdit != null && se.toEdit.app.texture != null );
		
		if ( se.toEdit == null ) {
			SkelGen.ensureMF( sf, se );
		}
		
		if ( se.toEdit.app.appMode == AppMode.Color )
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
							sg.setSkel( skel, sf, sg.lastOccluders );
							sg.tweed.getRootNode().updateGeometricState();
						}
					} );
			}
		};
		
		Plot p = new Plot( se.toEdit );
		
		c.changed();
		p.addEditListener( c );
	}
}
