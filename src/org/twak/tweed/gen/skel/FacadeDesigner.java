package org.twak.tweed.gen.skel;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JToggleButton;

import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener.Changed;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.FileDrop;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.FeatureGenerator;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.NSliders;
import org.twak.viewTrace.facades.Pix2Pix;

public class FacadeDesigner {
	
	
	public FacadeDesigner( PlanSkeleton skel, SuperFace sf, SuperEdge se, SkelGen sg ) {

		JToggleButton texture = new JToggleButton( "textured" );
		texture.setSelected( se.toEdit != null && se.toEdit.texture != null );
		
		if ( se.toEdit == null ) {
			SkelGen.ensureMF( sf, se );
			if ( !texture.isSelected() )
				se.toEdit.groundFloorHeight = 2;
		}

		if ( texture.isSelected() )  {
			SkelGen.patchWallTag (skel, se, se.toEdit);
			se.toEdit.width = se.length();
		}
		else
			se.toEdit.texture = null;
		
		if (se.toEdit.featureGen instanceof CGAMini) { // de-proecuralize before editing 
			((CGAMini) se.toEdit.featureGen).update();
			se.toEdit.featureGen = new FeatureGenerator( se.toEdit, se.toEdit.featureGen );
		}
		
		double[] z;
		
		FeatureGenerator gf = (FeatureGenerator) se.toEdit.featureGen;
		if ( gf.facadeStyle != null )
			z = gf.facadeStyle;
		else
			z = gf.facadeStyle = new double[Pix2Pix.LATENT_SIZE];
		
		List<MiniFacade> sameStyle = new ArrayList();
		for (HalfEdge e : sf ){
			SuperEdge se2 = (SuperEdge)e;
			if ( se2.toEdit.featureGen.facadeStyle == z)
				sameStyle.add( se2.toEdit );
		}
		
		Changed c = new Changed() {

			@Override
			public void changed() {

				PaintThing.debug.clear();
				if ( texture.isSelected() )
					new Thread( new Runnable() {
						@Override
						public void run() {
							new Pix2Pix().facade( sameStyle, z, new Runnable() {

								public void run() {
									sg.tweed.enqueue( new Runnable() {
										@Override
										public void run() {
											sg.setSkel( skel, sf, sg.lastOccluders );
											sg.tweed.getRootNode().updateGeometricState();
										}
									} );
								}
							} );
						}
					} ).start();
				else {
					
					se.toEdit.texture = null;
					
					sg.tweed.enqueue( new Runnable() {
						@Override
						public void run() {
							sg.setSkel( skel, sf, sg.lastOccluders );
						}
					} );
				}
			}
		};

		NSliders sliders = new NSliders(z, c);
		
		FileDrop drop = new FileDrop( "style" ) {
			public void process(java.io.File f) {
				new Pix2Pix().encode( f, z, new Runnable() {
					@Override
					public void run() {
						sliders.setValues( z );
					}
				} );
			};
		};
		
		Plot p = new Plot( se.toEdit, texture, sliders, drop );
		
		texture.addActionListener( l -> c.changed() );
		
		c.changed();
		p.addEditListener( c );
	}
}
