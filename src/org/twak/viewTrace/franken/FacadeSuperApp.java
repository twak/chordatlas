package org.twak.viewTrace.franken;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.twak.tweed.Tweed;
import org.twak.utils.collections.MultiMap;
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

	public double[] getZFor( MiniFacade e ) {
		return e.app.zuper.styleZ;
	}

	@Override
	public DRectangle boundsInMeters( FacState<MiniFacade> a ) {
		return Pix2Pix.findBounds( a.mf );
	}

	@Override
	public void setTexture( MiniFacade mf, String dest ) {
		mf.app.textureUVs = TextureUVs.SQUARE;
		mf.app.texture = dest + ".png";
	}
	
	
	public void drawCoarse( MultiMap<MiniFacade, FacState> todo, MiniFacade mf ) throws IOException {
		
		BufferedImage src = ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) );

		DRectangle mini = Pix2Pix.findBounds( mf );
		
		int 
			outWidth  =   (int) Math.ceil ( ( mini.width  * scale ) / tileWidth ) * tileWidth, // round to exact tile multiples
			outHeight =   (int) Math.ceil ( ( mini.height * scale ) / tileWidth ) * tileWidth;
				
		BufferedImage bigCoarse = new BufferedImage(
				outWidth  + overlap * 2,
				outHeight + overlap * 2, BufferedImage.TYPE_3BYTE_BGR );

		Graphics2D g = bigCoarse.createGraphics();
		g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
		g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );

		int 
			w = bigCoarse.getWidth()  - 2 * overlap, 
			h = bigCoarse.getHeight() - 2 * overlap;
		
		for ( int wi = -1; wi <= 1; wi++ )
			for ( int hi = -1; hi <= 1; hi++ )
				g.drawImage( src, 
						overlap + wi * w, overlap + hi * h, 
						w, h, null );

		g.dispose();
		
		FacState state = new FacState( bigCoarse, mf );
		
		for (int x =0; x <= w / tileWidth; x ++)
			for (int y =0; y <= h / tileWidth; y ++)
				state.nextTiles.add( new TileState( state, x, y ) );

		todo.put( mf, state );
	}
}
