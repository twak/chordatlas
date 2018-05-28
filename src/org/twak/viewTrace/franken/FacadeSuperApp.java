package org.twak.viewTrace.franken;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;

public class FacadeSuperApp extends SuperSuper <MiniFacade> implements HasApp {

	FacadeTexApp parent;
	
	public FacadeSuperApp( FacadeTexApp parent ) {
		super( parent );
		this.hasA = this;
		this.parent = parent;
	}

	public FacadeSuperApp( FacadeSuperApp o ) {

		super( (SuperSuper) o );

		this.parent = o.parent;
	}

	@Override
	public App copy() {
		return new FacadeSuperApp( this );
	}

	@Override
	public void drawCoarse( BufferedImage src, Graphics2D g, int w, int h ) {
				for ( int wi = -1; wi <= 1; wi++ )
					for ( int hi = -1; hi <= 1; hi++ )
						g.drawImage( src, 
								overlap + wi * w, overlap + hi * h, 
								w, h, null );
	}

	public double[] getZFor( Map.Entry<MiniFacade, FacState> e ) {
		return e.getKey().app.zuper.styleZ;
	}

	@Override
	public DRectangle boundsInMeters( MiniFacade a ) {
		return Pix2Pix.findBounds( a );
	}

	@Override
	public void setTexture( MiniFacade mf, String dest ) {
		mf.app.textureUVs = TextureUVs.SQUARE;
		mf.app.texture = dest + ".png";
	}
}
