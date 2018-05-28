package org.twak.viewTrace.franken;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;

public class RoofSuperApp extends SuperSuper <MiniRoof> implements HasApp {

	RoofTexApp parent;
	
	public RoofSuperApp( RoofTexApp parent ) {
		super( parent );
		this.hasA = this;
		this.parent = parent;
	}

	public RoofSuperApp( RoofSuperApp o ) {

		super( (SuperSuper) o );

		this.parent = o.parent;
	}

	@Override
	public App copy() {
		return new RoofSuperApp( this );
	}

	@Override
	public void drawCoarse( BufferedImage src, Graphics2D g, int w, int h ) {
				for ( int wi = -1; wi <= 1; wi++ )
					for ( int hi = -1; hi <= 1; hi++ )
						g.drawImage( src, 
								overlap + wi * w, overlap + hi * h, 
								w, h, null );
	}

	public double[] getZFor( Map.Entry<MiniRoof, FacState> e ) {
		return e.getKey().app.zuper.styleZ;
	}

	@Override
	public DRectangle boundsInMeters( MiniRoof a ) {
		return null; // Pix2Pix.findBounds( a );
	}

	@Override
	public void setTexture( MiniRoof mf, String dest ) {
		mf.app.textureUVs = TextureUVs.SQUARE;
		mf.app.texture = dest + ".png";
	}
}
