package org.twak.tweed.gen;

import org.twak.mmg.MOgram;
import org.twak.mmg.media.Facade2d;
import org.twak.mmg.media.Facade2d.RenderListener;
import org.twak.mmg.media.MMGGreeble;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.geom.HalfMesh2;
import org.twak.viewTrace.facades.GreebleSkel;

public class MMGSkelGen extends SkelGen {

	public MOgram mogram = null;
	
	public MMGSkelGen() {
		super();
	}
	
	public MMGSkelGen( HalfMesh2 mesh, Tweed tweed, BlockGen blockGen ) {
		super(mesh,tweed, blockGen);
	}
	
	@Override
	protected GreebleSkel newGreebleSkel( Tweed tweed, SuperFace sf ) {
		return mogram == null ? new GreebleSkel( tweed, sf ) : new MMGGreeble( tweed, sf, mogram );
	}
	
	public MOgram ensureMOGram( SuperFace sf ) {

		if (mogram == null)
			mogram = MMGGreeble.createTemplateMOgram();

		((Facade2d) mogram.medium).setRenderListener ( new RenderListener() {
			
			public void doRender( MOgram mogram ) {

				MMGSkelGen.this.mogram = mogram;

				PlanSkeleton skel = calc( sf );
				
				if ( skel != null )
					setSkel( skel, sf );
			}
		} );
		
		return mogram;
	}
}
