package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.Tweed;
import org.twak.utils.Imagez;
import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.NormSpecGen;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public abstract class SuperSuper <A extends HasApp> extends App implements HasApp {

	App parent;
	public double scale = 120;
	
	final static int overlap = 20, tileWidth = 256 - overlap * 2, outputPPM = 40, MAX_CONCURRENT = 32;
	
	public SuperSuper( App parent ) {
		super( (HasApp) null );
		this.hasA = this;
		this.parent = parent;
	}

	public SuperSuper( SuperSuper facadeCoarse ) {
		super( (App) facadeCoarse );
	}

	@Override
	public App getUp() {
		return (App) parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public abstract App copy();

	// add to the todo list; remember to pad coarse by overlap in all directions
	public abstract void drawCoarse( MultiMap<A, FacState> todo, A mf ) throws IOException;
	
	public abstract DRectangle boundsInMeters( FacState<A> state);

	public abstract void setTexture( A mf, String dest );
	
	public abstract double[] getZFor( A a );
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "scale", "scale", 20, 200 ) {
			public void updated(double value) {
//				System.out.println( globalUpdate );
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		MultiMap<A, FacState> todo = new MultiMap<>();
		
		for (App a : batch)
		{
			SuperSuper fs = (SuperSuper ) a;
			A mf = (A) fs.parent.hasA;
			
			try {
				drawCoarse( todo, mf );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, whenDone );
	}

	
	private synchronized void facadeContinue( MultiMap<A, FacState> todo, Runnable whenDone ) {

		if (todo.isEmpty()) {
			whenDone.run();
			return;
		}

		Pix2Pix p2 = new Pix2Pix ( NetInfo.get(this) );
		
		int count = 0;
		for (A a : todo.keySet() ) 
			for (FacState<A> state : todo.get(a)) {
			try {

				while ( count < MAX_CONCURRENT && !state.nextTiles.isEmpty() ) {
					
					TileState ts = state.nextTiles.remove( 0 );
					
					{
						BufferedImage toProcess = new BufferedImage( 256, 256, BufferedImage.TYPE_3BYTE_BGR );
						
						Graphics2D g = toProcess.createGraphics();
						
						g.drawImage( state.bigCoarse, 
								- tileWidth * ts.nextX ,
								- tileWidth * ts.nextY ,
								null );
						g.dispose();
						
						ts.coarse = toProcess;
						
						p2.addInput( toProcess, null, null, ts, getZFor( a ), null );
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
								
								rgb = createAlphaFeather (5, overlap-5, rgb);
								
								Graphics2D g = state.bigFine.createGraphics();
								
								g.drawImage( rgb, //ts.coarse, 
										
										tileWidth *  ts.nextX    ,
										tileWidth *  ts.nextY    ,
										tileWidth * (ts.nextX+1) + overlap * 2,
										tileWidth * (ts.nextY+1) + overlap * 2,
										
										0,0,rgb.getWidth(), rgb.getHeight(),
										
										null );
										
//								g.setStroke( new BasicStroke( 4 ) );
//								g.drawRect( tileWidth * ts.nextX     + overlap,
//										tileWidth * ts.nextY     + overlap,
//										tileWidth, tileWidth );
								
								g.dispose();
							}
							
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
					
					MultiMap<A, FacState> nextTime = new MultiMap<>();
					
					// finished - import texture!
					for (A mf : new ArrayList<> (todo.keySet())) 
						
						for (FacState<A> state : todo.get (mf) ) {
						
						if (!state.nextTiles.isEmpty()) {
							nextTime.put( mf, state );
						}
						else {
							
							DRectangle mfb = boundsInMeters( state );
							
							int dim = Mathz.nextPower2( (int)  Math.max( mfb.width * outputPPM, mfb.height * outputPPM)  );
							
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
								
								setTexture( mf, dest );
								
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
//		BufferedImage withAlpha = new BufferedImage( rgb.getWidth(), rgb.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
		

//		Graphics2D g = withAlpha.createGraphics();
//		g.drawImage( rgb, 0, 0, rgb.getWidth(), rgb.getHeight(), null );
//		g.dispose();

//		if (false)
		for (int x = 0; x < rgb.getWidth(); x++)
			for (int y = 0; y < rgb.getHeight(); y++) {
				
				int col = rgb.getRGB( x, y );
				
				int alpha = Math.min( fade(x, rgb.getWidth(), nothing, linear), fade(y, rgb.getHeight(), nothing,linear) ) ;
				
//				System.out.println(alpha);
//				(255- + 
				col = col & 0xffffff | (alpha << 24 );
//						0x77 << 24 +
//					  alpha << 16 +
//					  alpha << 8 +
//					  alpha << 0;
				
				withAlpha.setRGB( x, y, col );
			}
		
		return withAlpha;
	}
	
	private static int fade( int x, int max, int nothing, int linear ) {
		
		int middle = max / 2;
		
		return Mathz.clamp ( (-Math.abs ( -x + middle ) + (middle - nothing)) * 255 / linear, 0, 255 );
	}

	static class FacState<A> {
		
		public A mf;

		BufferedImage bigCoarse, bigFine;
		
		public List<TileState> nextTiles = new ArrayList<>();
		
		public FacState( BufferedImage coarse, 	A mf ) {
			
			this.bigCoarse = coarse;
			this.mf = mf;
			this.bigFine = Imagez.clone( coarse );
		}
	}
	
	static class TileState {
		
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
