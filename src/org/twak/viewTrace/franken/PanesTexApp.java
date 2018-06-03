package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.utils.Cache;
import org.twak.utils.Filez;
import org.twak.utils.Imagez;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.Pix2Pix.EResult;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class PanesTexApp extends App implements HasApp {

	public PanesLabelApp parent;
	public boolean useCoarseStyle = false;

	public PanesTexApp(PanesLabelApp parent) {
		super( (HasApp) null );
		super.hasA = this;
		this.parent = parent;
		
		if (TweedSettings.settings.sitePlanInteractiveTextures)
			appMode = AppMode.Net;
	}
	
	public PanesTexApp(PanesTexApp t) {
		super ( (App ) t);
		this.parent = t.parent;
		
		if (TweedSettings.settings.sitePlanInteractiveTextures)
			appMode = AppMode.Net;
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel p = new JPanel();
		
		p.add (new AutoCheckbox( this, "useCoarseStyle", "z from facade" ) {
			@Override
			public void updated( boolean selected ) {
				
				for (App a : apps)
					((PanesTexApp)a).useCoarseStyle = selected;
				
				globalUpdate.run();
			}
		});
		
		return p;
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
			return new PanesTexApp( this );
	}
	
	final static Map<Color, Color> specLookup = new HashMap<>();
	static {
		specLookup.put( Color.blue, Color.white );
		specLookup.put( Color.red, Color.darkGray );
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) { // first compute latent variables
		
		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( NetInfo.get(this) );
		

		List<Meta> otherMEta = new ArrayList<>();
		
		for ( App a : batch ) {

			try {

				PanesTexApp pta = (PanesTexApp) a;
				PanesLabelApp pla = pta.parent;

				if ( pla.label == null )
					continue;

				MiniFacade mf = ( (FRect) parent.hasA ).mf;


				DRectangle mfBounds = Pix2Pix.findBounds( mf, false );

				FRect r = (FRect) pla.hasA;

				Meta meta = new Meta( pta, ni.sizeZ, mf, r );
				
				if ( mf.app.texture == null || !pta.useCoarseStyle || !mfBounds.contains( r ) ) {
					
					meta.styleZ = a.styleZ;
					otherMEta.add( meta );
					
				} else {

					BufferedImage src = Imagez.read( new File ( Tweed.DATA+"/"+ mf.app.coarse ) );
					
					DRectangle inSrc = new DRectangle( ni.resolution, ni.resolution ).transform( mfBounds.normalize( r ) );

					inSrc.y = ni.resolution - inSrc.y - inSrc.height;

					BufferedImage toEncode = Imagez.scaleLongest( src.getSubimage( (int) inSrc.x, (int) inSrc.y, (int) inSrc.width, (int) inSrc.height ), 170 );
					toEncode = Imagez.padTo( toEncode, null, 256, 256, Color.black );
					p2.addEInput( toEncode, meta );
				}
				
				
			} catch ( Throwable e1 ) {
				e1.printStackTrace();
			}
		}

		p2.submit( new Job( new EResult() {

			@Override
			public void finished( Map<Object, double[]> results ) {

				List<Meta> next = new ArrayList<>();

				for ( Map.Entry<Object, double[]> e : results.entrySet() ) {
					Meta meta = (Meta) e.getKey();
					next.add( meta );
					meta.styleZ = e.getValue();
				}
				
				for (Meta m : otherMEta) {
					
					double bestArea = Double.MAX_VALUE;
					Meta bestM2 = null;
					
					for (Meta m2 : next) {
						
						double area = Math.abs ( m2.worldLocation.area() - m.worldLocation.area() );
						
						if (area < bestArea) {
							bestM2 = m2;
							bestArea = area;
						}
					}
						
					if (bestM2 != null )
						m.styleZ = bestM2.styleZ;
					
				}
				
				next.addAll( otherMEta );

				computeTextures( whenDone, next );
			}
		} ) );
				
	}
	
	public void computeTextures( Runnable whenDone, List<Meta> batch ) {

		
		NetInfo ni = NetInfo.get(this); 
		Pix2Pix p2 = new Pix2Pix( NetInfo.get(this) );

		BufferedImage lBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D lg =  lBi.createGraphics();
		
		BufferedImage eBi = new BufferedImage( ni.resolution, ni.resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D eg =  eBi.createGraphics();
		
			
		for ( Meta meta : batch ) {

			try {
				
				PanesTexApp pta = (PanesTexApp)meta.pta;
				PanesLabelApp pla = pta.parent;
				
				if (pla.label == null)
					continue;
				
				BufferedImage labelSrc = ImageIO.read( Tweed.toWorkspace( pla.label ) );

				FRect r = (FRect) pla.hasA;
				
				double scale = ( ni.resolution - 2 * PanesLabelApp.pad ) / Math.max( r.width, r.height );
				
				DRectangle imBounds = new DRectangle(r);
				
				imBounds = r.scale( scale );
				imBounds.x = (ni.resolution - imBounds.width) / 2;
				imBounds.y = (ni.resolution - imBounds.height) / 2;

				lg.setColor( Color.black );
				lg.fillRect( 0, 0, ni.resolution, ni.resolution );
				eg.setColor( Color.black );
				eg.fillRect( 0, 0, ni.resolution, ni.resolution );
				
				lg.drawImage( labelSrc, (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height, null);
				eg.setColor( Color.red );
				eg.fillRect( (int) imBounds.x, (int) imBounds.y, (int) imBounds.width, (int)imBounds.height);
				
				meta.imBounds = imBounds;
				
				p2.addInput( lBi, eBi, null, meta, meta.styleZ, pta.parent.frameScale );


			} catch ( IOException e1 ) {
				e1.printStackTrace();
			}
		}
		
		lg.dispose();
		eg.dispose();
		
		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {

				
				Cache<MiniFacade, BufferedImage[]> facadesImages = new Cache<MiniFacade, BufferedImage[]>() {

					@Override
					public BufferedImage[] create( MiniFacade mf ) {
						
						String src;
						
						if (mf.app.coarseWithWindows != null)
							src = mf.app.coarseWithWindows;
						else
							src = mf.app.coarse;
						
						if (src == null)
							return null;
						
						return new BufferedImage[] {
								Imagez.read( new File ( Tweed.DATA+"/"+ src ) ), 
								Imagez.read( new File ( Tweed.DATA+"/"+ Filez.extTo( src, "_spec.png" ) ) ), 
								Imagez.read( new File ( Tweed.DATA+"/"+ Filez.extTo( src, "_norm.png" ) ) ) 
						};
					}
				};
				
				try {
					e:
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						Meta meta = (Meta) e.getKey();
						
						BufferedImage[] maps = new BufferedImage[3];
						
						String dest = Pix2Pix.importTexture( e.getValue(), -1, specLookup, 
								meta.imBounds, null, maps );

						if ( dest != null ) {
							
							FRect frect = ((FRect) meta.pta.parent.hasA );
							MiniFacade mf = frect.mf;
							
							meta.pta.texture = dest;
							meta.pta.parent.texture = dest;
							meta.pta.textureUVs = TextureUVs.SQUARE;
							meta.pta.parent.textureUVs = TextureUVs.SQUARE;
							
							DRectangle d = new DRectangle(0, 0, ni.resolution, ni.resolution).transform( Pix2Pix.findBounds( mf, false ).normalize( frect ) );
							
							d.y = ni.resolution - d.y - d.height;
							
							BufferedImage[] toPatch = facadesImages.get(mf);
							
//							if (mf.postState != null) 
//							for (Point2d p : frect.points()) { 
//								if ( Loopz.inside( p, mf.postState.occluders) )
//									continue e;
//								if ( ! ( Loopz.inside( p, new LoopL<Point2d> ( (List) mf.postState.wallFaces) ) || 
//										 Loopz.inside( p, new LoopL<Point2d> ( (List) mf.postState.roofFaces) ) ) )
//									continue e;
//							}
							
							if (toPatch == null)
								continue;
							
//							if (false)
							for (int i = 0; i < 3; i++ ) {
								Graphics2D tpg = toPatch[i].createGraphics();
								tpg.drawImage( maps[i], (int) d.x, (int) d.y, (int) d.width, (int)d.height, null );
//								tpg.setColor (Color.magenta);
//								tpg.fillRect( (int) d.x, (int) d.y, (int) d.width, (int)d.height );
								tpg.dispose();
							}
						}
							
					}
					
//					if (false)
					for (Map.Entry<MiniFacade, BufferedImage[]> updated : facadesImages.cache.entrySet()) {
						
						if (updated.getValue() == null)
							continue;
						
						String fileName = "scratch/" + UUID.randomUUID() +".png";
						
						BufferedImage[] imgs = updated.getValue();
						
						ImageIO.write( imgs[0], "png", new File(Tweed.DATA + "/" +fileName ) );
						ImageIO.write( imgs[1], "png", new File(Tweed.DATA + "/" + Filez.extTo( fileName, "_spec.png" ) ) );
						ImageIO.write( imgs[2], "png", new File(Tweed.DATA + "/" + Filez.extTo( fileName, "_norm.png" ) )  );
						
						MiniFacade mf = updated.getKey();
						
						mf.app.coarseWithWindows = mf.app.texture = fileName;
//						updated.getKey().app.textureUVs = TextureUVs.Rectangle;
					}
					

				} catch ( Throwable th ) {
					th.printStackTrace();
				} finally {
					whenDone.run();
				}
			}
		} ) );
	}
	
	@Override
	public void finishedBatches( List<App> list, List<App> all ) {
		
		for (App a : list) {
			PanesTexApp pta = (PanesTexApp) a;
			PanesLabelApp pla = pta.parent;

			if ( pla.label == null )
				continue;

			MiniFacade mf = ( (FRect) parent.hasA ).mf;
			mf.app.coarseWithWindows = null;
		}
			
		
		super.finishedBatches( list, all );
	}
	

	private static class Meta {
		protected double[] styleZ;
		PanesTexApp pta;
		DRectangle imBounds;
		MiniFacade mf;
		private FRect worldLocation;
		
		private Meta( PanesTexApp pta, int sizeZ, MiniFacade mf, FRect r ) {
			this.pta = pta;
			this.styleZ = new double[sizeZ];
			this.mf = mf;
			this.worldLocation = r;
		}
	}

	public Enum[] getValidAppModes() {
		return new Enum[] { AppMode.Off, AppMode.Net };
	}
}
