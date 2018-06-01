package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.utils.Filez;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.CMPLabel;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.NormSpecGen;

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
	public void setTexture( MiniFacade mf, FacState<MiniFacade> state, BufferedImage cropped ) {
		
		NormSpecGen ns = renderLabels( mf, cropped );
		BufferedImage[] maps = new BufferedImage[] { cropped, ns.spec, ns.norm };

		NetInfo ni = NetInfo.get( this );

		String fileName = "scratch/" + UUID.randomUUID() + ".png";

		DRectangle mfBounds = Pix2Pix.findBounds( mf, false );
		
		try {
			for ( FRect f : mf.featureGen.get( Feature.WINDOW ) ) {

				DRectangle d = new DRectangle( 0, 0, maps[ 0 ].getWidth(), maps[ 0 ].getHeight() ).
						transform( mfBounds.normalize( f ) );

				d.y = maps[ 0 ].getHeight() - d.y - d.height;

				File wf = new File( Tweed.DATA + "/" + f.app.texture );
				
				if (!wf.exists())
					continue;
				
				BufferedImage[] windowMaps = 
						new BufferedImage[] {
								ImageIO.read( wf ), 
								ImageIO.read( Filez.extTo( wf, "_spec.png" ) ), 
								ImageIO.read( Filez.extTo( wf, "_norm.png" ) ) };

				for ( int i = 0; i < 3; i++ ) {

					Graphics2D tpg = maps[ i ].createGraphics();
					tpg.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
					tpg.drawImage( windowMaps[ i ], (int) d.x, (int) d.y, (int) d.width, (int) d.height, null );
					tpg.dispose();
				}
			}

			ImageIO.write( maps[ 0 ], "png", new File( Tweed.DATA + "/" + fileName ) );
			ImageIO.write( maps[ 1 ], "png", new File( Tweed.DATA + "/" + Filez.extTo( fileName, "_spec.png" ) ) );
			ImageIO.write( maps[ 2 ], "png", new File( Tweed.DATA + "/" + Filez.extTo( fileName, "_norm.png" ) ) );

		} catch ( IOException e1 ) {
			e1.printStackTrace();
		}
		
		
		mf.app.textureUVs = TextureUVs.SQUARE;
		mf.app.texture = fileName;
	}

	private NormSpecGen renderLabels( MiniFacade mf, BufferedImage cropped ) {
		BufferedImage labels = new BufferedImage( cropped.getWidth(), cropped.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
		
		DRectangle bounds = Pix2Pix.findBounds( mf, false );
		DRectangle cropRect = new DRectangle(cropped.getWidth(), cropped.getHeight()); 
		
		Graphics2D g = labels.createGraphics();
		
		g.setColor( CMPLabel.Facade.rgb );
		
		Stroke stroke = new BasicStroke( 3 );
		
		g.setStroke( stroke );
		
		for ( Loop<? extends Point2d> l : mf.postState.wallFaces ) {
			
			Polygon p = Pix2Pix.toPoly( mf, cropRect, bounds, l ) ; 
			
			g.fill( p );
			g.draw( p );
		}
		
		Pix2Pix.cmpRects( mf, g, bounds,  cropRect, CMPLabel.Window.rgb, new ArrayList<>( mf.postState.generatedWindows ) );
		
		for (Feature f : mf.featureGen.keySet())
			if (f != Feature.WINDOW)
				Pix2Pix.cmpRects( mf, g, bounds,  cropRect, f.color, mf.featureGen.get(f) );
		
		g.dispose();
		
		NormSpecGen ns = new NormSpecGen( cropped, labels, FacadeTexApp.specLookup );
		return ns;
	}
	
	public void drawCoarse( MultiMap<MiniFacade, FacState> todo, MiniFacade mf ) throws IOException {
		
		BufferedImage src = ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) );

		DRectangle mini = Pix2Pix.findBounds( mf, false );
		
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
		
		{
			DRectangle dest = new DRectangle(overlap, overlap, w, h);
			
			g.setColor( new Color (255, 255, 255, 50) );
			for (FRect s : mf.featureGen.get( Feature.CORNICE )) {
				DRectangle draw = dest.transform( mini.normalize( s ) );
				draw.y = bigCoarse.getHeight()  - draw.y - draw.height;
				g.fillRect( (int) draw.x, (int)draw.y, (int)draw.width, (int) draw.height );
			}
			
			g.setColor( new Color (78, 51, 51, 50) );
			for (FRect s : mf.featureGen.get( Feature.SILL )) {
				DRectangle draw = dest.transform( mini.normalize( s ) );
				draw.y = bigCoarse.getHeight()  - draw.y - draw.height;
				g.fillRect( (int) draw.x, (int)draw.y, (int)draw.width, (int) draw.height );
			}
			
			g.setColor( new Color (78, 51, 51, 50) );
			for (FRect s : mf.featureGen.get( Feature.MOULDING )) {
				DRectangle draw = dest.transform( mini.normalize( s ) );
				draw.y = bigCoarse.getHeight()  - draw.y - draw.height;
				g.fillRect( (int) draw.x, (int)draw.y, (int)draw.width, (int) draw.height );
			}
			
			
		}

		g.dispose();
		
		FacState state = new FacState( bigCoarse, mf, mini, null );
		
		for (int x =0; x <= w / tileWidth; x ++)
			for (int y =0; y <= h / tileWidth; y ++)
				state.nextTiles.add( new TileState( state, x, y ) );

		todo.put( mf, state );
	}
}
