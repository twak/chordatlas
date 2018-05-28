package org.twak.viewTrace.franken;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.twak.camp.Output.Face;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;

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

	public double[] getZFor( MiniRoof e ) {
		return e.app.zuper.styleZ;
	}

	@Override
	public DRectangle boundsInMeters( FacState<MiniRoof> a ) {
		return null; // Pix2Pix.findBounds( a );
	}

	@Override
	public void setTexture( MiniRoof mf, String dest ) {
		
		mf.app.textureUVs = TextureUVs.ZERO_ONE;
		mf.app.texture = dest + ".png";
		
	}
	
	@Override
	public void drawCoarse( MultiMap<MiniRoof, FacState> todo, MiniRoof mr ) throws IOException {
		
		BufferedImage src = ImageIO.read( Tweed.toWorkspace( parent.coarse ) );

		DRectangle imageBounds = textureRect;
		
		for ( Loop<Point2d> verticalPts : mr.getAllFaces() ) {
			
			Face origin = mr.origins.get( verticalPts );
			
			
			/**
			 *  is defining edg first in face?
			 *  get defining edge in face find rotation + translation to bring to (overlap,overlap) at bottom of bigCoarse
			 *  use Face slope to find y-scale factor cos(theta); multiply by coarse scale
			 *   
			 *  draw to new image, create UV pipeline to use per-pitch UVs with a app.textureRect
			 * 
			 * 
			 */
			
			double[] bounds = Loopz.minMax2d( verticalPts ); // needs to be bounds in 3d
			
			int 
				outWidth  =   (int) Math.ceil ( ( (bounds[1] - bounds[0] ) * scale ) / tileWidth ) * tileWidth, // round to exact tile multiples
				outHeight =   (int) Math.ceil ( ( (bounds[3] - bounds[2] ) * scale ) / tileWidth ) * tileWidth;
					
			BufferedImage bigCoarse = new BufferedImage(
					outWidth  + overlap * 2,
					outHeight + overlap * 2, BufferedImage.TYPE_3BYTE_BGR );
	
			Graphics2D g = bigCoarse.createGraphics();
			g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
			g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
	
			int 
				w = bigCoarse.getWidth()  - 2 * overlap, 
				h = bigCoarse.getHeight() - 2 * overlap;
			
			// rotate src to match face
			// scale src to account for rotation
			// draw with <overlap> padding into bigCoarse
			
			
//			g.drawImage (src)
			
			for ( int wi = -1; wi <= 1; wi++ )
				for ( int hi = -1; hi <= 1; hi++ )
					g.drawImage( src, 
							overlap + wi * w, overlap + hi * h, 
							w, h, null );
	
			g.dispose();
			
			FacState state = new FacState( bigCoarse, mr );
			
			for (int x =0; x <= w / tileWidth; x ++)
				for (int y =0; y <= h / tileWidth; y ++)
					state.nextTiles.add( new TileState( state, x, y ) );
	
			todo.put( mr, state );
		}
	}

}
