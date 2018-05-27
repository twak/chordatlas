package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.hsqldb.lib.HashSet;
import org.twak.tweed.Tweed;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.Imagez;
import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.NormSpecGen;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeSuperApp extends App implements HasApp {

	FacadeTexApp parent;
	public double scale = 120;
	
	public FacadeSuperApp( FacadeTexApp parent ) {
		super( (HasApp) null );
		this.hasA = this;
		this.parent = parent;
	}

	public FacadeSuperApp( FacadeSuperApp facadeCoarse ) {
		super( (App) facadeCoarse );
	}

	@Override
	public App getUp() {
		return parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
		return new FacadeSuperApp( this );
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "scale", "scale", 20, 200 ) {
			public void updated(double value) {
				System.out.println( globalUpdate );
//				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		Map<MiniFacade, FacState> todo = new LinkedHashMap();
		
		FacadeSuperApp fs = (FacadeSuperApp ) batch.get( 0 );
		
		MiniFacade mf = (MiniFacade) fs.parent.hasA;
		
		{
			try {

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) );

				DRectangle mini = Pix2Pix.findBounds( mf );
				
				BufferedImage bigCoarse = new BufferedImage( 
						(int)( mini.width * scale + overlap * 2),
						(int)( mini.height * scale + overlap * 2 ), BufferedImage.TYPE_3BYTE_BGR );

				Graphics2D g = bigCoarse.createGraphics();
				g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
				g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );

				int w = bigCoarse.getWidth() - 2 * overlap, h = bigCoarse.getHeight() - 2 * overlap;

				for ( int wi = -1; wi <= 1; wi++ )
					for ( int hi = -1; hi <= 1; hi++ )
						g.drawImage( src, 
								overlap + wi * w, overlap + hi * h, 
								w, h, null );

//				g.drawImage( src, overlap, overlap, bigCoarse.getWidth() - 2 * overlap, bigCoarse.getHeight() - 2 * overlap, null );

				g.dispose();
				
				FacState state = new FacState( bigCoarse, mf );
				
				for (int x =0; x <= w / tileWidth; x ++)
					for (int y =0; y <= h / tileWidth; y ++)
						state.nextTiles.add( new TileState( state, x, y ) );

				todo.put( mf, state );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, whenDone );
	}
	
	final static int overlap = 20, tileWidth = 256 - overlap * 2; 
	int MAX_CONCURRENT = 32;
	
	private synchronized void facadeContinue( Map<MiniFacade, FacState> todo, Runnable whenDone ) {

		if (todo.isEmpty()) {
			whenDone.run();
			return;
		}


		Pix2Pix p2 = new Pix2Pix ( NetInfo.get(this) );
		
		int count = 0;
		for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
			try {

				FacState state = e.getValue();
				
				while ( count < MAX_CONCURRENT && !state.nextTiles.isEmpty() ) {
					
					TileState ts = state.nextTiles.remove( 0 );
					
					{
						BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );
						
						Graphics2D g = toProcess.createGraphics();
						g.drawImage( state.bigCoarse, 
								- tileWidth * ts.nextX  + 256,
								- tileWidth * ts.nextY ,
								null );
						g.dispose();
						
						ts.coarse = toProcess.getSubimage( 256, 0, 256, 256 );
						
						p2.addInput( toProcess, ts, e.getKey().app.zuper.styleZ );
					}
					count++;
				}
								
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
			
		p2.submit ( new Job ( new JobResult() {
				@Override
				public void finished( Map<Object, File> results ) {
					
					
					// patch images with new tiles...
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						TileState ts = (TileState) e.getKey();
						FacState state = ts.state;
						
						try {
							
							File texture = e.getValue();
							
							if ( texture.exists() && texture.length() > 0 ) {

								BufferedImage rgb = ImageIO.read( texture );
								
//								rgb = createAlphaFeather (0, overlap, rgb);
								
								Graphics2D g = state.bigFine.createGraphics();
								
								g.drawImage( rgb, //ts.coarse, 
										
										tileWidth * ts.nextX     + overlap,
										tileWidth * ts.nextY     + overlap,
										tileWidth * (ts.nextX+1) + overlap,
										tileWidth * (ts.nextY+1) + overlap,
										
										overlap,
										overlap,
										tileWidth + overlap,
										tileWidth + overlap,
										
										null );
										
								g.setStroke( new BasicStroke( 4 ) );
								g.drawRect( tileWidth * ts.nextX     + overlap,
										tileWidth * ts.nextY     + overlap,
										tileWidth, tileWidth );
								
								g.dispose();
							}
							
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
					
//					update.run();
					
					Map<MiniFacade, FacState> nextTime = new HashMap<>();
					
					// finished - import texture!
					for (MiniFacade mf : new ArrayList<> (todo.keySet())) {
						
						FacState state = todo.get( mf );
						
						if (!state.nextTiles.isEmpty()) {
							nextTime.put( mf, state );
						}
						else {
							
							DRectangle mfb = Pix2Pix.findBounds( mf );
							
							int dim = Mathz.nextPower2( (int)  Math.max( mfb.width * 40, mfb.height * 40)  );
							
							try {
								ImageIO.write( state.bigFine, "png", new File ("/home/twak/Desktop/blah_fine.png") );
								ImageIO.write( state.bigCoarse, "png", new File ("/home/twak/Desktop/blah_coarse.png") );
								ImageIO.write( ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) ), "png", new File ("/home/twak/Desktop/blah_low.png") );
							} catch ( IOException e2 ) {
								e2.printStackTrace();
							}
							
							BufferedImage cropped = new BufferedImage( dim, dim, BufferedImage.TYPE_3BYTE_BGR );
							{
								Graphics2D g = cropped.createGraphics();
								
								g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
								g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
								
								g.drawImage( state.bigFine,
										0, 0,
										cropped.getWidth(), cropped.getHeight(), 
										overlap, overlap,
										state.bigFine.getWidth() - overlap, state.bigFine.getHeight() - overlap, null );
							}
							
							NormSpecGen ns = new NormSpecGen( cropped, null, null );

							String dest = "scratch/"+System.nanoTime() +"_"+ Math.random();
							
							try {
								ImageIO.write( cropped, "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
								ImageIO.write( ns.norm  , "png", new File( Tweed.DATA + "/" + ( dest + "_norm.png" ) ) );
//								ImageIO.write( ns.spec  , "png", new File( Tweed.DATA + "/" + ( dest + "_spec.png" ) ) );
								
								mf.app.textureUVs = TextureUVs.SQUARE;
								mf.app.texture = dest + ".png";
								
							} catch ( IOException e1 ) {
								e1.printStackTrace();
							}
						}
					}

					facadeContinue( nextTime, whenDone );
				}
			} ) );
	}
	
	private BufferedImage createAlphaFeather( int nothing, int linear, BufferedImage rgb ) {
		BufferedImage withAlpha = new BufferedImage( rgb.getWidth(), rgb.getHeight(), BufferedImage.TYPE_4BYTE_ABGR );
		

//		Graphics2D g = withAlpha.createGraphics();
//		g.drawImage( rgb, 0, 0, rgb.getWidth(), rgb.getHeight(), null );
//		g.dispose();

		for (int x = 0; x < rgb.getWidth(); x++)
			for (int y = 0; y < rgb.getHeight(); y++) {
				
				int col = rgb.getRGB( x, y );
				
				int alpha = Math.min( fade(x, rgb.getWidth(), nothing,linear), fade(y, rgb.getHeight(), nothing,linear) ) ;
				
				col = col & 0xFFFFFF + alpha << 24;
				
				withAlpha.setRGB( x, y, col );
			}
		
		return withAlpha;
	}
		
	private int fade( int x, int max, int nothing, int linear ) {
		
		int middle = max / 2;
		
		return Mathz.clamp ( (-Math.abs ( -x + middle ) + (middle - nothing)) * 255 / linear, 0, 255 );
	}

	private static class FacState {
		
		protected MiniFacade mf;

		BufferedImage bigCoarse, bigFine;
		
		public List<TileState> nextTiles = new ArrayList<>();
		
		public FacState( BufferedImage coarse, MiniFacade mf ) {
			
			this.bigCoarse = coarse;
			this.mf = mf;
			this.bigFine = Imagez.clone( coarse );
		}
	}
	
	private static class TileState {
		
		public BufferedImage coarse;
		int nextX = 0, nextY = 0;
		FacState state;
		
		public TileState( FacState state, int x, int y ) {
			this.state = state;
			this.nextX = x;
			this.nextY = y;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
